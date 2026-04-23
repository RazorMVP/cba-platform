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

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("StandingInstructionService — unit tests")
class StandingInstructionServiceTest {

    @Mock StandingInstructionRepository repository;
    @Mock AuditLogService auditLogService;

    @InjectMocks StandingInstructionService service;

    private UUID instructionId;
    private StandingInstruction instruction;

    @BeforeEach
    void setUp() {
        instructionId = UUID.randomUUID();
        instruction = new StandingInstruction();
        instruction.setId(instructionId);
        instruction.setName("Monthly Transfer");
        instruction.setStatus(StandingInstruction.Status.ACTIVE);
        instruction.setInstructionType(StandingInstruction.InstructionType.FIXED);
        instruction.setAmount(new BigDecimal("500.00"));
    }

    @Nested
    @DisplayName("List and Get")
    class ListAndGet {

        @Test
        @DisplayName("listInstructions returns page")
        void listInstructions_returnsPage() {
            when(repository.findAll(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(instruction)));
            assertThat(service.listInstructions(Pageable.unpaged()).getContent()).hasSize(1);
        }

        @Test
        @DisplayName("getInstruction returns instruction when found")
        void getInstruction_found() {
            when(repository.findById(instructionId)).thenReturn(Optional.of(instruction));
            assertThat(service.getInstruction(instructionId).getName()).isEqualTo("Monthly Transfer");
        }

        @Test
        @DisplayName("getInstruction throws when not found")
        void getInstruction_notFound_throws() {
            when(repository.findById(instructionId)).thenReturn(Optional.empty());
            assertThatThrownBy(() -> service.getInstruction(instructionId))
                .isInstanceOf(CbaException.class);
        }
    }

    @Nested
    @DisplayName("Create")
    class Create {

        @Test
        @DisplayName("createInstruction saves instruction with defaults")
        void createInstruction_withDefaults() {
            when(repository.save(any())).thenAnswer(inv -> {
                StandingInstruction si = inv.getArgument(0);
                si.setId(UUID.randomUUID());
                return si;
            });

            StandingInstructionService.CreateInstructionRequest req =
                new StandingInstructionService.CreateInstructionRequest(
                    "Monthly Transfer", UUID.randomUUID(), UUID.randomUUID(), null,
                    UUID.randomUUID(), UUID.randomUUID(), null,
                    null, null, new BigDecimal("500.00"),
                    null, null, null, 0, 0, null
                );
            StandingInstruction result = service.createInstruction(req);
            assertThat(result.getName()).isEqualTo("Monthly Transfer");
            assertThat(result.getFromAccountType()).isEqualTo("SAVINGS");
            assertThat(result.getInstructionType()).isEqualTo(StandingInstruction.InstructionType.FIXED);
            assertThat(result.getPriority()).isEqualTo(StandingInstruction.Priority.MEDIUM);
            assertThat(result.getRecurrenceFrequency()).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("Update")
    class Update {

        @Test
        @DisplayName("updateInstruction saves changes")
        void updateInstruction_success() {
            when(repository.findById(instructionId)).thenReturn(Optional.of(instruction));
            when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            StandingInstructionService.CreateInstructionRequest req =
                new StandingInstructionService.CreateInstructionRequest(
                    "Updated Transfer", UUID.randomUUID(), UUID.randomUUID(), "CHECKING",
                    UUID.randomUUID(), UUID.randomUUID(), "CHECKING",
                    StandingInstruction.InstructionType.FIXED, StandingInstruction.Priority.HIGH,
                    new BigDecimal("1000.00"), null, null,
                    StandingInstruction.RecurrenceType.PERIODIC_RECURRENCE, 1, 1, null
                );
            StandingInstruction result = service.updateInstruction(instructionId, req);
            assertThat(result.getName()).isEqualTo("Updated Transfer");
        }
    }

    @Nested
    @DisplayName("Enable, Disable, Delete")
    class StatusTransitions {

        @Test
        @DisplayName("disable sets DISABLED status")
        void disable_success() {
            when(repository.findById(instructionId)).thenReturn(Optional.of(instruction));
            when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            StandingInstruction result = service.disable(instructionId);
            assertThat(result.getStatus()).isEqualTo(StandingInstruction.Status.DISABLED);
        }

        @Test
        @DisplayName("enable sets ACTIVE status")
        void enable_success() {
            instruction.setStatus(StandingInstruction.Status.DISABLED);
            when(repository.findById(instructionId)).thenReturn(Optional.of(instruction));
            when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            StandingInstruction result = service.enable(instructionId);
            assertThat(result.getStatus()).isEqualTo(StandingInstruction.Status.ACTIVE);
        }

        @Test
        @DisplayName("deleteInstruction soft-deletes with DELETED status")
        void deleteInstruction_softDelete() {
            when(repository.findById(instructionId)).thenReturn(Optional.of(instruction));

            assertThatCode(() -> service.deleteInstruction(instructionId)).doesNotThrowAnyException();
            verify(repository).save(argThat(si -> si.getStatus() == StandingInstruction.Status.DELETED));
        }
    }
}
