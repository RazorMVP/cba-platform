package com.cba.social;

import com.cba.audit.AuditLogService;
import com.cba.common.exception.CbaException;
import com.cba.notification.sms.SmsProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SmsCampaignService {

    public record CreateCampaignRequest(
        String campaignName,
        SmsCampaign.CampaignType campaignType,
        SmsCampaign.TriggerType triggerType,
        String message,
        String recurrence,
        LocalDate runDate
    ) {}

    /** One target of a send request. {@code mobileNo} is required; {@code customerId} is optional metadata. */
    public record Recipient(UUID customerId, String mobileNo) {}

    /** Body of {@code POST /smscampaigns/{id}/send}. */
    public record SendCampaignRequest(List<Recipient> recipients) {}

    /** Aggregate outcome of a send batch. */
    public record SendResult(int total, int sent, int failed, int invalid, String provider) {}

    private final SmsCampaignRepository campaignRepository;
    private final SmsMessageRepository  messageRepository;
    private final AuditLogService        auditLogService;
    private final SmsDispatchService     dispatchService;

    @Transactional(readOnly = true)
    public Page<SmsCampaign> listCampaigns(Pageable p) {
        return campaignRepository.findAll(p);
    }

    @Transactional(readOnly = true)
    public SmsCampaign getCampaign(UUID id) {
        return campaignRepository.findById(id)
            .orElseThrow(() -> CbaException.notFound("SmsCampaign", id));
    }

    @Transactional
    public SmsCampaign createCampaign(CreateCampaignRequest req) {
        SmsCampaign c = new SmsCampaign();
        c.setCampaignName(req.campaignName());
        c.setCampaignType(req.campaignType());
        c.setTriggerType(req.triggerType() != null ? req.triggerType() : SmsCampaign.TriggerType.SCHEDULED);
        c.setMessage(req.message());
        c.setRecurrence(req.recurrence());
        c.setRunDate(req.runDate());
        SmsCampaign saved = campaignRepository.save(c);
        auditLogService.log("SmsCampaign", saved.getId().toString(), "CREATE", null, saved);
        return saved;
    }

    @Transactional
    public SmsCampaign updateCampaign(UUID id, CreateCampaignRequest req) {
        SmsCampaign c = getCampaign(id);
        c.setCampaignName(req.campaignName());
        c.setCampaignType(req.campaignType());
        c.setTriggerType(req.triggerType());
        c.setMessage(req.message());
        c.setRecurrence(req.recurrence());
        c.setRunDate(req.runDate());
        SmsCampaign saved = campaignRepository.save(c);
        auditLogService.log("SmsCampaign", id.toString(), "UPDATE", null, saved);
        return saved;
    }

    @Transactional
    public SmsCampaign activate(UUID id) {
        SmsCampaign c = getCampaign(id);
        if (c.getStatus() != SmsCampaign.Status.PENDING &&
            c.getStatus() != SmsCampaign.Status.WAITING_FOR_ACTIVATION) {
            throw CbaException.badRequest("INVALID_STATE",
                "Campaign must be PENDING or WAITING_FOR_ACTIVATION to activate");
        }
        c.setStatus(SmsCampaign.Status.ACTIVE);
        SmsCampaign saved = campaignRepository.save(c);
        auditLogService.log("SmsCampaign", id.toString(), "ACTIVATE", null, saved);
        return saved;
    }

    @Transactional
    public void deleteCampaign(UUID id) {
        SmsCampaign c = getCampaign(id);
        c.setStatus(SmsCampaign.Status.DELETED);
        campaignRepository.save(c);
        auditLogService.log("SmsCampaign", id.toString(), "DELETE", null, null);
    }

    @Transactional(readOnly = true)
    public Page<SmsMessage> listMessages(UUID campaignId, Pageable p) {
        getCampaign(campaignId); // validate exists
        return messageRepository.findByCampaignId(campaignId, p);
    }

    /**
     * Dispatch a campaign's message to an explicit recipient list through the active
     * {@link SmsProvider}. Each recipient becomes one {@link SmsMessage} delivery row;
     * the batch outcome is audited and the campaign's {@code lastTriggerDate} advanced.
     *
     * <p>Recipient resolution (broadcast "ALL", saved-query "QUERY") is intentionally
     * out of scope here — the caller supplies the resolved MSISDNs, keeping this method
     * free of encrypted-PII coupling. A DELETED campaign cannot be sent.
     */
    @Transactional
    public SendResult sendCampaign(UUID id, SendCampaignRequest req) {
        SmsCampaign c = getCampaign(id);
        if (c.getStatus() == SmsCampaign.Status.DELETED) {
            throw CbaException.badRequest("INVALID_STATE", "A deleted campaign cannot be sent");
        }
        if (req == null || req.recipients() == null || req.recipients().isEmpty()) {
            throw CbaException.badRequest("NO_RECIPIENTS", "At least one recipient is required");
        }

        int sent = 0, failed = 0, invalid = 0;
        for (Recipient r : req.recipients()) {
            SmsMessage m = dispatchService.dispatch(c, r.customerId(), r.mobileNo(), c.getMessage());
            switch (m.getDeliveryStatus()) {
                case SENT -> sent++;
                case INVALID -> invalid++;
                default -> failed++;
            }
        }

        c.setLastTriggerDate(OffsetDateTime.now());
        campaignRepository.save(c);

        SendResult result = new SendResult(req.recipients().size(), sent, failed, invalid,
                dispatchService.activeProvider());
        auditLogService.log("SmsCampaign", id.toString(), "SEND", null, result);
        return result;
    }
}
