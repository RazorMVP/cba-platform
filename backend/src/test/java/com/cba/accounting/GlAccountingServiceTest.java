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
        @DisplayName("throws when debit and credit count mismatch")
        void unequalCount_throws() {
            ManualJournalRequest req = new ManualJournalRequest(
                LocalDate.now(), "USD", "Unbalanced",
                List.of(new ManualJournalRequest.EntryLine("1000", new BigDecimal("100.00"), null),
                        new ManualJournalRequest.EntryLine("1001", new BigDecimal("50.00"), null)),
                List.of(new ManualJournalRequest.EntryLine("4000", new BigDecimal("150.00"), null))
            );

            assertThatThrownBy(() -> glAccountingService.postManualEntries(req))
                .isInstanceOf(CbaException.class)
                .hasMessageContaining("equal number");
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

        @Test
        @DisplayName("creates reversal entry and marks original as reversed")
        void reversal_success() {
            UUID entryId = UUID.randomUUID();
            JournalEntry original = new JournalEntry();
            original.setId(entryId);
            original.setGlAccount(debitAccount);
            original.setEntryType(JournalEntry.EntryType.DEBIT);
            original.setAmount(new BigDecimal("100.00"));
            original.setCurrencyCode("USD");
            original.setTransactionDate(LocalDate.now());
            original.setEntityType(JournalEntry.EntityType.ACCOUNT);

            when(journalEntryRepository.findById(entryId)).thenReturn(Optional.of(original));
            when(journalEntryRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            glAccountingService.reverseJournalEntry(entryId);

            assertThat(original.isReversed()).isTrue();
            verify(journalEntryRepository, times(2)).save(any());
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
        @DisplayName("throws when entry already reversed")
        void alreadyReversed_throws() {
            UUID entryId = UUID.randomUUID();
            JournalEntry original = new JournalEntry();
            original.setId(entryId);
            original.setReversed(true);

            when(journalEntryRepository.findById(entryId)).thenReturn(Optional.of(original));

            assertThatThrownBy(() -> glAccountingService.reverseJournalEntry(entryId))
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
