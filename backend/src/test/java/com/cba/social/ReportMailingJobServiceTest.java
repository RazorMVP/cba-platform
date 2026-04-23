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
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ReportMailingJobService — unit tests")
class ReportMailingJobServiceTest {

    @Mock ReportMailingJobRepository jobRepository;
    @Mock AuditLogService auditLogService;

    @InjectMocks ReportMailingJobService service;

    private UUID jobId;
    private ReportMailingJob job;

    @BeforeEach
    void setUp() {
        jobId = UUID.randomUUID();
        job = new ReportMailingJob();
        job.setId(jobId);
        job.setName("Monthly Loan Report");
        job.setReportName("ActiveLoans");
        job.setEmailRecipients("ops@cba.com");
        job.setOutputType(ReportMailingJob.OutputType.CSV);
        job.setActive(true);
        job.setRunCount(0);
    }

    @Nested
    @DisplayName("List and Get")
    class ListAndGet {

        @Test
        @DisplayName("listJobs returns page")
        void listJobs_returnsPage() {
            when(jobRepository.findAll(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(job)));
            assertThat(service.listJobs(Pageable.unpaged()).getContent()).hasSize(1);
        }

        @Test
        @DisplayName("getJob returns job when found")
        void getJob_found() {
            when(jobRepository.findById(jobId)).thenReturn(Optional.of(job));
            assertThat(service.getJob(jobId).getName()).isEqualTo("Monthly Loan Report");
        }

        @Test
        @DisplayName("getJob throws when not found")
        void getJob_notFound_throws() {
            when(jobRepository.findById(jobId)).thenReturn(Optional.empty());
            assertThatThrownBy(() -> service.getJob(jobId))
                .isInstanceOf(CbaException.class);
        }
    }

    @Nested
    @DisplayName("Create")
    class Create {

        @Test
        @DisplayName("createJob saves job")
        void createJob_success() {
            when(jobRepository.save(any())).thenReturn(job);

            ReportMailingJobService.CreateJobRequest req = new ReportMailingJobService.CreateJobRequest(
                "Monthly Loan Report", "Monthly active loan report",
                "FREQ=MONTHLY", "ops@cba.com", "ActiveLoans",
                Map.of("status", "ACTIVE"), ReportMailingJob.OutputType.CSV,
                "Monthly Report", "See attached.", true
            );
            ReportMailingJob result = service.createJob(req);
            assertThat(result.getName()).isEqualTo("Monthly Loan Report");
            verify(jobRepository).save(any(ReportMailingJob.class));
        }

        @Test
        @DisplayName("createJob defaults outputType to CSV when null")
        void createJob_nullOutputType_defaultsCsv() {
            when(jobRepository.save(any())).thenAnswer(inv -> {
                ReportMailingJob j = inv.getArgument(0);
                j.setId(UUID.randomUUID());
                return j;
            });

            ReportMailingJobService.CreateJobRequest req = new ReportMailingJobService.CreateJobRequest(
                "Test", null, null, "ops@cba.com", "TestReport",
                null, null, null, null, true
            );
            ReportMailingJob result = service.createJob(req);
            assertThat(result.getOutputType()).isEqualTo(ReportMailingJob.OutputType.CSV);
        }
    }

    @Nested
    @DisplayName("Update and Delete")
    class UpdateAndDelete {

        @Test
        @DisplayName("updateJob saves changes")
        void updateJob_success() {
            when(jobRepository.findById(jobId)).thenReturn(Optional.of(job));
            when(jobRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            ReportMailingJobService.CreateJobRequest req = new ReportMailingJobService.CreateJobRequest(
                "Updated Report", null, null, "new@cba.com",
                "LoansInArrears", null, ReportMailingJob.OutputType.PDF,
                null, null, true
            );
            ReportMailingJob result = service.updateJob(jobId, req);
            assertThat(result.getName()).isEqualTo("Updated Report");
        }

        @Test
        @DisplayName("deleteJob removes job")
        void deleteJob_success() {
            when(jobRepository.findById(jobId)).thenReturn(Optional.of(job));

            assertThatCode(() -> service.deleteJob(jobId)).doesNotThrowAnyException();
            verify(jobRepository).delete(job);
        }
    }

    @Nested
    @DisplayName("Run Now")
    class RunNow {

        @Test
        @DisplayName("runNow increments runCount and sets start time")
        void runNow_success() {
            when(jobRepository.findById(jobId)).thenReturn(Optional.of(job));
            when(jobRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            ReportMailingJob result = service.runNow(jobId);
            assertThat(result.getRunCount()).isEqualTo(1);
            assertThat(result.getPreviousRunStartTime()).isNotNull();
            assertThat(result.getPreviousRunStatus()).isEqualTo("MANUAL_RUN");
        }
    }
}
