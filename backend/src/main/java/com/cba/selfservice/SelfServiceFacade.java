package com.cba.selfservice;

import com.cba.account.AccountRepository;
import com.cba.account.AccountService;
import com.cba.account.dto.AccountResponse;
import com.cba.account.dto.TransactionResponse;
import com.cba.common.exception.CbaException;
import com.cba.customer.Customer;
import com.cba.customer.CustomerRepository;
import com.cba.customer.CustomerService;
import com.cba.customer.dto.CustomerResponse;
import com.cba.loan.LoanService;
import com.cba.loan.dto.LoanResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    private Customer resolveCustomer(String keycloakSub) {
        return customerRepository.findByKeycloakId(keycloakSub)
                .orElseThrow(() -> CbaException.notFound("Customer",
                        "No customer linked to this account. Contact your branch to link self-service access."));
    }
}
