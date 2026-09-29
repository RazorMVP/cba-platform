package com.cba.accounting;

import com.cba.audit.AuditLogService;
import com.cba.common.exception.CbaException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
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
    private final JdbcTemplate jdbcTemplate;

    // ── Auto-posting (called by domain services) ──────────────────────────────

    /**
     * One journal line: a GL account, a side, a positive amount and the currency it is in.
     * {@code positionCurrency} is set only on FX position equivalent lines: the foreign
     * currency whose position the functional-currency amount values.
     */
    public record JournalLine(GlAccount account, JournalEntry.EntryType side,
                              BigDecimal amount, String currencyCode, String positionCurrency) {
        public JournalLine(GlAccount account, JournalEntry.EntryType side, BigDecimal amount, String currencyCode) {
            this(account, side, amount, currencyCode, null);
        }
        public JournalLine withPosition(String currency) {
            return new JournalLine(account, side, amount, currencyCode, currency);
        }
        public static JournalLine debit(GlAccount account, BigDecimal amount, String currencyCode) {
            return new JournalLine(account, JournalEntry.EntryType.DEBIT, amount, currencyCode);
        }
        public static JournalLine credit(GlAccount account, BigDecimal amount, String currencyCode) {
            return new JournalLine(account, JournalEntry.EntryType.CREDIT, amount, currencyCode);
        }
    }

    /**
     * Post a multi-line journal atomically; returns its transaction id.
     * Must be called within the originating transaction so it rolls back together.
     *
     * <p>Zero-amount lines are dropped. Debits must equal credits <em>in each currency</em>:
     * a cross-currency posting balances through FX position accounts, never by adding
     * amounts in different currencies. Every account must be an enabled DETAIL account.
     */
    @Transactional
    public String postJournal(List<JournalLine> lines, LocalDate transactionDate, String description,
                              JournalEntry.EntityType entityType, UUID entityId, String referenceNumber) {
        return write("GL", lines, transactionDate, description, entityType, entityId, referenceNumber)
                .get(0).getTransactionId();
    }

    /**
     * Validates and saves one journal; returns its lines in order. Every posting — automatic,
     * manual or reversal — goes through here, so they all get the same checks: positive
     * amounts, balance per currency, enabled DETAIL accounts, and no date inside a closed
     * period.
     */
    private List<JournalEntry> write(String prefix, List<JournalLine> lines, LocalDate transactionDate,
                                     String description, JournalEntry.EntityType entityType, UUID entityId,
                                     String referenceNumber) {
        validateNotClosed(transactionDate);
        List<JournalLine> posted = lines.stream().filter(l -> l.amount().signum() != 0).toList();
        if (posted.isEmpty()) {
            throw new IllegalStateException("Journal has no non-zero lines: " + description);
        }
        java.util.Map<String, BigDecimal> netByCurrency = new java.util.TreeMap<>();
        for (JournalLine line : posted) {
            if (line.amount().signum() < 0) {
                throw new IllegalStateException("Journal line amounts must be positive: " + line);
            }
            GlAccount account = line.account();
            if (account.isDisabled() || account.getUsage() != GlAccount.Usage.DETAIL) {
                throw CbaException.badRequest("GL_ACCOUNT_NOT_POSTABLE",
                        "GL account " + account.getGlCode() + " is disabled or a header account");
            }
            BigDecimal signed = line.side() == JournalEntry.EntryType.DEBIT ? line.amount() : line.amount().negate();
            netByCurrency.merge(line.currencyCode(), signed, BigDecimal::add);
        }
        netByCurrency.forEach((currency, net) -> {
            if (net.signum() != 0) {
                throw new IllegalStateException("Unbalanced journal in " + currency + " (net " + net + "): " + description);
            }
        });

        String transactionId = newTransactionId(prefix);
        List<JournalEntry> saved = new java.util.ArrayList<>(posted.size());
        for (JournalLine line : posted) {
            JournalEntry entry = buildEntry(transactionId, line.account(), line.side(), line.amount(),
                    line.currencyCode(), transactionDate, description, entityType, entityId);
            entry.setReferenceNumber(referenceNumber);
            entry.setPositionCurrency(line.positionCurrency());
            saved.add(journalEntryRepository.save(entry));
        }
        log.debug("GL posted {} ({} lines) for entity {}:{}", transactionId, posted.size(), entityType, entityId);
        return saved;
    }

    /** The GL account mapped to a financial activity; rejects the transaction when unmapped. */
    @Transactional(readOnly = true)
    public GlAccount activityAccount(FinancialActivityAccount.FinancialActivity activity) {
        return financialActivityRepo.findByFinancialActivity(activity)
                .map(FinancialActivityAccount::getGlAccount)
                .orElseThrow(() -> CbaException.badRequest("ACTIVITY_NOT_MAPPED",
                        "Financial activity " + activity + " has no GL account mapping"));
    }

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
        postJournal(List.of(JournalLine.debit(debitAccount, amount, currencyCode),
                            JournalLine.credit(creditAccount, amount, currencyCode)),
                transactionDate, description, entityType, entityId, null);
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

    /**
     * A manual journal: any number of debit and credit lines in one currency, balanced by
     * amount. It goes through the same checks as automatic postings, and only accounts
     * that allow manual entries can take it.
     */
    @Transactional
    public List<JournalEntry> postManualEntries(ManualJournalRequest request) {
        BigDecimal totalDebits  = request.debits().stream().map(ManualJournalRequest.EntryLine::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalCredits = request.credits().stream().map(ManualJournalRequest.EntryLine::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (totalDebits.compareTo(totalCredits) != 0) {
            throw CbaException.badRequest("UNBALANCED_ENTRY",
                    "Debits (" + totalDebits + ") must equal credits (" + totalCredits + ")");
        }

        String currency = request.currencyCode().trim().toUpperCase(java.util.Locale.ROOT);
        List<JournalLine> lines = new java.util.ArrayList<>();
        request.debits().forEach(l -> lines.add(JournalLine.debit(manualAccount(l.glCode()), l.amount(), currency)));
        request.credits().forEach(l -> lines.add(JournalLine.credit(manualAccount(l.glCode()), l.amount(), currency)));

        List<JournalEntry> entries = write("MJ", lines, request.transactionDate(), request.comments(),
                JournalEntry.EntityType.MANUAL, null, request.referenceNumber());
        auditLogService.log("JOURNAL_ENTRY", entries.get(0).getTransactionId(),
                "MANUAL_POSTED", null, "actor=" + resolveActor() + ",amount=" + totalDebits + " " + currency);
        return entries;
    }

    private GlAccount manualAccount(String glCode) {
        GlAccount account = resolveByCode(glCode);
        if (!account.isManualEntriesAllowed()) {
            throw CbaException.badRequest("MANUAL_ENTRY_NOT_ALLOWED",
                    "GL account " + glCode + " does not allow manual entries");
        }
        return account;
    }

    /** The journal a reversal posted, and the one it reversed. */
    public record JournalReversal(String reversedTransactionId, String reversalTransactionId,
                                  List<JournalEntry> lines) {}

    /**
     * Reverses the whole journal that {@code entryId} belongs to: one new journal with every
     * line on the opposite side, dated today; the originals stay and are marked reversed.
     *
     * <p>Only journals created in the GL (manual journals) can be reversed here. A journal a
     * sub-ledger posted — a deposit, transfer, interest credit — is reversed at its source
     * (for example the payment reversal), which reverses the balance and the journal
     * together; reversing only the journal would leave the ledger out of step with the
     * customer balances. Oracle General Ledger applies the same rule.
     */
    @Transactional
    public JournalReversal reverseJournalEntry(UUID entryId) {
        JournalEntry entry = journalEntryRepository.findById(entryId)
                .orElseThrow(() -> CbaException.notFound("JournalEntry", entryId.toString()));
        if (entry.getEntityType() != JournalEntry.EntityType.MANUAL) {
            throw CbaException.badRequest("SUBLEDGER_JOURNAL_NOT_REVERSIBLE",
                    "Journal " + entry.getTransactionId() + " was posted by " + entry.getEntityType()
                            + "; reverse the source transaction instead");
        }
        if (entry.getReversalOf() != null) {
            throw CbaException.badRequest("CANNOT_REVERSE_REVERSAL",
                    "Journal " + entry.getTransactionId() + " is itself a reversal");
        }
        List<JournalEntry> originals = journalEntryRepository.findByTransactionIdOrderByIdAsc(entry.getTransactionId());
        if (originals.stream().anyMatch(JournalEntry::isReversed)) {
            throw CbaException.badRequest("ALREADY_REVERSED",
                    "Journal " + entry.getTransactionId() + " is already reversed");
        }

        List<JournalLine> lines = originals.stream().map(o -> new JournalLine(o.getGlAccount(),
                o.getEntryType() == JournalEntry.EntryType.DEBIT ? JournalEntry.EntryType.CREDIT : JournalEntry.EntryType.DEBIT,
                o.getAmount(), o.getCurrencyCode(), o.getPositionCurrency())).toList();
        List<JournalEntry> reversal = write("REV", lines, LocalDate.now(),
                "Reversal of journal " + entry.getTransactionId(),
                JournalEntry.EntityType.MANUAL, entry.getEntityId(), entry.getTransactionId());
        for (int k = 0; k < originals.size(); k++) {
            reversal.get(k).setReversalOf(originals.get(k));
            originals.get(k).setReversed(true);
        }
        journalEntryRepository.saveAll(reversal);
        journalEntryRepository.saveAll(originals);
        auditLogService.log("JOURNAL_ENTRY", entry.getTransactionId(), "REVERSED", null,
                "actor=" + resolveActor() + ",reversal=" + reversal.get(0).getTransactionId());
        return new JournalReversal(entry.getTransactionId(), reversal.get(0).getTransactionId(), reversal);
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

    // ── Trial Balance ─────────────────────────────────────────────────────────

    /** One GL account in one currency. Balances are debit-positive. */
    public record TrialBalanceRow(
            String glCode,
            String accountName,
            String accountType,
            String currencyCode,
            BigDecimal openingBalance,
            BigDecimal debitMovement,
            BigDecimal creditMovement,
            BigDecimal closingBalance) {}

    /**
     * Totals for one currency. Double entry holds within each currency, never across
     * them, so each currency is balanced on its own: the period's debits equal its
     * credits, and the closing debit balances equal the closing credit balances.
     */
    public record CurrencyTotals(
            String currencyCode,
            BigDecimal totalDebitMovement,
            BigDecimal totalCreditMovement,
            BigDecimal totalClosingDebit,
            BigDecimal totalClosingCredit,
            boolean balanced) {}

    public record TrialBalanceResponse(
            LocalDate fromDate,
            LocalDate toDate,
            List<TrialBalanceRow> rows,
            List<CurrencyTotals> currencies,
            boolean balanced) {}

    /**
     * Trial balance per GL account and currency, over every journal line up to {@code toDate}.
     * A reversed journal and its reversal both count, so together they net to zero.
     * Accounts with no lines at all are left out.
     */
    @Transactional(readOnly = true)
    public TrialBalanceResponse getTrialBalance(LocalDate fromDate, LocalDate toDate) {
        String sql = """
                SELECT ga.gl_code,
                       ga.name,
                       ga.account_type,
                       je.currency_code,
                       COALESCE(SUM(CASE WHEN je.transaction_date < ? AND je.entry_type = 'DEBIT'  THEN je.amount ELSE 0 END)
                              - SUM(CASE WHEN je.transaction_date < ? AND je.entry_type = 'CREDIT' THEN je.amount ELSE 0 END), 0) AS opening_balance,
                       COALESCE(SUM(CASE WHEN je.transaction_date BETWEEN ? AND ? AND je.entry_type = 'DEBIT'  THEN je.amount ELSE 0 END), 0) AS debit_movement,
                       COALESCE(SUM(CASE WHEN je.transaction_date BETWEEN ? AND ? AND je.entry_type = 'CREDIT' THEN je.amount ELSE 0 END), 0) AS credit_movement
                FROM gl_accounts ga
                JOIN journal_entries je ON je.gl_account_id = ga.id AND je.transaction_date <= ?
                WHERE ga.usage = 'DETAIL'
                GROUP BY ga.id, ga.gl_code, ga.name, ga.account_type, je.currency_code
                ORDER BY je.currency_code, ga.account_type, ga.gl_code
                """;

        List<TrialBalanceRow> rows = jdbcTemplate.query(
                sql,
                (rs, i) -> {
                    BigDecimal opening = rs.getBigDecimal("opening_balance");
                    BigDecimal debit   = rs.getBigDecimal("debit_movement");
                    BigDecimal credit  = rs.getBigDecimal("credit_movement");
                    return new TrialBalanceRow(
                            rs.getString("gl_code"),
                            rs.getString("name"),
                            rs.getString("account_type"),
                            rs.getString("currency_code"),
                            opening, debit, credit, opening.add(debit).subtract(credit));
                },
                fromDate, fromDate, fromDate, toDate, fromDate, toDate, toDate);

        java.util.Map<String, List<TrialBalanceRow>> byCurrency = new java.util.TreeMap<>();
        rows.forEach(r -> byCurrency.computeIfAbsent(r.currencyCode(), k -> new java.util.ArrayList<>()).add(r));
        List<CurrencyTotals> currencies = byCurrency.entrySet().stream().map(e -> {
            List<TrialBalanceRow> rs = e.getValue();
            BigDecimal debit  = rs.stream().map(TrialBalanceRow::debitMovement).reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal credit = rs.stream().map(TrialBalanceRow::creditMovement).reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal closingDebit = rs.stream().map(TrialBalanceRow::closingBalance)
                    .filter(b -> b.signum() > 0).reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal closingCredit = rs.stream().map(TrialBalanceRow::closingBalance)
                    .filter(b -> b.signum() < 0).map(BigDecimal::negate).reduce(BigDecimal.ZERO, BigDecimal::add);
            return new CurrencyTotals(e.getKey(), debit, credit, closingDebit, closingCredit,
                    debit.compareTo(credit) == 0 && closingDebit.compareTo(closingCredit) == 0);
        }).toList();

        return new TrialBalanceResponse(fromDate, toDate, rows, currencies,
                currencies.stream().allMatch(CurrencyTotals::balanced));
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

    /** One id per posting, shared by all its lines (journal_entries.transaction_id). */
    private static String newTransactionId(String prefix) {
        return prefix + "-" + UUID.randomUUID();
    }

    private JournalEntry buildEntry(String transactionId, GlAccount account, JournalEntry.EntryType type,
                                    BigDecimal amount, String currency, LocalDate date,
                                    String description, JournalEntry.EntityType entityType, UUID entityId) {
        JournalEntry e = new JournalEntry();
        e.setTransactionId(transactionId);
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

    /**
     * No journal may be dated on or before a GL closure (Fineract accounting closures).
     * Journal lines carry no office, so a closure for any office closes the ledger up to
     * its date.
     */
    private void validateNotClosed(LocalDate date) {
        if (glClosureRepository.existsByClosingDateGreaterThanEqual(date)) {
            throw CbaException.badRequest("GL_PERIOD_CLOSED",
                    "The ledger is closed up to a date on or after " + date + "; post with a later date");
        }
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
