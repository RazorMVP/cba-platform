package com.cba.cob;

import java.time.Instant;

/**
 * One execution of a CoB job, from Spring Batch's execution records. Field names
 * mirror the web app's {@code CobJobHistory} interface.
 *
 * @param id           Spring Batch job execution id
 * @param businessDate the ISO date the run was for
 * @param status       SUCCESS, FAILED or RUNNING
 * @param errorMessage first line of the failure; null on success
 */
public record CobRunView(
        String id,
        String jobName,
        String businessDate,
        Instant startTime,
        Instant endTime,
        String status,
        String errorMessage) {
}
