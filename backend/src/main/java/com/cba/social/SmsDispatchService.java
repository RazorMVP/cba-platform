package com.cba.social;

import com.cba.notification.sms.SmsProvider;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Persistence + dispatch seam between {@link SmsCampaign}s and the pluggable
 * {@link SmsProvider}. Creates one {@link SmsMessage} delivery row per recipient and
 * maps the provider's verdict onto {@link SmsMessage.DeliveryStatus}:
 *
 * <ul>
 *   <li>blank/absent number → {@code INVALID} (never sent — saves a gateway round trip)</li>
 *   <li>provider accepted   → {@code SENT} + {@code deliveredOn}</li>
 *   <li>provider rejected / threw → {@code FAILED}</li>
 * </ul>
 *
 * <p>Each message is dispatched in its own transaction so one bad number cannot roll
 * back the rest of a campaign batch.
 */
@Service
@RequiredArgsConstructor
public class SmsDispatchService {

    private static final Logger log = LoggerFactory.getLogger(SmsDispatchService.class);

    private final SmsProvider smsProvider;
    private final SmsMessageRepository messageRepository;

    @Transactional
    public SmsMessage dispatch(SmsCampaign campaign, UUID customerId, String mobileNo, String message) {
        SmsMessage m = new SmsMessage();
        m.setCampaign(campaign);
        m.setCustomerId(customerId);
        m.setMobileNo(mobileNo);
        m.setMessage(message);

        if (mobileNo == null || mobileNo.isBlank()) {
            m.setDeliveryStatus(SmsMessage.DeliveryStatus.INVALID);
            return messageRepository.save(m);
        }

        SmsProvider.SmsResult result = smsProvider.send(mobileNo, message);
        if (result.accepted()) {
            m.setDeliveryStatus(SmsMessage.DeliveryStatus.SENT);
            m.setDeliveredOn(OffsetDateTime.now());
        } else {
            m.setDeliveryStatus(SmsMessage.DeliveryStatus.FAILED);
            log.warn("[SMS] message rejected (campaign={}, code={}): {}",
                    campaign != null ? campaign.getId() : null, result.errorCode(), result.errorMessage());
        }
        return messageRepository.save(m);
    }

    /** Active provider id — surfaced in the send response so ops can see NONE vs HTTP. */
    public String activeProvider() {
        return smsProvider.providerId();
    }
}
