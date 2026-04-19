package com.cba.social;

import com.cba.common.exception.CbaException;
import com.cba.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Tag(name = "Report Mailing Jobs", description = "Scheduled report delivery — run reports on a recurrence and email results as CSV, PDF or XLS")
@RestController
@RequestMapping("/api/v1/reportmailingjobs")
@RequiredArgsConstructor
public class ReportMailingJobController {

    private final ReportMailingJobService jobService;

    @Operation(summary = "List report mailing jobs")
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<Page<ReportMailingJob>> list(Pageable pageable) {
        return ApiResponse.ok(jobService.listJobs(pageable));
    }

    @Operation(summary = "Get a report mailing job by ID")
    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<ReportMailingJob> get(@PathVariable UUID id) {
        return ApiResponse.ok(jobService.getJob(id));
    }

    @Operation(summary = "Create a new report mailing job")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<ReportMailingJob> create(@RequestBody ReportMailingJobService.CreateJobRequest req) {
        return ApiResponse.ok(jobService.createJob(req));
    }

    @Operation(summary = "Update a report mailing job")
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<ReportMailingJob> update(@PathVariable UUID id,
                                                 @RequestBody ReportMailingJobService.CreateJobRequest req) {
        return ApiResponse.ok(jobService.updateJob(id, req));
    }

    @Operation(summary = "Execute a command (?command=run) — triggers the job immediately")
    @PostMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<ReportMailingJob> command(@PathVariable UUID id,
                                                  @RequestParam String command) {
        if ("run".equalsIgnoreCase(command)) {
            return ApiResponse.ok(jobService.runNow(id));
        }
        throw new CbaException("UNKNOWN_COMMAND", "Unknown command: " + command,
            org.springframework.http.HttpStatus.BAD_REQUEST);
    }

    @Operation(summary = "Delete a report mailing job")
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMIN')")
    public void delete(@PathVariable UUID id) {
        jobService.deleteJob(id);
    }
}
