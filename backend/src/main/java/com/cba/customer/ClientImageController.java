package com.cba.customer;

import com.cba.common.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/clients/{customerId}/images")
@RequiredArgsConstructor
public class ClientImageController {

    private final ClientImageService clientImageService;

    // Returns {hasImage, contentType, size, fileName} — always 200, never 404
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','TELLER','CUSTOMER')")
    public ApiResponse<ClientImageService.ImageMeta> getMeta(@PathVariable UUID customerId) {
        return ApiResponse.ok(clientImageService.getMeta(customerId));
    }

    // Returns raw image bytes with correct Content-Type header
    @GetMapping("/data")
    @PreAuthorize("hasAnyRole('ADMIN','TELLER','CUSTOMER')")
    public ResponseEntity<byte[]> getData(@PathVariable UUID customerId) {
        ClientImageService.ImageMeta meta = clientImageService.getMeta(customerId);
        byte[] data = clientImageService.getImageData(customerId);
        String ct = meta.contentType() != null ? meta.contentType() : MediaType.APPLICATION_OCTET_STREAM_VALUE;
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, ct)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "inline; filename=\"" + (meta.fileName() != null ? meta.fileName() : "image") + "\"")
                .body(data);
    }

    // Accepts multipart/form-data with field name "file"
    @PutMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN','TELLER')")
    public ApiResponse<ClientImageService.ImageMeta> upload(
            @PathVariable UUID customerId,
            @RequestPart("file") MultipartFile file) {
        clientImageService.saveImage(customerId, file);
        return ApiResponse.ok(clientImageService.getMeta(customerId));
    }

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMIN')")
    public void delete(@PathVariable UUID customerId) {
        clientImageService.deleteImage(customerId);
    }
}
