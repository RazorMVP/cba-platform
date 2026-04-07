package com.cba.batch;

import com.cba.batch.dto.BatchRequest;
import com.cba.batch.dto.BatchResponse;
import com.cba.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/batches")
@RequiredArgsConstructor
@Tag(name = "Batch API", description = "Execute multiple API requests in a single HTTP call (Mifos-compatible)")
public class BatchController {

    private final BatchApiService batchApiService;

    @PostMapping
    @PreAuthorize("isAuthenticated()")
    @Operation(
        summary = "Execute a batch of API requests",
        description = """
            Executes multiple sub-requests in sequence. Dependent steps can reference prior results
            using JSON Path syntax: `"$.fieldName"` in the body or relativeUrl.

            When `enclosingTransaction=true`, all steps run in a single database transaction —
            any step returning HTTP 4xx/5xx causes a full rollback of all prior steps.
            """
    )
    public ResponseEntity<ApiResponse<List<BatchResponse>>> executeBatch(
            @Valid @RequestBody List<BatchRequest> requests,
            @RequestParam(defaultValue = "false") boolean enclosingTransaction,
            HttpServletRequest httpRequest) {

        List<BatchResponse> results = batchApiService.executeBatch(requests, enclosingTransaction, httpRequest);
        return ResponseEntity.ok(ApiResponse.ok(results));
    }
}
