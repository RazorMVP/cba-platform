package com.cba.customer;

import com.cba.account.algorithm.AccountNumberAlgorithmService;
import com.cba.audit.AuditLogService;
import com.cba.common.exception.CbaException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("BeneficiaryService — unit tests")
class BeneficiaryServiceTest {

    @Mock BeneficiaryRepository beneficiaryRepository;
    @Mock AccountNumberAlgorithmService accountNumberAlgorithmService;
    @Mock AuditLogService auditLogService;

    @InjectMocks BeneficiaryService service;

    private UUID customerId;
    private UUID beneficiaryId;
    private Beneficiary beneficiary;

    @BeforeEach
    void setUp() {
        customerId = UUID.randomUUID();
        beneficiaryId = UUID.randomUUID();

        beneficiary = new Beneficiary();
        beneficiary.setId(beneficiaryId);
        beneficiary.setCustomerId(customerId);
        beneficiary.setName("John Doe");
        beneficiary.setAccountNumber("001-SAV-0001234");
        beneficiary.setBankNumber("058");
        beneficiary.setTransferLimit(new BigDecimal("10000.00"));
        beneficiary.setActive(true);
    }

    @Nested
    @DisplayName("List")
    class ListOps {

        @Test
        @DisplayName("listBeneficiaries returns active beneficiaries for customer")
        void listBeneficiaries_returnsActive() {
            when(beneficiaryRepository.findByCustomerIdAndActiveTrue(customerId))
                .thenReturn(List.of(beneficiary));

            assertThat(service.listBeneficiaries(customerId)).hasSize(1);
        }
    }

    @Nested
    @DisplayName("Get")
    class GetOps {

        @Test
        @DisplayName("getBeneficiary returns beneficiary when owner matches")
        void getBeneficiary_found() {
            when(beneficiaryRepository.findById(beneficiaryId)).thenReturn(Optional.of(beneficiary));

            Beneficiary result = service.getBeneficiary(customerId, beneficiaryId);
            assertThat(result.getName()).isEqualTo("John Doe");
        }

        @Test
        @DisplayName("getBeneficiary throws 404 when not found")
        void getBeneficiary_notFound_throws() {
            when(beneficiaryRepository.findById(beneficiaryId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.getBeneficiary(customerId, beneficiaryId))
                .isInstanceOf(CbaException.class);
        }

        @Test
        @DisplayName("getBeneficiary throws 404 when customer does not own it")
        void getBeneficiary_wrongCustomer_throws() {
            UUID otherCustomer = UUID.randomUUID();
            when(beneficiaryRepository.findById(beneficiaryId)).thenReturn(Optional.of(beneficiary));

            assertThatThrownBy(() -> service.getBeneficiary(otherCustomer, beneficiaryId))
                .isInstanceOf(CbaException.class);
        }
    }

    @Nested
    @DisplayName("Create")
    class CreateOps {

        @Test
        @DisplayName("createBeneficiary saves and returns beneficiary")
        void createBeneficiary_success() {
            when(beneficiaryRepository.save(any())).thenReturn(beneficiary);

            BeneficiaryService.CreateBeneficiaryRequest req =
                new BeneficiaryService.CreateBeneficiaryRequest(
                    "John Doe", "001-SAV-0001234", "058", new BigDecimal("10000.00")
                );
            Beneficiary result = service.createBeneficiary(customerId, req);
            assertThat(result.getName()).isEqualTo("John Doe");
            verify(accountNumberAlgorithmService).validatePaymentDestination("001-SAV-0001234");
        }
    }

    @Nested
    @DisplayName("Update")
    class UpdateOps {

        @Test
        @DisplayName("updateBeneficiary saves changes")
        void updateBeneficiary_success() {
            when(beneficiaryRepository.findById(beneficiaryId)).thenReturn(Optional.of(beneficiary));
            when(beneficiaryRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            BeneficiaryService.CreateBeneficiaryRequest req =
                new BeneficiaryService.CreateBeneficiaryRequest(
                    "Jane Doe", "001-SAV-0009999", "058", new BigDecimal("5000.00")
                );
            Beneficiary result = service.updateBeneficiary(customerId, beneficiaryId, req);
            assertThat(result.getName()).isEqualTo("Jane Doe");
        }
    }

    @Nested
    @DisplayName("Deactivate")
    class DeactivateOps {

        @Test
        @DisplayName("deactivate sets active=false")
        void deactivate_success() {
            when(beneficiaryRepository.findById(beneficiaryId)).thenReturn(Optional.of(beneficiary));

            assertThatCode(() -> service.deactivate(customerId, beneficiaryId)).doesNotThrowAnyException();
            verify(beneficiaryRepository).save(argThat(b -> !b.isActive()));
        }
    }
}
