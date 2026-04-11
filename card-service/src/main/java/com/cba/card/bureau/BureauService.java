package com.cba.card.bureau;

import com.cba.card.card.Card;
import com.cba.card.card.CardRepository;
import com.cba.card.card.CardStatus;
import com.cba.card.card.PhysicalCardOrder;
import com.cba.card.card.PhysicalCardOrderRepository;
import com.cba.card.common.CbaException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Card personalization bureau service.
 *
 * <p>Manages the full lifecycle of a bureau job:
 * <ol>
 *   <li>{@link #createJob()} — collect all {@code ORDERED} physical card orders into a new batch</li>
 *   <li>{@link #submitJob(UUID)} — generate CDP data for each card, mark batch {@code SENT},
 *       update {@code PhysicalCardOrder.productionRequestDate}</li>
 *   <li>{@link #confirmJob(UUID, BureauConfirmRequest)} — bureau callback; mark items {@code PERSONALIZED},
 *       update card statuses to {@code PRODUCED}, close the job as {@code CONFIRMED}</li>
 *   <li>{@link #dispatchJob(UUID)} — bureau has physically dispatched cards; move cards to {@code DISPATCHED}</li>
 * </ol>
 *
 * <p><strong>Security note:</strong> CDP records contain the Jasypt-encrypted PAN.
 * This service never logs any PAN-derived field. The CDP hash (SHA-256) is the only
 * artefact stored in the database from the CDP generation step.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BureauService {

    private final BureauJobRepository       jobRepository;
    private final BureauJobItemRepository   itemRepository;
    private final CardRepository            cardRepository;
    private final PhysicalCardOrderRepository physicalOrderRepository;
    private final CdpGenerator              cdpGenerator;

    @Value("${card.bureau.name:CBA_BUREAU}")
    private String bureauName;

    private static final AtomicInteger SEQ = new AtomicInteger(1);

    // ── Job creation ──────────────────────────────────────────────────────────

    /**
     * Create a new bureau job by collecting all physical card orders in {@code ORDERED} status.
     *
     * <p>Each card order becomes one {@link BureauJobItem} in the batch.
     * If there are no pending orders an exception is thrown — don't create empty batches.
     *
     * @return the newly created {@link BureauJob} in {@code PENDING} status
     */
    @Transactional
    public BureauJob createJob() {
        List<PhysicalCardOrder> pending = physicalOrderRepository.findByStatus("ORDERED");
        if (pending.isEmpty()) {
            throw CbaException.badRequest("NO_PENDING_ORDERS",
                    "No physical card orders in ORDERED status — nothing to batch");
        }

        BureauJob job = new BureauJob();
        job.setBatchRef(generateBatchRef());
        job.setBureauName(bureauName);
        job.setCardCount(pending.size());
        job = jobRepository.save(job);

        for (PhysicalCardOrder order : pending) {
            Card card = order.getCard();
            BureauJobItem item = new BureauJobItem();
            item.setJob(job);
            item.setCard(card);
            item.setPhysicalOrder(order);
            item.setSchemeAid(cdpGenerator.generate(card).schemeAid());
            // Hash will be populated at submit time when CDP is fully generated
            item.setPersonalizationDataHash("pending");
            itemRepository.save(item);
        }

        log.info("Bureau job created: batchRef={} cards={}", job.getBatchRef(), pending.size());
        return job;
    }

    // ── Job submission ────────────────────────────────────────────────────────

    /**
     * Submit a PENDING bureau job to the bureau.
     *
     * <p>For each card item: generates the CDP record, stores the integrity hash,
     * and marks the physical order with today's {@code productionRequestDate}.
     * The job status moves to {@code SENT}.
     *
     * <p>In production, the CDP bytes would be encrypted with the bureau's public key
     * and transmitted via SFTP. Here we generate and hash the CDP data and log the
     * batch reference — the actual file transmission is pluggable via
     * {@code BureauFileTransport} (not implemented in dev stub).
     *
     * @param jobId the bureau job to submit
     * @return the updated job in {@code SENT} status
     */
    @Transactional
    public BureauJob submitJob(UUID jobId) {
        BureauJob job = findById(jobId);
        if (job.getStatus() != BureauJobStatus.PENDING) {
            throw CbaException.badRequest("INVALID_JOB_STATE",
                    "Only PENDING jobs can be submitted. Current status: " + job.getStatus());
        }

        List<BureauJobItem> items = itemRepository.findByJobId(jobId);
        for (BureauJobItem item : items) {
            Card card = item.getCard();
            try {
                CdpRecord cdp = cdpGenerator.generate(card);
                item.setPersonalizationDataHash(cdp.hash());
                item.setSchemeAid(cdp.schemeAid());

                // Update physical order production date
                PhysicalCardOrder order = item.getPhysicalOrder();
                order.setProductionRequestDate(LocalDate.now());
                physicalOrderRepository.save(order);
                itemRepository.save(item);

            } catch (Exception e) {
                log.error("CDP generation failed for card={}: {}", card.getId(), e.getMessage());
                item.setStatus(BureauJobItemStatus.FAILED);
                item.setFailureReason("CDP generation error: " + e.getMessage());
                itemRepository.save(item);
            }
        }

        job.setStatus(BureauJobStatus.SENT);
        job.setSubmittedAt(OffsetDateTime.now());
        job = jobRepository.save(job);

        log.info("Bureau job submitted: batchRef={} items={}", job.getBatchRef(), items.size());
        return job;
    }

    // ── Bureau confirmation callback ──────────────────────────────────────────

    /**
     * Process a confirmation callback from the bureau indicating that cards have been
     * personalised and are ready for physical dispatch.
     *
     * <p>For each confirmed item:
     * <ul>
     *   <li>Store the chip serial number assigned by the bureau</li>
     *   <li>Mark the item {@code PERSONALIZED}</li>
     *   <li>Update the card status to {@code PRODUCED}</li>
     *   <li>Update the physical order status to {@code PRODUCED}</li>
     * </ul>
     *
     * <p>Items not listed in the confirmation payload are left in {@code PENDING} status
     * (the bureau may confirm in partial batches). If all items are confirmed or failed
     * the job status moves to {@code CONFIRMED}.
     *
     * @param jobId   the bureau job being confirmed
     * @param request confirmation payload from the bureau
     * @return the updated job
     */
    @Transactional
    public BureauJob confirmJob(UUID jobId, BureauConfirmRequest request) {
        BureauJob job = findById(jobId);
        if (job.getStatus() != BureauJobStatus.SENT) {
            throw CbaException.badRequest("INVALID_JOB_STATE",
                    "Only SENT jobs can be confirmed. Current status: " + job.getStatus());
        }

        List<BureauJobItem> items = itemRepository.findByJobId(jobId);
        for (BureauJobItem item : items) {
            BureauConfirmRequest.ItemConfirmation conf = request.findByCardId(item.getCard().getId());
            if (conf == null) continue; // not in this partial confirmation

            if (conf.success()) {
                item.setStatus(BureauJobItemStatus.PERSONALIZED);
                item.setChipSerialNo(conf.chipSerialNo());

                // Advance card and physical order to PRODUCED
                Card card = item.getCard();
                card.setStatus(CardStatus.PRODUCED);
                cardRepository.save(card);

                PhysicalCardOrder order = item.getPhysicalOrder();
                order.setStatus("PRODUCED");
                order.setCardBureauRef(conf.bureauRef());
                physicalOrderRepository.save(order);

            } else {
                item.setStatus(BureauJobItemStatus.FAILED);
                item.setFailureReason(conf.failureReason());
                log.warn("Bureau personalization failed: card={} reason={}",
                        item.getCard().getId(), conf.failureReason());
            }
            itemRepository.save(item);
        }

        // Close job if no items remain pending
        long pendingCount = items.stream()
                .filter(i -> i.getStatus() == BureauJobItemStatus.PENDING)
                .count();
        if (pendingCount == 0) {
            job.setStatus(BureauJobStatus.CONFIRMED);
            job.setConfirmedAt(OffsetDateTime.now());
        }

        job = jobRepository.save(job);
        log.info("Bureau job confirmed: batchRef={} pendingRemaining={}", job.getBatchRef(), pendingCount);
        return job;
    }

    // ── Dispatch ──────────────────────────────────────────────────────────────

    /**
     * Mark all PRODUCED cards in a job as DISPATCHED (bureau has shipped the cards).
     * Updates the physical order {@code dispatchDate} and moves card status to {@code DISPATCHED}.
     *
     * @param jobId the bureau job whose cards are being dispatched
     * @return the updated job
     */
    @Transactional
    public BureauJob dispatchJob(UUID jobId) {
        BureauJob job = findById(jobId);
        if (job.getStatus() != BureauJobStatus.CONFIRMED) {
            throw CbaException.badRequest("INVALID_JOB_STATE",
                    "Only CONFIRMED jobs can be dispatched. Current status: " + job.getStatus());
        }

        List<BureauJobItem> items = itemRepository.findByJobIdAndStatus(
                jobId, BureauJobItemStatus.PERSONALIZED);

        for (BureauJobItem item : items) {
            Card card = item.getCard();
            card.setStatus(CardStatus.DISPATCHED);
            cardRepository.save(card);

            PhysicalCardOrder order = item.getPhysicalOrder();
            order.setStatus("DISPATCHED");
            order.setDispatchDate(LocalDate.now());
            physicalOrderRepository.save(order);
        }

        log.info("Bureau job dispatched: batchRef={} cards={}", job.getBatchRef(), items.size());
        return job;
    }

    // ── Failure handling ──────────────────────────────────────────────────────

    /**
     * Mark a job as FAILED (e.g. bureau unreachable, file rejected).
     *
     * @param jobId  the job to fail
     * @param reason human-readable reason stored in the {@code notes} column
     */
    @Transactional
    public BureauJob failJob(UUID jobId, String reason) {
        BureauJob job = findById(jobId);
        if (job.getStatus() == BureauJobStatus.CONFIRMED) {
            throw CbaException.badRequest("INVALID_JOB_STATE", "Cannot fail a CONFIRMED job");
        }
        job.setStatus(BureauJobStatus.FAILED);
        job.setNotes(reason);
        return jobRepository.save(job);
    }

    // ── Queries ───────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public BureauJob findById(UUID id) {
        return jobRepository.findById(id)
                .orElseThrow(() -> CbaException.notFound("BUREAU_JOB_NOT_FOUND",
                        "Bureau job not found: " + id));
    }

    @Transactional(readOnly = true)
    public List<BureauJob> listAll() {
        return jobRepository.findAllByOrderByCreatedAtDesc();
    }

    @Transactional(readOnly = true)
    public List<BureauJobItem> listItems(UUID jobId) {
        findById(jobId); // validate job exists
        return itemRepository.findByJobId(jobId);
    }

    @Transactional(readOnly = true)
    public CdpRecord generateCdpPreview(UUID jobId, UUID cardId) {
        findById(jobId); // validate job exists
        Card card = cardRepository.findById(cardId)
                .orElseThrow(() -> CbaException.notFound("CARD_NOT_FOUND", "Card not found: " + cardId));
        return cdpGenerator.generate(card);
    }

    // ── Internal helpers ──────────────────────────────────────────────────────

    private String generateBatchRef() {
        String date = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
        return "CBA-" + date + "-" + String.format("%06d", SEQ.getAndIncrement());
    }
}
