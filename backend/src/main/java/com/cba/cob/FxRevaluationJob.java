package com.cba.cob;

import com.cba.accounting.FxRevaluationService;
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
 * Close-of-Business FX revaluation (IAS 21 §23, §28): retranslates every open foreign
 * currency position at the closing rate and posts the exchange difference to profit or
 * loss. Runs last, after every other job of the day has posted its movements.
 */
@Configuration
@RequiredArgsConstructor
@Slf4j
public class FxRevaluationJob {

    private final FxRevaluationService fxRevaluationService;

    @Bean("fxRevaluationBatchJob")
    public Job fxRevaluationJob(JobRepository jobRepository, Step fxRevaluationStep) {
        return new JobBuilder("fxRevaluationJob", jobRepository)
                .start(fxRevaluationStep)
                .build();
    }

    /** Takes the step-scoped tasklet proxy; its job parameters bind when the step runs. */
    @Bean
    public Step fxRevaluationStep(JobRepository jobRepository, Tasklet fxRevaluationTasklet,
                                  PlatformTransactionManager transactionManager) {
        return new StepBuilder("fxRevaluationStep", jobRepository)
                .tasklet(fxRevaluationTasklet, transactionManager)
                .build();
    }

    @Bean
    @StepScope
    public Tasklet fxRevaluationTasklet(
            @Value("#{jobParameters['" + CobJobDefinition.BUSINESS_DATE + "']}") String businessDateParam) {
        LocalDate businessDate = CobJobDefinition.businessDate(businessDateParam);
        return (contribution, chunkContext) -> {
            var results = fxRevaluationService.revalue(businessDate);
            long posted = results.stream().filter(r -> r.transactionId() != null).count();
            contribution.incrementWriteCount(posted);
            log.info("FX revaluation for {}: {} currencies, {} adjusted", businessDate, results.size(), posted);
            return RepeatStatus.FINISHED;
        };
    }
}
