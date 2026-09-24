package com.cba.cob;

import com.cba.payment.PaymentService;
import com.cba.payment.StandingOrder;
import com.cba.payment.StandingOrderRepository;
import com.cba.payment.dto.TransferRequest;
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
import org.springframework.batch.support.transaction.ResourcelessTransactionManager;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Executes all standing orders due on the business date and advances their next
 * execution date.
 *
 * <p>Each order runs in its <b>own</b> transaction: the transfer and the schedule
 * advance commit together or not at all. A chunk-oriented step can't give that —
 * {@code PaymentService.transfer} joins the chunk transaction, so one failed order
 * (e.g. insufficient funds) marks the whole chunk rollback-only and undoes the
 * orders that succeeded alongside it. The step itself uses a resourceless
 * transaction manager because all real work happens in the per-order transactions.
 */
@Configuration
@RequiredArgsConstructor
@Slf4j
public class StandingOrderExecutionJob {

    private final StandingOrderRepository standingOrderRepository;
    private final PaymentService paymentService;

    @Bean("standingOrderExecutionBatchJob")
    public Job standingOrderExecutionJob(JobRepository jobRepository,
                                          Step standingOrderStep) {
        return new JobBuilder("standingOrderExecutionJob", jobRepository)
                .start(standingOrderStep)
                .build();
    }

    @Bean
    public Step standingOrderStep(JobRepository jobRepository) {
        return new StepBuilder("standingOrderStep", jobRepository)
                .tasklet(standingOrderTasklet(null, null), new ResourcelessTransactionManager())
                .build();
    }

    @Bean
    @StepScope
    public Tasklet standingOrderTasklet(
            @Value("#{jobParameters['" + CobJobDefinition.BUSINESS_DATE + "']}") String businessDateParam,
            PlatformTransactionManager transactionManager) {
        LocalDate businessDate = CobJobDefinition.businessDate(businessDateParam);
        TransactionTemplate perOrder = new TransactionTemplate(transactionManager);

        return (contribution, chunkContext) -> {
            List<UUID> dueIds = standingOrderRepository.findDueOrderIds(businessDate);
            int executed = 0;
            int failed = 0;

            for (UUID id : dueIds) {
                contribution.incrementReadCount();
                try {
                    Boolean ran = perOrder.execute(tx -> executeOrder(id, businessDate));
                    if (Boolean.TRUE.equals(ran)) {
                        executed++;
                    } else {
                        contribution.incrementFilterCount(1);
                    }
                } catch (RuntimeException e) {
                    // Rolled back as a unit; the order stays due and is retried next run.
                    failed++;
                    contribution.incrementProcessSkipCount();
                    log.error("Standing order {} execution failed: {}", id, e.getMessage());
                }
            }

            contribution.incrementWriteCount(executed);
            log.info("Standing orders for {}: {} due, {} executed, {} failed",
                    businessDate, dueIds.size(), executed, failed);
            return RepeatStatus.FINISHED;
        };
    }

    /**
     * Runs one order inside the caller's transaction. Re-reads the order so a change
     * since the ID snapshot (paused, cancelled, already run) is honoured; returns
     * {@code false} when the order is no longer due.
     */
    private boolean executeOrder(UUID id, LocalDate businessDate) {
        StandingOrder order = standingOrderRepository.findById(id).orElse(null);
        if (order == null
                || order.getStatus() != StandingOrder.Status.ACTIVE
                || order.getNextExecutionDate().isAfter(businessDate)) {
            return false;
        }

        paymentService.transfer(new TransferRequest(
                order.getSourceAccount().getId(),
                order.getDestinationAccount().getId(),
                order.getAmount(),
                "Standing order: " + order.getDescription(),
                null
        ), "system");

        order.setLastExecutedAt(Instant.now());
        order.setNextExecutionDate(computeNext(order));
        if (order.getEndDate() != null && order.getNextExecutionDate().isAfter(order.getEndDate())) {
            order.setStatus(StandingOrder.Status.COMPLETED);
        }
        // @Version on StandingOrder: a concurrent run that already advanced this order
        // fails here with an optimistic-lock error, rolling back its duplicate transfer.
        standingOrderRepository.saveAndFlush(order);
        return true;
    }

    private LocalDate computeNext(StandingOrder order) {
        return switch (order.getFrequency()) {
            case DAILY     -> order.getNextExecutionDate().plusDays(1);
            case WEEKLY    -> order.getNextExecutionDate().plusWeeks(1);
            case MONTHLY   -> order.getNextExecutionDate().plusMonths(1);
            case QUARTERLY -> order.getNextExecutionDate().plusMonths(3);
            case ANNUALLY  -> order.getNextExecutionDate().plusYears(1);
        };
    }
}
