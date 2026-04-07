package com.cba.share;

import com.cba.common.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/shareproducts")
@RequiredArgsConstructor
public class ShareProductController {

    private final ShareService service;

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Page<ShareProduct>> list(Pageable pageable) {
        return ApiResponse.ok(service.listProducts(pageable));
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<ShareProduct> get(@PathVariable UUID id) {
        return ApiResponse.ok(service.getProduct(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<ShareProduct> create(@RequestBody ShareService.CreateShareProductRequest req) {
        return ApiResponse.ok(service.createProduct(req));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<ShareProduct> update(@PathVariable UUID id, @RequestBody ShareService.CreateShareProductRequest req) {
        return ApiResponse.ok(service.updateProduct(id, req));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMIN')")
    public void delete(@PathVariable UUID id) {
        service.deleteProduct(id);
    }
}
