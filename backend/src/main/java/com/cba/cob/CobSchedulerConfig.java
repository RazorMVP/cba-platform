package com.cba.cob;

import lombok.extern.slf4j.Slf4j;
import org.quartz.*;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Quartz triggers for nightly Close-of-Business jobs.
 * All jobs run at 23:55 server time to ensure the business date is correct.
 *
 * Job execution order:
 *  1. standing-orders (23:55) — execute scheduled payments first
 *  2. interest-accrual (23:57) — accrue on post-payment balances
 *  3. arrears-classification (23:59) — classify after interest
 */
@Configuration
@Slf4j
public class CobSchedulerConfig {

    private final JobLauncher jobLauncher;
    private final CobJobHistoryRepository historyRepository;
    private final Job standingOrderJob;
    private final Job interestAccrualJob;
    private final Job dormancyJob;
    private final Job arrearsJob;

    public CobSchedulerConfig(
            JobLauncher jobLauncher,
            CobJobHistoryRepository historyRepository,
            @Qualifier("standingOrderExecutionBatchJob")  Job standingOrderJob,
            @Qualifier("interestAccrualBatchJob")         Job interestAccrualJob,
            @Qualifier("dormancyClassificationBatchJob")  Job dormancyJob,
            @Qualifier("arrearsClassificationBatchJob")   Job arrearsJob) {
        this.jobLauncher        = jobLauncher;
        this.historyRepository  = historyRepository;
        this.standingOrderJob   = standingOrderJob;
        this.interestAccrualJob = interestAccrualJob;
        this.dormancyJob        = dormancyJob;
        this.arrearsJob         = arrearsJob;
    }

    // ── Quartz job detail beans ───────────────────────────────────────────────

    @Bean
    public JobDetail standingOrderJobDetail() {
        return JobBuilder.newJob(QuartzJobBridge.class)
                .withIdentity("standingOrderExecution", "cob")
                .usingJobData("jobBeanName", "standingOrderExecutionBatchJob")
                .storeDurably()
                .build();
    }

    @Bean
    public JobDetail interestAccrualJobDetail() {
        return JobBuilder.newJob(QuartzJobBridge.class)
                .withIdentity("interestAccrual", "cob")
                .usingJobData("jobBeanName", "interestAccrualBatchJob")
                .storeDurably()
                .build();
    }

    @Bean
    public JobDetail dormancyJobDetail() {
        return JobBuilder.newJob(QuartzJobBridge.class)
                .withIdentity("dormancyClassification", "cob")
                .usingJobData("jobBeanName", "dormancyClassificationBatchJob")
                .storeDurably()
                .build();
    }

    @Bean
    public JobDetail arrearsJobDetail() {
        return JobBuilder.newJob(QuartzJobBridge.class)
                .withIdentity("arrearsClassification", "cob")
                .usingJobData("jobBeanName", "arrearsClassificationBatchJob")
                .storeDurably()
                .build();
    }

    // ── Cron triggers ─────────────────────────────────────────────────────────

    @Bean
    public Trigger standingOrderTrigger(JobDetail standingOrderJobDetail) {
        return TriggerBuilder.newTrigger()
                .forJob(standingOrderJobDetail)
                .withIdentity("standingOrderTrigger", "cob")
                .withSchedule(CronScheduleBuilder.cronSchedule("0 55 23 * * ?")) // 23:55 daily
                .build();
    }

    @Bean
    public Trigger interestAccrualTrigger(JobDetail interestAccrualJobDetail) {
        return TriggerBuilder.newTrigger()
                .forJob(interestAccrualJobDetail)
                .withIdentity("interestAccrualTrigger", "cob")
                .withSchedule(CronScheduleBuilder.cronSchedule("0 57 23 * * ?")) // 23:57 daily
                .build();
    }

    @Bean
    public Trigger dormancyTrigger(JobDetail dormancyJobDetail) {
        return TriggerBuilder.newTrigger()
                .forJob(dormancyJobDetail)
                .withIdentity("dormancyTrigger", "cob")
                .withSchedule(CronScheduleBuilder.cronSchedule("0 56 23 * * ?")) // 23:56 daily
                .build();
    }

    @Bean
    public Trigger arrearsTrigger(JobDetail arrearsJobDetail) {
        return TriggerBuilder.newTrigger()
                .forJob(arrearsJobDetail)
                .withIdentity("arrearsTrigger", "cob")
                .withSchedule(CronScheduleBuilder.cronSchedule("0 59 23 * * ?")) // 23:59 daily
                .build();
    }

    // ── Manual trigger (called from CobController) ────────────────────────────

    public void triggerJobNow(String jobName) {
        Job job = switch (jobName) {
            case "standingOrderExecutionJob"  -> standingOrderJob;
            case "interestAccrualJob"         -> interestAccrualJob;
            case "dormancyClassificationJob"  -> dormancyJob;
            case "arrearsClassificationJob"   -> arrearsJob;
            default -> throw new IllegalArgumentException("Unknown job: " + jobName);
        };

        CobJobHistory history = new CobJobHistory();
        history.setJobName(jobName);
        history.setBusinessDate(LocalDate.now());
        history.setStatus(CobJobHistory.JobStatus.RUNNING);
        history.setStartedAt(Instant.now());
        CobJobHistory saved = historyRepository.save(history);

        try {
            JobParameters params = new JobParametersBuilder()
                    .addString("businessDate", LocalDate.now().toString())
                    .addLong("runAt", System.currentTimeMillis())
                    .toJobParameters();
            var execution = jobLauncher.run(job, params);
            saved.setStatus(CobJobHistory.JobStatus.COMPLETED);
            saved.setCompletedAt(Instant.now());
            saved.setSpringBatchJobExecutionId(execution.getId());
            log.info("CoB job '{}' completed. Exit: {}", jobName, execution.getExitStatus().getExitCode());
        } catch (Exception e) {
            saved.setStatus(CobJobHistory.JobStatus.FAILED);
            saved.setCompletedAt(Instant.now());
            saved.setErrorMessage(e.getMessage());
            log.error("CoB job '{}' failed: {}", jobName, e.getMessage(), e);
        } finally {
            historyRepository.save(saved);
        }
    }
}
