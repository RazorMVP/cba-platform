package com.cba.treasury;

import com.cba.common.exception.CbaException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("LiquidityService — unit tests")
class LiquidityServiceTest {

    @Mock JdbcTemplate jdbc;
    @Mock LiquidityReserveRequirementRepository reserveRepo;
    @Mock LiquiditySnapshotRepository snapshotRepo;

    @InjectMocks LiquidityService service;

    private UUID reserveId;
    private LiquidityReserveRequirement reserve;

    @BeforeEach
    void setUp() {
        reserveId = UUID.randomUUID();

        reserve = new LiquidityReserveRequirement();
        reserve.setId(reserveId);
        reserve.setCurrencyCode("USD");
        reserve.setMinimumBalance(new BigDecimal("1000000.00"));
        reserve.setAlertThresholdPercent(new BigDecimal("10.00"));
        reserve.setActive(true);
    }

    private void stubActiveCurrencies(String... currencies) {
        when(jdbc.queryForList(contains("FROM accounts"), eq(String.class)))
            .thenReturn(List.of(currencies));
        when(jdbc.queryForList(contains("FROM treasury_placements"), eq(String.class)))
            .thenReturn(List.of());
        when(jdbc.queryForList(contains("FROM treasury_interbank_positions"), eq(String.class)))
            .thenReturn(List.of());
    }

    private void stubPosition(String currency) {
        // cash on hand
        when(jdbc.queryForObject(
            contains("FROM accounts WHERE status = 'ACTIVE'"), eq(BigDecimal.class), eq(currency)))
            .thenReturn(new BigDecimal("5000000.00"));
        // placements deployed
        when(jdbc.queryForObject(
            contains("FROM treasury_placements WHERE status = 'ACTIVE'"), eq(BigDecimal.class), eq(currency)))
            .thenReturn(new BigDecimal("1000000.00"));
        // interbank lending
        when(jdbc.queryForObject(
            contains("direction = 'LENDING'"), eq(BigDecimal.class), eq(currency)))
            .thenReturn(new BigDecimal("500000.00"));
        // interbank borrowing
        when(jdbc.queryForObject(
            contains("direction = 'BORROWING'"), eq(BigDecimal.class), eq(currency)))
            .thenReturn(new BigDecimal("200000.00"));
    }

    @Nested
    @DisplayName("Position Queries")
    class PositionQueries {

        @Test
        @DisplayName("getAllPositions returns positions for each active currency")
        void getAllPositions_returnsList() {
            stubActiveCurrencies("USD");
            stubPosition("USD");
            when(reserveRepo.findByCurrencyCode("USD")).thenReturn(Optional.of(reserve));

            List<LiquidityService.LiquidityPositionDto> result = service.getAllPositions();
            assertThat(result).hasSize(1);
            assertThat(result.get(0).currency()).isEqualTo("USD");
        }

        @Test
        @DisplayName("getPosition returns computed position for currency")
        void getPosition_returnsDto() {
            stubPosition("USD");
            when(reserveRepo.findByCurrencyCode("USD")).thenReturn(Optional.empty());

            LiquidityService.LiquidityPositionDto dto = service.getPosition("USD");
            assertThat(dto.currency()).isEqualTo("USD");
            assertThat(dto.alertLevel()).isEqualTo("OK");
        }

        @Test
        @DisplayName("alertLevel is BREACH when surplus is negative")
        void getPosition_breach() {
            // net = 5M + 0.2M - 0.5M - 1M = 3.7M; requirement = 5M; surplus = -1.3M → BREACH
            when(jdbc.queryForObject(contains("FROM accounts WHERE status = 'ACTIVE'"),
                eq(BigDecimal.class), anyString())).thenReturn(new BigDecimal("2000000.00"));
            when(jdbc.queryForObject(contains("FROM treasury_placements WHERE status = 'ACTIVE'"),
                eq(BigDecimal.class), anyString())).thenReturn(new BigDecimal("1000000.00"));
            when(jdbc.queryForObject(contains("direction = 'LENDING'"),
                eq(BigDecimal.class), anyString())).thenReturn(BigDecimal.ZERO);
            when(jdbc.queryForObject(contains("direction = 'BORROWING'"),
                eq(BigDecimal.class), anyString())).thenReturn(BigDecimal.ZERO);

            LiquidityReserveRequirement bigReserve = new LiquidityReserveRequirement();
            bigReserve.setMinimumBalance(new BigDecimal("5000000.00"));
            bigReserve.setAlertThresholdPercent(new BigDecimal("10.00"));
            when(reserveRepo.findByCurrencyCode("USD")).thenReturn(Optional.of(bigReserve));

            LiquidityService.LiquidityPositionDto dto = service.getPosition("usd");
            assertThat(dto.alertLevel()).isEqualTo("BREACH");
        }

        @Test
        @DisplayName("getAllPositions returns empty when no active currencies")
        void getAllPositions_empty() {
            stubActiveCurrencies();
            assertThat(service.getAllPositions()).isEmpty();
        }
    }

    @Nested
    @DisplayName("Cash Flow Forecast")
    class CashFlowForecast {

        @Test
        @DisplayName("getCashFlowForecast returns sorted entries")
        void getCashFlowForecast_returnsList() {
            java.sql.Date sqlDate = java.sql.Date.valueOf(LocalDate.now().plusDays(5));
            Map<String, Object> row = Map.of(
                "maturity_date", sqlDate,
                "reference", "PL-001",
                "amount", new BigDecimal("100000.00"));

            when(jdbc.queryForList(contains("treasury_placements"),
                eq("USD"), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(List.of(row));
            when(jdbc.queryForList(contains("direction = 'LENDING'"),
                eq("USD"), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(List.of());
            when(jdbc.queryForList(contains("direction = 'BORROWING'"),
                eq("USD"), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(List.of());
            when(jdbc.queryForList(contains("loan_repayment_schedule"),
                any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(List.of());

            List<LiquidityService.CashFlowEntryDto> entries = service.getCashFlowForecast("USD", 30);
            assertThat(entries).hasSize(1);
            assertThat(entries.get(0).type()).isEqualTo("PLACEMENT_MATURITY");
            assertThat(entries.get(0).direction()).isEqualTo("INFLOW");
        }

        @Test
        @DisplayName("getCashFlowForecast with borrowing creates OUTFLOW entry")
        void getCashFlowForecast_borrowing() {
            java.sql.Date sqlDate = java.sql.Date.valueOf(LocalDate.now().plusDays(10));
            Map<String, Object> row = Map.of(
                "maturity_date", sqlDate,
                "reference", "IB-001",
                "amount", new BigDecimal("50000.00"));

            when(jdbc.queryForList(contains("treasury_placements"),
                eq("USD"), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(List.of());
            when(jdbc.queryForList(contains("direction = 'LENDING'"),
                eq("USD"), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(List.of());
            when(jdbc.queryForList(contains("direction = 'BORROWING'"),
                eq("USD"), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(List.of(row));
            when(jdbc.queryForList(contains("loan_repayment_schedule"),
                any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(List.of());

            List<LiquidityService.CashFlowEntryDto> entries = service.getCashFlowForecast("USD", 30);
            assertThat(entries).hasSize(1);
            assertThat(entries.get(0).direction()).isEqualTo("OUTFLOW");
        }
    }

    @Nested
    @DisplayName("Reserve Requirements")
    class ReserveRequirements {

        @Test
        @DisplayName("listReserves returns all")
        void listReserves_returnsAll() {
            when(reserveRepo.findAll()).thenReturn(List.of(reserve));
            assertThat(service.listReserves()).hasSize(1);
        }

        @Test
        @DisplayName("createReserve saves when no existing for currency")
        void createReserve_success() {
            when(reserveRepo.findByCurrencyCode("USD")).thenReturn(Optional.empty());
            when(reserveRepo.save(any())).thenReturn(reserve);

            LiquidityReserveRequest req = new LiquidityReserveRequest(
                "USD", new BigDecimal("1000000.00"), null, new BigDecimal("10.00"), null);
            assertThat(service.createReserve(req)).isNotNull();
        }

        @Test
        @DisplayName("createReserve throws when currency already has reserve")
        void createReserve_duplicate_throws() {
            when(reserveRepo.findByCurrencyCode("USD")).thenReturn(Optional.of(reserve));

            LiquidityReserveRequest req = new LiquidityReserveRequest(
                "USD", new BigDecimal("1000000.00"), null, null, null);
            assertThatThrownBy(() -> service.createReserve(req))
                .isInstanceOf(CbaException.class)
                .hasMessageContaining("already exists");
        }

        @Test
        @DisplayName("updateReserve updates and saves")
        void updateReserve_success() {
            when(reserveRepo.findById(reserveId)).thenReturn(Optional.of(reserve));
            when(reserveRepo.save(any())).thenReturn(reserve);

            LiquidityReserveRequest req = new LiquidityReserveRequest(
                "USD", new BigDecimal("2000000.00"), null, new BigDecimal("15.00"), null);
            assertThat(service.updateReserve(reserveId, req)).isNotNull();
        }

        @Test
        @DisplayName("updateReserve throws when not found")
        void updateReserve_notFound_throws() {
            when(reserveRepo.findById(reserveId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.updateReserve(reserveId,
                new LiquidityReserveRequest("USD", BigDecimal.TEN, null, null, null)))
                .isInstanceOf(CbaException.class);
        }

        @Test
        @DisplayName("deleteReserve soft-deletes by setting active=false")
        void deleteReserve_success() {
            when(reserveRepo.findById(reserveId)).thenReturn(Optional.of(reserve));
            when(reserveRepo.save(any())).thenReturn(reserve);

            assertThatCode(() -> service.deleteReserve(reserveId)).doesNotThrowAnyException();
            verify(reserveRepo).save(argThat(r -> !r.isActive()));
        }

        @Test
        @DisplayName("deleteReserve throws when not found")
        void deleteReserve_notFound_throws() {
            when(reserveRepo.findById(reserveId)).thenReturn(Optional.empty());
            assertThatThrownBy(() -> service.deleteReserve(reserveId))
                .isInstanceOf(CbaException.class);
        }
    }

    @Nested
    @DisplayName("Snapshots")
    class Snapshots {

        @Test
        @DisplayName("getSnapshots returns limited snapshots")
        void getSnapshots_returnsLimited() {
            LiquiditySnapshot s1 = new LiquiditySnapshot();
            LiquiditySnapshot s2 = new LiquiditySnapshot();
            when(snapshotRepo.findByCurrencyCodeOrderBySnapshotDateDesc("USD"))
                .thenReturn(List.of(s1, s2));

            assertThat(service.getSnapshots("USD", 1)).hasSize(1);
        }

        @Test
        @DisplayName("getSnapshots returns empty when none exist")
        void getSnapshots_empty() {
            when(snapshotRepo.findByCurrencyCodeOrderBySnapshotDateDesc("KES"))
                .thenReturn(List.of());
            assertThat(service.getSnapshots("KES", 10)).isEmpty();
        }
    }
}
