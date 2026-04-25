package com.cba.accounting;

import com.cba.common.response.ApiResponse;
import com.cba.office.OfficeRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@Tag(name = "GL Accounting", description = "Chart of accounts, journal entries, financial activity mappings, GL closures")
public class GlAccountingController {

    private final GlAccountingService glService;
    private final GlAccountRepository glAccountRepository;
    private final JournalEntryRepository journalEntryRepository;
    private final GlClosureRepository glClosureRepository;
    private final OfficeRepository officeRepository;
    private final FinancialActivityAccountRepository financialActivityAccountRepository;

    // ── Chart of Accounts ─────────────────────────────────────────────────────

    @GetMapping("/api/v1/glaccounts")
    @PreAuthorize("hasAnyRole('ADMIN', 'TELLER')")
    @Operation(summary = "List all GL accounts")
    public ResponseEntity<ApiResponse<List<GlAccount>>> listAccounts() {
        return ResponseEntity.ok(ApiResponse.ok(glAccountRepository.findByDisabledFalse()));
    }

    @GetMapping("/api/v1/glaccounts/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'TELLER')")
    @Operation(summary = "Get a GL account")
    public ResponseEntity<ApiResponse<GlAccount>> getAccount(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(
                glAccountRepository.findById(id).orElseThrow(
                        () -> com.cba.common.exception.CbaException.notFound("GlAccount", id.toString()))));
    }

    // ── Journal Entries ───────────────────────────────────────────────────────

    @PostMapping("/api/v1/journalentries")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Post manual journal entries (balanced debit/credit pairs)")
    public ResponseEntity<ApiResponse<List<JournalEntry>>> postManualEntries(
            @Valid @RequestBody ManualJournalRequest req) {
        return ResponseEntity.ok(ApiResponse.ok(glService.postManualEntries(req)));
    }

    @GetMapping("/api/v1/journalentries")
    @PreAuthorize("hasAnyRole('ADMIN', 'TELLER')")
    @Operation(summary = "List journal entries for a date range")
    public ResponseEntity<ApiResponse<List<JournalEntry>>> listEntries(
            @RequestParam LocalDate from,
            @RequestParam LocalDate to) {
        return ResponseEntity.ok(ApiResponse.ok(
                journalEntryRepository.findByTransactionDateBetween(from, to,
                        org.springframework.data.domain.PageRequest.of(0, 1000)).getContent()));
    }

    @PostMapping("/api/v1/journalentries/{id}/reverse")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Reverse a journal entry")
    public ResponseEntity<ApiResponse<Void>> reverseEntry(@PathVariable UUID id) {
        glService.reverseJournalEntry(id);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    // ── GL Closures ───────────────────────────────────────────────────────────

    @PostMapping("/api/v1/glclosures")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Close the GL for a given date and office")
    public ResponseEntity<ApiResponse<GlClosure>> createClosure(
            @RequestParam UUID officeId,
            @RequestParam LocalDate closingDate,
            @RequestParam(required = false) String comments) {
        return ResponseEntity.ok(ApiResponse.ok(
                glService.createClosure(officeId, closingDate, comments, officeRepository)));
    }

    @GetMapping("/api/v1/glclosures")
    @PreAuthorize("hasAnyRole('ADMIN', 'TELLER')")
    @Operation(summary = "List GL closures for an office")
    public ResponseEntity<ApiResponse<List<GlClosure>>> listClosures(@RequestParam UUID officeId) {
        return ResponseEntity.ok(ApiResponse.ok(
                glClosureRepository.findByOfficeIdOrderByClosingDateDesc(officeId)));
    }

    // ── Trial Balance ─────────────────────────────────────────────────────────

    @GetMapping("/api/v1/accounting/trial-balance")
    @PreAuthorize("hasAnyRole('ADMIN', 'TELLER')")
    @Operation(summary = "Generate a trial balance for a date range")
    public ResponseEntity<ApiResponse<GlAccountingService.TrialBalanceResponse>> getTrialBalance(
            @RequestParam LocalDate fromDate,
            @RequestParam LocalDate toDate) {
        return ResponseEntity.ok(ApiResponse.ok(glService.getTrialBalance(fromDate, toDate)));
    }

    // ── Financial Activity Accounts ───────────────────────────────────────────

    record FinancialActivityRequest(
        FinancialActivityAccount.FinancialActivity financialActivity,
        UUID glAccountId) {}

    @GetMapping("/api/v1/financialactivityaccounts")
    @PreAuthorize("hasAnyRole('ADMIN', 'TELLER')")
    @Operation(summary = "List all financial activity → GL account mappings")
    public ResponseEntity<ApiResponse<List<FinancialActivityAccount>>> listActivityAccounts() {
        return ResponseEntity.ok(ApiResponse.ok(financialActivityAccountRepository.findAll()));
    }

    @GetMapping("/api/v1/financialactivityaccounts/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'TELLER')")
    @Operation(summary = "Get a single financial activity mapping")
    public ResponseEntity<ApiResponse<FinancialActivityAccount>> getActivityAccount(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(
                financialActivityAccountRepository.findById(id)
                        .orElseThrow(() -> com.cba.common.exception.CbaException.notFound("FinancialActivityAccount", id.toString()))));
    }

    @PostMapping("/api/v1/financialactivityaccounts")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Map a financial activity to a GL account")
    public ResponseEntity<ApiResponse<FinancialActivityAccount>> createActivityAccount(
            @RequestBody FinancialActivityRequest req) {
        GlAccount gl = glAccountRepository.findById(req.glAccountId())
                .orElseThrow(() -> com.cba.common.exception.CbaException.notFound("GlAccount", req.glAccountId().toString()));
        FinancialActivityAccount mapping = new FinancialActivityAccount();
        mapping.setFinancialActivity(req.financialActivity());
        mapping.setGlAccount(gl);
        return ResponseEntity.ok(ApiResponse.ok(financialActivityAccountRepository.save(mapping)));
    }

    @PutMapping("/api/v1/financialactivityaccounts/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Update the GL account linked to a financial activity")
    public ResponseEntity<ApiResponse<FinancialActivityAccount>> updateActivityAccount(
            @PathVariable UUID id, @RequestBody FinancialActivityRequest req) {
        FinancialActivityAccount mapping = financialActivityAccountRepository.findById(id)
                .orElseThrow(() -> com.cba.common.exception.CbaException.notFound("FinancialActivityAccount", id.toString()));
        GlAccount gl = glAccountRepository.findById(req.glAccountId())
                .orElseThrow(() -> com.cba.common.exception.CbaException.notFound("GlAccount", req.glAccountId().toString()));
        mapping.setFinancialActivity(req.financialActivity());
        mapping.setGlAccount(gl);
        return ResponseEntity.ok(ApiResponse.ok(financialActivityAccountRepository.save(mapping)));
    }

    @DeleteMapping("/api/v1/financialactivityaccounts/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Delete a financial activity mapping")
    public ResponseEntity<ApiResponse<Void>> deleteActivityAccount(@PathVariable UUID id) {
        FinancialActivityAccount mapping = financialActivityAccountRepository.findById(id)
                .orElseThrow(() -> com.cba.common.exception.CbaException.notFound("FinancialActivityAccount", id.toString()));
        financialActivityAccountRepository.delete(mapping);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }
}
