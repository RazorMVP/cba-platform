package com.cba.report.dto;

import com.cba.report.Report;
import com.cba.report.ReportParameter;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * A report definition, in Mifos's field names ({@code reportCategory}, {@code useReport},
 * {@code reportParameters}). Never the entity: {@code Report.parameters} and
 * {@code ReportParameter.report} point at each other, so serialising the entity recursed
 * until Jackson's nesting limit and every list call failed.
 */
public record ReportResponse(
        UUID id,
        String reportName,
        String reportType,
        Report.Category reportCategory,
        String description,
        String reportSql,
        boolean coreReport,
        boolean useReport,
        List<Parameter> reportParameters
) {
    @Schema(name = "ReportParameterResponse")
    public record Parameter(
            UUID id,
            String parameterName,
            String parameterLabel,
            String parameterType,
            String defaultValue,
            boolean optional,
            int sortOrder
    ) {
        static Parameter from(ReportParameter p) {
            return new Parameter(p.getId(), p.getParameterName(), p.getParameterLabel(), p.getParameterType(),
                    p.getDefaultValue(), !p.isRequired(), p.getSortOrder());
        }
    }

    public static ReportResponse from(Report r) {
        return new ReportResponse(r.getId(), r.getReportName(), r.getReportType(), r.getCategory(),
                r.getDescription(), r.getReportSql(), r.isCoreReport(), r.isEnabled(),
                r.getParameters().stream()
                        .sorted(Comparator.comparingInt(ReportParameter::getSortOrder))
                        .map(Parameter::from).toList());
    }
}
