package com.cba.social;

import com.cba.notification.sms.SmsProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("SmsDispatchService — provider verdict → delivery status")
class SmsDispatchServiceTest {

    @Mock SmsProvider smsProvider;
    @Mock SmsMessageRepository messageRepository;

    @InjectMocks SmsDispatchService service;

    private SmsCampaign campaign;

    @BeforeEach
    void setUp() {
        campaign = new SmsCampaign();
        // lenient: the activeProvider() test never persists a message
        lenient().when(messageRepository.save(any(SmsMessage.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    @DisplayName("provider accepted → SENT with deliveredOn set")
    void dispatch_accepted_sent() {
        when(smsProvider.send(anyString(), anyString()))
            .thenReturn(SmsProvider.SmsResult.accepted("id-1"));

        SmsMessage m = service.dispatch(campaign, null, "+254700000000", "hi");

        assertThat(m.getDeliveryStatus()).isEqualTo(SmsMessage.DeliveryStatus.SENT);
        assertThat(m.getDeliveredOn()).isNotNull();
    }

    @Test
    @DisplayName("provider rejected → FAILED, no deliveredOn")
    void dispatch_rejected_failed() {
        when(smsProvider.send(anyString(), anyString()))
            .thenReturn(SmsProvider.SmsResult.rejected("TRANSPORT_ERROR", "boom"));

        SmsMessage m = service.dispatch(campaign, null, "+254700000000", "hi");

        assertThat(m.getDeliveryStatus()).isEqualTo(SmsMessage.DeliveryStatus.FAILED);
        assertThat(m.getDeliveredOn()).isNull();
    }

    @Test
    @DisplayName("blank number → INVALID, provider never called")
    void dispatch_blankNumber_invalid() {
        SmsMessage m = service.dispatch(campaign, null, "  ", "hi");

        assertThat(m.getDeliveryStatus()).isEqualTo(SmsMessage.DeliveryStatus.INVALID);
        verify(smsProvider, never()).send(anyString(), anyString());
    }

    @Test
    @DisplayName("activeProvider surfaces the provider id")
    void activeProvider() {
        when(smsProvider.providerId()).thenReturn("NONE");
        assertThat(service.activeProvider()).isEqualTo("NONE");
    }
}
