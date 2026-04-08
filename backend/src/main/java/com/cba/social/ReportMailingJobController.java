package com.cba.social;

import com.cba.common.exception.CbaException;
import com.cba.common.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/reportmailingjobs")
@RequiredArgsConstructor
public class ReportMailingJobController {

    private final ReportMailingJobService jobService;

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<Page<ReportMailingJob>> list(Pageable pageable) {
        return ApiResponse.ok(jobService.listJobs(pageable));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<ReportMailingJob> get(@PathVariable UUID id) {
        return ApiResponse.ok(jobService.getJob(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<ReportMailingJob> create(@RequestBody ReportMailingJobService.CreateJobRequest req) {
        return ApiResponse.ok(jobService.createJob(req));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<ReportMailingJob> update(@PathVariable UUID id,
                                                 @RequestBody ReportMailingJobService.CreateJobRequest req) {
        return ApiResponse.ok(jobService.updateJob(id, req));
    }

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

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMIN')")
    public void delete(@PathVariable UUID id) {
        jobService.deleteJob(id);
    }
}
