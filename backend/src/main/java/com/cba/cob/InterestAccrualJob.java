package com.cba.cob;

import com.cba.account.Account;
import com.cba.account.AccountGlPosting;
import com.cba.account.AccountRepository;
import com.cba.account.AccountStatus;
import com.cba.account.Transaction;
import com.cba.account.TransactionRepository;
import com.cba.account.TransactionType;
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

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Spring Batch job that accrues daily interest on all ACTIVE savings accounts.
 * Formula: dailyInterest = balance × (annualRate / 365)
 *
 * <p>For every account credited it writes, in the same chunk transaction:
 * <ul>
 *   <li>the balance change and an INTEREST_CREDIT {@link Transaction}, and</li>
 *   <li>the GL double entry: DR interest on savings / CR savings control, dated by the
 *       business date — each resolved from the deposit product's GL links, else from
 *       {@code EXPENSE_INTEREST_ON_SAVINGS} / {@code LIABILITY_SAVINGS_CONTROL}.</li>
 * </ul>
 * If an account has no GL mapping the chunk rolls back and the job fails — interest is
 * never credited to a customer without its ledger entry.
 *
 * <p>Paging is safe here: the job changes balances, never status, so the ACTIVE
 * result set doesn't shift under the reader.
 */
@Configuration
@RequiredArgsConstructor
@Slf4j
public class InterestAccrualJob {

    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private final AccountGlPosting accountGlPosting;

    record AccrualResult(Account account, BigDecimal interestAmount) {}

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
                .<Account, AccrualResult>chunk(100, transactionManager)
                .reader(activeAccountReader())
                .processor(accrualProcessor())
                .writer(accrualWriter(null))
                .build();
    }

    @Bean
    @StepScope
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
    public ItemProcessor<Account, AccrualResult> accrualProcessor() {
        return account -> {
            if (account.getBalance().compareTo(BigDecimal.ZERO) <= 0) return null;
            if (account.getProduct() == null) return null;

            BigDecimal annualRate = account.getProduct().getInterestRate();
            if (annualRate == null || annualRate.compareTo(BigDecimal.ZERO) == 0) return null;

            BigDecimal dailyInterest = account.getBalance()
                    .multiply(annualRate)
                    .divide(BigDecimal.valueOf(100L * 365), 4, RoundingMode.HALF_UP);

            if (dailyInterest.compareTo(BigDecimal.ZERO) <= 0) return null;

            account.setBalance(account.getBalance().add(dailyInterest));
            log.debug("Accrued {} interest on account {}", dailyInterest, account.getAccountNumber());
            return new AccrualResult(account, dailyInterest);
        };
    }

    @Bean
    @StepScope
    public ItemWriter<AccrualResult> accrualWriter(
            @Value("#{jobParameters['" + CobJobDefinition.BUSINESS_DATE + "']}") String businessDateParam) {
        LocalDate businessDate = CobJobDefinition.businessDate(businessDateParam);
        return results -> {
            List<Account> accounts = new ArrayList<>(results.size());
            List<Transaction> transactions = new ArrayList<>(results.size());

            for (AccrualResult result : results) {
                Account account = result.account();
                accounts.add(account);
                String reference = "INT-" + System.currentTimeMillis() + "-" + account.getId().toString().substring(0, 8);
                transactions.add(Transaction.of(
                        account,
                        TransactionType.INTEREST_CREDIT,
                        result.interestAmount(),
                        account.getBalance(),
                        "Daily interest accrual",
                        reference,
                        "system"
                ));
                accountGlPosting.postInterestCredit(account,
                        account.getBalance().subtract(result.interestAmount()), businessDate,
                        "Daily interest accrual " + account.getAccountNumber(), reference);
            }

            accountRepository.saveAll(accounts);
            transactionRepository.saveAll(transactions);
            log.info("Interest accrual: credited and posted {} accounts for {}", accounts.size(), businessDate);
        };
    }
}
