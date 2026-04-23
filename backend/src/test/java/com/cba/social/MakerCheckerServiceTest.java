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

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("MakerCheckerService — unit tests")
class MakerCheckerServiceTest {

    @Mock MakerCheckerRepository makerCheckerRepository;
    @Mock AuditLogService auditLogService;

    @InjectMocks MakerCheckerService service;

    private UUID entryId;
    private MakerChecker entry;

    @BeforeEach
    void setUp() {
        entryId = UUID.randomUUID();
        entry = new MakerChecker();
        entry.setId(entryId);
        entry.setActionName("CREATE_LOAN");
        entry.setEntityName("Loan");
        entry.setCommandAsJson("{\"amount\":5000}");
        entry.setStatus(MakerChecker.Status.PENDING);
    }

    @Nested
    @DisplayName("Read Operations")
    class ReadOperations {

        @Test
        @DisplayName("listPending returns pending entries")
        void listPending_returnsList() {
            when(makerCheckerRepository.findByStatus(eq(MakerChecker.Status.PENDING), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(entry)));

            assertThat(service.listPending(Pageable.unpaged()).getContent()).hasSize(1);
        }

        @Test
        @DisplayName("get returns entry when found")
        void get_found() {
            when(makerCheckerRepository.findById(entryId)).thenReturn(Optional.of(entry));
            assertThat(service.get(entryId).getActionName()).isEqualTo("CREATE_LOAN");
        }

        @Test
        @DisplayName("get throws when not found")
        void get_notFound_throws() {
            when(makerCheckerRepository.findById(entryId)).thenReturn(Optional.empty());
            assertThatThrownBy(() -> service.get(entryId))
                .isInstanceOf(CbaException.class);
        }
    }

    @Nested
    @DisplayName("Create")
    class Create {

        @Test
        @DisplayName("create saves new maker-checker entry")
        void create_success() {
            when(makerCheckerRepository.save(any())).thenReturn(entry);

            MakerCheckerService.CreateMakerCheckerRequest req = new MakerCheckerService.CreateMakerCheckerRequest(
                "CREATE_LOAN", "Loan", UUID.randomUUID(), "{}", UUID.randomUUID()
            );
            MakerChecker result = service.create(req);
            assertThat(result.getActionName()).isEqualTo("CREATE_LOAN");
        }
    }

    @Nested
    @DisplayName("Approve and Reject")
    class ApproveAndReject {

        @Test
        @DisplayName("approve sets APPROVED status")
        void approve_success() {
            when(makerCheckerRepository.findById(entryId)).thenReturn(Optional.of(entry));
            when(makerCheckerRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            MakerChecker result = service.approve(entryId, UUID.randomUUID());
            assertThat(result.getStatus()).isEqualTo(MakerChecker.Status.APPROVED);
        }

        @Test
        @DisplayName("approve throws when entry is not PENDING")
        void approve_notPending_throws() {
            entry.setStatus(MakerChecker.Status.APPROVED);
            when(makerCheckerRepository.findById(entryId)).thenReturn(Optional.of(entry));

            assertThatThrownBy(() -> service.approve(entryId, UUID.randomUUID()))
                .isInstanceOf(CbaException.class)
                .hasMessageContaining("not in PENDING state");
        }

        @Test
        @DisplayName("reject sets REJECTED status")
        void reject_success() {
            when(makerCheckerRepository.findById(entryId)).thenReturn(Optional.of(entry));
            when(makerCheckerRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            MakerChecker result = service.reject(entryId, UUID.randomUUID());
            assertThat(result.getStatus()).isEqualTo(MakerChecker.Status.REJECTED);
        }

        @Test
        @DisplayName("reject throws when entry is not PENDING")
        void reject_notPending_throws() {
            entry.setStatus(MakerChecker.Status.REJECTED);
            when(makerCheckerRepository.findById(entryId)).thenReturn(Optional.of(entry));

            assertThatThrownBy(() -> service.reject(entryId, UUID.randomUUID()))
                .isInstanceOf(CbaException.class);
        }
    }

    @Nested
    @DisplayName("Delete")
    class Delete {

        @Test
        @DisplayName("delete removes PENDING entry")
        void delete_success() {
            when(makerCheckerRepository.findById(entryId)).thenReturn(Optional.of(entry));

            assertThatCode(() -> service.delete(entryId)).doesNotThrowAnyException();
            verify(makerCheckerRepository).delete(entry);
        }

        @Test
        @DisplayName("delete throws when entry is not PENDING")
        void delete_notPending_throws() {
            entry.setStatus(MakerChecker.Status.APPROVED);
            when(makerCheckerRepository.findById(entryId)).thenReturn(Optional.of(entry));

            assertThatThrownBy(() -> service.delete(entryId))
                .isInstanceOf(CbaException.class)
                .hasMessageContaining("Only PENDING entries can be deleted");
        }
    }
}
