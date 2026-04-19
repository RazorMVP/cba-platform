package com.cba.system;

import com.cba.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Security Policy", description = "Keycloak realm security settings — brute-force, password rules, session timeouts")
@RestController
@RequestMapping("/api/v1/security-policy")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class SecurityPolicyController {

    private final SecurityPolicyService svc;

    @Operation(summary = "Get current security policy")
    @GetMapping
    public ApiResponse<SecurityPolicyService.SecurityPolicy> getPolicy() {
        return ApiResponse.ok(svc.getPolicy());
    }

    @Operation(summary = "Update security policy — all fields optional; omitted fields are unchanged")
    @PutMapping
    public ApiResponse<SecurityPolicyService.SecurityPolicy> updatePolicy(
            @RequestBody SecurityPolicyService.UpdateSecurityPolicyRequest req) {
        return ApiResponse.ok(svc.updatePolicy(req));
    }
}
