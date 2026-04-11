package com.cba.card.card;

import com.cba.card.common.ApiResponse;
import com.cba.card.common.CbaException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/cards/products")
@RequiredArgsConstructor
public class CardProductController {

    private final CardProductRepository cardProductRepository;

    @GetMapping
    public ResponseEntity<ApiResponse<List<CardProduct>>> list(
            @RequestParam(required = false) String type) {
        List<CardProduct> products = type != null
                ? cardProductRepository.findByCardType(CardType.valueOf(type.toUpperCase()))
                : cardProductRepository.findByActiveTrue();
        return ResponseEntity.ok(ApiResponse.ok(products));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<CardProduct>> get(@PathVariable UUID id) {
        CardProduct p = cardProductRepository.findById(id)
                .orElseThrow(() -> CbaException.notFound("CARD_PRODUCT_NOT_FOUND", "Card product not found: " + id));
        return ResponseEntity.ok(ApiResponse.ok(p));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<CardProduct>> create(@RequestBody CardProduct product) {
        CardProduct saved = cardProductRepository.save(product);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(saved));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<CardProduct>> update(@PathVariable UUID id,
                                                            @RequestBody CardProduct req) {
        CardProduct p = cardProductRepository.findById(id)
                .orElseThrow(() -> CbaException.notFound("CARD_PRODUCT_NOT_FOUND", "Card product not found: " + id));
        p.setName(req.getName());
        p.setDefaultDailyLimit(req.getDefaultDailyLimit());
        p.setFeatures(req.getFeatures());
        p.setActive(req.isActive());
        return ResponseEntity.ok(ApiResponse.ok(cardProductRepository.save(p)));
    }
}
