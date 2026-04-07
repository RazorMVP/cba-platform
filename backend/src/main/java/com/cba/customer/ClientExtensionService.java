package com.cba.customer;

import com.cba.audit.AuditLogService;
import com.cba.common.exception.CbaException;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ClientExtensionService {

    public record CreateIdentifierRequest(
        UUID documentTypeCodeValueId, String documentKey,
        String description, LocalDate expiryDate
    ) {}

    public record CreateAddressRequest(
        ClientAddress.AddressType addressType,
        String addressLine1, String addressLine2, String addressLine3,
        String city, String stateProvince, String postalCode, String countryCode
    ) {}

    private final ClientIdentifierRepository identifierRepository;
    private final ClientAddressRepository addressRepository;
    private final EntityManager entityManager;
    private final AuditLogService auditLogService;

    // ── Identifiers ───────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Page<ClientIdentifier> listIdentifiers(UUID customerId, Pageable p) {
        return identifierRepository.findByCustomerId(customerId, p);
    }

    @Transactional
    public ClientIdentifier createIdentifier(UUID customerId, CreateIdentifierRequest req) {
        Customer customer = findCustomer(customerId);
        ClientIdentifier ci = new ClientIdentifier();
        ci.setCustomer(customer);
        ci.setDocumentTypeCodeValueId(req.documentTypeCodeValueId());
        ci.setDocumentKey(req.documentKey());
        ci.setDescription(req.description());
        ci.setExpiryDate(req.expiryDate());
        ClientIdentifier saved = identifierRepository.save(ci);
        auditLogService.log("ClientIdentifier", saved.getId().toString(), "CREATE", null, saved);
        return saved;
    }

    @Transactional
    public void deleteIdentifier(UUID customerId, UUID identifierId) {
        ClientIdentifier ci = identifierRepository.findById(identifierId)
            .orElseThrow(() -> CbaException.notFound("ClientIdentifier", identifierId));
        identifierRepository.delete(ci);
        auditLogService.log("ClientIdentifier", identifierId.toString(), "DELETE", null, null);
    }

    // ── Addresses ─────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Page<ClientAddress> listAddresses(UUID customerId, Pageable p) {
        return addressRepository.findByCustomerId(customerId, p);
    }

    @Transactional
    public ClientAddress createAddress(UUID customerId, CreateAddressRequest req) {
        Customer customer = findCustomer(customerId);
        ClientAddress addr = new ClientAddress();
        addr.setCustomer(customer);
        addr.setAddressType(req.addressType() != null ? req.addressType() : ClientAddress.AddressType.HOME);
        addr.setAddressLine1(req.addressLine1());
        addr.setAddressLine2(req.addressLine2());
        addr.setAddressLine3(req.addressLine3());
        addr.setCity(req.city());
        addr.setStateProvince(req.stateProvince());
        addr.setPostalCode(req.postalCode());
        addr.setCountryCode(req.countryCode());
        ClientAddress saved = addressRepository.save(addr);
        auditLogService.log("ClientAddress", saved.getId().toString(), "CREATE", null, saved);
        return saved;
    }

    @Transactional
    public void deleteAddress(UUID customerId, UUID addressId) {
        ClientAddress addr = addressRepository.findById(addressId)
            .orElseThrow(() -> CbaException.notFound("ClientAddress", addressId));
        addressRepository.delete(addr);
        auditLogService.log("ClientAddress", addressId.toString(), "DELETE", null, null);
    }

    // ── Helper ────────────────────────────────────────────────────────

    private Customer findCustomer(UUID customerId) {
        Customer customer = entityManager.find(Customer.class, customerId);
        if (customer == null) throw CbaException.notFound("Customer", customerId);
        return customer;
    }
}
