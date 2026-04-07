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

    private final CobSchedulerConfig cobSchedulerConfig;
    private final CobJobHistoryRepository historyRepository;

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "List available CoB jobs and recent history")
    public ResponseEntity<ApiResponse<List<CobJobHistory>>> recentJobs() {
        return ResponseEntity.ok(ApiResponse.ok(historyRepository.findTop10ByOrderByStartedAtDesc()));
    }

    @PostMapping("/{jobName}/run")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(
        summary = "Manually trigger a CoB job",
        description = "Valid job names: standingOrderExecutionJob, interestAccrualJob, arrearsClassificationJob"
    )
    public ResponseEntity<ApiResponse<Void>> runJob(@PathVariable String jobName) {
        cobSchedulerConfig.triggerJobNow(jobName);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    @GetMapping("/{jobName}/history")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Get execution history for a specific job")
    public ResponseEntity<ApiResponse<List<CobJobHistory>>> jobHistory(@PathVariable String jobName) {
        return ResponseEntity.ok(ApiResponse.ok(
                historyRepository.findByJobNameOrderByBusinessDateDesc(jobName)));
    }
}
