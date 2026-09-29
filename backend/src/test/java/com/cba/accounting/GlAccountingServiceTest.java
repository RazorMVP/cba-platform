package com.cba.accounting;

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
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("GlAccountingService — unit tests")
class GlAccountingServiceTest {

    @Mock GlAccountRepository glAccountRepository;
    @Mock JournalEntryRepository journalEntryRepository;
    @Mock FinancialActivityAccountRepository financialActivityRepo;
    @Mock GlClosureRepository glClosureRepository;
    @Mock AuditLogService auditLogService;

    @InjectMocks GlAccountingService glAccountingService;

    private GlAccount debitAccount;
    private GlAccount creditAccount;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        debitAccount = buildGlAccount("1000", "Cash", GlAccount.AccountType.ASSET);
        creditAccount = buildGlAccount("4000", "Interest Income", GlAccount.AccountType.INCOME);
    }

    private GlAccount buildGlAccount(String code, String name, GlAccount.AccountType type) {
        GlAccount acc = new GlAccount();
        acc.setId(UUID.randomUUID());
        acc.setGlCode(code);
        acc.setName(name);
        acc.setAccountType(type);
        acc.setManualEntriesAllowed(true);
        return acc;
    }

    @Nested
    @DisplayName("postDoubleEntry")
    class PostDoubleEntry {

        @Test
        @DisplayName("saves debit and credit journal entries")
        void postDoubleEntry_savesBothEntries() {
            when(glAccountRepository.findByGlCode("1000")).thenReturn(Optional.of(debitAccount));
            when(glAccountRepository.findByGlCode("4000")).thenReturn(Optional.of(creditAccount));
            when(journalEntryRepository.save(any())).thenAnswer(inv -> {
                JournalEntry e = inv.getArgument(0);
                e.setId(UUID.randomUUID());
                return e;
            });

            glAccountingService.postDoubleEntry("1000", "4000",
                new BigDecimal("500.00"), "USD",
                LocalDate.now(), "Test entry",
                JournalEntry.EntityType.ACCOUNT, UUID.randomUUID());

            verify(journalEntryRepository, times(2)).save(any(JournalEntry.class));
        }

        @Test
        @DisplayName("throws when debit GL code not found")
        void debitGlNotFound_throws() {
            when(glAccountRepository.findByGlCode("9999")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> glAccountingService.postDoubleEntry(
                "9999", "4000", BigDecimal.TEN, "USD",
                LocalDate.now(), "desc", JournalEntry.EntityType.ACCOUNT, UUID.randomUUID()))
                .isInstanceOf(CbaException.class)
                .hasMessageContaining("9999");
        }

        @Test
        @DisplayName("throws when credit GL code not found")
        void creditGlNotFound_throws() {
            when(glAccountRepository.findByGlCode("1000")).thenReturn(Optional.of(debitAccount));
            when(glAccountRepository.findByGlCode("8888")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> glAccountingService.postDoubleEntry(
                "1000", "8888", BigDecimal.TEN, "USD",
                LocalDate.now(), "desc", JournalEntry.EntityType.ACCOUNT, UUID.randomUUID()))
                .isInstanceOf(CbaException.class)
                .hasMessageContaining("8888");
        }
    }

    @Nested
    @DisplayName("postByActivity")
    class PostByActivity {

        @Test
        @DisplayName("resolves activity codes and posts double entry")
        void postByActivity_success() {
            FinancialActivityAccount debitFaa = new FinancialActivityAccount();
            debitFaa.setGlAccount(debitAccount);
            FinancialActivityAccount creditFaa = new FinancialActivityAccount();
            creditFaa.setGlAccount(creditAccount);

            when(financialActivityRepo.findByFinancialActivity(FinancialActivityAccount.FinancialActivity.ASSET_FUND_SOURCE))
                .thenReturn(Optional.of(debitFaa));
            when(financialActivityRepo.findByFinancialActivity(FinancialActivityAccount.FinancialActivity.INCOME_INTEREST))
                .thenReturn(Optional.of(creditFaa));
            when(glAccountRepository.findByGlCode("1000")).thenReturn(Optional.of(debitAccount));
            when(glAccountRepository.findByGlCode("4000")).thenReturn(Optional.of(creditAccount));
            when(journalEntryRepository.save(any())).thenAnswer(inv -> {
                JournalEntry e = inv.getArgument(0);
                e.setId(UUID.randomUUID());
                return e;
            });

            glAccountingService.postByActivity(
                FinancialActivityAccount.FinancialActivity.ASSET_FUND_SOURCE,
                FinancialActivityAccount.FinancialActivity.INCOME_INTEREST,
                new BigDecimal("200.00"), "USD",
                LocalDate.now(), "Interest posting",
                JournalEntry.EntityType.LOAN, UUID.randomUUID());

            verify(journalEntryRepository, times(2)).save(any());
        }

        @Test
        @DisplayName("throws when activity not mapped to GL account")
        void activityNotMapped_throws() {
            when(financialActivityRepo.findByFinancialActivity(any())).thenReturn(Optional.empty());

            assertThatThrownBy(() -> glAccountingService.postByActivity(
                FinancialActivityAccount.FinancialActivity.ASSET_FUND_SOURCE,
                FinancialActivityAccount.FinancialActivity.INCOME_INTEREST,
                BigDecimal.TEN, "USD", LocalDate.now(), "desc",
                JournalEntry.EntityType.LOAN, UUID.randomUUID()))
                .isInstanceOf(CbaException.class)
                .hasMessageContaining("Financial activity");
        }
    }

    @Nested
    @DisplayName("postManualEntries")
    class PostManualEntries {

        @Test
        @DisplayName("posts balanced manual journal entries")
        void balanced_postsSuccessfully() {
            SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("admin", null,
                    List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));

            when(glAccountRepository.findByGlCode("1000")).thenReturn(Optional.of(debitAccount));
            when(glAccountRepository.findByGlCode("4000")).thenReturn(Optional.of(creditAccount));
            when(journalEntryRepository.save(any())).thenAnswer(inv -> {
                JournalEntry e = inv.getArgument(0);
                e.setId(UUID.randomUUID());
                return e;
            });

            ManualJournalRequest req = new ManualJournalRequest(
                LocalDate.now(), "USD", "Test manual entry",
                List.of(new ManualJournalRequest.EntryLine("1000", new BigDecimal("100.00"), null)),
                List.of(new ManualJournalRequest.EntryLine("4000", new BigDecimal("100.00"), null))
            );

            List<JournalEntry> result = glAccountingService.postManualEntries(req);
            assertThat(result).hasSize(2);
            verify(auditLogService).log(eq("JOURNAL_ENTRY"), any(), eq("MANUAL_POSTED"), isNull(), any());
        }

        @Test
        @DisplayName("one debit against two credits posts: lines needn't pair up, only the amounts must balance")
        void unequalLineCount_balanced_posts() {
            GlAccount fees = buildGlAccount("4001", "Fees", GlAccount.AccountType.INCOME);
            when(glAccountRepository.findByGlCode("1000")).thenReturn(Optional.of(debitAccount));
            when(glAccountRepository.findByGlCode("4000")).thenReturn(Optional.of(creditAccount));
            when(glAccountRepository.findByGlCode("4001")).thenReturn(Optional.of(fees));
            when(journalEntryRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            ManualJournalRequest req = new ManualJournalRequest(
                LocalDate.now(), "usd", "Split",
                List.of(new ManualJournalRequest.EntryLine("1000", new BigDecimal("150.00"), null)),
                List.of(new ManualJournalRequest.EntryLine("4000", new BigDecimal("100.00"), null),
                        new ManualJournalRequest.EntryLine("4001", new BigDecimal("50.00"), null)));

            List<JournalEntry> result = glAccountingService.postManualEntries(req);

            assertThat(result).hasSize(3);
            assertThat(result).allSatisfy(e -> {
                assertThat(e.getCurrencyCode()).isEqualTo("USD");
                assertThat(e.getEntityType()).isEqualTo(JournalEntry.EntityType.MANUAL);
                assertThat(e.getTransactionId()).startsWith("MJ-").isEqualTo(result.get(0).getTransactionId());
            });
        }

        @Test
        @DisplayName("a date inside a closed period is rejected (GL_PERIOD_CLOSED) and nothing is saved")
        void closedPeriod_rejected() {
            when(glAccountRepository.findByGlCode("1000")).thenReturn(Optional.of(debitAccount));
            when(glAccountRepository.findByGlCode("4000")).thenReturn(Optional.of(creditAccount));
            when(glClosureRepository.existsByClosingDateGreaterThanEqual(any())).thenReturn(true);

            ManualJournalRequest req = new ManualJournalRequest(
                LocalDate.now().minusDays(40), "USD", "Backdated",
                List.of(new ManualJournalRequest.EntryLine("1000", new BigDecimal("100.00"), null)),
                List.of(new ManualJournalRequest.EntryLine("4000", new BigDecimal("100.00"), null)));

            assertThatThrownBy(() -> glAccountingService.postManualEntries(req))
                .isInstanceOf(CbaException.class).hasMessageContaining("closed");
            verify(journalEntryRepository, never()).save(any());
        }

        @Test
        @DisplayName("throws when totals are unequal")
        void unequalTotals_throws() {
            ManualJournalRequest req = new ManualJournalRequest(
                LocalDate.now(), "USD", "Unbalanced",
                List.of(new ManualJournalRequest.EntryLine("1000", new BigDecimal("100.00"), null)),
                List.of(new ManualJournalRequest.EntryLine("4000", new BigDecimal("200.00"), null))
            );

            assertThatThrownBy(() -> glAccountingService.postManualEntries(req))
                .isInstanceOf(CbaException.class)
                .hasMessageContaining("Debits");
        }

        @Test
        @DisplayName("throws when GL account does not allow manual entries")
        void manualEntriesNotAllowed_throws() {
            GlAccount headerAccount = buildGlAccount("1000", "Header", GlAccount.AccountType.ASSET);
            headerAccount.setManualEntriesAllowed(false);

            when(glAccountRepository.findByGlCode("1000")).thenReturn(Optional.of(headerAccount));

            ManualJournalRequest req = new ManualJournalRequest(
                LocalDate.now(), "USD", "Blocked",
                List.of(new ManualJournalRequest.EntryLine("1000", new BigDecimal("100.00"), null)),
                List.of(new ManualJournalRequest.EntryLine("4000", new BigDecimal("100.00"), null))
            );

            assertThatThrownBy(() -> glAccountingService.postManualEntries(req))
                .isInstanceOf(CbaException.class)
                .hasMessageContaining("does not allow manual entries");
        }
    }

    @Nested
    @DisplayName("reverseJournalEntry")
    class ReverseJournalEntry {

        private JournalEntry line(GlAccount account, JournalEntry.EntryType side, String amount,
                                  JournalEntry.EntityType entityType) {
            JournalEntry e = new JournalEntry();
            e.setId(UUID.randomUUID());
            e.setTransactionId("MJ-1");
            e.setGlAccount(account);
            e.setEntryType(side);
            e.setAmount(new BigDecimal(amount));
            e.setCurrencyCode("USD");
            e.setTransactionDate(LocalDate.now().minusDays(1));
            e.setEntityType(entityType);
            return e;
        }

        @Test
        @DisplayName("reverses every line of the journal as one balanced journal; originals marked reversed")
        void reversal_wholeJournal() {
            JournalEntry dr = line(debitAccount, JournalEntry.EntryType.DEBIT, "100.00", JournalEntry.EntityType.MANUAL);
            JournalEntry cr = line(creditAccount, JournalEntry.EntryType.CREDIT, "100.00", JournalEntry.EntityType.MANUAL);
            when(journalEntryRepository.findById(dr.getId())).thenReturn(Optional.of(dr));
            when(journalEntryRepository.findByTransactionIdOrderByIdAsc("MJ-1")).thenReturn(List.of(dr, cr));
            when(journalEntryRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            GlAccountingService.JournalReversal r = glAccountingService.reverseJournalEntry(dr.getId());

            assertThat(r.reversedTransactionId()).isEqualTo("MJ-1");
            assertThat(r.reversalTransactionId()).startsWith("REV-");
            assertThat(r.lines()).hasSize(2);
            assertThat(r.lines().get(0).getEntryType()).isEqualTo(JournalEntry.EntryType.CREDIT);
            assertThat(r.lines().get(0).getGlAccount()).isSameAs(debitAccount);
            assertThat(r.lines().get(0).getReversalOf()).isSameAs(dr);
            assertThat(r.lines().get(1).getEntryType()).isEqualTo(JournalEntry.EntryType.DEBIT);
            assertThat(r.lines().get(1).getReversalOf()).isSameAs(cr);
            assertThat(r.lines()).allSatisfy(l -> assertThat(l.getReferenceNumber()).isEqualTo("MJ-1"));
            assertThat(dr.isReversed()).isTrue();
            assertThat(cr.isReversed()).isTrue();
        }

        @Test
        @DisplayName("a journal posted by a sub-ledger is not reversible in the GL (reverse its source)")
        void subLedgerJournal_rejected() {
            JournalEntry dr = line(debitAccount, JournalEntry.EntryType.DEBIT, "100.00", JournalEntry.EntityType.ACCOUNT);
            when(journalEntryRepository.findById(dr.getId())).thenReturn(Optional.of(dr));

            assertThatThrownBy(() -> glAccountingService.reverseJournalEntry(dr.getId()))
                .isInstanceOf(CbaException.class).hasMessageContaining("reverse the source transaction");
            verify(journalEntryRepository, never()).save(any());
        }

        @Test
        @DisplayName("a reversal can't itself be reversed")
        void reversalOfReversal_rejected() {
            JournalEntry original = line(debitAccount, JournalEntry.EntryType.DEBIT, "100.00", JournalEntry.EntityType.MANUAL);
            JournalEntry reversal = line(debitAccount, JournalEntry.EntryType.CREDIT, "100.00", JournalEntry.EntityType.MANUAL);
            reversal.setReversalOf(original);
            when(journalEntryRepository.findById(reversal.getId())).thenReturn(Optional.of(reversal));

            assertThatThrownBy(() -> glAccountingService.reverseJournalEntry(reversal.getId()))
                .isInstanceOf(CbaException.class).hasMessageContaining("itself a reversal");
        }

        @Test
        @DisplayName("throws when entry not found")
        void entryNotFound_throws() {
            UUID entryId = UUID.randomUUID();
            when(journalEntryRepository.findById(entryId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> glAccountingService.reverseJournalEntry(entryId))
                .isInstanceOf(CbaException.class);
        }

        @Test
        @DisplayName("throws when the journal is already reversed")
        void alreadyReversed_throws() {
            JournalEntry dr = line(debitAccount, JournalEntry.EntryType.DEBIT, "100.00", JournalEntry.EntityType.MANUAL);
            dr.setReversed(true);
            when(journalEntryRepository.findById(dr.getId())).thenReturn(Optional.of(dr));
            when(journalEntryRepository.findByTransactionIdOrderByIdAsc("MJ-1")).thenReturn(List.of(dr));

            assertThatThrownBy(() -> glAccountingService.reverseJournalEntry(dr.getId()))
                .isInstanceOf(CbaException.class)
                .hasMessageContaining("already reversed");
        }
    }

    @Nested
    @DisplayName("getEntriesForEntity")
    class GetEntriesForEntity {

        @Test
        @DisplayName("returns entries for the given entity")
        void returnsEntries() {
            UUID entityId = UUID.randomUUID();
            JournalEntry entry = new JournalEntry();
            entry.setId(UUID.randomUUID());
            when(journalEntryRepository.findByEntityTypeAndEntityId(JournalEntry.EntityType.LOAN, entityId))
                .thenReturn(List.of(entry));

            List<JournalEntry> result = glAccountingService.getEntriesForEntity(
                JournalEntry.EntityType.LOAN, entityId);
            assertThat(result).hasSize(1);
        }
    }
}
