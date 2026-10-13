package com.cba.cob;

import com.cba.loan.LoanImpairmentService;
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
 * Close-of-Business loan impairment (IFRS 9): for every open loan, its stage, expected
 * credit losses (EAD × PD × LGD) and CBN prudential provision, posting the change in the
 * loss allowance ({@link LoanImpairmentService}). Runs after loan accrual, so days past
 * due and the receivables are current. Each loan runs in its own transaction; a loan
 * that fails is logged and the others still post, and the run then fails so the CoB
 * screen shows it.
 */
@Configuration
@RequiredArgsConstructor
@Slf4j
public class LoanImpairmentJob {

    private final LoanRepository loanRepository;
    private final LoanImpairmentService loanImpairmentService;

    @Bean("loanImpairmentBatchJob")
    public Job loanImpairmentJob(JobRepository jobRepository, Step loanImpairmentStep) {
        return new JobBuilder("loanImpairmentJob", jobRepository)
                .start(loanImpairmentStep)
                .build();
    }

    @Bean
    public Step loanImpairmentStep(JobRepository jobRepository, Tasklet loanImpairmentTasklet,
                                PlatformTransactionManager transactionManager) {
        return new StepBuilder("loanImpairmentStep", jobRepository)
                .tasklet(loanImpairmentTasklet, transactionManager)
                .build();
    }

    @Bean
    @StepScope
    public Tasklet loanImpairmentTasklet(
            @Value("#{jobParameters['" + CobJobDefinition.BUSINESS_DATE + "']}") String businessDateParam) {
        LocalDate businessDate = CobJobDefinition.businessDate(businessDateParam);
        return (contribution, chunkContext) -> {
            List<UUID> loanIds = loanRepository.findIdsByStatusIn(EnumSet.of(LoanStatus.ACTIVE, LoanStatus.IN_ARREARS));
            int failed = 0;
            for (UUID id : loanIds) {
                try {
                    loanImpairmentService.measure(id, businessDate);
                    contribution.incrementWriteCount(1);
                } catch (RuntimeException e) {
                    failed++;
                    log.error("Loan impairment failed for loan {} on {}: {}", id, businessDate, e.getMessage());
                }
            }
            log.info("Loan impairment for {}: {} loans, {} failed", businessDate, loanIds.size(), failed);
            if (failed > 0) {
                throw new IllegalStateException(failed + " of " + loanIds.size() + " loans failed impairment measurement on " + businessDate);
            }
            return RepeatStatus.FINISHED;
        };
    }
}
