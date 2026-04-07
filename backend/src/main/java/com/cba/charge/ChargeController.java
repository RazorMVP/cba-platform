package com.cba.charge;

import com.cba.common.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/charges")
@RequiredArgsConstructor
public class ChargeController {

    private final ChargeService chargeService;

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Page<ChargeDefinition>> list(
            @RequestParam(required = false) ChargeDefinition.ChargeAppliesTo appliesTo,
            Pageable pageable) {
        return ApiResponse.ok(chargeService.listCharges(appliesTo, pageable));
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<ChargeDefinition> get(@PathVariable UUID id) {
        return ApiResponse.ok(chargeService.getCharge(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<ChargeDefinition> create(@RequestBody ChargeService.CreateChargeRequest req) {
        return ApiResponse.ok(chargeService.createCharge(req));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<ChargeDefinition> update(@PathVariable UUID id, @RequestBody ChargeService.CreateChargeRequest req) {
        return ApiResponse.ok(chargeService.updateCharge(id, req));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMIN')")
    public void delete(@PathVariable UUID id) {
        chargeService.deleteCharge(id);
    }
}
