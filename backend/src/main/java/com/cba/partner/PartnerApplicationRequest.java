package com.cba.partner;

public record PartnerApplicationRequest(
        String businessType,
        String useCase,
        String estimatedMonthlyCalls,
        String website,
        String technicalContact,
        String complianceNotes
) {}
