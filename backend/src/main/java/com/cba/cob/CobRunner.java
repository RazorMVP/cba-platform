package com.cba.cob;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Runs the Close-of-Business jobs in {@link CobJobDefinition} order, each one
 * starting only after the previous one has finished.
 *
 * <p>The jobs used to have one Quartz trigger each, a minute apart. Nothing made a
 * job wait for the one before it, so a slow standing-order run overlapped interest
 * accrual, and a catch-up after downtime fired all four at once — interest was
 * accrued on balances the standing orders hadn't yet moved.
 *
 * <p>A failed job does <b>not</b> stop the ones after it. They don't depend on each
 * other's output, only on the order, and skipping them would compound one failure
 * (a standing-order outage would also cost every customer a day of interest). The
 * failure is logged and visible on the CoB Scheduler screen.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CobRunner {

    private final Map<String, Job> jobsByBeanName;
    private final JobLauncher jobLauncher;

    /** Runs all jobs for {@code businessDate}; returns one execution per job that launched. */
    public List<JobExecution> runAll(LocalDate businessDate) {
        log.info("Close of Business for {}: starting {} jobs", businessDate, CobJobDefinition.values().length);
        List<JobExecution> executions = new ArrayList<>();

        for (CobJobDefinition job : CobJobDefinition.values()) {
            try {
                // JobLauncher is synchronous (Boot's default), so this returns when the job ends.
                JobExecution execution = jobLauncher.run(
                        jobsByBeanName.get(job.beanName()), CobJobDefinition.parameters(businessDate));
                executions.add(execution);
                if (execution.getStatus() == BatchStatus.COMPLETED) {
                    log.info("Close of Business: {} completed", job.jobName());
                } else {
                    log.error("Close of Business: {} ended {} — continuing with the next job",
                            job.jobName(), execution.getStatus());
                }
            } catch (Exception e) {
                log.error("Close of Business: {} could not be launched — continuing: {}",
                        job.jobName(), e.getMessage(), e);
            }
        }

        long failed = executions.stream().filter(e -> e.getStatus() != BatchStatus.COMPLETED).count()
                + (CobJobDefinition.values().length - executions.size());
        log.info("Close of Business for {} finished: {} of {} jobs succeeded",
                businessDate, CobJobDefinition.values().length - failed, CobJobDefinition.values().length);
        return executions;
    }
}
