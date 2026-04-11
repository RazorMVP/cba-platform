package com.cba.card.bin;

import com.cba.card.common.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/bins")
@RequiredArgsConstructor
public class BinController {

    private final BinService binService;

    /** All BIN ranges — admin UI list. */
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<List<BinRange>>> list() {
        return ResponseEntity.ok(ApiResponse.ok(binService.listAll()));
    }

    /**
     * Full BIN→scheme mapping export for FEP cache pre-population.
     *
     * <p>Called by {@code fep-service} {@code CardServiceClient.getAllBinMappings()}.
     * Returns a raw {@code Map<binStart, schemeName>} — no ApiResponse wrapper
     * so the FEP can parse it directly.
     */
    @GetMapping("/all")
    public ResponseEntity<Map<String, String>> allMappings() {
        return ResponseEntity.ok(binService.getAllMappings());
    }

    /**
     * Scheme lookup by BIN prefix — dev/ops diagnostic tool.
     *
     * <p>Called from admin UI. Accepts full or partial PAN; resolves scheme
     * via BIN range scan.
     */
    @GetMapping("/lookup")
    public ResponseEntity<ApiResponse<SchemeType>> lookup(@RequestParam String pan) {
        return ResponseEntity.ok(ApiResponse.ok(binService.lookupScheme(pan)));
    }

    /**
     * Scheme lookup by BIN prefix — machine-to-machine endpoint for fep-service.
     *
     * <p>Called by {@code CardServiceClient.lookupBinScheme(binPrefix)} as a
     * fallback when the local BIN cache has no entry. Returns
     * {@code { "scheme": "VISA" }} — raw map, no ApiResponse envelope, so the
     * FEP client can read {@code response.get("scheme")} directly.
     *
     * <p>Path: {@code GET /api/v1/bins/{bin}/scheme}
     * where {@code bin} is the 6 or 8-digit BIN prefix.
     */
    @GetMapping("/{bin}/scheme")
    public ResponseEntity<Map<String, String>> schemeByBin(@PathVariable String bin) {
        SchemeType scheme = binService.lookupScheme(bin);
        return ResponseEntity.ok(Map.of("scheme", scheme.name()));
    }

    /** Single BIN range by UUID — admin detail view. */
    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<BinRange>> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(binService.findById(id)));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<BinRange>> create(@Valid @RequestBody BinRangeRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(binService.create(req)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<BinRange>> update(@PathVariable UUID id,
                                                         @Valid @RequestBody BinRangeRequest req) {
        return ResponseEntity.ok(ApiResponse.ok(binService.update(id, req)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        binService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
