package com.cba.customer;

import com.cba.audit.AuditLogService;
import com.cba.customer.dto.CreateCustomerRequest;
import com.cba.customer.dto.CustomerResponse;
import com.cba.customer.dto.UpdateKycStatusRequest;
import com.cba.common.exception.CbaException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("CustomerService — unit tests")
class CustomerServiceTest {

    @Mock CustomerRepository customerRepository;
    @Mock AuditLogService auditLogService;
    @Spy  CustomerMapper customerMapper = new CustomerMapperImpl();

    @InjectMocks CustomerService customerService;

    private CreateCustomerRequest validRequest;

    @BeforeEach
    void setUp() {
        validRequest = new CreateCustomerRequest(
            "John", "Doe", "john@example.com",
            "+1-555-0100", "NID123456",
            LocalDate.of(1985, 3, 15), null
        );
    }

    @Test
    @DisplayName("createCustomer persists a customer with PENDING_KYC status")
    void createCustomer_setsInitialKycStatus() {
        Customer saved = new Customer();
        saved.setId(UUID.randomUUID());
        saved.setExternalId("CUST-001");
        saved.setFirstName("John");
        saved.setLastName("Doe");
        saved.setEmail("john@example.com");
        saved.setKycStatus(KycStatus.PENDING_KYC);

        when(customerRepository.save(any(Customer.class))).thenReturn(saved);

        CustomerResponse response = customerService.createCustomer(validRequest);

        assertThat(response.kycStatus()).isEqualTo(KycStatus.PENDING_KYC);
        verify(auditLogService).log(eq("CUSTOMER"), any(), eq("CREATED"), isNull(), any());
    }

    @Test
    @DisplayName("getCustomer throws 404 when ID does not exist")
    void getCustomer_notFound_throws() {
        UUID id = UUID.randomUUID();
        when(customerRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> customerService.getCustomer(id))
            .isInstanceOf(CbaException.class)
            .hasMessageContaining("not found");
    }

    @Test
    @DisplayName("updateKycStatus transitions to ACTIVE and logs the change")
    void updateKycStatus_transitionsCorrectly() {
        UUID id = UUID.randomUUID();
        Customer customer = new Customer();
        customer.setId(id);
        customer.setFirstName("Jane");
        customer.setLastName("Smith");
        customer.setEmail("jane@example.com");
        customer.setKycStatus(KycStatus.PENDING_KYC);

        when(customerRepository.findById(id)).thenReturn(Optional.of(customer));
        when(customerRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        UpdateKycStatusRequest req = new UpdateKycStatusRequest(KycStatus.ACTIVE, "KYC documents verified");
        CustomerResponse response = customerService.updateKycStatus(id, req);

        assertThat(response.kycStatus()).isEqualTo(KycStatus.ACTIVE);
        verify(auditLogService).log(eq("CUSTOMER"), eq(id.toString()),
            eq("KYC_STATUS_CHANGED"), eq("PENDING_KYC"), eq("ACTIVE"));
    }
}
