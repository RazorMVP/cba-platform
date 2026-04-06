package com.cba.customer.dto;

import com.cba.customer.KycStatus;
import jakarta.validation.constraints.NotNull;

public record UpdateKycStatusRequest(
        @NotNull(message = "KYC status is required")
        KycStatus kycStatus,
        String notes
) {}
