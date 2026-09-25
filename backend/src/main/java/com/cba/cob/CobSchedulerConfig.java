package com.cba.cob;

import org.quartz.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The single Quartz trigger for Close of Business (23:55 server time).
 *
 * It starts {@link CloseOfBusinessQuartzJob}, which runs the jobs one after another
 * in {@link CobJobDefinition} order — standing orders → dormancy → interest accrual
 * → arrears — each starting only when the previous one has finished.
 *
 * The four per-job triggers this replaced are deleted by migration V53: Quartz
 * persists triggers in the database, so removing their beans alone would leave
 * them firing.
 */
@Configuration
public class CobSchedulerConfig {

    @Bean
    public JobDetail closeOfBusinessJobDetail() {
        return JobBuilder.newJob(CloseOfBusinessQuartzJob.class)
                .withIdentity(CobJobDefinition.QUARTZ_JOB_NAME, CobJobDefinition.TRIGGER_GROUP)
                .storeDurably()
                .build();
    }

    @Bean
    public Trigger closeOfBusinessTrigger(JobDetail closeOfBusinessJobDetail) {
        return TriggerBuilder.newTrigger()
                .forJob(closeOfBusinessJobDetail)
                .withIdentity(CobJobDefinition.TRIGGER_NAME, CobJobDefinition.TRIGGER_GROUP)
                .withSchedule(CronScheduleBuilder.cronSchedule(CobJobDefinition.CRON))
                .build();
    }
}
