package com.cba.cob;

import lombok.extern.slf4j.Slf4j;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.context.ApplicationContext;
import org.springframework.scheduling.quartz.QuartzJobBean;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/**
 * Bridge between Quartz and Spring Batch.
 * Reads the `jobBeanName` job data key, looks up the Spring Batch Job bean,
 * and launches it via JobLauncher.
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
            JobParameters params = new JobParametersBuilder()
                    .addString("businessDate", LocalDate.now().toString())
                    .addLong("runAt", System.currentTimeMillis())
                    .toJobParameters();
            jobLauncher.run(job, params);
            log.info("Quartz triggered job '{}' completed", jobBeanName);
        } catch (Exception e) {
            log.error("Quartz job '{}' failed: {}", jobBeanName, e.getMessage(), e);
            throw new JobExecutionException(e);
        }
    }
}
