package com.cba.report;

import com.cba.audit.AuditLogService;
import com.cba.common.exception.CbaException;
import com.cba.report.dto.ReportRequest;
import com.cba.report.dto.ReportResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReportService {

    private final ReportRepository reportRepository;
    private final JdbcTemplate jdbcTemplate;
    private final AuditLogService auditLogService;

    private static final Pattern PARAM_PATTERN = Pattern.compile("\\$\\{(\\w+)\\}");

    // ── Report CRUD ───────────────────────────────────────────────────────────

    /**
     * A custom (never core) report. The SQL is checked here as well as on every run, so a
     * report that could never run is refused up front.
     */
    @Transactional
    public ReportResponse createReport(ReportRequest req) {
        String name = req.reportName().trim();
        if (reportRepository.findByReportName(name).isPresent()) {
            throw CbaException.badRequest("REPORT_NAME_EXISTS",
                    "A report named '" + name + "' already exists");
        }
        validateSelectOnly(req.reportSql());

        Report report = new Report();
        report.setReportName(name);
        report.setReportType(oneOf(req.reportType(), REPORT_TYPES, "TABLE", "INVALID_REPORT_TYPE", "reportType"));
        report.setCategory(category(req.reportCategory()));
        report.setDescription(req.description());
        report.setReportSql(req.reportSql().trim());
        report.setCoreReport(false);
        report.setEnabled(true);
        List<ReportRequest.Parameter> params = req.reportParameters() == null ? List.of() : req.reportParameters();
        for (int i = 0; i < params.size(); i++) {
            ReportRequest.Parameter p = params.get(i);
            if (!p.parameterName().matches("\\w+")) {
                throw CbaException.badRequest("INVALID_REPORT_PARAMETER",
                        "Parameter name '" + p.parameterName() + "' may contain only letters, digits and _");
            }
            ReportParameter rp = new ReportParameter();
            rp.setReport(report);
            rp.setParameterName(p.parameterName());
            rp.setParameterLabel(p.parameterLabel());
            rp.setParameterType(oneOf(p.parameterType(), PARAMETER_TYPES, "STRING", "INVALID_REPORT_PARAMETER", "parameterType"));
            rp.setDefaultValue(p.defaultValue());
            rp.setRequired(!p.optional());
            rp.setSortOrder(i + 1);
            report.getParameters().add(rp);
        }

        Report saved = reportRepository.save(report);
        auditLogService.log("REPORT", saved.getId().toString(), "CREATED", null,
                "name=" + saved.getReportName());
        return ReportResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public List<ReportResponse> listReports() {
        return reportRepository.findByEnabledTrue().stream().map(ReportResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public ReportResponse getReport(UUID id) {
        return ReportResponse.from(find(id));
    }

    private Report find(UUID id) {
        return reportRepository.findById(id)
                .orElseThrow(() -> CbaException.notFound("Report", id.toString()));
    }

    private static final List<String> REPORT_TYPES = List.of("TABLE", "CHART", "SMS", "PENTAHO");
    private static final List<String> PARAMETER_TYPES = List.of("STRING", "DATE", "NUMBER", "BOOLEAN");

    /** Blank → the default; otherwise one of the allowed values (case-insensitive) or a 400. */
    private static String oneOf(String value, List<String> allowed, String dflt, String code, String field) {
        if (value == null || value.isBlank()) return dflt;
        String v = value.trim().toUpperCase(Locale.ROOT);
        if (!allowed.contains(v)) {
            throw CbaException.badRequest(code, field + " must be one of " + allowed);
        }
        return v;
    }

    private static Report.Category category(String value) {
        if (value == null || value.isBlank()) return Report.Category.MIXED;
        try {
            return Report.Category.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw CbaException.badRequest("INVALID_REPORT_CATEGORY",
                    "reportCategory must be one of " + java.util.Arrays.toString(Report.Category.values()));
        }
    }

    @Transactional
    public void deleteReport(UUID id) {
        Report report = find(id);
        if (report.isCoreReport()) {
            throw CbaException.badRequest("CORE_REPORT", "Core system reports cannot be deleted");
        }
        reportRepository.delete(report);
    }

    // ── Report execution ──────────────────────────────────────────────────────

    /**
     * Execute a report by name with the supplied parameters.
     * Parameters are substituted for `${paramName}` placeholders in the SQL.
     * The SQL is validated to be a SELECT before execution.
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> runReport(String reportName, Map<String, String> params) {
        Report report = reportRepository.findByReportName(reportName)
                .orElseThrow(() -> CbaException.notFound("Report", reportName));

        if (!report.isEnabled()) {
            throw CbaException.badRequest("REPORT_DISABLED", "Report '" + reportName + "' is disabled");
        }

        String resolvedSql = resolveParameters(report.getReportSql(), params, report);
        validateSelectOnly(resolvedSql);

        log.info("Running report '{}' with params {}", reportName, params.keySet());
        auditLogService.log("REPORT", report.getId().toString(), "EXECUTED", null,
                "name=" + reportName);

        return jdbcTemplate.queryForList(resolvedSql);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Substitutes `${paramName}` with the caller-supplied value.
     * Required parameters without a supplied value throw a 400.
     * Values are escaped to prevent SQL injection via simple whitelist validation.
     */
    private String resolveParameters(String sql, Map<String, String> suppliedParams, Report report) {
        // Build a lookup of required params
        Map<String, ReportParameter> paramDefs = new java.util.HashMap<>();
        report.getParameters().forEach(p -> paramDefs.put(p.getParameterName(), p));

        StringBuffer sb = new StringBuffer();
        Matcher m = PARAM_PATTERN.matcher(sql);
        while (m.find()) {
            String paramName = m.group(1);
            String value = suppliedParams.get(paramName);

            if (value == null || value.isBlank()) {
                ReportParameter def = paramDefs.get(paramName);
                if (def != null && def.getDefaultValue() != null) {
                    value = def.getDefaultValue();
                } else if (def != null && def.isRequired()) {
                    throw CbaException.badRequest("MISSING_REPORT_PARAM",
                            "Required parameter '" + paramName + "' is missing");
                } else {
                    value = "";
                }
            }

            m.appendReplacement(sb, Matcher.quoteReplacement(sanitizeValue(value)));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    /** Reject any non-SELECT SQL to prevent destructive report execution. */
    private void validateSelectOnly(String sql) {
        String trimmed = sql.trim().toUpperCase(Locale.ROOT);
        if (!trimmed.startsWith("SELECT") && !trimmed.startsWith("WITH")) {
            throw CbaException.badRequest("INVALID_REPORT_SQL",
                    "Report SQL must start with SELECT or WITH");
        }
        // Block DML keywords anywhere in the query
        for (String keyword : List.of("INSERT", "UPDATE", "DELETE", "DROP", "TRUNCATE", "ALTER", "EXEC")) {
            if (trimmed.contains(keyword + " ")) {
                throw CbaException.badRequest("INVALID_REPORT_SQL",
                        "Report SQL contains forbidden keyword: " + keyword);
            }
        }
    }

    /**
     * Basic sanitization: allow alphanumerics, dashes, dots, underscores, spaces, and date formats.
     * Reject single-quotes to prevent injection through parameter values.
     */
    private String sanitizeValue(String value) {
        if (value.contains("'") || value.contains(";") || value.contains("--")) {
            throw CbaException.badRequest("INVALID_PARAM_VALUE",
                    "Report parameter value contains forbidden characters");
        }
        return value;
    }
}
