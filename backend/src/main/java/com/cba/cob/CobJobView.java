package com.cba.cob;

import java.time.Instant;

/**
 * One CoB job as shown on the CoB Scheduler screen. Field names mirror the web
 * app's {@code CobJob} interface (web/src/app/features/reports/report.service.ts).
 *
 * @param previousRunStatus SUCCESS, FAILED or RUNNING; null if the job never ran
 * @param active            false when the Quartz trigger is paused or missing
 */
public record CobJobView(
        String jobName,
        String displayName,
        String cronExpression,
        Instant nextRunTime,
        Instant previousRunStartTime,
        Instant previousRunEndTime,
        String previousRunStatus,
        boolean active,
        boolean currentlyRunning) {
}
