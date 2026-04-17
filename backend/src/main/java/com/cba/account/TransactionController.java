package com.cba.account;

import com.cba.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/transactions")
@RequiredArgsConstructor
@Tag(name = "Transactions", description = "Global transaction listing across all accounts")
public class TransactionController {

    private final TransactionRepository transactionRepository;

    public record RecentTransactionResponse(
            UUID id,
            String accountNumber,
            String customerName,
            String transactionType,
            BigDecimal amount,
            BigDecimal runningBalance,
            String currencyCode,
            String description,
            String referenceNumber,
            Instant createdAt
    ) {}

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'TELLER')")
    @Operation(summary = "List recent transactions across all accounts (dashboard use)")
    public ResponseEntity<ApiResponse<Page<RecentTransactionResponse>>> listRecent(
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "10") int size) {

        PageRequest pr = PageRequest.of(page, size, Sort.by("transactionDate").descending());
        Page<RecentTransactionResponse> result = transactionRepository.findAllWithAccount(pr)
                .map(tx -> new RecentTransactionResponse(
                        tx.getId(),
                        tx.getAccount().getAccountNumber(),
                        null, // customerName omitted — customer PII requires separate decryption context
                        tx.getTransactionType().name(),
                        tx.getAmount(),
                        tx.getRunningBalance(),
                        tx.getCurrencyCode(),
                        tx.getDescription(),
                        tx.getReferenceNumber(),
                        tx.getTransactionDate()
                ));

        return ResponseEntity.ok(ApiResponse.ok(result,
                ApiResponse.PageMeta.of(result.getNumber(), result.getSize(), result.getTotalElements())));
    }
}
