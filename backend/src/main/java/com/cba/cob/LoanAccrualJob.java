package com.cba.cob;

import com.cba.loan.LoanAccrualService;
import com.cba.loan.LoanRepository;
import com.cba.loan.LoanStatus;
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
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

/**
 * Close-of-Business loan income: for every open loan, days past due, the non-performing
 * suspense rule, charges falling due, and interest and origination-fee accrual through
 * the business date ({@link LoanAccrualService}). Each loan runs in its own transaction;
 * a loan that fails is logged and the others still post, and the run then fails so the
 * CoB screen shows it.
 */
@Configuration
@RequiredArgsConstructor
@Slf4j
public class LoanAccrualJob {

    private final LoanRepository loanRepository;
    private final LoanAccrualService loanAccrualService;

    @Bean("loanAccrualBatchJob")
    public Job loanAccrualJob(JobRepository jobRepository, Step loanAccrualStep) {
        return new JobBuilder("loanAccrualJob", jobRepository)
                .start(loanAccrualStep)
                .build();
    }

    @Bean
    public Step loanAccrualStep(JobRepository jobRepository, Tasklet loanAccrualTasklet,
                                PlatformTransactionManager transactionManager) {
        return new StepBuilder("loanAccrualStep", jobRepository)
                .tasklet(loanAccrualTasklet, transactionManager)
                .build();
    }

    @Bean
    @StepScope
    public Tasklet loanAccrualTasklet(
            @Value("#{jobParameters['" + CobJobDefinition.BUSINESS_DATE + "']}") String businessDateParam) {
        LocalDate businessDate = CobJobDefinition.businessDate(businessDateParam);
        return (contribution, chunkContext) -> {
            List<UUID> loanIds = loanRepository.findIdsByStatusIn(EnumSet.of(LoanStatus.ACTIVE, LoanStatus.IN_ARREARS));
            int failed = 0;
            for (UUID id : loanIds) {
                try {
                    loanAccrualService.runCloseOfBusiness(id, businessDate);
                    contribution.incrementWriteCount(1);
                } catch (RuntimeException e) {
                    failed++;
                    log.error("Loan accrual failed for loan {} on {}: {}", id, businessDate, e.getMessage());
                }
            }
            log.info("Loan accrual for {}: {} loans, {} failed", businessDate, loanIds.size(), failed);
            if (failed > 0) {
                throw new IllegalStateException(failed + " of " + loanIds.size() + " loans failed to accrue on " + businessDate);
            }
            return RepeatStatus.FINISHED;
        };
    }
}
