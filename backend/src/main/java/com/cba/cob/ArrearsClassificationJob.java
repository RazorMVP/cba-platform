package com.cba.cob;

import com.cba.loan.Loan;
import com.cba.loan.LoanRepository;
import com.cba.loan.LoanRepaymentSchedule;
import com.cba.loan.LoanStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.batch.item.ItemWriter;
import org.springframework.batch.item.data.RepositoryItemReader;
import org.springframework.batch.item.data.builder.RepositoryItemReaderBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.Sort;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Classifies active loans as IN_ARREARS when any installment is overdue.
 * Clears the IN_ARREARS flag when all past installments are paid.
 */
@Configuration
@RequiredArgsConstructor
@Slf4j
public class ArrearsClassificationJob {

    private final LoanRepository loanRepository;

    @Bean("arrearsClassificationBatchJob")
    public Job arrearsClassificationJob(JobRepository jobRepository,
                                         Step arrearsClassificationStep) {
        return new JobBuilder("arrearsClassificationJob", jobRepository)
                .start(arrearsClassificationStep)
                .build();
    }

    @Bean
    public Step arrearsClassificationStep(JobRepository jobRepository,
                                           PlatformTransactionManager transactionManager) {
        return new StepBuilder("arrearsClassificationStep", jobRepository)
                .<Loan, Loan>chunk(50, transactionManager)
                .reader(activeLoanReader())
                .processor(arrearsProcessor(null))
                .writer(arrearsWriter())
                .build();
    }

    /**
     * Paging is safe here, unlike dormancy: the job only moves loans between ACTIVE
     * and IN_ARREARS, both inside the query, so the result set never shifts.
     * Step-scoped so each run gets a fresh reader instead of sharing one instance.
     */
    @Bean
    @StepScope
    public RepositoryItemReader<Loan> activeLoanReader() {
        return new RepositoryItemReaderBuilder<Loan>()
                .name("activeLoanReader")
                .repository(loanRepository)
                .methodName("findByStatusIn")
                // The builder's arguments(List) overload treats the list AS the argument
                // list, so a bare List.of(ACTIVE, IN_ARREARS) became two LoanStatus
                // arguments → NoSuchMethodException. Wrap it: one argument, a List.
                .arguments(List.of(List.of(LoanStatus.ACTIVE, LoanStatus.IN_ARREARS)))
                .sorts(Map.of("id", Sort.Direction.ASC))
                .pageSize(50)
                .build();
    }

    @Bean
    @StepScope
    public ItemProcessor<Loan, Loan> arrearsProcessor(
            @Value("#{jobParameters['" + CobJobDefinition.BUSINESS_DATE + "']}") String businessDateParam) {
        LocalDate businessDate = CobJobDefinition.businessDate(businessDateParam);
        return loan -> {
            boolean hasOverdue = loan.getRepaymentSchedule().stream()
                    .anyMatch(inst ->
                        inst.getStatus() == LoanRepaymentSchedule.InstallmentStatus.PENDING
                        && inst.getDueDate().isBefore(businessDate));

            LoanStatus newStatus = hasOverdue ? LoanStatus.IN_ARREARS : LoanStatus.ACTIVE;
            if (loan.getStatus() != newStatus) {
                loan.setStatus(newStatus);
                return loan;
            }
            return null; // no change — skip write
        };
    }

    @Bean
    public ItemWriter<Loan> arrearsWriter() {
        return loans -> {
            loanRepository.saveAll(loans.getItems());
            log.info("Arrears classification: updated {} loans", loans.size());
        };
    }
}
