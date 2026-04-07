package com.cba.deposit;

import com.cba.common.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/recurringdepositproducts")
@RequiredArgsConstructor
public class RecurringDepositProductController {

    private final RecurringDepositService service;

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Page<RecurringDepositProduct>> list(Pageable pageable) {
        return ApiResponse.ok(service.listProducts(pageable));
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<RecurringDepositProduct> get(@PathVariable UUID id) {
        return ApiResponse.ok(service.getProduct(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<RecurringDepositProduct> create(@RequestBody RecurringDepositService.CreateRdProductRequest req) {
        return ApiResponse.ok(service.createProduct(req));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<RecurringDepositProduct> update(@PathVariable UUID id, @RequestBody RecurringDepositService.CreateRdProductRequest req) {
        return ApiResponse.ok(service.updateProduct(id, req));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMIN')")
    public void delete(@PathVariable UUID id) {
        service.deleteProduct(id);
    }
}
