package com.cba.bulkimport;

import com.cba.customer.CustomerService;
import com.cba.customer.dto.CreateCustomerRequest;
import com.cba.loan.LoanService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.Authentication;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("BulkImportService — unit tests")
class BulkImportServiceTest {

    @Mock BulkImportJobRepository jobRepo;
    @Mock CustomerService customerService;
    @Mock LoanService loanService;
    @Mock Authentication auth;

    @InjectMocks BulkImportService service;

    private void stubJobSave() {
        doAnswer(inv -> {
            BulkImportJob j = inv.getArgument(0);
            if (j.getId() == null) j.setId(java.util.UUID.randomUUID());
            return null;
        }).when(jobRepo).save(any(BulkImportJob.class));
    }

    @Nested
    @DisplayName("Customer Import")
    class CustomerImport {

        @BeforeEach
        void setUp() {
            when(auth.getName()).thenReturn("admin");
            stubJobSave();
        }

        @Test
        @DisplayName("importCustomers with valid CSV creates customers and returns COMPLETED")
        void importCustomers_success() throws Exception {
            String csv = "firstName,lastName,email,dateOfBirth\n" +
                         "Alice,Smith,alice@example.com,1990-05-15\n" +
                         "Bob,Jones,bob@example.com,1985-11-20\n";
            MockMultipartFile file = mockCsv("customers.csv", csv);

            BulkImportResult result = service.importCustomers(file, auth);
            assertThat(result.status()).isEqualTo("COMPLETED");
            assertThat(result.successCount()).isEqualTo(2);
            assertThat(result.errors()).isEmpty();
            verify(customerService, times(2)).createCustomer(any(CreateCustomerRequest.class));
        }

        @Test
        @DisplayName("importCustomers skips rows with missing required fields")
        void importCustomers_missingFields_recordsErrors() throws Exception {
            String csv = "firstName,lastName,email\n" +
                         ",Smith,smith@example.com\n";  // missing firstName
            MockMultipartFile file = mockCsv("customers.csv", csv);

            BulkImportResult result = service.importCustomers(file, auth);
            assertThat(result.errors()).isNotEmpty();
            assertThat(result.successCount()).isEqualTo(0);
            verify(customerService, never()).createCustomer(any());
        }

        @Test
        @DisplayName("importCustomers with invalid date records error and continues")
        void importCustomers_invalidDate_recordsError() throws Exception {
            String csv = "firstName,lastName,email,dateOfBirth\n" +
                         "Alice,Smith,alice@example.com,not-a-date\n";
            MockMultipartFile file = mockCsv("customers.csv", csv);

            BulkImportResult result = service.importCustomers(file, auth);
            assertThat(result.errors()).hasSize(1);
            assertThat(result.errors().get(0).field()).isEqualTo("dateOfBirth");
        }

        @Test
        @DisplayName("importCustomers marks PARTIAL when some rows succeed and some fail")
        void importCustomers_partial() throws Exception {
            String csv = "firstName,lastName,email\n" +
                         "Alice,Smith,alice@example.com\n" +
                         ",Jones,\n";  // missing required fields
            MockMultipartFile file = mockCsv("customers.csv", csv);

            BulkImportResult result = service.importCustomers(file, auth);
            assertThat(result.status()).isEqualTo("PARTIAL");
        }

        @Test
        @DisplayName("importCustomers captures exception from CustomerService as row error")
        void importCustomers_serviceThrows_recordsError() throws Exception {
            String csv = "firstName,lastName,email\n" +
                         "Alice,Smith,alice@example.com\n";
            MockMultipartFile file = mockCsv("customers.csv", csv);

            doThrow(new RuntimeException("Duplicate email")).when(customerService).createCustomer(any());

            BulkImportResult result = service.importCustomers(file, auth);
            assertThat(result.errors()).hasSize(1);
            assertThat(result.errors().get(0).message()).contains("Duplicate email");
        }
    }

    @Nested
    @DisplayName("Loan Import")
    class LoanImport {

        @BeforeEach
        void setUp() {
            when(auth.getName()).thenReturn("admin");
            stubJobSave();
        }

        @Test
        @DisplayName("importLoans with valid CSV creates loans and returns COMPLETED")
        void importLoans_success() throws Exception {
            String customerId = "550e8400-e29b-41d4-a716-446655440000";
            String productId  = "6ba7b810-9dad-11d1-80b4-00c04fd430c8";
            String accountId  = "6ba7b811-9dad-11d1-80b4-00c04fd430c8";
            String csv = "customerId,productId,principalAmount,linkedAccountId,termMonths\n" +
                         customerId + "," + productId + ",50000.00," + accountId + ",12\n";
            MockMultipartFile file = mockCsv("loans.csv", csv);

            BulkImportResult result = service.importLoans(file, auth);
            assertThat(result.status()).isEqualTo("COMPLETED");
            assertThat(result.successCount()).isEqualTo(1);
            verify(loanService, times(1)).applyForLoan(any());
        }

        @Test
        @DisplayName("importLoans with invalid UUID records error")
        void importLoans_invalidUuid_recordsError() throws Exception {
            String csv = "customerId,productId,principalAmount,linkedAccountId\n" +
                         "not-a-uuid,also-not-uuid,5000.00,neither-valid\n";
            MockMultipartFile file = mockCsv("loans.csv", csv);

            BulkImportResult result = service.importLoans(file, auth);
            assertThat(result.errors()).isNotEmpty();
            verify(loanService, never()).applyForLoan(any());
        }

        @Test
        @DisplayName("importLoans with invalid amount records error")
        void importLoans_invalidAmount_recordsError() throws Exception {
            String customerId = "550e8400-e29b-41d4-a716-446655440000";
            String productId  = "6ba7b810-9dad-11d1-80b4-00c04fd430c8";
            String accountId  = "6ba7b811-9dad-11d1-80b4-00c04fd430c8";
            String csv = "customerId,productId,principalAmount,linkedAccountId\n" +
                         customerId + "," + productId + ",not-a-number," + accountId + "\n";
            MockMultipartFile file = mockCsv("loans.csv", csv);

            BulkImportResult result = service.importLoans(file, auth);
            assertThat(result.errors()).hasSize(1);
            assertThat(result.errors().get(0).field()).isEqualTo("principalAmount");
        }

        @Test
        @DisplayName("importLoans with invalid termMonths records error")
        void importLoans_invalidTermMonths_recordsError() throws Exception {
            String customerId = "550e8400-e29b-41d4-a716-446655440000";
            String productId  = "6ba7b810-9dad-11d1-80b4-00c04fd430c8";
            String accountId  = "6ba7b811-9dad-11d1-80b4-00c04fd430c8";
            String csv = "customerId,productId,principalAmount,linkedAccountId,termMonths\n" +
                         customerId + "," + productId + ",5000.00," + accountId + ",abc\n";
            MockMultipartFile file = mockCsv("loans.csv", csv);

            BulkImportResult result = service.importLoans(file, auth);
            assertThat(result.errors()).hasSize(1);
            assertThat(result.errors().get(0).field()).isEqualTo("termMonths");
        }
    }

    @Nested
    @DisplayName("Recent Jobs")
    class RecentJobs {

        @Test
        @DisplayName("recentJobs with entityType filters by type")
        void recentJobs_withType() {
            BulkImportJob job = new BulkImportJob();
            when(jobRepo.findTop20ByEntityTypeOrderByCreatedAtDesc("CUSTOMERS"))
                .thenReturn(List.of(job));

            assertThat(service.recentJobs("CUSTOMERS")).hasSize(1);
        }

        @Test
        @DisplayName("recentJobs without entityType returns all recent")
        void recentJobs_noType() {
            when(jobRepo.findTop20ByOrderByCreatedAtDesc()).thenReturn(List.of());
            assertThat(service.recentJobs(null)).isEmpty();
        }
    }

    private MockMultipartFile mockCsv(String filename, String content) {
        return new MockMultipartFile(
            "file", filename, "text/csv",
            content.getBytes(StandardCharsets.UTF_8)
        );
    }
}
