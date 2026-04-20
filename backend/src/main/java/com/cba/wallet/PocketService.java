package com.cba.wallet;

import com.cba.account.Account;
import com.cba.account.AccountRepository;
import com.cba.common.exception.CbaException;
import com.cba.customer.CustomerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PocketService {

    private final PocketRepository pocketRepository;
    private final PocketAccountRepository pocketAccountRepository;
    private final AccountRepository accountRepository;
    private final CustomerRepository customerRepository;

    // ── DTOs ──────────────────────────────────────────────────────────────────

    public record CreatePocketRequest(
            UUID customerId,
            String name,
            String description,
            List<UUID> accountIds
    ) {}

    public record UpdatePocketRequest(String name, String description) {}

    public record LinkRequest(List<UUID> accountIds) {}

    public record PocketResponse(
            UUID id,
            UUID customerId,
            String name,
            String description,
            Pocket.PocketStatus status,
            BigDecimal totalBalance,
            List<LinkedAccountSummary> accounts
    ) {}

    public record LinkedAccountSummary(
            UUID accountId,
            String accountNumber,
            String accountType,
            BigDecimal balance,
            String currency
    ) {}

    // ── Commands ──────────────────────────────────────────────────────────────

    @Transactional
    public PocketResponse createPocket(CreatePocketRequest req) {
        customerRepository.findById(req.customerId())
                .orElseThrow(() -> CbaException.notFound("Customer", req.customerId().toString()));

        Pocket pocket = new Pocket();
        pocket.setCustomerId(req.customerId());
        pocket.setName(req.name());
        pocket.setDescription(req.description());
        pocket = pocketRepository.save(pocket);

        if (req.accountIds() != null && !req.accountIds().isEmpty()) {
            linkAccountsToPocket(pocket, req.customerId(), req.accountIds());
        }
        return toResponse(pocketRepository.findById(pocket.getId()).orElseThrow());
    }

    @Transactional
    public PocketResponse linkAccounts(UUID pocketId, UUID customerId, List<UUID> accountIds) {
        Pocket pocket = getPocketOwnedBy(pocketId, customerId);
        linkAccountsToPocket(pocket, customerId, accountIds);
        return toResponse(pocket);
    }

    @Transactional
    public PocketResponse delinkAccounts(UUID pocketId, UUID customerId, List<UUID> accountIds) {
        Pocket pocket = getPocketOwnedBy(pocketId, customerId);
        accountIds.forEach(accountId ->
                pocketAccountRepository.findByAccountId(accountId).ifPresent(pa -> {
                    if (!pa.getPocket().getId().equals(pocketId)) {
                        throw CbaException.badRequest("ACCOUNT_NOT_IN_POCKET",
                                "Account " + accountId + " does not belong to this pocket");
                    }
                    pocket.getPocketAccounts().remove(pa);
                    pocketAccountRepository.delete(pa);
                })
        );
        return toResponse(pocket);
    }

    @Transactional
    public PocketResponse updatePocket(UUID pocketId, UUID customerId, UpdatePocketRequest req) {
        Pocket pocket = getPocketOwnedBy(pocketId, customerId);
        if (req.name() != null && !req.name().isBlank()) pocket.setName(req.name());
        if (req.description() != null) pocket.setDescription(req.description());
        return toResponse(pocketRepository.save(pocket));
    }

    @Transactional
    public void closePocket(UUID pocketId, UUID customerId) {
        Pocket pocket = getPocketOwnedBy(pocketId, customerId);
        pocket.getPocketAccounts().clear();
        pocket.setStatus(Pocket.PocketStatus.CLOSED);
        pocketRepository.save(pocket);
    }

    // ── Queries ───────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<PocketResponse> listPockets(UUID customerId) {
        return pocketRepository.findActiveByCustomerId(customerId)
                .stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public PocketResponse getPocket(UUID pocketId, UUID customerId) {
        return toResponse(getPocketOwnedBy(pocketId, customerId));
    }

    // ── Internals ─────────────────────────────────────────────────────────────

    private void linkAccountsToPocket(Pocket pocket, UUID customerId, List<UUID> accountIds) {
        for (UUID accountId : accountIds) {
            Account account = accountRepository.findById(accountId)
                    .orElseThrow(() -> CbaException.notFound("Account", accountId.toString()));

            if (!account.getCustomer().getId().equals(customerId)) {
                throw CbaException.badRequest("ACCOUNT_NOT_OWNED",
                        "Account " + accountId + " does not belong to this customer");
            }
            if (pocketAccountRepository.existsByAccountId(accountId)) {
                throw CbaException.badRequest("ACCOUNT_ALREADY_IN_POCKET",
                        "Account " + accountId + " is already linked to a pocket. Delink it first.");
            }
            PocketAccount pa = new PocketAccount();
            pa.setPocket(pocket);
            pa.setAccount(account);
            pocketAccountRepository.save(pa);
            pocket.getPocketAccounts().add(pa);
        }
    }

    private Pocket getPocketOwnedBy(UUID pocketId, UUID customerId) {
        Pocket pocket = pocketRepository.findById(pocketId)
                .orElseThrow(() -> CbaException.notFound("Pocket", pocketId.toString()));
        if (!pocket.getCustomerId().equals(customerId)) {
            throw CbaException.notFound("Pocket", pocketId.toString()); // 404 not 403 — prevent enumeration
        }
        if (pocket.getStatus() == Pocket.PocketStatus.CLOSED) {
            throw CbaException.badRequest("POCKET_CLOSED", "This pocket is closed");
        }
        return pocket;
    }

    PocketResponse toResponse(Pocket pocket) {
        List<LinkedAccountSummary> accounts = pocket.getPocketAccounts().stream()
                .map(pa -> {
                    Account a = pa.getAccount();
                    return new LinkedAccountSummary(
                            a.getId(),
                            a.getAccountNumber(),
                            a.getAccountType() != null ? a.getAccountType().name() : null,
                            a.getBalance(),
                            a.getCurrencyCode()
                    );
                }).toList();

        BigDecimal total = accounts.stream()
                .map(LinkedAccountSummary::balance)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return new PocketResponse(
                pocket.getId(), pocket.getCustomerId(),
                pocket.getName(), pocket.getDescription(),
                pocket.getStatus(), total, accounts
        );
    }
}
