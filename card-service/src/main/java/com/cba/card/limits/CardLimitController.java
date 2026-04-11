package com.cba.card.limits;

import com.cba.card.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/cards/{cardId}/limits")
@RequiredArgsConstructor
public class CardLimitController {

    private final CardLimitService cardLimitService;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','TELLER')")
    public ResponseEntity<ApiResponse<CardLimit>> get(@PathVariable UUID cardId) {
        return ResponseEntity.ok(ApiResponse.ok(cardLimitService.getForCard(cardId)));
    }

    @PutMapping
    @PreAuthorize("hasAnyRole('ADMIN','TELLER')")
    public ResponseEntity<ApiResponse<CardLimit>> update(
            @PathVariable UUID cardId,
            @RequestBody UpdateLimitsRequest req) {
        CardLimit updated = cardLimitService.update(
                cardId, req.dailyPurchaseLimit(), req.dailyWithdrawalLimit(),
                req.perTxnLimit(), req.monthlyLimit());
        return ResponseEntity.ok(ApiResponse.ok(updated));
    }

    public record UpdateLimitsRequest(
            BigDecimal dailyPurchaseLimit,
            BigDecimal dailyWithdrawalLimit,
            BigDecimal perTxnLimit,
            BigDecimal monthlyLimit) {}
}
