package com.cba.customer;

import com.cba.audit.AuditLogService;
import com.cba.common.exception.CbaException;
import com.cba.customer.dto.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class CustomerService {

    private final CustomerRepository customerRepository;
    private final CustomerMapper customerMapper;
    private final AuditLogService auditLogService;

    // ── Create ───────────────────────────────────────────────────────────────

    @Transactional
    public CustomerResponse createCustomer(CreateCustomerRequest request) {
        Customer customer = customerMapper.toEntity(request);
        customer.setExternalId("CUST-" + System.currentTimeMillis());
        customer.setKycStatus(KycStatus.PENDING_KYC);

        Customer saved = customerRepository.save(customer);
        auditLogService.log("CUSTOMER", saved.getId().toString(), "CREATED", null, request);
        log.info("Customer created: externalId={}", saved.getExternalId());
        return customerMapper.toResponse(saved);
    }

    // ── Read ─────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public CustomerResponse getCustomer(UUID id) {
        return customerMapper.toResponse(findById(id));
    }

    @Transactional(readOnly = true)
    public Page<CustomerResponse> listCustomers(Pageable pageable) {
        return customerRepository.findAll(pageable).map(customerMapper::toResponse);
    }

    @Transactional(readOnly = true)
    public Page<CustomerResponse> listCustomersByStatus(KycStatus status, Pageable pageable) {
        return customerRepository.findByKycStatus(status, pageable).map(customerMapper::toResponse);
    }

    // ── Update profile ───────────────────────────────────────────────────────

    @Transactional
    public CustomerResponse updateCustomer(UUID id, UpdateCustomerRequest request) {
        Customer customer = findById(id);
        if (request.firstName()   != null) customer.setFirstName(request.firstName());
        if (request.lastName()    != null) customer.setLastName(request.lastName());
        if (request.email()       != null) customer.setEmail(request.email());
        if (request.phone()       != null) customer.setPhone(request.phone());
        if (request.nationalId()  != null) customer.setNationalId(request.nationalId());
        if (request.dateOfBirth() != null) customer.setDateOfBirth(request.dateOfBirth());
        if (request.notes()       != null) customer.setNotes(request.notes());

        Customer saved = customerRepository.save(customer);
        auditLogService.log("CUSTOMER", id.toString(), "UPDATED", null, request);
        return customerMapper.toResponse(saved);
    }

    // ── KYC status (legacy endpoint — kept for backward compat) ──────────────

    @Transactional
    public CustomerResponse updateKycStatus(UUID id, UpdateKycStatusRequest request) {
        Customer customer = findById(id);
        KycStatus oldStatus = customer.getKycStatus();
        customer.setKycStatus(request.kycStatus());
        if (request.notes() != null) customer.setNotes(request.notes());

        Customer saved = customerRepository.save(customer);
        auditLogService.log("CUSTOMER", id.toString(), "KYC_STATUS_CHANGED",
                oldStatus.name(), request.kycStatus().name());
        log.info("Customer KYC status updated: id={} {} → {}", id, oldStatus, request.kycStatus());
        return customerMapper.toResponse(saved);
    }

    // ── Commands ─────────────────────────────────────────────────────────────

    @Transactional
    public CustomerResponse executeCommand(UUID id, String command, CustomerCommandRequest payload) {
        Customer customer = findById(id);
        return switch (command) {
            case "activate"         -> activate(customer);
            case "reject"           -> reject(customer, payload);
            case "withdraw"         -> withdraw(customer, payload);
            case "reactivate"       -> reactivate(customer);
            case "undoRejection"    -> undoRejection(customer);
            case "undoWithdrawal"   -> undoWithdrawal(customer);
            case "suspend"          -> suspend(customer, payload);
            case "close"            -> close(customer, payload);
            case "assignStaff"      -> assignStaff(customer, payload);
            case "unassignStaff"    -> unassignStaff(customer);
            case "proposeTransfer"  -> proposeTransfer(customer, payload);
            case "acceptTransfer"   -> acceptTransfer(customer);
            case "rejectTransfer"   -> rejectTransfer(customer);
            case "withdrawTransfer" -> withdrawTransfer(customer);
            default -> throw CbaException.badRequest("UNKNOWN_COMMAND",
                    "Unknown customer command: " + command);
        };
    }

    private CustomerResponse activate(Customer c) {
        requireStatus(c, KycStatus.PENDING_KYC);
        c.setKycStatus(KycStatus.ACTIVE);
        c.setActivationDate(LocalDate.now());
        return save(c, "ACTIVATED");
    }

    private CustomerResponse reject(Customer c, CustomerCommandRequest p) {
        requireStatus(c, KycStatus.PENDING_KYC);
        c.setKycStatus(KycStatus.REJECTED);
        c.setRejectionDate(LocalDate.now());
        c.setRejectionReason(p.reason());
        return save(c, "REJECTED");
    }

    private CustomerResponse withdraw(Customer c, CustomerCommandRequest p) {
        requireAnyStatus(c, KycStatus.PENDING_KYC, KycStatus.ACTIVE);
        c.setKycStatus(KycStatus.WITHDRAWN);
        c.setWithdrawalDate(LocalDate.now());
        c.setWithdrawalReason(p.reason());
        return save(c, "WITHDRAWN");
    }

    private CustomerResponse reactivate(Customer c) {
        requireAnyStatus(c, KycStatus.SUSPENDED, KycStatus.REJECTED, KycStatus.WITHDRAWN);
        c.setKycStatus(KycStatus.ACTIVE);
        return save(c, "REACTIVATED");
    }

    private CustomerResponse undoRejection(Customer c) {
        requireStatus(c, KycStatus.REJECTED);
        c.setKycStatus(KycStatus.PENDING_KYC);
        c.setRejectionDate(null);
        c.setRejectionReason(null);
        return save(c, "UNDO_REJECTION");
    }

    private CustomerResponse undoWithdrawal(Customer c) {
        requireStatus(c, KycStatus.WITHDRAWN);
        c.setKycStatus(KycStatus.PENDING_KYC);
        c.setWithdrawalDate(null);
        c.setWithdrawalReason(null);
        return save(c, "UNDO_WITHDRAWAL");
    }

    private CustomerResponse suspend(Customer c, CustomerCommandRequest p) {
        requireStatus(c, KycStatus.ACTIVE);
        c.setKycStatus(KycStatus.SUSPENDED);
        if (p.reason() != null) c.setNotes(p.reason());
        return save(c, "SUSPENDED");
    }

    private CustomerResponse close(Customer c, CustomerCommandRequest p) {
        requireAnyStatus(c, KycStatus.ACTIVE, KycStatus.SUSPENDED);
        c.setKycStatus(KycStatus.CLOSED);
        c.setClosureDate(LocalDate.now());
        c.setClosureReason(p.reason());
        return save(c, "CLOSED");
    }

    private CustomerResponse assignStaff(Customer c, CustomerCommandRequest p) {
        if (p.staffId() == null) {
            throw CbaException.badRequest("MISSING_STAFF_ID", "staffId is required for assignStaff");
        }
        c.setStaffId(p.staffId());
        return save(c, "STAFF_ASSIGNED");
    }

    private CustomerResponse unassignStaff(Customer c) {
        c.setStaffId(null);
        return save(c, "STAFF_UNASSIGNED");
    }

    private CustomerResponse proposeTransfer(Customer c, CustomerCommandRequest p) {
        if (p.destinationOfficeId() == null) {
            throw CbaException.badRequest("MISSING_OFFICE_ID", "destinationOfficeId is required for proposeTransfer");
        }
        c.setKycStatus(KycStatus.TRANSFER_IN_PROGRESS);
        c.setTransferToOfficeId(p.destinationOfficeId());
        c.setTransferDate(p.transferDate() != null ? p.transferDate() : LocalDate.now());
        c.setTransferNote(p.transferNote());
        return save(c, "TRANSFER_PROPOSED");
    }

    private CustomerResponse acceptTransfer(Customer c) {
        requireStatus(c, KycStatus.TRANSFER_IN_PROGRESS);
        c.setOfficeId(c.getTransferToOfficeId());
        c.setTransferToOfficeId(null);
        c.setTransferDate(null);
        c.setTransferNote(null);
        c.setKycStatus(KycStatus.ACTIVE);
        return save(c, "TRANSFER_ACCEPTED");
    }

    private CustomerResponse rejectTransfer(Customer c) {
        requireStatus(c, KycStatus.TRANSFER_IN_PROGRESS);
        c.setTransferToOfficeId(null);
        c.setTransferDate(null);
        c.setTransferNote(null);
        c.setKycStatus(KycStatus.ACTIVE);
        return save(c, "TRANSFER_REJECTED");
    }

    private CustomerResponse withdrawTransfer(Customer c) {
        requireStatus(c, KycStatus.TRANSFER_IN_PROGRESS);
        c.setTransferToOfficeId(null);
        c.setTransferDate(null);
        c.setTransferNote(null);
        c.setKycStatus(KycStatus.ACTIVE);
        return save(c, "TRANSFER_WITHDRAWN");
    }

    // ── Delete ────────────────────────────────────────────────────────────────

    @Transactional
    public void deleteCustomer(UUID id) {
        Customer customer = findById(id);
        if (customer.getKycStatus() != KycStatus.PENDING_KYC) {
            throw CbaException.badRequest("CANNOT_DELETE_ACTIVE_CUSTOMER",
                    "Only PENDING_KYC customers can be deleted. Current status: " + customer.getKycStatus());
        }
        customerRepository.delete(customer);
        auditLogService.log("CUSTOMER", id.toString(), "DELETED", customer.getKycStatus().name(), null);
        log.info("Customer deleted: id={}", id);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private CustomerResponse save(Customer c, String action) {
        Customer saved = customerRepository.save(c);
        auditLogService.log("CUSTOMER", c.getId().toString(), action,
                null, saved.getKycStatus().name());
        log.info("Customer command executed: id={} action={} status={}", c.getId(), action, saved.getKycStatus());
        return customerMapper.toResponse(saved);
    }

    private Customer findById(UUID id) {
        return customerRepository.findById(id)
                .orElseThrow(() -> CbaException.notFound("Customer", id));
    }

    private void requireStatus(Customer c, KycStatus required) {
        if (c.getKycStatus() != required) {
            throw CbaException.badRequest("INVALID_STATUS_TRANSITION",
                    "Expected status " + required + " but was " + c.getKycStatus());
        }
    }

    private void requireAnyStatus(Customer c, KycStatus... allowed) {
        for (KycStatus s : allowed) {
            if (c.getKycStatus() == s) return;
        }
        throw CbaException.badRequest("INVALID_STATUS_TRANSITION",
                "Status " + c.getKycStatus() + " does not allow this operation");
    }
}
