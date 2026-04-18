package com.cba.share;

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

@Tag(name = "Share Products", description = "Equity share product catalogue — unit price, minimum/maximum share holdings, lock-in period and dividend policy configuration")
@RestController
@RequestMapping("/api/v1/shareproducts")
@RequiredArgsConstructor
public class ShareProductController {

    private final ShareService service;

    @Operation(summary = "List all share products")
    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Page<ShareProduct>> list(Pageable pageable) {
        return ApiResponse.ok(service.listProducts(pageable));
    }

    @Operation(summary = "Get a share product by ID")
    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<ShareProduct> get(@PathVariable UUID id) {
        return ApiResponse.ok(service.getProduct(id));
    }

    @Operation(summary = "Create a new share product")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<ShareProduct> create(@RequestBody ShareService.CreateShareProductRequest req) {
        return ApiResponse.ok(service.createProduct(req));
    }

    @Operation(summary = "Update a share product")
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<ShareProduct> update(@PathVariable UUID id, @RequestBody ShareService.CreateShareProductRequest req) {
        return ApiResponse.ok(service.updateProduct(id, req));
    }

    @Operation(summary = "Delete a share product")
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMIN')")
    public void delete(@PathVariable UUID id) {
        service.deleteProduct(id);
    }
}
