package com.cba.user;

import com.cba.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Tag(name = "Two-Factor Authentication", description = "OTP token generation and verification for platform users — 6-digit codes with 10-minute expiry delivered via EMAIL or SMS")
@RestController
@RequiredArgsConstructor
public class TwoFactorController {

    private final TwoFactorService twoFactorService;

    @Operation(summary = "Generate a 2FA OTP token for a user (EMAIL or SMS delivery)")
    @PostMapping("/api/v1/twofactor/generate")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN','TELLER')")
    public ApiResponse<TwoFactorToken> generate(
            @RequestBody TwoFactorService.GenerateTokenRequest req) {
        return ApiResponse.ok(twoFactorService.generateToken(req));
    }

    @Operation(summary = "Verify a 2FA OTP token — marks it used on success")
    @PostMapping("/api/v1/twofactor/verify")
    @PreAuthorize("hasAnyRole('ADMIN','TELLER','CUSTOMER')")
    public ApiResponse<TwoFactorToken> verify(
            @RequestBody TwoFactorService.VerifyTokenRequest req) {
        return ApiResponse.ok(twoFactorService.verifyToken(req));
    }

    @Operation(summary = "List all 2FA tokens for a user (ADMIN/TELLER only)")
    @GetMapping("/api/v1/users/{userId}/twofactor")
    @PreAuthorize("hasAnyRole('ADMIN','TELLER')")
    public ApiResponse<List<TwoFactorToken>> list(@PathVariable UUID userId) {
        return ApiResponse.ok(twoFactorService.listTokens(userId));
    }
}
