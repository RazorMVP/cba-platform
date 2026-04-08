package com.cba.social;

import com.cba.audit.AuditLogService;
import com.cba.common.exception.CbaException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
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

    private final SmsCampaignRepository campaignRepository;
    private final SmsMessageRepository  messageRepository;
    private final AuditLogService        auditLogService;

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
}
