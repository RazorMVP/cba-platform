package com.cba.selfservice;

import com.cba.account.AccountRepository;
import com.cba.account.AccountService;
import com.cba.account.dto.AccountResponse;
import com.cba.account.dto.TransactionResponse;
import com.cba.common.exception.CbaException;
import com.cba.customer.Beneficiary;
import com.cba.customer.BeneficiaryService;
import com.cba.customer.Customer;
import com.cba.customer.CustomerRepository;
import com.cba.customer.CustomerService;
import com.cba.customer.dto.CustomerResponse;
import com.cba.loan.LoanService;
import com.cba.loan.dto.LoanApplicationRequest;
import com.cba.loan.dto.LoanResponse;
import com.cba.loan.dto.RepaymentScheduleResponse;
import com.cba.wallet.PocketService;
import com.cba.wallet.QrPaymentService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

import java.util.List;
import java.util.UUID;

/**
 * Facade for self-service endpoints.
 * All methods accept the Keycloak subject (JWT sub) and resolve the customer,
 * then enforce ownership before returning any data.
 */
@Service
@RequiredArgsConstructor
public class SelfServiceFacade {

    private final CustomerRepository customerRepository;
    private final CustomerService customerService;
    private final AccountService accountService;
    private final AccountRepository accountRepository;
    private final LoanService loanService;
    private final BeneficiaryService beneficiaryService;
    private final PocketService pocketService;
    private final QrPaymentService qrPaymentService;

    @Transactional(readOnly = true)
    public CustomerResponse getProfile(String keycloakSub) {
        Customer customer = resolveCustomer(keycloakSub);
        return customerService.getCustomer(customer.getId());
    }

    @Transactional(readOnly = true)
    public List<AccountResponse> getAccounts(String keycloakSub) {
        Customer customer = resolveCustomer(keycloakSub);
        return accountService.getCustomerAccounts(customer.getId(),
                PageRequest.of(0, 200, Sort.by("createdAt").descending())).getContent();
    }

    @Transactional(readOnly = true)
    public List<TransactionResponse> getTransactions(String keycloakSub, UUID accountId) {
        Customer customer = resolveCustomer(keycloakSub);
        // Ownership check — the account must belong to this customer
        accountRepository.findById(accountId)
                .filter(a -> a.getCustomer().getId().equals(customer.getId()))
                .orElseThrow(() -> CbaException.notFound("Account", accountId.toString()));

        return accountService.getTransactions(accountId,
                PageRequest.of(0, 500, Sort.by("createdAt").descending())).getContent();
    }

    @Transactional(readOnly = true)
    public List<LoanResponse> getLoans(String keycloakSub) {
        Customer customer = resolveCustomer(keycloakSub);
        return loanService.getCustomerLoans(customer.getId(),
                PageRequest.of(0, 200, Sort.by("createdAt").descending())).getContent();
    }

    @Transactional(readOnly = true)
    public LoanResponse getLoan(String keycloakSub, UUID loanId) {
        Customer customer = resolveCustomer(keycloakSub);
        LoanResponse loan = loanService.getLoan(loanId);
        if (!loan.customerId().equals(customer.getId())) {
            throw CbaException.notFound("Loan", loanId.toString()); // return 404 rather than 403 to avoid enumeration
        }
        return loan;
    }

    // ── Self-Service Loan Application ─────────────────────────────────────────

    public record SelfLoanApplicationRequest(
        java.util.UUID productId,
        java.util.UUID linkedAccountId,
        @jakarta.validation.constraints.DecimalMin("1.00") BigDecimal principalAmount,
        @jakarta.validation.constraints.Min(1) Integer termMonths,
        String notes
    ) {}

    @Transactional
    public LoanResponse applyForLoan(String keycloakSub, SelfLoanApplicationRequest req) {
        Customer customer = resolveCustomer(keycloakSub);
        LoanApplicationRequest appReq = new LoanApplicationRequest(
                customer.getId(),
                req.productId(),
                req.linkedAccountId(),
                req.principalAmount(),
                req.termMonths(),
                req.notes()
        );
        return loanService.applyForLoan(appReq);
    }

    @Transactional(readOnly = true)
    public List<RepaymentScheduleResponse> getRepaymentSchedule(String keycloakSub, UUID loanId) {
        Customer customer = resolveCustomer(keycloakSub);
        LoanResponse loan = loanService.getLoan(loanId);
        if (!loan.customerId().equals(customer.getId())) {
            throw CbaException.notFound("Loan", loanId.toString());
        }
        return loanService.getRepaymentSchedule(loanId);
    }

    // ── Self-Service Beneficiaries ────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<Beneficiary> getBeneficiaries(String keycloakSub) {
        return beneficiaryService.listBeneficiaries(resolveCustomer(keycloakSub).getId());
    }

    @Transactional
    public Beneficiary addBeneficiary(String keycloakSub, BeneficiaryService.CreateBeneficiaryRequest req) {
        return beneficiaryService.createBeneficiary(resolveCustomer(keycloakSub).getId(), req);
    }

    @Transactional
    public void removeBeneficiary(String keycloakSub, UUID beneficiaryId) {
        beneficiaryService.deactivate(resolveCustomer(keycloakSub).getId(), beneficiaryId);
    }

    // ── Self-Service Pockets ──────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<PocketService.PocketResponse> getMyPockets(String keycloakSub) {
        return pocketService.listPockets(resolveCustomer(keycloakSub).getId());
    }

    @Transactional
    public PocketService.PocketResponse createPocket(String keycloakSub,
            PocketService.CreatePocketRequest req) {
        UUID customerId = resolveCustomer(keycloakSub).getId();
        return pocketService.createPocket(new PocketService.CreatePocketRequest(
                customerId, req.name(), req.description(), req.accountIds()));
    }

    @Transactional
    public PocketService.PocketResponse linkAccountsToPocket(String keycloakSub,
            UUID pocketId, List<UUID> accountIds) {
        return pocketService.linkAccounts(pocketId, resolveCustomer(keycloakSub).getId(), accountIds);
    }

    @Transactional
    public PocketService.PocketResponse delinkAccountsFromPocket(String keycloakSub,
            UUID pocketId, List<UUID> accountIds) {
        return pocketService.delinkAccounts(pocketId, resolveCustomer(keycloakSub).getId(), accountIds);
    }

    // ── Self-Service QR Payments ──────────────────────────────────────────────

    @Transactional
    public QrPaymentService.QrResponse generateQrForAccount(String keycloakSub,
            UUID accountId, BigDecimal presetAmount, String reference, Integer expiryMinutes) {
        // Ownership check — account must belong to this customer
        Customer customer = resolveCustomer(keycloakSub);
        accountRepository.findById(accountId)
                .filter(a -> a.getCustomer().getId().equals(customer.getId()))
                .orElseThrow(() -> CbaException.notFound("Account", accountId.toString()));
        return qrPaymentService.generateQr(
                new QrPaymentService.GenerateQrRequest(accountId, presetAmount, reference, expiryMinutes));
    }

    @Transactional
    public Object scanAndPay(String keycloakSub, String token, UUID payerAccountId, BigDecimal amount) {
        // Ownership check — payer account must belong to this customer
        Customer customer = resolveCustomer(keycloakSub);
        accountRepository.findById(payerAccountId)
                .filter(a -> a.getCustomer().getId().equals(customer.getId()))
                .orElseThrow(() -> CbaException.notFound("Account", payerAccountId.toString()));
        return qrPaymentService.decodeAndPay(
                new QrPaymentService.DecodeAndPayRequest(token, payerAccountId, amount),
                customer.getId().toString());
    }

    private Customer resolveCustomer(String keycloakSub) {
        return customerRepository.findByKeycloakId(keycloakSub)
                .orElseThrow(() -> CbaException.notFound("Customer",
                        "No customer linked to this account. Contact your branch to link self-service access."));
    }
}
