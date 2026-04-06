package com.cba.integration;

import com.cba.customer.Customer;
import com.cba.customer.CustomerRepository;
import com.cba.customer.KycStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.*;

@DisplayName("CustomerRepository — integration tests against real PostgreSQL")
@Transactional
class CustomerRepositoryIT extends AbstractIntegrationTest {

    @Autowired CustomerRepository customerRepository;

    @Test
    @DisplayName("save and find customer — PII round-trips through encryption")
    void saveAndFind_encryptionRoundTrip() {
        Customer customer = new Customer();
        customer.setExternalId("CUST-TEST-" + System.currentTimeMillis());
        customer.setFirstName("Integration");
        customer.setLastName("Test");
        customer.setEmail("integration@test.com");
        customer.setPhone("+1-555-9999");
        customer.setDateOfBirth(LocalDate.of(1990, 1, 1));
        customer.setKycStatus(KycStatus.PENDING_KYC);

        Customer saved = customerRepository.save(customer);

        Customer found = customerRepository.findById(saved.getId()).orElseThrow();
        // After decryption these must match the originals — verifies round-trip
        assertThat(found.getFirstName()).isEqualTo("Integration");
        assertThat(found.getEmail()).isEqualTo("integration@test.com");
        assertThat(found.getKycStatus()).isEqualTo(KycStatus.PENDING_KYC);
    }

    @Test
    @DisplayName("findByKycStatus returns only customers with matching status")
    void findByKycStatus_filtersCorrectly() {
        // Demo data from V2 has 2 ACTIVE, 1 PENDING_KYC
        Page<Customer> active = customerRepository.findByKycStatus(KycStatus.ACTIVE, PageRequest.of(0, 10));
        Page<Customer> pending = customerRepository.findByKycStatus(KycStatus.PENDING_KYC, PageRequest.of(0, 10));

        assertThat(active.getContent()).allSatisfy(c ->
            assertThat(c.getKycStatus()).isEqualTo(KycStatus.ACTIVE));
        assertThat(pending.getContent()).allSatisfy(c ->
            assertThat(c.getKycStatus()).isEqualTo(KycStatus.PENDING_KYC));
    }
}
