package com.cba.teller;

import com.cba.account.Account;
import com.cba.account.AccountRepository;
import com.cba.account.AccountStatus;
import com.cba.account.Transaction;
import com.cba.account.TransactionRepository;
import com.cba.account.TransactionType;
import com.cba.audit.AuditLogService;
import com.cba.common.exception.CbaException;
import com.cba.teller.dto.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TellerService {

    private final TellerRepository tellerRepository;
    private final CashierRepository cashierRepository;
    private final TellerSessionRepository sessionRepository;
    private final CashTransactionRepository cashTransactionRepository;
    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private final AuditLogService auditLogService;

    // ── Teller CRUD ──────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<TellerResponse> getAllTellers() {
        return tellerRepository.findAll().stream().map(TellerResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public TellerResponse getTeller(UUID id) {
        return TellerResponse.from(findTellerById(id));
    }

    @Transactional
    public TellerResponse createTeller(TellerRequest request) {
        Teller teller = new Teller();
        applyTellerFields(teller, request);
        Teller saved = tellerRepository.save(teller);
        auditLogService.log("Teller", saved.getId().toString(), "CREATE", null, saved);
        return TellerResponse.from(saved);
    }

    @Transactional
    public TellerResponse updateTeller(UUID id, TellerRequest request) {
        Teller teller = findTellerById(id);
        applyTellerFields(teller, request);
        Teller saved = tellerRepository.save(teller);
        auditLogService.log("Teller", id.toString(), "UPDATE", null, saved);
        return TellerResponse.from(saved);
    }

    @Transactional
    public TellerResponse activateTeller(UUID id) {
        Teller teller = findTellerById(id);
        teller.setStatus(TellerStatus.ACTIVE);
        Teller saved = tellerRepository.save(teller);
        auditLogService.log("Teller", id.toString(), "ACTIVATE", null, saved);
        return TellerResponse.from(saved);
    }

    @Transactional
    public TellerResponse closeTeller(UUID id) {
        Teller teller = findTellerById(id);
        teller.setStatus(TellerStatus.CLOSED);
        teller.setEndDate(LocalDate.now());
        Teller saved = tellerRepository.save(teller);
        auditLogService.log("Teller", id.toString(), "CLOSE", null, saved);
        return TellerResponse.from(saved);
    }

    // ── Cashier management ───────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<CashierResponse> getCashiers(UUID tellerId) {
        findTellerById(tellerId);
        return cashierRepository.findByTellerId(tellerId)
                .stream().map(CashierResponse::from).toList();
    }

    @Transactional
    public CashierResponse assignCashier(UUID tellerId, CashierRequest request) {
        Teller teller = findTellerById(tellerId);
        if (teller.getStatus() != TellerStatus.ACTIVE) {
            throw CbaException.badRequest("TELLER_NOT_ACTIVE",
                    "Teller " + tellerId + " is not active — cannot assign cashiers");
        }

        Cashier cashier = new Cashier();
        cashier.setTeller(teller);
        cashier.setStaffId(request.staffId());
        cashier.setDescription(request.description());
        cashier.setStartDate(request.startDate() != null ? request.startDate() : LocalDate.now());
        cashier.setEndDate(request.endDate());
        cashier.setFullDay(request.fullDay());
        cashier.setStartTime(request.startTime());
        cashier.setEndTime(request.endTime());

        Cashier saved = cashierRepository.save(cashier);
        auditLogService.log("Cashier", saved.getId().toString(), "ASSIGN", null, saved);
        return CashierResponse.from(saved);
    }

    // ── Session lifecycle ────────────────────────────────────────────

    @Transactional
    public SessionResponse openSession(UUID tellerId, UUID cashierId, OpenSessionRequest request) {
        Teller teller = findTellerById(tellerId);
        Cashier cashier = findCashierById(cashierId);

        if (!cashier.getTeller().getId().equals(tellerId)) {
            throw CbaException.badRequest("CASHIER_TELLER_MISMATCH",
                    "Cashier " + cashierId + " does not belong to teller " + tellerId);
        }
        if (teller.getStatus() != TellerStatus.ACTIVE) {
            throw CbaException.badRequest("TELLER_NOT_ACTIVE",
                    "Teller " + tellerId + " must be ACTIVE to open a session");
        }

        LocalDate today = LocalDate.now();
        if (sessionRepository.findByCashierIdAndSessionDate(cashierId, today).isPresent()) {
            throw CbaException.conflict("SESSION_ALREADY_OPEN",
                    "Cashier " + cashierId + " already has an open session for today");
        }

        TellerSession session = new TellerSession();
        session.setTeller(teller);
        session.setCashier(cashier);
        session.setSessionDate(today);
        session.setOpeningBalance(request.openingBalance());
        session.setCurrencyCode(request.currencyCode() != null ? request.currencyCode().toUpperCase() : "USD");
        session.setStatus(SessionStatus.OPEN);

        TellerSession saved = sessionRepository.save(session);
        auditLogService.log("TellerSession", saved.getId().toString(), "OPEN",
                null, java.util.Map.of("openingBalance", request.openingBalance()));
        return SessionResponse.from(saved);
    }

    @Transactional
    public SessionResponse closeSession(UUID sessionId, CloseSessionRequest request) {
        TellerSession session = findSessionById(sessionId);

        if (session.getStatus() != SessionStatus.OPEN) {
            throw CbaException.conflict("SESSION_NOT_OPEN",
                    "Session " + sessionId + " is already closed");
        }

        BigDecimal totalCashIn  = cashTransactionRepository.sumBySessionIdAndType(sessionId, CashTransactionType.CASH_IN);
        BigDecimal totalCashOut = cashTransactionRepository.sumBySessionIdAndType(sessionId, CashTransactionType.CASH_OUT);
        BigDecimal expectedClosing = session.getOpeningBalance().add(totalCashIn).subtract(totalCashOut);

        session.setClosingBalance(expectedClosing);
        session.setActualCash(request.actualCash());
        session.setDifference(request.actualCash().subtract(expectedClosing));
        session.setSettlementNote(request.settlementNote());
        session.setStatus(SessionStatus.CLOSED);
        session.setClosedAt(Instant.now());

        TellerSession saved = sessionRepository.save(session);
        auditLogService.log("TellerSession", sessionId.toString(), "CLOSE",
                null, java.util.Map.of("closingBalance", expectedClosing, "difference", session.getDifference()));
        return SessionResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public SessionResponse getSession(UUID sessionId) {
        return SessionResponse.from(findSessionById(sessionId));
    }

    @Transactional(readOnly = true)
    public List<SessionResponse> getSessions(UUID tellerId) {
        findTellerById(tellerId);
        return sessionRepository.findByTellerId(tellerId)
                .stream().map(SessionResponse::from).toList();
    }

    // ── Cash transactions ────────────────────────────────────────────

    @Transactional
    public CashTransactionResponse recordCashTransaction(UUID sessionId, CashTransactionRequest request) {
        TellerSession session = findSessionById(sessionId);

        if (session.getStatus() != SessionStatus.OPEN) {
            throw CbaException.badRequest("SESSION_NOT_OPEN",
                    "Session " + sessionId + " is closed — cannot record transactions");
        }

        Account account = null;
        if (request.accountId() != null) {
            account = accountRepository.findById(request.accountId())
                    .orElseThrow(() -> CbaException.notFound("Account", request.accountId()));
            if (account.getStatus() != AccountStatus.ACTIVE) {
                throw CbaException.badRequest("ACCOUNT_NOT_ACTIVE",
                        "Account " + request.accountId() + " is not active");
            }

            // Mirror cash movement into the account's transaction ledger
            if (request.transactionType() == CashTransactionType.CASH_IN) {
                account.setBalance(account.getBalance().add(request.amount()));
                recordAccountTransaction(account, TransactionType.DEPOSIT, request.amount(), "Teller cash deposit");
            } else {
                if (account.getBalance().compareTo(request.amount()) < 0) {
                    throw CbaException.badRequest("INSUFFICIENT_FUNDS",
                            "Insufficient balance for cash withdrawal");
                }
                account.setBalance(account.getBalance().subtract(request.amount()));
                recordAccountTransaction(account, TransactionType.WITHDRAWAL, request.amount(), "Teller cash withdrawal");
            }
            accountRepository.save(account);
        }

        CashTransaction tx = new CashTransaction();
        tx.setSession(session);
        tx.setTeller(session.getTeller());
        tx.setCashier(session.getCashier());
        tx.setAccount(account);
        tx.setTransactionType(request.transactionType());
        tx.setAmount(request.amount());
        tx.setCurrencyCode(request.currencyCode() != null ? request.currencyCode().toUpperCase() : session.getCurrencyCode());
        tx.setDescription(request.description());
        tx.setReferenceNumber("CASH-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase());

        CashTransaction saved = cashTransactionRepository.save(tx);
        auditLogService.log("CashTransaction", saved.getId().toString(), "RECORD", null, saved);
        return CashTransactionResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public List<CashTransactionResponse> getSessionTransactions(UUID sessionId) {
        findSessionById(sessionId);
        return cashTransactionRepository.findBySessionId(sessionId)
                .stream().map(CashTransactionResponse::from).toList();
    }

    // ── Private helpers ──────────────────────────────────────────────

    private Teller findTellerById(UUID id) {
        return tellerRepository.findById(id)
                .orElseThrow(() -> CbaException.notFound("Teller", id));
    }

    private Cashier findCashierById(UUID id) {
        return cashierRepository.findById(id)
                .orElseThrow(() -> CbaException.notFound("Cashier", id));
    }

    private TellerSession findSessionById(UUID id) {
        return sessionRepository.findById(id)
                .orElseThrow(() -> CbaException.notFound("TellerSession", id));
    }

    private void applyTellerFields(Teller teller, TellerRequest request) {
        teller.setName(request.name());
        teller.setDescription(request.description());
        teller.setBranchCode(request.branchCode());
        teller.setOfficeId(request.officeId());
        if (request.startDate() != null) teller.setStartDate(request.startDate());
        teller.setEndDate(request.endDate());
    }

    private void recordAccountTransaction(Account account, TransactionType type,
                                          BigDecimal amount, String description) {
        String ref = "CASH-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase();
        Transaction tx = Transaction.of(account, type, amount, account.getBalance(),
                description, ref, "teller");
        transactionRepository.save(tx);
    }
}
