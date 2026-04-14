package com.cba.cob;

import com.cba.account.Account;
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

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Map;

/**
 * Spring Batch job that accrues daily interest on all ACTIVE savings accounts.
 * Formula: dailyInterest = balance × (annualRate / 365)
 * Runs nightly via Quartz trigger.
 */
@Configuration
@RequiredArgsConstructor
@Slf4j
public class InterestAccrualJob {

    private final AccountRepository accountRepository;

    @Bean("interestAccrualBatchJob")
    public Job interestAccrualJob(JobRepository jobRepository,
                                   Step interestAccrualStep) {
        return new JobBuilder("interestAccrualJob", jobRepository)
                .start(interestAccrualStep)
                .build();
    }

    @Bean
    public Step interestAccrualStep(JobRepository jobRepository,
                                    PlatformTransactionManager transactionManager) {
        return new StepBuilder("interestAccrualStep", jobRepository)
                .<Account, Account>chunk(100, transactionManager)
                .reader(activeAccountReader())
                .processor(accrualProcessor())
                .writer(accrualWriter())
                .build();
    }

    @Bean
    public RepositoryItemReader<Account> activeAccountReader() {
        return new RepositoryItemReaderBuilder<Account>()
                .name("activeAccountReader")
                .repository(accountRepository)
                .methodName("findByStatus")
                .arguments(AccountStatus.ACTIVE)
                .sorts(Map.of("id", Sort.Direction.ASC))
                .pageSize(100)
                .build();
    }

    @Bean
    public ItemProcessor<Account, Account> accrualProcessor() {
        return account -> {
            if (account.getBalance().compareTo(BigDecimal.ZERO) <= 0) return null;
            if (account.getProduct() == null) return null;

            BigDecimal annualRate = account.getProduct().getInterestRate();
            if (annualRate == null || annualRate.compareTo(BigDecimal.ZERO) == 0) return null;

            BigDecimal dailyInterest = account.getBalance()
                    .multiply(annualRate)
                    .divide(BigDecimal.valueOf(100 * 365), 4, RoundingMode.HALF_UP);

            if (dailyInterest.compareTo(BigDecimal.ZERO) > 0) {
                account.setBalance(account.getBalance().add(dailyInterest));
                log.debug("Accrued {} interest on account {}", dailyInterest, account.getAccountNumber());
            }
            return account;
        };
    }

    @Bean
    public ItemWriter<Account> accrualWriter() {
        return accounts -> {
            accountRepository.saveAll(accounts.getItems());
            log.info("Interest accrual: processed {} accounts for {}", accounts.size(), LocalDate.now());
        };
    }
}
