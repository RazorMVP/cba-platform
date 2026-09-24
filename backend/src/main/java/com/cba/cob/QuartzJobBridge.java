package com.cba.cob;

import lombok.extern.slf4j.Slf4j;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.context.ApplicationContext;
import org.springframework.scheduling.quartz.QuartzJobBean;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/**
 * Bridge between Quartz and Spring Batch.
 * Reads the `jobBeanName` job data key, looks up the Spring Batch Job bean,
 * and launches it via JobLauncher for today's business date.
 */
@Component
@Slf4j
public class QuartzJobBridge extends QuartzJobBean {

    private final JobLauncher jobLauncher;
    private final ApplicationContext applicationContext;

    public QuartzJobBridge(JobLauncher jobLauncher, ApplicationContext applicationContext) {
        this.jobLauncher = jobLauncher;
        this.applicationContext = applicationContext;
    }

    @Override
    protected void executeInternal(JobExecutionContext context) throws JobExecutionException {
        String jobBeanName = context.getJobDetail().getJobDataMap().getString("jobBeanName");
        Job job = applicationContext.getBean(jobBeanName, Job.class);

        try {
            var execution = jobLauncher.run(job, CobJobDefinition.parameters(LocalDate.now()));
            log.info("Quartz triggered job '{}' finished: {}", jobBeanName, execution.getStatus());
        } catch (Exception e) {
            log.error("Quartz job '{}' failed: {}", jobBeanName, e.getMessage(), e);
            throw new JobExecutionException(e);
        }
    }
}
