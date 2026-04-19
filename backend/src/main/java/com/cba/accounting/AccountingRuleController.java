package com.cba.accounting;

import com.cba.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Tag(name = "Accounting Rules", description = "GL debit/credit mapping rules — configure which accounts are posted when a specific journal entry type fires")
@RestController
@RequestMapping("/api/v1/accountingrules")
@RequiredArgsConstructor
public class AccountingRuleController {

    private final AccountingRuleService accountingRuleService;

    @Operation(summary = "List all accounting rules")
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','TELLER')")
    public ApiResponse<Page<AccountingRule>> list(Pageable pageable) {
        return ApiResponse.ok(accountingRuleService.listRules(pageable));
    }

    @Operation(summary = "Get an accounting rule by ID")
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','TELLER')")
    public ApiResponse<AccountingRule> get(@PathVariable UUID id) {
        return ApiResponse.ok(accountingRuleService.getRule(id));
    }

    @Operation(summary = "Create a new accounting rule")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<AccountingRule> create(
            @RequestBody AccountingRuleService.CreateRuleRequest req) {
        return ApiResponse.ok(accountingRuleService.createRule(req));
    }

    @Operation(summary = "Update an accounting rule")
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<AccountingRule> update(@PathVariable UUID id,
            @RequestBody AccountingRuleService.CreateRuleRequest req) {
        return ApiResponse.ok(accountingRuleService.updateRule(id, req));
    }

    @Operation(summary = "Delete an accounting rule")
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMIN')")
    public void delete(@PathVariable UUID id) {
        accountingRuleService.deleteRule(id);
    }
}
