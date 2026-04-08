package com.cba.accounting;

import com.cba.common.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/accountingrules")
@RequiredArgsConstructor
public class AccountingRuleController {

    private final AccountingRuleService accountingRuleService;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','TELLER')")
    public ApiResponse<Page<AccountingRule>> list(Pageable pageable) {
        return ApiResponse.ok(accountingRuleService.listRules(pageable));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','TELLER')")
    public ApiResponse<AccountingRule> get(@PathVariable UUID id) {
        return ApiResponse.ok(accountingRuleService.getRule(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<AccountingRule> create(
            @RequestBody AccountingRuleService.CreateRuleRequest req) {
        return ApiResponse.ok(accountingRuleService.createRule(req));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<AccountingRule> update(@PathVariable UUID id,
            @RequestBody AccountingRuleService.CreateRuleRequest req) {
        return ApiResponse.ok(accountingRuleService.updateRule(id, req));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMIN')")
    public void delete(@PathVariable UUID id) {
        accountingRuleService.deleteRule(id);
    }
}
