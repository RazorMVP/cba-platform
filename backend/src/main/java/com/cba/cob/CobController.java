package com.cba.cob;

import com.cba.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/jobs")
@RequiredArgsConstructor
@Tag(name = "CoB Scheduler", description = "Close-of-Business batch job management and history")
public class CobController {

    private final CobJobService cobJobService;

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(
        summary = "List the CoB jobs",
        description = "One entry per nightly job in schedule order: cron expression, next scheduled run, "
                    + "and the outcome of the most recent run (scheduled or manual)."
    )
    public ResponseEntity<ApiResponse<List<CobJobView>>> listJobs() {
        return ResponseEntity.ok(ApiResponse.ok(cobJobService.listJobs()));
    }

    @PostMapping("/{jobName}/run")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(
        summary = "Manually trigger a CoB job",
        description = "Runs the job synchronously for today's business date and returns the run, "
                    + "including status SUCCESS or FAILED. 404 for an unknown job; 409 if it is already running. "
                    + "Valid job names: standingOrderExecutionJob, dormancyClassificationJob, "
                    + "interestAccrualJob, arrearsClassificationJob"
    )
    public ResponseEntity<ApiResponse<CobRunView>> runJob(@PathVariable String jobName) {
        return ResponseEntity.ok(ApiResponse.ok(cobJobService.runNow(jobName)));
    }

    @GetMapping("/{jobName}/history")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Get execution history for a specific job",
               description = "The 30 most recent runs, newest first, scheduled and manual alike.")
    public ResponseEntity<ApiResponse<List<CobRunView>>> jobHistory(@PathVariable String jobName) {
        return ResponseEntity.ok(ApiResponse.ok(cobJobService.history(jobName)));
    }
}
