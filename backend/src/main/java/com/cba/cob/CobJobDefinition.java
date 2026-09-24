package com.cba.cob;

import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.Optional;

/**
 * The nightly Close-of-Business jobs, in schedule order. One place that ties each
 * job's Spring Batch name to its bean, its Quartz trigger and its display name, so
 * the scheduler, the manual trigger and the CoB Scheduler screen can't drift apart.
 */
public enum CobJobDefinition {

    STANDING_ORDERS ("standingOrderExecutionJob", "Standing Order Execution",
                     "standingOrderExecutionBatchJob", "standingOrderTrigger"),
    DORMANCY        ("dormancyClassificationJob", "Dormancy Classification",
                     "dormancyClassificationBatchJob", "dormancyTrigger"),
    INTEREST_ACCRUAL("interestAccrualJob", "Interest Accrual",
                     "interestAccrualBatchJob", "interestAccrualTrigger"),
    ARREARS         ("arrearsClassificationJob", "Arrears Classification",
                     "arrearsClassificationBatchJob", "arrearsTrigger");

    /** Job parameter every CoB run carries: the ISO business date the run is for. */
    public static final String BUSINESS_DATE = "businessDate";
    public static final String TRIGGER_GROUP = "cob";

    private final String jobName;
    private final String displayName;
    private final String beanName;
    private final String triggerName;

    CobJobDefinition(String jobName, String displayName, String beanName, String triggerName) {
        this.jobName = jobName;
        this.displayName = displayName;
        this.beanName = beanName;
        this.triggerName = triggerName;
    }

    public String jobName()     { return jobName; }
    public String displayName() { return displayName; }
    public String beanName()    { return beanName; }
    public String triggerName() { return triggerName; }

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
