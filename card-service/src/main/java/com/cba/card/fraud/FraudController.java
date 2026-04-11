package com.cba.card.fraud;

import com.cba.card.common.ApiResponse;
import com.cba.card.common.CbaException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/cards/fraud")
@RequiredArgsConstructor
public class FraudController {

    private final FraudRuleEntityRepository ruleRepository;

    @GetMapping("/rules")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<List<FraudRuleEntity>>> listRules() {
        return ResponseEntity.ok(ApiResponse.ok(ruleRepository.findAll()));
    }

    @PutMapping("/rules/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<FraudRuleEntity>> updateRule(
            @PathVariable UUID id,
            @RequestBody UpdateRuleRequest req) {
        FraudRuleEntity rule = ruleRepository.findById(id)
                .orElseThrow(() -> CbaException.notFound("RULE_NOT_FOUND", "Fraud rule not found: " + id));
        if (req.weight() != null)  rule.setWeight(req.weight());
        if (req.enabled() != null) rule.setEnabled(req.enabled());
        if (req.params()  != null) rule.setParams(req.params());
        return ResponseEntity.ok(ApiResponse.ok(ruleRepository.save(rule)));
    }

    public record UpdateRuleRequest(
            Integer weight,
            Boolean enabled,
            java.util.Map<String, Object> params) {}
}
