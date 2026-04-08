package com.cba.customer;

import com.cba.common.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/clients/{customerId}/images")
@RequiredArgsConstructor
public class ClientImageController {

    private final ClientImageService clientImageService;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','TELLER')")
    public ApiResponse<ClientImage> get(@PathVariable UUID customerId) {
        return ApiResponse.ok(clientImageService.getImage(customerId));
    }

    @PutMapping
    @PreAuthorize("hasAnyRole('ADMIN','TELLER')")
    public ApiResponse<ClientImage> upsert(@PathVariable UUID customerId,
            @RequestBody ClientImageService.SaveImageRequest req) {
        return ApiResponse.ok(clientImageService.saveImage(customerId, req));
    }

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMIN')")
    public void delete(@PathVariable UUID customerId) {
        clientImageService.deleteImage(customerId);
    }
}
