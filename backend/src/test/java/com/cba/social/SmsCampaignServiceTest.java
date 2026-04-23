package com.cba.social;

import com.cba.audit.AuditLogService;
import com.cba.common.exception.CbaException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("SmsCampaignService — unit tests")
class SmsCampaignServiceTest {

    @Mock SmsCampaignRepository campaignRepository;
    @Mock SmsMessageRepository messageRepository;
    @Mock AuditLogService auditLogService;

    @InjectMocks SmsCampaignService service;

    private UUID campaignId;
    private SmsCampaign campaign;

    @BeforeEach
    void setUp() {
        campaignId = UUID.randomUUID();
        campaign = new SmsCampaign();
        campaign.setId(campaignId);
        campaign.setCampaignName("Test Campaign");
        campaign.setCampaignType(SmsCampaign.CampaignType.ALL);
        campaign.setTriggerType(SmsCampaign.TriggerType.SCHEDULED);
        campaign.setStatus(SmsCampaign.Status.PENDING);
    }

    @Nested
    @DisplayName("List and Get")
    class ListAndGet {

        @Test
        @DisplayName("listCampaigns returns page")
        void listCampaigns_returnsPage() {
            when(campaignRepository.findAll(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(campaign)));

            assertThat(service.listCampaigns(Pageable.unpaged()).getContent()).hasSize(1);
        }

        @Test
        @DisplayName("getCampaign returns campaign when found")
        void getCampaign_found() {
            when(campaignRepository.findById(campaignId)).thenReturn(Optional.of(campaign));
            assertThat(service.getCampaign(campaignId).getCampaignName()).isEqualTo("Test Campaign");
        }

        @Test
        @DisplayName("getCampaign throws when not found")
        void getCampaign_notFound_throws() {
            when(campaignRepository.findById(campaignId)).thenReturn(Optional.empty());
            assertThatThrownBy(() -> service.getCampaign(campaignId))
                .isInstanceOf(CbaException.class);
        }
    }

    @Nested
    @DisplayName("Create")
    class Create {

        @Test
        @DisplayName("createCampaign saves campaign")
        void createCampaign_success() {
            when(campaignRepository.save(any())).thenReturn(campaign);

            SmsCampaignService.CreateCampaignRequest req = new SmsCampaignService.CreateCampaignRequest(
                "Test Campaign", SmsCampaign.CampaignType.ALL,
                SmsCampaign.TriggerType.DIRECT, "Hello {name}", "FREQ=WEEKLY", LocalDate.now()
            );
            SmsCampaign result = service.createCampaign(req);
            assertThat(result.getCampaignName()).isEqualTo("Test Campaign");
            verify(campaignRepository).save(any(SmsCampaign.class));
        }

        @Test
        @DisplayName("createCampaign defaults triggerType to SCHEDULED when null")
        void createCampaign_nullTriggerType_defaultsToScheduled() {
            when(campaignRepository.save(any())).thenAnswer(inv -> {
                SmsCampaign c = inv.getArgument(0);
                c.setId(UUID.randomUUID());
                return c;
            });

            SmsCampaignService.CreateCampaignRequest req = new SmsCampaignService.CreateCampaignRequest(
                "Null Trigger", SmsCampaign.CampaignType.INDIVIDUAL,
                null, "Hello", null, null
            );
            SmsCampaign result = service.createCampaign(req);
            assertThat(result.getTriggerType()).isEqualTo(SmsCampaign.TriggerType.SCHEDULED);
        }
    }

    @Nested
    @DisplayName("Update")
    class Update {

        @Test
        @DisplayName("updateCampaign saves changes")
        void updateCampaign_success() {
            when(campaignRepository.findById(campaignId)).thenReturn(Optional.of(campaign));
            when(campaignRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            SmsCampaignService.CreateCampaignRequest req = new SmsCampaignService.CreateCampaignRequest(
                "Updated", SmsCampaign.CampaignType.QUERY,
                SmsCampaign.TriggerType.TRIGGERED, "Updated msg", "FREQ=DAILY", LocalDate.now()
            );
            SmsCampaign result = service.updateCampaign(campaignId, req);
            assertThat(result.getCampaignName()).isEqualTo("Updated");
        }
    }

    @Nested
    @DisplayName("Activate")
    class Activate {

        @Test
        @DisplayName("activate sets ACTIVE status from PENDING")
        void activate_fromPending_success() {
            when(campaignRepository.findById(campaignId)).thenReturn(Optional.of(campaign));
            when(campaignRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            SmsCampaign result = service.activate(campaignId);
            assertThat(result.getStatus()).isEqualTo(SmsCampaign.Status.ACTIVE);
        }

        @Test
        @DisplayName("activate sets ACTIVE from WAITING_FOR_ACTIVATION")
        void activate_fromWaiting_success() {
            campaign.setStatus(SmsCampaign.Status.WAITING_FOR_ACTIVATION);
            when(campaignRepository.findById(campaignId)).thenReturn(Optional.of(campaign));
            when(campaignRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            SmsCampaign result = service.activate(campaignId);
            assertThat(result.getStatus()).isEqualTo(SmsCampaign.Status.ACTIVE);
        }

        @Test
        @DisplayName("activate throws when already ACTIVE")
        void activate_alreadyActive_throws() {
            campaign.setStatus(SmsCampaign.Status.ACTIVE);
            when(campaignRepository.findById(campaignId)).thenReturn(Optional.of(campaign));

            assertThatThrownBy(() -> service.activate(campaignId))
                .isInstanceOf(CbaException.class)
                .hasMessageContaining("PENDING or WAITING_FOR_ACTIVATION");
        }
    }

    @Nested
    @DisplayName("Delete")
    class Delete {

        @Test
        @DisplayName("deleteCampaign soft-deletes by setting status to DELETED")
        void deleteCampaign_softDelete() {
            when(campaignRepository.findById(campaignId)).thenReturn(Optional.of(campaign));

            assertThatCode(() -> service.deleteCampaign(campaignId)).doesNotThrowAnyException();
            verify(campaignRepository).save(argThat(c -> c.getStatus() == SmsCampaign.Status.DELETED));
        }
    }

    @Nested
    @DisplayName("Messages")
    class Messages {

        @Test
        @DisplayName("listMessages returns messages for existing campaign")
        void listMessages_success() {
            when(campaignRepository.findById(campaignId)).thenReturn(Optional.of(campaign));
            when(messageRepository.findByCampaignId(eq(campaignId), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

            assertThat(service.listMessages(campaignId, Pageable.unpaged()).getContent()).isEmpty();
        }

        @Test
        @DisplayName("listMessages throws when campaign not found")
        void listMessages_campaignNotFound_throws() {
            when(campaignRepository.findById(campaignId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.listMessages(campaignId, Pageable.unpaged()))
                .isInstanceOf(CbaException.class);
        }
    }
}
