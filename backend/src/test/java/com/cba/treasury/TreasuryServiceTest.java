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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("TreasuryService — unit tests")
class TreasuryServiceTest {

    @Mock TreasuryPlacementRepository placementRepo;
    @Mock TreasuryInterbankPositionRepository interbankRepo;

    @InjectMocks TreasuryService service;

    private UUID placementId;
    private UUID positionId;
    private TreasuryPlacement placement;
    private TreasuryInterbankPosition position;

    @BeforeEach
    void setUp() {
        placementId = UUID.randomUUID();
        positionId = UUID.randomUUID();

        placement = new TreasuryPlacement();
        placement.setId(placementId);
        placement.setReference("PL-001");
        placement.setStatus(TreasuryPlacement.Status.PENDING);
        placement.setPlacementType(TreasuryPlacement.PlacementType.FIXED_DEPOSIT);
        placement.setPrincipal(new BigDecimal("100000.00"));
        placement.setInterestRate(new BigDecimal("5.00"));

        position = new TreasuryInterbankPosition();
        position.setId(positionId);
        position.setReference("IB-001");
        position.setStatus(TreasuryInterbankPosition.Status.ACTIVE);
        position.setDirection(TreasuryInterbankPosition.Direction.LENDING);
        position.setAmount(new BigDecimal("50000.00"));
        position.setInterestRate(new BigDecimal("4.50"));
    }

    @Nested
    @DisplayName("Placements")
    class Placements {

        @Test
        @DisplayName("listPlacements returns all")
        void listPlacements_returnsAll() {
            when(placementRepo.findAll()).thenReturn(List.of(placement));
            assertThat(service.listPlacements()).hasSize(1);
        }

        @Test
        @DisplayName("getPlacement returns when found")
        void getPlacement_found() {
            when(placementRepo.findById(placementId)).thenReturn(Optional.of(placement));
            assertThat(service.getPlacement(placementId).getReference()).isEqualTo("PL-001");
        }

        @Test
        @DisplayName("getPlacement throws when not found")
        void getPlacement_notFound() {
            when(placementRepo.findById(placementId)).thenReturn(Optional.empty());
            assertThatThrownBy(() -> service.getPlacement(placementId))
                .isInstanceOf(CbaException.class);
        }

        @Test
        @DisplayName("createPlacement saves new placement")
        void createPlacement_success() {
            when(placementRepo.findByReference("PL-001")).thenReturn(Optional.empty());
            when(placementRepo.save(any())).thenReturn(placement);

            TreasuryPlacementRequest req = new TreasuryPlacementRequest(
                "PL-001", "Bank A", "BANKA", "FIXED_DEPOSIT",
                new BigDecimal("100000.00"), new BigDecimal("5.00"),
                "USD", LocalDate.now(), LocalDate.now().plusMonths(6),
                null, null, null, null);
            assertThat(service.createPlacement(req)).isNotNull();
        }

        @Test
        @DisplayName("createPlacement throws on duplicate reference")
        void createPlacement_duplicate_throws() {
            when(placementRepo.findByReference("PL-001")).thenReturn(Optional.of(placement));

            TreasuryPlacementRequest req = new TreasuryPlacementRequest(
                "PL-001", "Bank A", null, "FIXED_DEPOSIT",
                new BigDecimal("100000.00"), new BigDecimal("5.00"),
                "USD", LocalDate.now(), LocalDate.now().plusMonths(6),
                null, null, null, null);
            assertThatThrownBy(() -> service.createPlacement(req))
                .isInstanceOf(CbaException.class)
                .hasMessageContaining("already exists");
        }

        @Test
        @DisplayName("updatePlacement updates and saves")
        void updatePlacement_success() {
            when(placementRepo.findById(placementId)).thenReturn(Optional.of(placement));
            when(placementRepo.save(any())).thenReturn(placement);

            TreasuryPlacementRequest req = new TreasuryPlacementRequest(
                "PL-001", "Bank B", null, "BOND",
                new BigDecimal("200000.00"), new BigDecimal("6.00"),
                "USD", LocalDate.now(), LocalDate.now().plusYears(1),
                null, null, null, "updated");
            assertThat(service.updatePlacement(placementId, req)).isNotNull();
        }

        @Test
        @DisplayName("commandPlacement activate — PENDING → ACTIVE")
        void commandPlacement_activate() {
            when(placementRepo.findById(placementId)).thenReturn(Optional.of(placement));
            when(placementRepo.save(any())).thenReturn(placement);

            TreasuryPlacement result = service.commandPlacement(placementId, "activate");
            assertThat(result.getStatus()).isEqualTo(TreasuryPlacement.Status.ACTIVE);
        }

        @Test
        @DisplayName("commandPlacement activate throws when not PENDING")
        void commandPlacement_activate_wrongState() {
            placement.setStatus(TreasuryPlacement.Status.ACTIVE);
            when(placementRepo.findById(placementId)).thenReturn(Optional.of(placement));

            assertThatThrownBy(() -> service.commandPlacement(placementId, "activate"))
                .isInstanceOf(CbaException.class)
                .hasMessageContaining("Only PENDING");
        }

        @Test
        @DisplayName("commandPlacement mature — ACTIVE → MATURED")
        void commandPlacement_mature() {
            placement.setStatus(TreasuryPlacement.Status.ACTIVE);
            when(placementRepo.findById(placementId)).thenReturn(Optional.of(placement));
            when(placementRepo.save(any())).thenReturn(placement);

            TreasuryPlacement result = service.commandPlacement(placementId, "mature");
            assertThat(result.getStatus()).isEqualTo(TreasuryPlacement.Status.MATURED);
        }

        @Test
        @DisplayName("commandPlacement cancel — non-MATURED → CANCELLED")
        void commandPlacement_cancel() {
            placement.setStatus(TreasuryPlacement.Status.PENDING);
            when(placementRepo.findById(placementId)).thenReturn(Optional.of(placement));
            when(placementRepo.save(any())).thenReturn(placement);

            TreasuryPlacement result = service.commandPlacement(placementId, "cancel");
            assertThat(result.getStatus()).isEqualTo(TreasuryPlacement.Status.CANCELLED);
        }

        @Test
        @DisplayName("commandPlacement cancel throws when already MATURED")
        void commandPlacement_cancelMatured_throws() {
            placement.setStatus(TreasuryPlacement.Status.MATURED);
            when(placementRepo.findById(placementId)).thenReturn(Optional.of(placement));

            assertThatThrownBy(() -> service.commandPlacement(placementId, "cancel"))
                .isInstanceOf(CbaException.class)
                .hasMessageContaining("Matured");
        }

        @Test
        @DisplayName("commandPlacement unknown throws")
        void commandPlacement_unknown_throws() {
            when(placementRepo.findById(placementId)).thenReturn(Optional.of(placement));
            assertThatThrownBy(() -> service.commandPlacement(placementId, "teleport"))
                .isInstanceOf(CbaException.class)
                .hasMessageContaining("Unknown command");
        }

        @Test
        @DisplayName("deletePlacement removes non-ACTIVE placement")
        void deletePlacement_success() {
            placement.setStatus(TreasuryPlacement.Status.CANCELLED);
            when(placementRepo.findById(placementId)).thenReturn(Optional.of(placement));

            assertThatCode(() -> service.deletePlacement(placementId)).doesNotThrowAnyException();
            verify(placementRepo).delete(placement);
        }

        @Test
        @DisplayName("deletePlacement throws when ACTIVE")
        void deletePlacement_active_throws() {
            placement.setStatus(TreasuryPlacement.Status.ACTIVE);
            when(placementRepo.findById(placementId)).thenReturn(Optional.of(placement));

            assertThatThrownBy(() -> service.deletePlacement(placementId))
                .isInstanceOf(CbaException.class)
                .hasMessageContaining("Cannot delete an ACTIVE");
        }
    }

    @Nested
    @DisplayName("Interbank Positions")
    class InterbankPositions {

        @Test
        @DisplayName("listPositions returns all")
        void listPositions_returnsAll() {
            when(interbankRepo.findAll()).thenReturn(List.of(position));
            assertThat(service.listPositions()).hasSize(1);
        }

        @Test
        @DisplayName("getPosition returns when found")
        void getPosition_found() {
            when(interbankRepo.findById(positionId)).thenReturn(Optional.of(position));
            assertThat(service.getPosition(positionId).getReference()).isEqualTo("IB-001");
        }

        @Test
        @DisplayName("getPosition throws when not found")
        void getPosition_notFound() {
            when(interbankRepo.findById(positionId)).thenReturn(Optional.empty());
            assertThatThrownBy(() -> service.getPosition(positionId))
                .isInstanceOf(CbaException.class);
        }

        @Test
        @DisplayName("createPosition saves new position")
        void createPosition_success() {
            when(interbankRepo.findByReference("IB-001")).thenReturn(Optional.empty());
            when(interbankRepo.save(any())).thenReturn(position);

            TreasuryInterbankRequest req = new TreasuryInterbankRequest(
                "IB-001", "Bank A", null, "LENDING",
                new BigDecimal("50000.00"), "USD", new BigDecimal("4.50"),
                LocalDate.now(), LocalDate.now().plusMonths(3), null, null);
            assertThat(service.createPosition(req)).isNotNull();
        }

        @Test
        @DisplayName("createPosition throws on duplicate reference")
        void createPosition_duplicate_throws() {
            when(interbankRepo.findByReference("IB-001")).thenReturn(Optional.of(position));

            TreasuryInterbankRequest req = new TreasuryInterbankRequest(
                "IB-001", "Bank A", null, "LENDING",
                new BigDecimal("50000.00"), "USD", new BigDecimal("4.50"),
                LocalDate.now(), null, null, null);
            assertThatThrownBy(() -> service.createPosition(req))
                .isInstanceOf(CbaException.class)
                .hasMessageContaining("already exists");
        }

        @Test
        @DisplayName("commandPosition settle — ACTIVE → SETTLED")
        void commandPosition_settle() {
            when(interbankRepo.findById(positionId)).thenReturn(Optional.of(position));
            when(interbankRepo.save(any())).thenReturn(position);

            TreasuryInterbankPosition result = service.commandPosition(positionId, "settle");
            assertThat(result.getStatus()).isEqualTo(TreasuryInterbankPosition.Status.SETTLED);
        }

        @Test
        @DisplayName("commandPosition cancel — ACTIVE → CANCELLED")
        void commandPosition_cancel() {
            when(interbankRepo.findById(positionId)).thenReturn(Optional.of(position));
            when(interbankRepo.save(any())).thenReturn(position);

            TreasuryInterbankPosition result = service.commandPosition(positionId, "cancel");
            assertThat(result.getStatus()).isEqualTo(TreasuryInterbankPosition.Status.CANCELLED);
        }

        @Test
        @DisplayName("commandPosition settle throws when not ACTIVE")
        void commandPosition_settle_wrongState() {
            position.setStatus(TreasuryInterbankPosition.Status.SETTLED);
            when(interbankRepo.findById(positionId)).thenReturn(Optional.of(position));

            assertThatThrownBy(() -> service.commandPosition(positionId, "settle"))
                .isInstanceOf(CbaException.class)
                .hasMessageContaining("Only ACTIVE");
        }

        @Test
        @DisplayName("commandPosition unknown throws")
        void commandPosition_unknown_throws() {
            when(interbankRepo.findById(positionId)).thenReturn(Optional.of(position));
            assertThatThrownBy(() -> service.commandPosition(positionId, "teleport"))
                .isInstanceOf(CbaException.class)
                .hasMessageContaining("Unknown command");
        }

        @Test
        @DisplayName("deletePosition removes non-ACTIVE position")
        void deletePosition_success() {
            position.setStatus(TreasuryInterbankPosition.Status.SETTLED);
            when(interbankRepo.findById(positionId)).thenReturn(Optional.of(position));

            assertThatCode(() -> service.deletePosition(positionId)).doesNotThrowAnyException();
            verify(interbankRepo).delete(position);
        }

        @Test
        @DisplayName("deletePosition throws when ACTIVE")
        void deletePosition_active_throws() {
            when(interbankRepo.findById(positionId)).thenReturn(Optional.of(position));

            assertThatThrownBy(() -> service.deletePosition(positionId))
                .isInstanceOf(CbaException.class)
                .hasMessageContaining("Cannot delete an ACTIVE");
        }
    }
}
