package com.cba.integration;

import com.cba.cob.CobJobDefinition;
import com.cba.cob.CobJobService;
import com.cba.cob.CobJobView;
import com.cba.cob.CobRunView;
import com.cba.common.exception.CbaException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

/**
 * Launches the real CoB jobs against PostgreSQL. Spring Batch resolves reader
 * methods by reflection at run time, so a wrong method name or argument list only
 * fails when the job actually runs — which is how the standing-order and arrears
 * jobs failed on every night from April to September 2026 unnoticed.
 *
 * <p>Tests create their own accounts, or pick a business date that only matches
 * their own data, so the shared demo data other ITs rely on is left as it was.
 */
@DisplayName("CoB jobs — run end to end against a real database")
class CobJobsIT extends AbstractIntegrationTest {

    static final UUID DEMO_CUSTOMER = UUID.fromString("30000000-0000-0000-0000-000000000001");
    static final UUID SAVINGS_PRODUCT = UUID.fromString("10000000-0000-0000-0000-000000000001");
    static final UUID JOHN_SAVINGS = UUID.fromString("40000000-0000-0000-0000-000000000001");
    /** ACTIVE demo loan whose instalment 4 (due 2024-04-05) is still PENDING. */
    static final UUID DEMO_LOAN = UUID.fromString("60000000-0000-0000-0000-000000000001");
    static final LocalDate BEFORE_FIRST_PENDING_DUE = LocalDate.of(2024, 4, 1);

    @Autowired JobLauncher jobLauncher;
    @Autowired JdbcTemplate jdbc;
    @Autowired CobJobService cobJobService;
    @Autowired @Qualifier("standingOrderExecutionBatchJob") Job standingOrderJob;
    @Autowired @Qualifier("dormancyClassificationBatchJob") Job dormancyJob;
    @Autowired @Qualifier("arrearsClassificationBatchJob") Job arrearsJob;

    @AfterEach
    void restoreDemoLoan() throws Exception {
        // Arrears tests move the shared demo loan; a run dated before its first
        // pending instalment always puts it back to ACTIVE.
        run(arrearsJob, BEFORE_FIRST_PENDING_DUE);
    }

    // ── Standing orders ───────────────────────────────────────────────────────

    @Test
    @DisplayName("standing orders: executes every due order (more than one page) and isolates a failing one")
    void standingOrders_executesAllDue_andIsolatesFailure() throws Exception {
        LocalDate businessDate = LocalDate.now();
        BigDecimal amount = new BigDecimal("10.00");

        // 25 orders > the old reader's page size of 20, and each on its own pair of
        // accounts so the fraud velocity rule (10 txns/hour/account) never trips.
        List<UUID[]> orders = new ArrayList<>();
        for (int i = 0; i < 25; i++) {
            UUID src = account("TST-SO-S-" + i, new BigDecimal("1000.00"), LocalDate.of(2025, 1, 1));
            UUID dst = account("TST-SO-D-" + i, new BigDecimal("1000.00"), LocalDate.of(2025, 1, 1));
            orders.add(new UUID[] { standingOrder(src, dst, amount, businessDate), src, dst });
        }
        // Insufficient funds: must fail alone, without rolling back the 25 above.
        UUID poorSrc = account("TST-SO-POOR-S", new BigDecimal("150.00"), LocalDate.of(2025, 1, 1));
        UUID poorDst = account("TST-SO-POOR-D", new BigDecimal("1000.00"), LocalDate.of(2025, 1, 1));
        UUID failing = standingOrder(poorSrc, poorDst, new BigDecimal("5000.00"), businessDate);

        JobExecution execution = run(standingOrderJob, businessDate);

        assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        for (UUID[] o : orders) {
            assertThat(balance(o[1])).isEqualByComparingTo("990.00");
            assertThat(balance(o[2])).isEqualByComparingTo("1010.00");
            assertThat(nextExecutionDate(o[0])).isEqualTo(businessDate.plusDays(1));
        }
        assertThat(balance(poorSrc)).isEqualByComparingTo("150.00");
        assertThat(balance(poorDst)).isEqualByComparingTo("1000.00");
        assertThat(nextExecutionDate(failing))
                .as("a failed order stays due, to be retried next run")
                .isEqualTo(businessDate);
    }

    // ── Dormancy ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("dormancy: uses the run's business date and marks every candidate (more than one chunk)")
    void dormancy_usesBusinessDate_andMarksAllCandidates() throws Exception {
        // Business date 2021-01-01 → cutoff 2020-10-03. Only accounts opened before
        // the cutoff qualify, so the 2022 demo accounts are out of scope; with the
        // wall-clock date instead, they would be marked DORMANT too.
        LocalDate businessDate = LocalDate.of(2021, 1, 1);
        List<UUID> stale = new ArrayList<>();
        for (int i = 0; i < 150; i++) { // 150 > chunk size 100: a paged read skipped some
            stale.add(account("TST-DRM-" + i, new BigDecimal("500.00"), LocalDate.of(2019, 1, 1)));
        }
        UUID recent = account("TST-DRM-RECENT", new BigDecimal("500.00"), LocalDate.of(2020, 12, 1));

        JobExecution execution = run(dormancyJob, businessDate);

        assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(stale).allSatisfy(id -> assertThat(status(id)).isEqualTo("DORMANT"));
        assertThat(status(recent)).isEqualTo("ACTIVE");
        assertThat(status(JOHN_SAVINGS)).isEqualTo("ACTIVE");
    }

    // ── Arrears ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("arrears: flags a loan with an overdue instalment, and clears it against an earlier date")
    void arrears_classifiesAgainstBusinessDate() throws Exception {
        JobExecution today = run(arrearsJob, LocalDate.now());
        assertThat(today.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(loanStatus(DEMO_LOAN)).isEqualTo("IN_ARREARS");

        JobExecution before = run(arrearsJob, BEFORE_FIRST_PENDING_DUE);
        assertThat(before.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(loanStatus(DEMO_LOAN)).isEqualTo("ACTIVE");
    }

    // ── CoB Scheduler screen API ──────────────────────────────────────────────

    @Test
    @DisplayName("scheduler API: lists all four jobs with their schedule, in run order")
    void listJobs_returnsDefinitionsWithSchedule() {
        List<CobJobView> jobs = cobJobService.listJobs();

        assertThat(jobs).extracting(CobJobView::jobName).containsExactly(
                "standingOrderExecutionJob", "dormancyClassificationJob",
                "interestAccrualJob", "arrearsClassificationJob");
        assertThat(jobs).allSatisfy(job -> {
            assertThat(job.displayName()).isNotBlank();
            assertThat(job.cronExpression()).isNotBlank();
            assertThat(job.nextRunTime()).isNotNull();
        });
    }

    @Test
    @DisplayName("scheduler API: Run Now reports the real outcome and the run appears in history")
    void runNow_reportsOutcome_andRecordsHistory() {
        CobRunView run = cobJobService.runNow("arrearsClassificationJob");

        assertThat(run.status()).isEqualTo("SUCCESS");
        assertThat(run.businessDate()).isEqualTo(LocalDate.now().toString());
        assertThat(run.errorMessage()).isNull();
        assertThat(cobJobService.history("arrearsClassificationJob"))
                .extracting(CobRunView::id).contains(run.id());
        assertThat(cobJobService.listJobs())
                .filteredOn(j -> j.jobName().equals("arrearsClassificationJob"))
                .singleElement()
                .satisfies(j -> assertThat(j.previousRunStatus()).isEqualTo("SUCCESS"));
    }

    @Test
    @DisplayName("scheduler API: an unknown job name is a 404, not a 500")
    void runNow_unknownJob_isNotFound() {
        assertThatThrownBy(() -> cobJobService.runNow("noSuchJob"))
                .isInstanceOf(CbaException.class)
                .hasMessageContaining("noSuchJob");
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private JobExecution run(Job job, LocalDate businessDate) throws Exception {
        return jobLauncher.run(job, CobJobDefinition.parameters(businessDate));
    }

    private UUID account(String number, BigDecimal balance, LocalDate openedDate) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO accounts (id, account_number, customer_id, product_id, account_type,
                                      status, balance, currency_code, opened_date, created_by)
                VALUES (?, ?, ?, ?, 'SAVINGS', 'ACTIVE', ?, 'USD', ?, 'cob-it')""",
                id, number + "-" + id.toString().substring(0, 6), DEMO_CUSTOMER, SAVINGS_PRODUCT,
                balance, openedDate);
        return id;
    }

    private UUID standingOrder(UUID src, UUID dst, BigDecimal amount, LocalDate due) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO standing_orders (id, source_account_id, destination_account_id, amount,
                                             currency_code, frequency, start_date, next_execution_date,
                                             description, status)
                VALUES (?, ?, ?, ?, 'USD', 'DAILY', ?, ?, 'CoB IT', 'ACTIVE')""",
                id, src, dst, amount, due, due);
        return id;
    }

    private BigDecimal balance(UUID account) {
        return jdbc.queryForObject("SELECT balance FROM accounts WHERE id = ?", BigDecimal.class, account);
    }

    private String status(UUID account) {
        return jdbc.queryForObject("SELECT status FROM accounts WHERE id = ?", String.class, account);
    }

    private String loanStatus(UUID loan) {
        return jdbc.queryForObject("SELECT status FROM loans WHERE id = ?", String.class, loan);
    }

    private LocalDate nextExecutionDate(UUID order) {
        return jdbc.queryForObject("SELECT next_execution_date FROM standing_orders WHERE id = ?",
                LocalDate.class, order);
    }
}
