package com.cba.cardsettlement;

import com.cba.cardsettlement.dto.CardSchemeSettlementRequest;
import com.cba.cardsettlement.dto.CardSchemeSettlementResult;
import com.cba.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/card-settlements")
@RequiredArgsConstructor
@Tag(name = "Card Scheme Settlement", description = "The cash leg of card scheme settlement (card-service)")
@SecurityRequirement(name = "oauth2")
public class CardSchemeSettlementController {

    private final CardSchemeSettlementService service;

    @PostMapping
    @PreAuthorize("hasRole('CARD_SERVICE') or hasRole('ADMIN')")
    @Operation(summary = "Post a card scheme's net settlement (card-service, after the scheme report matched)",
               description = "Positive amount: the bank paid the scheme — DR 2300 Card Scheme Settlement Payable / "
                       + "CR 1003 Card Scheme Settlement Account; negative: the scheme paid the bank — DR 1003 / CR 2300. "
                       + "Journal dated today. Idempotent on reference (repeat=true, no second journal). reasons: "
                       + "REFERENCE_CONFLICT, ZERO_AMOUNT, INVALID_AMOUNT, UNKNOWN_CURRENCY.")
    public ResponseEntity<ApiResponse<CardSchemeSettlementResult>> record(
            @Valid @RequestBody CardSchemeSettlementRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(ApiResponse.ok(service.record(request, actor(jwt))));
    }

    private static String actor(Jwt jwt) {
        if (jwt == null) return "system";
        String user = jwt.getClaimAsString("preferred_username");
        return user != null ? user : jwt.getClaimAsString("azp");
    }
}
