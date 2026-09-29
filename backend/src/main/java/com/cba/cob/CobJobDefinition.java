package com.cba.cob;

import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.Optional;

/**
 * The nightly Close-of-Business jobs, <b>in run order</b>. One place that ties each
 * job's Spring Batch name to its bean and display name, so the nightly sequence,
 * the manual trigger and the CoB Scheduler screen can't drift apart.
 *
 * <p>The declaration order is the execution order: {@link CobRunner} runs the jobs
 * one after another from a single Quartz trigger ({@link #TRIGGER_NAME}).
 */
public enum CobJobDefinition {

    STANDING_ORDERS ("standingOrderExecutionJob", "Standing Order Execution",
                     "standingOrderExecutionBatchJob"),
    DORMANCY        ("dormancyClassificationJob", "Dormancy Classification",
                     "dormancyClassificationBatchJob"),
    INTEREST_ACCRUAL("interestAccrualJob", "Interest Accrual",
                     "interestAccrualBatchJob"),
    ARREARS         ("arrearsClassificationJob", "Arrears Classification",
                     "arrearsClassificationBatchJob");

    /** Job parameter every CoB run carries: the ISO business date the run is for. */
    public static final String BUSINESS_DATE = "businessDate";

    /** The one Quartz trigger that starts the whole sequence. */
    public static final String TRIGGER_GROUP = "cob";
    public static final String TRIGGER_NAME = "closeOfBusinessTrigger";
    public static final String QUARTZ_JOB_NAME = "closeOfBusiness";
    public static final String CRON = "0 55 23 * * ?"; // 23:55 daily, server time

    private final String jobName;
    private final String displayName;
    private final String beanName;

    CobJobDefinition(String jobName, String displayName, String beanName) {
        this.jobName = jobName;
        this.displayName = displayName;
        this.beanName = beanName;
    }

    public String jobName()     { return jobName; }
    public String displayName() { return displayName; }
    public String beanName()    { return beanName; }

    public static Optional<CobJobDefinition> byJobName(String jobName) {
        return Arrays.stream(values()).filter(d -> d.jobName.equals(jobName)).findFirst();
    }

    /**
     * Parameters for one run. {@code runAt} makes every launch a new job instance, so
     * a job can run again on the same business date (e.g. a manual re-run).
     */
    public static JobParameters parameters(LocalDate businessDate) {
        return new JobParametersBuilder()
                .addString(BUSINESS_DATE, businessDate.toString())
                .addLong("runAt", System.currentTimeMillis())
                .toJobParameters();
    }

    /**
     * Resolves the {@code businessDate} job parameter. Step-scoped beans must use
     * this rather than {@code LocalDate.now()}: a singleton bean evaluates the date
     * once, at application start-up, and keeps using it on every later run.
     */
    public static LocalDate businessDate(String parameter) {
        return parameter == null ? LocalDate.now() : LocalDate.parse(parameter);
    }
}
