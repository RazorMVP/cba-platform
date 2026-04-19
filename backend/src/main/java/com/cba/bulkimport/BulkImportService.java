package com.cba.bulkimport;

import com.cba.bulkimport.BulkImportResult.RowError;
import com.cba.customer.CustomerService;
import com.cba.customer.dto.CreateCustomerRequest;
import com.cba.loan.LoanService;
import com.cba.loan.dto.LoanApplicationRequest;
import lombok.RequiredArgsConstructor;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class BulkImportService {

    private final BulkImportJobRepository jobRepo;
    private final CustomerService customerService;
    private final LoanService loanService;

    // ── Customer import ───────────────────────────────────────────────────────

    public BulkImportResult importCustomers(MultipartFile file, Authentication auth) throws Exception {
        BulkImportJob job = new BulkImportJob();
        job.setEntityType("CUSTOMERS");
        job.setFileName(file.getOriginalFilename());
        job.setImportedBy(auth != null ? auth.getName() : "system");

        List<RowError> errors = new ArrayList<>();
        int row = 1;
        int success = 0;

        try (CSVParser parser = CSVFormat.DEFAULT.builder()
                .setHeader()
                .setSkipHeaderRecord(true)
                .setTrim(true)
                .build()
                .parse(new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {

            for (CSVRecord record : parser) {
                row++;
                try {
                    String firstName  = required(record, "firstName",  row, errors);
                    String lastName   = required(record, "lastName",   row, errors);
                    String email      = required(record, "email",      row, errors);
                    if (firstName == null || lastName == null || email == null) continue;

                    LocalDate dob = null;
                    String dobStr = get(record, "dateOfBirth");
                    if (dobStr != null && !dobStr.isBlank()) {
                        try { dob = LocalDate.parse(dobStr); }
                        catch (DateTimeParseException e) {
                            errors.add(new RowError(row, "dateOfBirth", "Expected ISO date (yyyy-MM-dd), got: " + dobStr));
                            continue;
                        }
                    }

                    CreateCustomerRequest req = new CreateCustomerRequest(
                            firstName, lastName, email,
                            get(record, "phone"),
                            get(record, "nationalId"),
                            dob,
                            get(record, "notes")
                    );
                    customerService.createCustomer(req);
                    success++;
                } catch (Exception e) {
                    errors.add(new RowError(row, "—", e.getMessage()));
                }
            }
        }

        job.setTotalRows(row - 1);
        job.setSuccessCount(success);
        job.setFailureCount(errors.size());
        job.setStatus(errors.isEmpty() ? "COMPLETED" : (success == 0 ? "FAILED" : "PARTIAL"));
        if (!errors.isEmpty()) {
            job.setErrorSummary(errors.size() + " row(s) failed — first: " + errors.get(0).message());
        }
        jobRepo.save(job);
        return BulkImportResult.of(job, errors);
    }

    // ── Loan import ───────────────────────────────────────────────────────────

    public BulkImportResult importLoans(MultipartFile file, Authentication auth) throws Exception {
        BulkImportJob job = new BulkImportJob();
        job.setEntityType("LOANS");
        job.setFileName(file.getOriginalFilename());
        job.setImportedBy(auth != null ? auth.getName() : "system");

        List<RowError> errors = new ArrayList<>();
        int row = 1;
        int success = 0;

        try (CSVParser parser = CSVFormat.DEFAULT.builder()
                .setHeader()
                .setSkipHeaderRecord(true)
                .setTrim(true)
                .build()
                .parse(new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {

            for (CSVRecord record : parser) {
                row++;
                try {
                    String customerIdStr = required(record, "customerId",  row, errors);
                    String productIdStr  = required(record, "productId",   row, errors);
                    String amountStr     = required(record, "principalAmount", row, errors);
                    if (customerIdStr == null || productIdStr == null || amountStr == null) continue;

                    UUID customerId, productId;
                    BigDecimal principal;
                    try {
                        customerId = UUID.fromString(customerIdStr);
                        productId  = UUID.fromString(productIdStr);
                    } catch (IllegalArgumentException e) {
                        errors.add(new RowError(row, "id", "Invalid UUID in customerId or productId"));
                        continue;
                    }
                    try {
                        principal = new BigDecimal(amountStr);
                    } catch (NumberFormatException e) {
                        errors.add(new RowError(row, "principalAmount", "Not a valid number: " + amountStr));
                        continue;
                    }

                    String linkedAccountStr = required(record, "linkedAccountId", row, errors);
                    if (linkedAccountStr == null) continue;
                    UUID linkedAccountId;
                    try { linkedAccountId = UUID.fromString(linkedAccountStr); }
                    catch (IllegalArgumentException e) {
                        errors.add(new RowError(row, "linkedAccountId", "Not a valid UUID: " + linkedAccountStr));
                        continue;
                    }

                    Integer termMonths = null;
                    String termStr = get(record, "termMonths");
                    if (termStr != null && !termStr.isBlank()) {
                        try { termMonths = Integer.parseInt(termStr); }
                        catch (NumberFormatException e) {
                            errors.add(new RowError(row, "termMonths", "Not a valid integer: " + termStr));
                            continue;
                        }
                    }

                    loanService.applyForLoan(new LoanApplicationRequest(
                            customerId, productId, linkedAccountId,
                            principal, termMonths,
                            get(record, "notes")
                    ));
                    success++;
                } catch (Exception e) {
                    errors.add(new RowError(row, "—", e.getMessage()));
                }
            }
        }

        job.setTotalRows(row - 1);
        job.setSuccessCount(success);
        job.setFailureCount(errors.size());
        job.setStatus(errors.isEmpty() ? "COMPLETED" : (success == 0 ? "FAILED" : "PARTIAL"));
        if (!errors.isEmpty()) {
            job.setErrorSummary(errors.size() + " row(s) failed — first: " + errors.get(0).message());
        }
        jobRepo.save(job);
        return BulkImportResult.of(job, errors);
    }

    // ── History ───────────────────────────────────────────────────────────────

    public List<BulkImportJob> recentJobs(String entityType) {
        return entityType != null
                ? jobRepo.findTop20ByEntityTypeOrderByCreatedAtDesc(entityType)
                : jobRepo.findTop20ByOrderByCreatedAtDesc();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private String required(CSVRecord r, String col, int row, List<RowError> errors) {
        String v = get(r, col);
        if (v == null || v.isBlank()) {
            errors.add(new RowError(row, col, col + " is required"));
            return null;
        }
        return v;
    }

    private String get(CSVRecord r, String col) {
        try { return r.get(col); }
        catch (IllegalArgumentException e) { return null; }
    }
}
