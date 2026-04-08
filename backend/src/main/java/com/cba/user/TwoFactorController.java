package com.cba.user;

import com.cba.common.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class TwoFactorController {

    private final TwoFactorService twoFactorService;

    @PostMapping("/api/v1/twofactor/generate")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN','TELLER')")
    public ApiResponse<TwoFactorToken> generate(
            @RequestBody TwoFactorService.GenerateTokenRequest req) {
        return ApiResponse.ok(twoFactorService.generateToken(req));
    }

    @PostMapping("/api/v1/twofactor/verify")
    @PreAuthorize("hasAnyRole('ADMIN','TELLER','CUSTOMER')")
    public ApiResponse<TwoFactorToken> verify(
            @RequestBody TwoFactorService.VerifyTokenRequest req) {
        return ApiResponse.ok(twoFactorService.verifyToken(req));
    }

    @GetMapping("/api/v1/users/{userId}/twofactor")
    @PreAuthorize("hasAnyRole('ADMIN','TELLER')")
    public ApiResponse<List<TwoFactorToken>> list(@PathVariable UUID userId) {
        return ApiResponse.ok(twoFactorService.listTokens(userId));
    }
}
