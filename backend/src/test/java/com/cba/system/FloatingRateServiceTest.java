package com.cba.system;

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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("FloatingRateService — unit tests")
class FloatingRateServiceTest {

    @Mock FloatingRateRepository floatingRateRepository;
    @Mock AuditLogService auditLogService;

    @InjectMocks FloatingRateService service;

    private UUID rateId;
    private FloatingRate rate;

    @BeforeEach
    void setUp() {
        rateId = UUID.randomUUID();
        rate = new FloatingRate();
        rate.setId(rateId);
        rate.setName("PRIME");
        rate.setBaseLendingRate(true);
        rate.setActive(true);
        rate.setRatePeriods(new ArrayList<>());
    }

    @Nested
    @DisplayName("List and Get")
    class ListAndGet {

        @Test
        @DisplayName("listRates with activeOnly=true calls findByActiveTrue")
        void listRates_activeOnly() {
            when(floatingRateRepository.findByActiveTrue(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(rate)));

            assertThat(service.listRates(true, Pageable.unpaged()).getContent()).hasSize(1);
        }

        @Test
        @DisplayName("listRates with activeOnly=false calls findAll")
        void listRates_all() {
            when(floatingRateRepository.findAll(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(rate)));

            assertThat(service.listRates(false, Pageable.unpaged()).getContent()).hasSize(1);
        }

        @Test
        @DisplayName("getRate returns rate when found")
        void getRate_found() {
            when(floatingRateRepository.findById(rateId)).thenReturn(Optional.of(rate));
            assertThat(service.getRate(rateId).getName()).isEqualTo("PRIME");
        }

        @Test
        @DisplayName("getRate throws when not found")
        void getRate_notFound_throws() {
            when(floatingRateRepository.findById(rateId)).thenReturn(Optional.empty());
            assertThatThrownBy(() -> service.getRate(rateId))
                .isInstanceOf(CbaException.class);
        }
    }

    @Nested
    @DisplayName("Create")
    class Create {

        @Test
        @DisplayName("createRate saves rate with periods")
        void createRate_success() {
            when(floatingRateRepository.existsByName("PRIME")).thenReturn(false);
            when(floatingRateRepository.save(any())).thenReturn(rate);

            FloatingRateService.PeriodRequest period = new FloatingRateService.PeriodRequest(
                LocalDate.now(), new BigDecimal("5.50"), false
            );
            FloatingRateService.CreateFloatingRateRequest req =
                new FloatingRateService.CreateFloatingRateRequest("PRIME", true, true, List.of(period));

            FloatingRate result = service.createRate(req);
            assertThat(result.getName()).isEqualTo("PRIME");
            verify(floatingRateRepository).save(any(FloatingRate.class));
        }

        @Test
        @DisplayName("createRate throws when name already exists")
        void createRate_duplicateName_throws() {
            when(floatingRateRepository.existsByName("PRIME")).thenReturn(true);

            FloatingRateService.CreateFloatingRateRequest req =
                new FloatingRateService.CreateFloatingRateRequest("PRIME", false, true, null);

            assertThatThrownBy(() -> service.createRate(req))
                .isInstanceOf(CbaException.class)
                .hasMessageContaining("already exists");
        }
    }

    @Nested
    @DisplayName("Update and Delete")
    class UpdateAndDelete {

        @Test
        @DisplayName("updateRate saves changes")
        void updateRate_success() {
            when(floatingRateRepository.findById(rateId)).thenReturn(Optional.of(rate));
            when(floatingRateRepository.save(any())).thenReturn(rate);

            FloatingRateService.CreateFloatingRateRequest req =
                new FloatingRateService.CreateFloatingRateRequest("PRIME", true, true, List.of());

            FloatingRate result = service.updateRate(rateId, req);
            assertThat(result.getName()).isEqualTo("PRIME");
        }

        @Test
        @DisplayName("deleteRate removes the rate")
        void deleteRate_success() {
            when(floatingRateRepository.findById(rateId)).thenReturn(Optional.of(rate));

            assertThatCode(() -> service.deleteRate(rateId)).doesNotThrowAnyException();
            verify(floatingRateRepository).delete(rate);
        }
    }
}
