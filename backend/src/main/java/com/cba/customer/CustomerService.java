package com.cba.customer;

import com.cba.audit.AuditLogService;
import com.cba.common.exception.CbaException;
import com.cba.customer.dto.CreateCustomerRequest;
import com.cba.customer.dto.CustomerResponse;
import com.cba.customer.dto.UpdateKycStatusRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class CustomerService {

    private final CustomerRepository customerRepository;
    private final CustomerMapper customerMapper;
    private final AuditLogService auditLogService;

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

    @Transactional
    public CustomerResponse updateKycStatus(UUID id, UpdateKycStatusRequest request) {
        Customer customer = findById(id);
        KycStatus oldStatus = customer.getKycStatus();

        customer.setKycStatus(request.kycStatus());
        if (request.notes() != null) {
            customer.setNotes(request.notes());
        }

        Customer saved = customerRepository.save(customer);

        auditLogService.log("CUSTOMER", id.toString(), "KYC_STATUS_CHANGED",
            oldStatus.name(), request.kycStatus().name());

        log.info("Customer KYC status updated: id={} {} → {}", id, oldStatus, request.kycStatus());
        return customerMapper.toResponse(saved);
    }

    private Customer findById(UUID id) {
        return customerRepository.findById(id)
            .orElseThrow(() -> CbaException.notFound("Customer", id));
    }
}
