package com.cba.card.bin;

import com.cba.card.common.ApiResponse;
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

    /** All BIN ranges (paginated list for admin UI). */
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<List<BinRange>>> list() {
        return ResponseEntity.ok(ApiResponse.ok(binService.listAll()));
    }

    /**
     * Full BIN→scheme mapping export for FEP cache pre-population.
     * Called by fep-service CardServiceClient.getAllBinMappings().
     */
    @GetMapping("/all")
    public ResponseEntity<Map<String, String>> allMappings() {
        return ResponseEntity.ok(binService.getAllMappings());
    }

    /** Single BIN lookup — test/ops tool. */
    @GetMapping("/lookup")
    public ResponseEntity<ApiResponse<SchemeType>> lookup(@RequestParam String pan) {
        return ResponseEntity.ok(ApiResponse.ok(binService.lookupScheme(pan)));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<BinRange>> create(@RequestBody BinRange range) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(binService.create(range)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<BinRange>> update(@PathVariable UUID id,
                                                         @RequestBody BinRange req) {
        return ResponseEntity.ok(ApiResponse.ok(binService.update(id, req)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        binService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
