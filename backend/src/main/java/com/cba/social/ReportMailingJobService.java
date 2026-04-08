package com.cba.social;

import com.cba.audit.AuditLogService;
import com.cba.common.exception.CbaException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ReportMailingJobService {

    public record CreateJobRequest(
        String name,
        String description,
        String recurrence,
        String emailRecipients,
        String reportName,
        Map<String, String> reportParams,
        ReportMailingJob.OutputType outputType,
        String emailSubject,
        String emailMessage,
        boolean active
    ) {}

    private final ReportMailingJobRepository jobRepository;
    private final AuditLogService            auditLogService;

    @Transactional(readOnly = true)
    public Page<ReportMailingJob> listJobs(Pageable p) {
        return jobRepository.findAll(p);
    }

    @Transactional(readOnly = true)
    public ReportMailingJob getJob(UUID id) {
        return jobRepository.findById(id)
            .orElseThrow(() -> CbaException.notFound("ReportMailingJob", id));
    }

    @Transactional
    public ReportMailingJob createJob(CreateJobRequest req) {
        ReportMailingJob job = new ReportMailingJob();
        applyRequest(job, req);
        ReportMailingJob saved = jobRepository.save(job);
        auditLogService.log("ReportMailingJob", saved.getId().toString(), "CREATE", null, saved);
        return saved;
    }

    @Transactional
    public ReportMailingJob updateJob(UUID id, CreateJobRequest req) {
        ReportMailingJob job = getJob(id);
        applyRequest(job, req);
        ReportMailingJob saved = jobRepository.save(job);
        auditLogService.log("ReportMailingJob", id.toString(), "UPDATE", null, saved);
        return saved;
    }

    @Transactional
    public ReportMailingJob runNow(UUID id) {
        ReportMailingJob job = getJob(id);
        job.setRunCount(job.getRunCount() + 1);
        job.setPreviousRunStartTime(OffsetDateTime.now());
        job.setPreviousRunStatus("MANUAL_RUN");
        ReportMailingJob saved = jobRepository.save(job);
        auditLogService.log("ReportMailingJob", id.toString(), "RUN", null, saved);
        return saved;
    }

    @Transactional
    public void deleteJob(UUID id) {
        jobRepository.delete(getJob(id));
        auditLogService.log("ReportMailingJob", id.toString(), "DELETE", null, null);
    }

    private void applyRequest(ReportMailingJob job, CreateJobRequest req) {
        job.setName(req.name());
        job.setDescription(req.description());
        job.setRecurrence(req.recurrence());
        job.setEmailRecipients(req.emailRecipients());
        job.setReportName(req.reportName());
        job.setReportParams(req.reportParams());
        job.setOutputType(req.outputType() != null ? req.outputType() : ReportMailingJob.OutputType.CSV);
        job.setEmailSubject(req.emailSubject());
        job.setEmailMessage(req.emailMessage());
        job.setActive(req.active());
    }
}
