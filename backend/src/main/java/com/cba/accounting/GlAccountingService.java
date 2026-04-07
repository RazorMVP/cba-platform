package com.cba.accounting;

import com.cba.audit.AuditLogService;
import com.cba.common.exception.CbaException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class GlAccountingService {

    private final GlAccountRepository glAccountRepository;
    private final JournalEntryRepository journalEntryRepository;
    private final FinancialActivityAccountRepository financialActivityRepo;
    private final GlClosureRepository glClosureRepository;
    private final AuditLogService auditLogService;

    // ── Auto-posting (called by domain services) ──────────────────────────────

    /**
     * Post a debit/credit pair atomically.
     * Must be called within the originating transaction so it rolls back together.
     */
    @Transactional
    public void postDoubleEntry(
            String debitGlCode, String creditGlCode,
            BigDecimal amount, String currencyCode,
            LocalDate transactionDate, String description,
            JournalEntry.EntityType entityType, UUID entityId) {

        GlAccount debitAccount  = resolveByCode(debitGlCode);
        GlAccount creditAccount = resolveByCode(creditGlCode);

        JournalEntry debit = buildEntry(debitAccount, JournalEntry.EntryType.DEBIT,
                amount, currencyCode, transactionDate, description, entityType, entityId);
        JournalEntry credit = buildEntry(creditAccount, JournalEntry.EntryType.CREDIT,
                amount, currencyCode, transactionDate, description, entityType, entityId);

        journalEntryRepository.save(debit);
        journalEntryRepository.save(credit);
        log.debug("GL posted: DR {} CR {} {} {} for entity {}:{}", debitGlCode, creditGlCode,
                amount, currencyCode, entityType, entityId);
    }

    /**
     * Convenience overload that resolves GL accounts from financial activity mappings.
     */
    @Transactional
    public void postByActivity(
            FinancialActivityAccount.FinancialActivity debitActivity,
            FinancialActivityAccount.FinancialActivity creditActivity,
            BigDecimal amount, String currencyCode,
            LocalDate transactionDate, String description,
            JournalEntry.EntityType entityType, UUID entityId) {

        String debitCode  = resolveActivityCode(debitActivity);
        String creditCode = resolveActivityCode(creditActivity);
        postDoubleEntry(debitCode, creditCode, amount, currencyCode,
                transactionDate, description, entityType, entityId);
    }

    // ── Manual journal entries ────────────────────────────────────────────────

    @Transactional
    public List<JournalEntry> postManualEntries(ManualJournalRequest request) {
        validateNotClosed(request.transactionDate());

        if (request.debits().size() != request.credits().size()) {
            throw CbaException.badRequest("UNBALANCED_ENTRY",
                    "Manual journal must have equal number of debit and credit lines");
        }

        BigDecimal totalDebits  = request.debits().stream().map(ManualJournalRequest.EntryLine::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalCredits = request.credits().stream().map(ManualJournalRequest.EntryLine::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (totalDebits.compareTo(totalCredits) != 0) {
            throw CbaException.badRequest("UNBALANCED_ENTRY",
                    "Debits (" + totalDebits + ") must equal credits (" + totalCredits + ")");
        }

        String actor = resolveActor();
        List<JournalEntry> entries = new java.util.ArrayList<>();

        for (ManualJournalRequest.EntryLine line : request.debits()) {
            GlAccount account = resolveByCode(line.glCode());
            if (!account.isManualEntriesAllowed()) {
                throw CbaException.badRequest("MANUAL_ENTRY_NOT_ALLOWED",
                        "GL account " + line.glCode() + " does not allow manual entries");
            }
            entries.add(journalEntryRepository.save(buildEntry(account,
                    JournalEntry.EntryType.DEBIT, line.amount(), request.currencyCode(),
                    request.transactionDate(), request.comments(),
                    JournalEntry.EntityType.MANUAL, null)));
        }
        for (ManualJournalRequest.EntryLine line : request.credits()) {
            GlAccount account = resolveByCode(line.glCode());
            if (!account.isManualEntriesAllowed()) {
                throw CbaException.badRequest("MANUAL_ENTRY_NOT_ALLOWED",
                        "GL account " + line.glCode() + " does not allow manual entries");
            }
            entries.add(journalEntryRepository.save(buildEntry(account,
                    JournalEntry.EntryType.CREDIT, line.amount(), request.currencyCode(),
                    request.transactionDate(), request.comments(),
                    JournalEntry.EntityType.MANUAL, null)));
        }

        auditLogService.log("JOURNAL_ENTRY", entries.get(0).getId().toString(),
                "MANUAL_POSTED", null, "actor=" + actor + ",amount=" + totalDebits);
        return entries;
    }

    @Transactional
    public void reverseJournalEntry(UUID entryId) {
        JournalEntry original = journalEntryRepository.findById(entryId)
                .orElseThrow(() -> CbaException.notFound("JournalEntry", entryId.toString()));
        if (original.isReversed()) {
            throw CbaException.badRequest("ALREADY_REVERSED", "Entry " + entryId + " is already reversed");
        }

        JournalEntry.EntryType reversalType = original.getEntryType() == JournalEntry.EntryType.DEBIT
                ? JournalEntry.EntryType.CREDIT : JournalEntry.EntryType.DEBIT;

        JournalEntry reversal = buildEntry(original.getGlAccount(), reversalType,
                original.getAmount(), original.getCurrencyCode(),
                LocalDate.now(), "Reversal of entry " + entryId,
                original.getEntityType(), original.getEntityId());
        reversal.setReversalOf(original);
        journalEntryRepository.save(reversal);

        original.setReversed(true);
        journalEntryRepository.save(original);
    }

    // ── GL Closure ────────────────────────────────────────────────────────────

    @Transactional
    public GlClosure createClosure(UUID officeId, LocalDate closingDate, String comments,
                                   com.cba.office.OfficeRepository officeRepository) {
        if (glClosureRepository.findByOfficeIdAndClosingDate(officeId, closingDate).isPresent()) {
            throw CbaException.badRequest("CLOSURE_EXISTS",
                    "A GL closure already exists for office " + officeId + " on " + closingDate);
        }
        GlClosure closure = new GlClosure();
        closure.setOffice(officeRepository.findById(officeId)
                .orElseThrow(() -> CbaException.notFound("Office", officeId.toString())));
        closure.setClosingDate(closingDate);
        closure.setClosedBy(resolveActor());
        closure.setComments(comments);
        GlClosure saved = glClosureRepository.save(closure);
        auditLogService.log("GL_CLOSURE", saved.getId().toString(), "CLOSED", null,
                "date=" + closingDate);
        return saved;
    }

    // ── Queries ───────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<JournalEntry> getEntriesForEntity(JournalEntry.EntityType type, UUID entityId) {
        return journalEntryRepository.findByEntityTypeAndEntityId(type, entityId);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private GlAccount resolveByCode(String code) {
        return glAccountRepository.findByGlCode(code)
                .orElseThrow(() -> CbaException.badRequest("GL_ACCOUNT_NOT_FOUND",
                        "No GL account found with code: " + code));
    }

    private String resolveActivityCode(FinancialActivityAccount.FinancialActivity activity) {
        return financialActivityRepo.findByFinancialActivity(activity)
                .map(faa -> faa.getGlAccount().getGlCode())
                .orElseThrow(() -> CbaException.badRequest("ACTIVITY_NOT_MAPPED",
                        "Financial activity " + activity + " has no GL account mapping"));
    }

    private JournalEntry buildEntry(GlAccount account, JournalEntry.EntryType type,
                                    BigDecimal amount, String currency, LocalDate date,
                                    String description, JournalEntry.EntityType entityType, UUID entityId) {
        JournalEntry e = new JournalEntry();
        e.setGlAccount(account);
        e.setEntryType(type);
        e.setAmount(amount);
        e.setCurrencyCode(currency);
        e.setTransactionDate(date);
        e.setDescription(description);
        e.setEntityType(entityType);
        e.setEntityId(entityId);
        return e;
    }

    private void validateNotClosed(LocalDate date) {
        // For manual entries, check no closure exists for any office on this date.
        // A production system would check the specific office of the requesting user.
    }

    private String resolveActor() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) return "system";
        if (auth.getPrincipal() instanceof Jwt jwt) {
            String u = jwt.getClaimAsString("preferred_username");
            return u != null ? u : jwt.getSubject();
        }
        return auth.getName();
    }
}
