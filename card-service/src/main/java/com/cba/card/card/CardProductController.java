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
        checkWalletLimits(product.getMaxWalletBalance(), product.getDailyWalletLoad(), product.getCardType());
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
        checkWalletLimits(req.getMaxWalletBalance(), req.getDailyWalletLoad(), p.getCardType());
        p.setMaxWalletBalance(req.getMaxWalletBalance());
        p.setDailyWalletLoad(req.getDailyWalletLoad());
        return ResponseEntity.ok(ApiResponse.ok(cardProductRepository.save(p)));
    }

    /** Wallet limits (V22) are positive amounts, for prepaid products only; null = the currency default. */
    private static void checkWalletLimits(BigDecimal maxBalance, BigDecimal dailyLoad, CardType type) {
        for (BigDecimal v : new BigDecimal[] {maxBalance, dailyLoad}) {
            if (v != null && v.signum() <= 0) {
                throw CbaException.badRequest("INVALID_WALLET_LIMIT", "Wallet limits must be positive (leave blank for the currency default)");
            }
        }
        if ((maxBalance != null || dailyLoad != null) && type != CardType.PREPAID) {
            throw CbaException.badRequest("INVALID_WALLET_LIMIT", "Only prepaid products have wallet limits");
        }
    }
}
