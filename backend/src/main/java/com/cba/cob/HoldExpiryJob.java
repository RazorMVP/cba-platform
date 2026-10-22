package com.cba.cob;

import com.cba.account.AccountHoldRepository;
import com.cba.audit.AuditLogService;
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

import java.time.Instant;
import java.time.LocalDate;

/**
 * Close-of-Business hold sweep: ACTIVE holds whose expiry date is before the business date become
 * EXPIRED. Card authorization holds expire with the scheme authorization lifetime
 * ({@code card.hold-expiry-days}); a hold past that date already stopped reducing the balance, and
 * this records it. A cleared presentment that arrives later is a late presentment (checked like a
 * withdrawal, never force-posted). Holds without an expiry date are untouched.
 */
@Configuration
@RequiredArgsConstructor
@Slf4j
public class HoldExpiryJob {

    private final AccountHoldRepository accountHoldRepository;
    private final AuditLogService auditLogService;

    @Bean("holdExpiryBatchJob")
    public Job holdExpiryJob(JobRepository jobRepository, Step holdExpiryStep) {
        return new JobBuilder("holdExpiryJob", jobRepository)
                .start(holdExpiryStep)
                .build();
    }

    @Bean
    public Step holdExpiryStep(JobRepository jobRepository, Tasklet holdExpiryTasklet,
                               PlatformTransactionManager transactionManager) {
        return new StepBuilder("holdExpiryStep", jobRepository)
                .tasklet(holdExpiryTasklet, transactionManager)
                .build();
    }

    @Bean
    @StepScope
    public Tasklet holdExpiryTasklet(
            @Value("#{jobParameters['" + CobJobDefinition.BUSINESS_DATE + "']}") String businessDateParam) {
        LocalDate businessDate = CobJobDefinition.businessDate(businessDateParam);
        return (contribution, chunkContext) -> {
            int expired = accountHoldRepository.expireDue(businessDate, Instant.now());
            contribution.incrementWriteCount(expired);
            if (expired > 0) {
                auditLogService.log("ACCOUNT_HOLD", "hold-expiry-sweep", "HOLDS_EXPIRED", null,
                        "count=" + expired + " businessDate=" + businessDate);
            }
            log.info("Hold expiry for {}: {} holds marked EXPIRED", businessDate, expired);
            return RepeatStatus.FINISHED;
        };
    }
}
