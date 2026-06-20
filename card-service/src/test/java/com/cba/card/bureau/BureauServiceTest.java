package com.cba.card.bureau;

import com.cba.card.card.CardRepository;
import com.cba.card.card.PhysicalCardOrderRepository;
import com.cba.card.common.CbaException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link BureauService} state-machine guards — each lifecycle command
 * accepts only the correct source status. (CDP-generating happy paths need the
 * full CdpRecord and are covered at integration level.)
 */
@ExtendWith(MockitoExtension.class)
class BureauServiceTest {

    @Mock BureauJobRepository jobRepository;
    @Mock BureauJobItemRepository itemRepository;
    @Mock CardRepository cardRepository;
    @Mock PhysicalCardOrderRepository physicalOrderRepository;
    @Mock CdpGenerator cdpGenerator;

    @InjectMocks BureauService service;

    private static BureauJob job(BureauJobStatus status) {
        BureauJob j = new BureauJob();
        j.setStatus(status);
        return j;
    }

    @Test
    @DisplayName("createJob refuses to create an empty batch when no orders are ORDERED")
    void createJobNoPendingOrders() {
        when(physicalOrderRepository.findByStatus("ORDERED")).thenReturn(List.of());
        assertThatThrownBy(() -> service.createJob()).isInstanceOf(CbaException.class);
    }

    @Test
    @DisplayName("submitJob accepts only PENDING jobs")
    void submitJobRequiresPending() {
        UUID id = UUID.randomUUID();
        when(jobRepository.findById(id)).thenReturn(Optional.of(job(BureauJobStatus.SENT)));
        assertThatThrownBy(() -> service.submitJob(id)).isInstanceOf(CbaException.class);
    }

    @Test
    @DisplayName("confirmJob accepts only SENT jobs")
    void confirmJobRequiresSent() {
        UUID id = UUID.randomUUID();
        when(jobRepository.findById(id)).thenReturn(Optional.of(job(BureauJobStatus.PENDING)));
        assertThatThrownBy(() -> service.confirmJob(id, null)).isInstanceOf(CbaException.class);
    }

    @Test
    @DisplayName("dispatchJob accepts only CONFIRMED jobs")
    void dispatchJobRequiresConfirmed() {
        UUID id = UUID.randomUUID();
        when(jobRepository.findById(id)).thenReturn(Optional.of(job(BureauJobStatus.PENDING)));
        assertThatThrownBy(() -> service.dispatchJob(id)).isInstanceOf(CbaException.class);
    }

    @Test
    @DisplayName("failJob cannot fail a CONFIRMED job")
    void failJobNotConfirmed() {
        UUID id = UUID.randomUUID();
        when(jobRepository.findById(id)).thenReturn(Optional.of(job(BureauJobStatus.CONFIRMED)));
        assertThatThrownBy(() -> service.failJob(id, "reason")).isInstanceOf(CbaException.class);
    }

    @Test
    @DisplayName("findById throws when the job does not exist")
    void findByIdNotFound() {
        UUID id = UUID.randomUUID();
        when(jobRepository.findById(id)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.findById(id)).isInstanceOf(CbaException.class);
    }
}
