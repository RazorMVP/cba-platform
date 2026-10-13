package com.cba.loan;

import com.cba.common.response.ApiResponse;
import com.cba.loan.EclParameterService.PdRateRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Expected Credit Losses", description = "IFRS 9 loan impairment: PD table, portfolio allowance by stage, CBN regulatory risk reserve")
@RestController
@RequestMapping("/api/v1/ecl")
@RequiredArgsConstructor
@Validated
public class EclController {

    private final EclParameterService eclParameterService;

    @Operation(summary = "List the probability-of-default bands",
               description = "By IFRS 9 stage and days past due. Stage 1 PDs are 12-month, stages 2 and 3 lifetime.")
    @GetMapping("/pd-rates")
    @PreAuthorize("hasAnyRole('ADMIN','TELLER')")
    public ApiResponse<List<EclParameterService.PdRateView>> listPdRates() {
        return ApiResponse.ok(eclParameterService.listPdRates());
    }

    @Operation(summary = "Replace the probability-of-default bands",
               description = "Replaces the whole table. Every stage (1–3) needs at least one band, bands may not "
                           + "overlap within a stage, and PDs are percentages 0–100. 400 ECL_PD_RATES_INVALID otherwise. "
                           + "Takes effect at the next impairment run.")
    @PutMapping("/pd-rates")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<List<EclParameterService.PdRateView>> replacePdRates(
            @RequestBody @NotEmpty List<@Valid PdRateRequest> rates) {
        return ApiResponse.ok(eclParameterService.replacePdRates(rates));
    }

    @Operation(summary = "Impairment position of the loan book",
               description = "Open loans by currency and IFRS 9 stage (count, principal, loss allowance, CBN prudential "
                           + "provision), and the regulatory risk reserve: prudential provisions vs the allowance in the "
                           + "functional currency, the reserve's target, its GL balance and the move still to post.")
    @GetMapping("/summary")
    @PreAuthorize("hasAnyRole('ADMIN','TELLER')")
    public ApiResponse<EclParameterService.EclSummary> summary() {
        return ApiResponse.ok(eclParameterService.summary());
    }
}
