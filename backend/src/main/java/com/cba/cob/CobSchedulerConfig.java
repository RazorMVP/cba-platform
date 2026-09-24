package com.cba.cob;

import org.quartz.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Quartz triggers for the nightly Close-of-Business jobs (server time).
 *
 * Job execution order:
 *  1. standing-orders         (23:55) — execute scheduled payments first
 *  2. dormancy-classification (23:56) — flag inactive accounts
 *  3. interest-accrual        (23:57) — accrue on post-payment balances
 *  4. arrears-classification  (23:59) — classify after interest
 *
 * Manual runs and the job list shown on the CoB Scheduler screen live in
 * {@link CobJobService}.
 */
@Configuration
public class CobSchedulerConfig {

    // ── Quartz job detail beans ───────────────────────────────────────────────

    @Bean
    public JobDetail standingOrderJobDetail() {
        return jobDetail("standingOrderExecution", CobJobDefinition.STANDING_ORDERS);
    }

    @Bean
    public JobDetail interestAccrualJobDetail() {
        return jobDetail("interestAccrual", CobJobDefinition.INTEREST_ACCRUAL);
    }

    @Bean
    public JobDetail dormancyJobDetail() {
        return jobDetail("dormancyClassification", CobJobDefinition.DORMANCY);
    }

    @Bean
    public JobDetail arrearsJobDetail() {
        return jobDetail("arrearsClassification", CobJobDefinition.ARREARS);
    }

    // ── Cron triggers ─────────────────────────────────────────────────────────

    @Bean
    public Trigger standingOrderTrigger(JobDetail standingOrderJobDetail) {
        return trigger(standingOrderJobDetail, CobJobDefinition.STANDING_ORDERS, "0 55 23 * * ?"); // 23:55 daily
    }

    @Bean
    public Trigger interestAccrualTrigger(JobDetail interestAccrualJobDetail) {
        return trigger(interestAccrualJobDetail, CobJobDefinition.INTEREST_ACCRUAL, "0 57 23 * * ?"); // 23:57 daily
    }

    @Bean
    public Trigger dormancyTrigger(JobDetail dormancyJobDetail) {
        return trigger(dormancyJobDetail, CobJobDefinition.DORMANCY, "0 56 23 * * ?"); // 23:56 daily
    }

    @Bean
    public Trigger arrearsTrigger(JobDetail arrearsJobDetail) {
        return trigger(arrearsJobDetail, CobJobDefinition.ARREARS, "0 59 23 * * ?"); // 23:59 daily
    }

    private static JobDetail jobDetail(String identity, CobJobDefinition job) {
        return JobBuilder.newJob(QuartzJobBridge.class)
                .withIdentity(identity, CobJobDefinition.TRIGGER_GROUP)
                .usingJobData("jobBeanName", job.beanName())
                .storeDurably()
                .build();
    }

    private static Trigger trigger(JobDetail detail, CobJobDefinition job, String cron) {
        return TriggerBuilder.newTrigger()
                .forJob(detail)
                .withIdentity(job.triggerName(), CobJobDefinition.TRIGGER_GROUP)
                .withSchedule(CronScheduleBuilder.cronSchedule(cron))
                .build();
    }
}
