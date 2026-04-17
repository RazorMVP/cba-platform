package com.cba.cob;

import com.cba.account.Account;
import com.cba.account.AccountHoldRepository;
import com.cba.account.AccountRepository;
import com.cba.account.AccountStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.batch.item.ItemWriter;
import org.springframework.batch.item.data.RepositoryItemReader;
import org.springframework.batch.item.data.builder.RepositoryItemReaderBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.Sort;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.LocalDate;
import java.util.Map;

/**
 * Marks ACTIVE accounts as DORMANT when there have been no transactions for
 * the configured dormancy period (default: 90 days).
 *
 * Dormancy criteria:
 *   - Account status is ACTIVE
 *   - lastTransactionDate is NULL or older than today minus dormancyDays
 *   - openedDate is also older than dormancyDays (avoids flagging brand-new accounts)
 *
 * Reactivation is a manual operation via POST /api/v1/accounts/{id}?command=reactivate.
 * Runs at 23:56 (after interest accrual, before arrears classification).
 */
@Configuration
@RequiredArgsConstructor
@Slf4j
public class DormancyClassificationJob {

    private static final int DORMANCY_DAYS = 90;

    private final AccountRepository accountRepository;
    private final AccountHoldRepository accountHoldRepository;

    @Bean("dormancyClassificationBatchJob")
    public Job dormancyClassificationJob(JobRepository jobRepository,
                                          Step dormancyClassificationStep) {
        return new JobBuilder("dormancyClassificationJob", jobRepository)
                .start(dormancyClassificationStep)
                .build();
    }

    @Bean
    public Step dormancyClassificationStep(JobRepository jobRepository,
                                            PlatformTransactionManager transactionManager) {
        return new StepBuilder("dormancyClassificationStep", jobRepository)
                .<Account, Account>chunk(100, transactionManager)
                .reader(dormancyCandidateReader())
                .processor(dormancyProcessor())
                .writer(dormancyWriter())
                .build();
    }

    @Bean
    public RepositoryItemReader<Account> dormancyCandidateReader() {
        LocalDate cutoff = LocalDate.now().minusDays(DORMANCY_DAYS);
        return new RepositoryItemReaderBuilder<Account>()
                .name("dormancyCandidateReader")
                .repository(accountRepository)
                .methodName("findCandidatesForDormancy")
                .arguments(cutoff)
                .sorts(Map.of("id", Sort.Direction.ASC))
                .pageSize(100)
                .build();
    }

    @Bean
    public ItemProcessor<Account, Account> dormancyProcessor() {
        return account -> {
            // Release any active holds on the account before dormancy
            var activeHolds = accountHoldRepository.findByAccountIdAndStatus(
                    account.getId(), com.cba.account.AccountHoldStatus.ACTIVE);
            if (!activeHolds.isEmpty()) {
                activeHolds.forEach(h -> {
                    h.setStatus(com.cba.account.AccountHoldStatus.EXPIRED);
                    h.setReleasedAt(java.time.Instant.now());
                    h.setReleasedBy("dormancy-cob-job");
                });
                accountHoldRepository.saveAll(activeHolds);
                log.info("Expired {} active holds on account {} before dormancy",
                        activeHolds.size(), account.getAccountNumber());
            }
            account.setStatus(AccountStatus.DORMANT);
            return account;
        };
    }

    @Bean
    public ItemWriter<Account> dormancyWriter() {
        return accounts -> {
            accountRepository.saveAll(accounts.getItems());
            log.info("Dormancy classification: marked {} accounts as DORMANT", accounts.size());
        };
    }
}
