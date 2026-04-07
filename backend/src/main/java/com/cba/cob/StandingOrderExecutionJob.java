package com.cba.cob;

import com.cba.payment.PaymentService;
import com.cba.payment.StandingOrder;
import com.cba.payment.StandingOrderRepository;
import com.cba.payment.dto.TransferRequest;
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

import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;

/**
 * Executes all standing orders due today and advances their next execution date.
 */
@Configuration
@RequiredArgsConstructor
@Slf4j
public class StandingOrderExecutionJob {

    private final StandingOrderRepository standingOrderRepository;
    private final PaymentService paymentService;

    @Bean("standingOrderExecutionJob")
    public Job standingOrderExecutionJob(JobRepository jobRepository,
                                          Step standingOrderStep) {
        return new JobBuilder("standingOrderExecutionJob", jobRepository)
                .start(standingOrderStep)
                .build();
    }

    @Bean
    public Step standingOrderStep(JobRepository jobRepository,
                                   PlatformTransactionManager transactionManager) {
        return new StepBuilder("standingOrderStep", jobRepository)
                .<StandingOrder, StandingOrder>chunk(20, transactionManager)
                .reader(dueOrdersReader())
                .processor(orderProcessor())
                .writer(orderWriter())
                .build();
    }

    @Bean
    public RepositoryItemReader<StandingOrder> dueOrdersReader() {
        return new RepositoryItemReaderBuilder<StandingOrder>()
                .name("dueOrdersReader")
                .repository(standingOrderRepository)
                .methodName("findDueOrders")
                .arguments(LocalDate.now())
                .sorts(Map.of("id", Sort.Direction.ASC))
                .pageSize(20)
                .build();
    }

    @Bean
    public ItemProcessor<StandingOrder, StandingOrder> orderProcessor() {
        return order -> {
            try {
                TransferRequest transferReq = new TransferRequest(
                        order.getSourceAccount().getId(),
                        order.getDestinationAccount().getId(),
                        order.getAmount(),
                        "Standing order: " + order.getDescription()
                );
                paymentService.transfer(transferReq, "system");

                order.setLastExecutedAt(Instant.now());
                order.setNextExecutionDate(computeNext(order));

                // Auto-complete if end date reached
                if (order.getEndDate() != null && order.getNextExecutionDate().isAfter(order.getEndDate())) {
                    order.setStatus(StandingOrder.Status.COMPLETED);
                }
                return order;
            } catch (Exception e) {
                log.error("Standing order {} execution failed: {}", order.getId(), e.getMessage());
                return null; // skip failed orders — they remain due tomorrow
            }
        };
    }

    @Bean
    public ItemWriter<StandingOrder> orderWriter() {
        return orders -> {
            standingOrderRepository.saveAll(orders.getItems());
            log.info("Standing orders executed: {}", orders.size());
        };
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
