package com.cba.report.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * A custom report (Mifos field names). The SQL must be a single SELECT/WITH query; it may
 * use {@code ${paramName}} placeholders, one per entry in {@code reportParameters}.
 *
 * @param reportType     TABLE (default), CHART, SMS or PENTAHO
 * @param reportCategory LOAN, SAVINGS, TELLER, CUSTOMER, ACCOUNTING or MIXED (default)
 */
public record ReportRequest(
        @NotBlank @Size(max = 100) String reportName,
        String reportType,
        String reportCategory,
        String description,
        @NotBlank String reportSql,
        List<@Valid Parameter> reportParameters
) {
    /** {@code optional} defaults to false: a parameter is required unless it says otherwise. */
    @Schema(name = "ReportParameterRequest")
    public record Parameter(
            @NotBlank @Size(max = 100) String parameterName,
            @Size(max = 100) String parameterLabel,
            String parameterType,
            @Size(max = 255) String defaultValue,
            boolean optional
    ) {}
}
