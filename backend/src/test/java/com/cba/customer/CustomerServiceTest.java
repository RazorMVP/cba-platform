package com.cba.customer;

import com.cba.audit.AuditLogService;
import com.cba.common.exception.CbaException;
import com.cba.customer.dto.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("CustomerService — unit tests")
class CustomerServiceTest {

    @Mock CustomerRepository customerRepository;
    @Mock AuditLogService auditLogService;
    @Spy  CustomerMapper customerMapper = new CustomerMapperImpl();

    @InjectMocks CustomerService customerService;

    private UUID customerId;
    private Customer pendingCustomer;
    private Customer activeCustomer;

    @BeforeEach
    void setUp() {
        customerId = UUID.randomUUID();

        pendingCustomer = new Customer();
        pendingCustomer.setId(customerId);
        pendingCustomer.setFirstName("John");
        pendingCustomer.setLastName("Doe");
        pendingCustomer.setEmail("john@example.com");
        pendingCustomer.setKycStatus(KycStatus.PENDING_KYC);

        activeCustomer = new Customer();
        activeCustomer.setId(customerId);
        activeCustomer.setFirstName("Jane");
        activeCustomer.setLastName("Smith");
        activeCustomer.setEmail("jane@example.com");
        activeCustomer.setKycStatus(KycStatus.ACTIVE);
    }

    @Nested
    @DisplayName("createCustomer")
    class CreateCustomer {

        @Test
        @DisplayName("persists customer with PENDING_KYC status and audits creation")
        void createCustomer_setsInitialKycStatus() {
            CreateCustomerRequest req = new CreateCustomerRequest(
                "John", "Doe", "john@example.com",
                "+1-555-0100", "NID123456",
                LocalDate.of(1985, 3, 15), null
            );
            when(customerRepository.save(any(Customer.class))).thenReturn(pendingCustomer);

            CustomerResponse response = customerService.createCustomer(req);

            assertThat(response.kycStatus()).isEqualTo(KycStatus.PENDING_KYC);
            verify(auditLogService).log(eq("CUSTOMER"), any(), eq("CREATED"), isNull(), any());
        }
    }

    @Nested
    @DisplayName("getCustomer")
    class GetCustomer {

        @Test
        @DisplayName("returns customer response when found")
        void getCustomer_found_returnsResponse() {
            when(customerRepository.findById(customerId)).thenReturn(Optional.of(activeCustomer));

            CustomerResponse response = customerService.getCustomer(customerId);

            assertThat(response).isNotNull();
        }

        @Test
        @DisplayName("throws when customer not found")
        void getCustomer_notFound_throws() {
            when(customerRepository.findById(customerId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> customerService.getCustomer(customerId))
                .isInstanceOf(CbaException.class)
                .hasMessageContaining("not found");
        }
    }

    @Nested
    @DisplayName("listCustomers")
    class ListCustomers {

        @Test
        @DisplayName("returns page of all customers")
        void listCustomers_returnsPage() {
            Page<Customer> page = new PageImpl<>(List.of(activeCustomer));
            when(customerRepository.findAll(any(Pageable.class))).thenReturn(page);

            Page<CustomerResponse> result = customerService.listCustomers(Pageable.unpaged());
            assertThat(result.getContent()).hasSize(1);
        }

        @Test
        @DisplayName("returns page filtered by KYC status")
        void listCustomersByStatus_returnsFiltered() {
            Page<Customer> page = new PageImpl<>(List.of(activeCustomer));
            when(customerRepository.findByKycStatus(eq(KycStatus.ACTIVE), any(Pageable.class)))
                .thenReturn(page);

            Page<CustomerResponse> result = customerService.listCustomersByStatus(KycStatus.ACTIVE, Pageable.unpaged());
            assertThat(result.getContent()).hasSize(1);
        }
    }

    @Nested
    @DisplayName("updateCustomer")
    class UpdateCustomer {

        @Test
        @DisplayName("updates non-null fields and audits the change")
        void updateCustomer_appliesNonNullFields() {
            when(customerRepository.findById(customerId)).thenReturn(Optional.of(activeCustomer));
            when(customerRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            UpdateCustomerRequest req = new UpdateCustomerRequest(
                "NewFirst", null, null, null, null, null, null
            );
            CustomerResponse response = customerService.updateCustomer(customerId, req);

            assertThat(response).isNotNull();
            verify(auditLogService).log(eq("CUSTOMER"), eq(customerId.toString()),
                eq("UPDATED"), isNull(), eq("PROFILE_UPDATED"));
        }
    }

    @Nested
    @DisplayName("updateKycStatus")
    class UpdateKycStatus {

        @Test
        @DisplayName("transitions KYC status and logs the change")
        void updateKycStatus_transitionsCorrectly() {
            when(customerRepository.findById(customerId)).thenReturn(Optional.of(pendingCustomer));
            when(customerRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            UpdateKycStatusRequest req = new UpdateKycStatusRequest(KycStatus.ACTIVE, "KYC verified");
            CustomerResponse response = customerService.updateKycStatus(customerId, req);

            assertThat(response.kycStatus()).isEqualTo(KycStatus.ACTIVE);
            verify(auditLogService).log(eq("CUSTOMER"), eq(customerId.toString()),
                eq("KYC_STATUS_CHANGED"), eq("PENDING_KYC"), eq("ACTIVE"));
        }
    }

    @Nested
    @DisplayName("executeCommand")
    class ExecuteCommand {

        @Test
        @DisplayName("activate transitions PENDING_KYC → ACTIVE")
        void activate_fromPendingKyc_succeeds() {
            when(customerRepository.findById(customerId)).thenReturn(Optional.of(pendingCustomer));
            when(customerRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            CustomerResponse resp = customerService.executeCommand(customerId, "activate", new CustomerCommandRequest());
            assertThat(resp.kycStatus()).isEqualTo(KycStatus.ACTIVE);
        }

        @Test
        @DisplayName("activate throws when customer is not PENDING_KYC")
        void activate_wrongStatus_throws() {
            when(customerRepository.findById(customerId)).thenReturn(Optional.of(activeCustomer));

            assertThatThrownBy(() -> customerService.executeCommand(customerId, "activate", new CustomerCommandRequest()))
                .isInstanceOf(CbaException.class)
                .hasMessageContaining("Expected status");
        }

        @Test
        @DisplayName("reject transitions PENDING_KYC → REJECTED with reason")
        void reject_fromPendingKyc_succeeds() {
            when(customerRepository.findById(customerId)).thenReturn(Optional.of(pendingCustomer));
            when(customerRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            CustomerCommandRequest req = new CustomerCommandRequest("Incomplete docs", null, null, null, null);
            CustomerResponse resp = customerService.executeCommand(customerId, "reject", req);
            assertThat(resp.kycStatus()).isEqualTo(KycStatus.REJECTED);
        }

        @Test
        @DisplayName("withdraw transitions ACTIVE → WITHDRAWN")
        void withdraw_fromActive_succeeds() {
            when(customerRepository.findById(customerId)).thenReturn(Optional.of(activeCustomer));
            when(customerRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            CustomerCommandRequest req = new CustomerCommandRequest("Customer request", null, null, null, null);
            CustomerResponse resp = customerService.executeCommand(customerId, "withdraw", req);
            assertThat(resp.kycStatus()).isEqualTo(KycStatus.WITHDRAWN);
        }

        @Test
        @DisplayName("reactivate transitions SUSPENDED → ACTIVE")
        void reactivate_fromSuspended_succeeds() {
            activeCustomer.setKycStatus(KycStatus.SUSPENDED);
            when(customerRepository.findById(customerId)).thenReturn(Optional.of(activeCustomer));
            when(customerRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            CustomerResponse resp = customerService.executeCommand(customerId, "reactivate", new CustomerCommandRequest());
            assertThat(resp.kycStatus()).isEqualTo(KycStatus.ACTIVE);
        }

        @Test
        @DisplayName("suspend transitions ACTIVE → SUSPENDED")
        void suspend_fromActive_succeeds() {
            when(customerRepository.findById(customerId)).thenReturn(Optional.of(activeCustomer));
            when(customerRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            CustomerCommandRequest req = new CustomerCommandRequest("Policy violation", null, null, null, null);
            CustomerResponse resp = customerService.executeCommand(customerId, "suspend", req);
            assertThat(resp.kycStatus()).isEqualTo(KycStatus.SUSPENDED);
        }

        @Test
        @DisplayName("close transitions ACTIVE → CLOSED")
        void close_fromActive_succeeds() {
            when(customerRepository.findById(customerId)).thenReturn(Optional.of(activeCustomer));
            when(customerRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            CustomerCommandRequest req = new CustomerCommandRequest("Account closed on request", null, null, null, null);
            CustomerResponse resp = customerService.executeCommand(customerId, "close", req);
            assertThat(resp.kycStatus()).isEqualTo(KycStatus.CLOSED);
        }

        @Test
        @DisplayName("undoRejection transitions REJECTED → PENDING_KYC")
        void undoRejection_fromRejected_succeeds() {
            pendingCustomer.setKycStatus(KycStatus.REJECTED);
            when(customerRepository.findById(customerId)).thenReturn(Optional.of(pendingCustomer));
            when(customerRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            CustomerResponse resp = customerService.executeCommand(customerId, "undoRejection", new CustomerCommandRequest());
            assertThat(resp.kycStatus()).isEqualTo(KycStatus.PENDING_KYC);
        }

        @Test
        @DisplayName("undoWithdrawal transitions WITHDRAWN → PENDING_KYC")
        void undoWithdrawal_fromWithdrawn_succeeds() {
            pendingCustomer.setKycStatus(KycStatus.WITHDRAWN);
            when(customerRepository.findById(customerId)).thenReturn(Optional.of(pendingCustomer));
            when(customerRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            CustomerResponse resp = customerService.executeCommand(customerId, "undoWithdrawal", new CustomerCommandRequest());
            assertThat(resp.kycStatus()).isEqualTo(KycStatus.PENDING_KYC);
        }

        @Test
        @DisplayName("assignStaff sets staffId on customer")
        void assignStaff_setsStaffId() {
            UUID staffId = UUID.randomUUID();
            when(customerRepository.findById(customerId)).thenReturn(Optional.of(activeCustomer));
            when(customerRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            CustomerCommandRequest req = new CustomerCommandRequest(null, staffId, null, null, null);
            CustomerResponse resp = customerService.executeCommand(customerId, "assignStaff", req);
            assertThat(resp).isNotNull();
        }

        @Test
        @DisplayName("assignStaff throws when staffId is missing")
        void assignStaff_missingStaffId_throws() {
            when(customerRepository.findById(customerId)).thenReturn(Optional.of(activeCustomer));

            assertThatThrownBy(() -> customerService.executeCommand(customerId, "assignStaff", new CustomerCommandRequest()))
                .isInstanceOf(CbaException.class)
                .hasMessageContaining("staffId is required");
        }

        @Test
        @DisplayName("unassignStaff clears staffId")
        void unassignStaff_clearsStaffId() {
            activeCustomer.setStaffId(UUID.randomUUID());
            when(customerRepository.findById(customerId)).thenReturn(Optional.of(activeCustomer));
            when(customerRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            CustomerResponse resp = customerService.executeCommand(customerId, "unassignStaff", new CustomerCommandRequest());
            assertThat(resp).isNotNull();
        }

        @Test
        @DisplayName("proposeTransfer sets TRANSFER_IN_PROGRESS status")
        void proposeTransfer_setsTransferStatus() {
            UUID officeId = UUID.randomUUID();
            when(customerRepository.findById(customerId)).thenReturn(Optional.of(activeCustomer));
            when(customerRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            CustomerCommandRequest req = new CustomerCommandRequest(null, null, officeId, null, "Branch transfer");
            CustomerResponse resp = customerService.executeCommand(customerId, "proposeTransfer", req);
            assertThat(resp.kycStatus()).isEqualTo(KycStatus.TRANSFER_IN_PROGRESS);
        }

        @Test
        @DisplayName("proposeTransfer throws when destinationOfficeId missing")
        void proposeTransfer_missingOfficeId_throws() {
            when(customerRepository.findById(customerId)).thenReturn(Optional.of(activeCustomer));

            assertThatThrownBy(() -> customerService.executeCommand(customerId, "proposeTransfer", new CustomerCommandRequest()))
                .isInstanceOf(CbaException.class)
                .hasMessageContaining("destinationOfficeId is required");
        }

        @Test
        @DisplayName("acceptTransfer transitions TRANSFER_IN_PROGRESS → ACTIVE")
        void acceptTransfer_completesTransfer() {
            activeCustomer.setKycStatus(KycStatus.TRANSFER_IN_PROGRESS);
            activeCustomer.setTransferToOfficeId(UUID.randomUUID());
            when(customerRepository.findById(customerId)).thenReturn(Optional.of(activeCustomer));
            when(customerRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            CustomerResponse resp = customerService.executeCommand(customerId, "acceptTransfer", new CustomerCommandRequest());
            assertThat(resp.kycStatus()).isEqualTo(KycStatus.ACTIVE);
        }

        @Test
        @DisplayName("rejectTransfer transitions TRANSFER_IN_PROGRESS → ACTIVE")
        void rejectTransfer_cancelsTransfer() {
            activeCustomer.setKycStatus(KycStatus.TRANSFER_IN_PROGRESS);
            when(customerRepository.findById(customerId)).thenReturn(Optional.of(activeCustomer));
            when(customerRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            CustomerResponse resp = customerService.executeCommand(customerId, "rejectTransfer", new CustomerCommandRequest());
            assertThat(resp.kycStatus()).isEqualTo(KycStatus.ACTIVE);
        }

        @Test
        @DisplayName("withdrawTransfer transitions TRANSFER_IN_PROGRESS → ACTIVE")
        void withdrawTransfer_cancelsTransfer() {
            activeCustomer.setKycStatus(KycStatus.TRANSFER_IN_PROGRESS);
            when(customerRepository.findById(customerId)).thenReturn(Optional.of(activeCustomer));
            when(customerRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            CustomerResponse resp = customerService.executeCommand(customerId, "withdrawTransfer", new CustomerCommandRequest());
            assertThat(resp.kycStatus()).isEqualTo(KycStatus.ACTIVE);
        }

        @Test
        @DisplayName("unknown command throws UNKNOWN_COMMAND")
        void unknownCommand_throws() {
            when(customerRepository.findById(customerId)).thenReturn(Optional.of(activeCustomer));

            assertThatThrownBy(() -> customerService.executeCommand(customerId, "teleport", new CustomerCommandRequest()))
                .isInstanceOf(CbaException.class)
                .hasMessageContaining("Unknown customer command");
        }
    }

    @Nested
    @DisplayName("deleteCustomer")
    class DeleteCustomer {

        @Test
        @DisplayName("deletes PENDING_KYC customer successfully")
        void deleteCustomer_pendingKyc_succeeds() {
            when(customerRepository.findById(customerId)).thenReturn(Optional.of(pendingCustomer));

            assertThatCode(() -> customerService.deleteCustomer(customerId))
                .doesNotThrowAnyException();
            verify(customerRepository).delete(pendingCustomer);
            verify(auditLogService).log(eq("CUSTOMER"), eq(customerId.toString()),
                eq("DELETED"), eq("PENDING_KYC"), isNull());
        }

        @Test
        @DisplayName("throws when trying to delete non-PENDING_KYC customer")
        void deleteCustomer_activeCustomer_throws() {
            when(customerRepository.findById(customerId)).thenReturn(Optional.of(activeCustomer));

            assertThatThrownBy(() -> customerService.deleteCustomer(customerId))
                .isInstanceOf(CbaException.class)
                .hasMessageContaining("Only PENDING_KYC customers can be deleted");
        }
    }
}
