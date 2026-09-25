package com.cba.cob;

import com.cba.common.exception.CbaException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.quartz.CronTrigger;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.Trigger;
import org.quartz.TriggerKey;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobInstance;
import org.springframework.batch.core.explore.JobExplorer;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Read and run side of the CoB Scheduler screen.
 *
 * <p>Run history comes from Spring Batch's own execution records, not from
 * {@code cob_job_history}: that table is only written by manual runs, so it never
 * showed the nightly Quartz runs. Schedule details come from the live Quartz triggers.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CobJobService {

    static final int HISTORY_LIMIT = 30;
    private static final int ERROR_MESSAGE_LIMIT = 300;

    private final Map<String, Job> jobsByBeanName;
    private final JobLauncher jobLauncher;
    private final JobExplorer jobExplorer;
    private final Scheduler scheduler;
    private final CobJobHistoryRepository historyRepository;

    public List<CobJobView> listJobs() {
        return Arrays.stream(CobJobDefinition.values()).map(this::toJobView).toList();
    }

    public List<CobRunView> history(String jobName) {
        CobJobDefinition job = require(jobName);
        return jobExplorer.getJobInstances(job.jobName(), 0, HISTORY_LIMIT).stream()
                .flatMap(instance -> jobExplorer.getJobExecutions(instance).stream())
                .sorted(Comparator.comparing(JobExecution::getCreateTime).reversed())
                .limit(HISTORY_LIMIT)
                .map(CobJobService::toRunView)
                .toList();
    }

    /**
     * Runs the job synchronously for today's business date and reports how it ended.
     * {@code JobLauncher.run} returns normally even when the job FAILS, so the status
     * must be read from the execution — not inferred from the absence of an exception.
     */
    public CobRunView runNow(String jobName) {
        CobJobDefinition job = require(jobName);
        if (!jobExplorer.findRunningJobExecutions(job.jobName()).isEmpty()) {
            throw CbaException.conflict("COB_JOB_ALREADY_RUNNING",
                    "Job " + job.jobName() + " is already running");
        }

        LocalDate businessDate = LocalDate.now();
        CobJobHistory history = new CobJobHistory();
        history.setJobName(job.jobName());
        history.setBusinessDate(businessDate);
        history.setStatus(CobJobHistory.JobStatus.RUNNING);
        history.setStartedAt(Instant.now());
        history = historyRepository.save(history);

        try {
            JobExecution execution = jobLauncher.run(
                    jobsByBeanName.get(job.beanName()), CobJobDefinition.parameters(businessDate));
            CobRunView run = toRunView(execution);
            history.setStatus(execution.getStatus() == BatchStatus.COMPLETED
                    ? CobJobHistory.JobStatus.COMPLETED : CobJobHistory.JobStatus.FAILED);
            history.setSpringBatchJobExecutionId(execution.getId());
            history.setErrorMessage(run.errorMessage());
            log.info("CoB job '{}' run manually: {}", job.jobName(), execution.getStatus());
            return run;
        } catch (Exception e) {
            history.setStatus(CobJobHistory.JobStatus.FAILED);
            history.setErrorMessage(e.getMessage());
            log.error("CoB job '{}' could not be launched: {}", job.jobName(), e.getMessage(), e);
            throw CbaException.conflict("COB_JOB_LAUNCH_FAILED",
                    "Job " + job.jobName() + " could not be launched: " + e.getMessage());
        } finally {
            history.setCompletedAt(Instant.now());
            historyRepository.save(history);
        }
    }

    private CobJobView toJobView(CobJobDefinition job) {
        Trigger trigger = trigger(job);
        JobExecution last = lastExecution(job);
        return new CobJobView(
                job.jobName(),
                job.displayName(),
                trigger instanceof CronTrigger cron ? cron.getCronExpression() : null,
                trigger != null && trigger.getNextFireTime() != null
                        ? trigger.getNextFireTime().toInstant() : null,
                last != null ? toInstant(last.getStartTime()) : null,
                last != null ? toInstant(last.getEndTime()) : null,
                last != null ? runStatus(last.getStatus()) : null,
                trigger != null && isActive(trigger),
                !jobExplorer.findRunningJobExecutions(job.jobName()).isEmpty());
    }

    private JobExecution lastExecution(CobJobDefinition job) {
        JobInstance instance = jobExplorer.getLastJobInstance(job.jobName());
        return instance == null ? null : jobExplorer.getLastJobExecution(instance);
    }

    /** All jobs share the one CoB trigger: they run in sequence from its fire time. */
    private Trigger trigger(CobJobDefinition job) {
        try {
            return scheduler.getTrigger(TriggerKey.triggerKey(CobJobDefinition.TRIGGER_NAME, CobJobDefinition.TRIGGER_GROUP));
        } catch (SchedulerException e) {
            log.warn("Could not read Quartz trigger for {}: {}", job.jobName(), e.getMessage());
            return null;
        }
    }

    private boolean isActive(Trigger trigger) {
        try {
            Trigger.TriggerState state = scheduler.getTriggerState(trigger.getKey());
            return state != Trigger.TriggerState.PAUSED && state != Trigger.TriggerState.NONE;
        } catch (SchedulerException e) {
            return false;
        }
    }

    private static CobJobDefinition require(String jobName) {
        return CobJobDefinition.byJobName(jobName)
                .orElseThrow(() -> CbaException.notFound("CoB job", jobName));
    }

    static CobRunView toRunView(JobExecution execution) {
        return new CobRunView(
                String.valueOf(execution.getId()),
                execution.getJobInstance().getJobName(),
                execution.getJobParameters().getString(CobJobDefinition.BUSINESS_DATE),
                toInstant(execution.getStartTime()),
                toInstant(execution.getEndTime()),
                runStatus(execution.getStatus()),
                execution.getStatus() == BatchStatus.COMPLETED ? null
                        : firstLine(execution.getExitStatus().getExitDescription()));
    }

    /** The screen's three states. STOPPED / ABANDONED / UNKNOWN all mean "did not complete". */
    static String runStatus(BatchStatus status) {
        return switch (status) {
            case COMPLETED -> "SUCCESS";
            case STARTING, STARTED, STOPPING -> "RUNNING";
            default -> "FAILED";
        };
    }

    /** Spring Batch stores wall-clock LocalDateTime in the JVM zone; send an absolute instant. */
    private static Instant toInstant(LocalDateTime time) {
        return time == null ? null : time.atZone(ZoneId.systemDefault()).toInstant();
    }

    /** Exit descriptions hold full stack traces; the screen needs the first line. */
    private static String firstLine(String description) {
        if (description == null || description.isBlank()) return null;
        String line = description.lines().findFirst().orElse(description).strip();
        return line.length() > ERROR_MESSAGE_LIMIT ? line.substring(0, ERROR_MESSAGE_LIMIT) + "…" : line;
    }
}
