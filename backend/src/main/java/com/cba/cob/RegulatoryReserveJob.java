package com.cba.cob;

import com.cba.loan.RegulatoryReserveService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.LocalDate;

/**
 * Close-of-Business CBN regulatory risk reserve: prudential provisions in excess of the
 * IFRS 9 allowance move from retained earnings to a non-distributable reserve
 * ({@link RegulatoryReserveService}). Nightly runs post only on the last day of a month;
 * a manual run ({@link CobJobDefinition#MANUAL}) posts whatever the date. Runs after the
 * impairment job, so every loan's provisions are current.
 */
@Configuration
@RequiredArgsConstructor
@Slf4j
public class RegulatoryReserveJob {

    private final RegulatoryReserveService regulatoryReserveService;

    @Bean("regulatoryRiskReserveBatchJob")
    public Job regulatoryRiskReserveJob(JobRepository jobRepository, Step regulatoryRiskReserveStep) {
        return new JobBuilder("regulatoryRiskReserveJob", jobRepository)
                .start(regulatoryRiskReserveStep)
                .build();
    }

    @Bean
    public Step regulatoryRiskReserveStep(JobRepository jobRepository, Tasklet regulatoryRiskReserveTasklet,
                                          PlatformTransactionManager transactionManager) {
        return new StepBuilder("regulatoryRiskReserveStep", jobRepository)
                .tasklet(regulatoryRiskReserveTasklet, transactionManager)
                .build();
    }

    @Bean
    @StepScope
    public Tasklet regulatoryRiskReserveTasklet(
            @Value("#{jobParameters['" + CobJobDefinition.BUSINESS_DATE + "']}") String businessDateParam,
            @Value("#{jobParameters['" + CobJobDefinition.MANUAL + "']}") String manualParam) {
        LocalDate businessDate = CobJobDefinition.businessDate(businessDateParam);
        boolean manual = Boolean.parseBoolean(manualParam);
        return (contribution, chunkContext) -> {
            var run = regulatoryReserveService.post(businessDate, manual);
            if (run.skipped()) {
                log.info("Regulatory risk reserve: {} is not a month end; nothing to do", businessDate);
            } else if (run.transactionId() != null) {
                contribution.incrementWriteCount(1);
            }
            return RepeatStatus.FINISHED;
        };
    }
}
