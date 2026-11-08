package com.cba.report;

import com.cba.audit.AuditLogService;
import com.cba.common.exception.CbaException;
import com.cba.report.dto.ReportRequest;
import com.cba.report.dto.ReportResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.Types;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reports (backlog 10). Report SQL runs only in the sandbox ({@link ReportingDatabase}: the read-only
 * {@code cba_reporting} role over the {@code reporting} views), after the shape check
 * ({@link ReportSqlValidator}). Parameters are <b>bound</b> ({@code :name}), typed by their definition —
 * never pasted into the SQL text. ADMINs create reports; a new report is disabled until a different
 * ADMIN enables it (decision D6a).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ReportService {

    private final ReportRepository reportRepository;
    private final AuditLogService auditLogService;
    private final ReportSqlValidator sqlValidator;
    private final ReportingDatabase reportingDatabase;
    /** The same identity {@code created_by} records — so "not the creator" compares like with like. */
    private final org.springframework.data.domain.AuditorAware<String> auditorAware;

    /** {@code :name}, not {@code ::type} casts. */
    private static final Pattern NAMED_PARAM = Pattern.compile("(?<![:\\w]):([A-Za-z_]\\w*)");
    private static final List<String> REPORT_TYPES = List.of("TABLE", "CHART", "SMS", "PENTAHO");
    private static final List<String> PARAMETER_TYPES = List.of("STRING", "DATE", "NUMBER", "BOOLEAN");

    // ── Report CRUD ───────────────────────────────────────────────────────────

    /** Enabled reports; ADMINs also see the disabled ones (waiting to be enabled). */
    @Transactional(readOnly = true)
    public List<ReportResponse> listReports(boolean includeDisabled) {
        List<Report> reports = includeDisabled ? reportRepository.findAll() : reportRepository.findByEnabledTrue();
        return reports.stream().map(ReportResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public ReportResponse getReport(UUID id) {
        return ReportResponse.from(find(id));
    }

    private Report find(UUID id) {
        return reportRepository.findById(id)
                .orElseThrow(() -> CbaException.notFound("Report", id.toString()));
    }

    /**
     * A custom report (ADMIN). The SQL passes the shape check and an {@code EXPLAIN} as the reporting
     * role (so it compiles against the views) before it is saved — <b>disabled</b>, until another ADMIN
     * enables it. Every {@code :name} in the SQL must have a parameter definition.
     */
    @Transactional
    public ReportResponse createReport(ReportRequest req) {
        String name = req.reportName().trim();
        if (reportRepository.findByReportName(name).isPresent()) {
            throw CbaException.badRequest("REPORT_NAME_EXISTS", "A report named '" + name + "' already exists");
        }
        String sql = req.reportSql().trim();
        if (sql.contains("${")) {
            throw CbaException.badRequest("INVALID_REPORT_SQL",
                    "Use :name for parameters (they are bound), not ${name}");
        }
        sqlValidator.validate(sql);

        Report report = new Report();
        report.setReportName(name);
        report.setReportType(oneOf(req.reportType(), REPORT_TYPES, "TABLE", "INVALID_REPORT_TYPE", "reportType"));
        report.setCategory(category(req.reportCategory()));
        report.setDescription(req.description());
        report.setReportSql(sql);
        report.setCoreReport(false);
        report.setEnabled(false);
        List<ReportRequest.Parameter> params = req.reportParameters() == null ? List.of() : req.reportParameters();
        Set<String> defined = new LinkedHashSet<>();
        for (int i = 0; i < params.size(); i++) {
            ReportRequest.Parameter p = params.get(i);
            if (!p.parameterName().matches("[A-Za-z_]\\w*") || !defined.add(p.parameterName())) {
                throw CbaException.badRequest("INVALID_REPORT_PARAMETER",
                        "Parameter name '" + p.parameterName() + "' must be unique, letters, digits and _");
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
        for (String used : sqlParameters(sql)) {
            if (!defined.contains(used)) {
                throw CbaException.badRequest("INVALID_REPORT_PARAMETER",
                        "The SQL uses :" + used + " but no parameter defines it");
            }
        }
        reportingDatabase.explain(sql, bind(report, Map.of(), true));

        Report saved = reportRepository.save(report);
        auditLogService.log("REPORT", saved.getId().toString(), "CREATED", null,
                Map.of("name", saved.getReportName(), "enabled", false));
        return ReportResponse.from(saved);
    }

    /** Enable a report (ADMIN) — not by the ADMIN who created it: {@code 403 SAME_USER}. */
    @Transactional
    public ReportResponse enableReport(UUID id) {
        Report report = find(id);
        String actor = currentUser();
        if (report.getCreatedBy() != null && report.getCreatedBy().equals(actor)) {
            throw CbaException.forbidden("SAME_USER",
                    "A report must be enabled by a different administrator from the one who created it");
        }
        sqlValidator.validate(report.getReportSql());
        report.setEnabled(true);
        Report saved = reportRepository.save(report);
        auditLogService.log("REPORT", saved.getId().toString(), "ENABLED", null,
                Map.of("name", saved.getReportName(), "enabledBy", actor == null ? "" : actor));
        return ReportResponse.from(saved);
    }

    @Transactional
    public void deleteReport(UUID id) {
        Report report = find(id);
        if (report.isCoreReport()) {
            throw CbaException.badRequest("CORE_REPORT", "Core system reports cannot be deleted");
        }
        reportRepository.delete(report);
        auditLogService.log("REPORT", id.toString(), "DELETED", null, Map.of("name", report.getReportName()));
    }

    // ── Report execution ──────────────────────────────────────────────────────

    /**
     * Run a report by name in the sandbox. Parameters are bound and typed by their definitions
     * (DATE {@code yyyy-MM-dd}, NUMBER, BOOLEAN, STRING); unknown query parameters are ignored.
     */
    @Transactional(readOnly = true)
    public ReportingDatabase.ReportRows runReport(String reportName, Map<String, String> params) {
        Report report = reportRepository.findByReportName(reportName)
                .orElseThrow(() -> CbaException.notFound("Report", reportName));
        if (!report.isEnabled()) {
            throw CbaException.badRequest("REPORT_DISABLED", "Report '" + reportName + "' is disabled");
        }
        sqlValidator.validate(report.getReportSql());
        MapSqlParameterSource bound = bind(report, params, false);

        log.info("Running report '{}' with params {}", reportName, params.keySet());
        auditLogService.log("REPORT", report.getId().toString(), "EXECUTED", null, "name=" + reportName);
        return reportingDatabase.query(report.getReportSql(), bound);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** Typed, bound parameter values. {@code dryRun}: every parameter null (for EXPLAIN). */
    private static MapSqlParameterSource bind(Report report, Map<String, String> supplied, boolean dryRun) {
        MapSqlParameterSource source = new MapSqlParameterSource();
        Map<String, ReportParameter> defs = new HashMap<>();
        report.getParameters().forEach(p -> defs.put(p.getParameterName(), p));
        for (ReportParameter def : defs.values()) {
            String type = def.getParameterType() == null ? "STRING" : def.getParameterType().toUpperCase(Locale.ROOT);
            String raw = dryRun ? null : supplied.get(def.getParameterName());
            if (!dryRun && (raw == null || raw.isBlank())) {
                raw = def.getDefaultValue();
                if ((raw == null || raw.isBlank()) && def.isRequired()) {
                    throw CbaException.badRequest("MISSING_REPORT_PARAM",
                            "Required parameter '" + def.getParameterName() + "' is missing");
                }
            }
            String value = raw == null || raw.isBlank() ? null : raw.trim();
            try {
                switch (type) {
                    case "DATE" -> source.addValue(def.getParameterName(), value == null ? null : LocalDate.parse(value), Types.DATE);
                    case "NUMBER" -> source.addValue(def.getParameterName(), value == null ? null : new BigDecimal(value), Types.NUMERIC);
                    case "BOOLEAN" -> source.addValue(def.getParameterName(), value == null ? null : bool(value), Types.BOOLEAN);
                    default -> source.addValue(def.getParameterName(), value, Types.VARCHAR);
                }
            } catch (DateTimeParseException | NumberFormatException e) {
                throw CbaException.badRequest("INVALID_PARAM_VALUE",
                        "Parameter '" + def.getParameterName() + "' must be a " + type
                                + ("DATE".equals(type) ? " (yyyy-MM-dd)" : ""));
            }
        }
        return source;
    }

    private static Boolean bool(String value) {
        if ("true".equalsIgnoreCase(value)) return Boolean.TRUE;
        if ("false".equalsIgnoreCase(value)) return Boolean.FALSE;
        throw new NumberFormatException(value);
    }

    /** The {@code :name} parameters the SQL uses (string literals and {@code ::} casts excluded). */
    static Set<String> sqlParameters(String sql) {
        String code = sql.replaceAll("'(?:[^']|'')*'", "''");
        Set<String> names = new LinkedHashSet<>();
        Matcher m = NAMED_PARAM.matcher(code);
        while (m.find()) names.add(m.group(1));
        return names;
    }

    /**
     * The caller as {@code created_by} records it (preferred_username for Keycloak users, not the JWT
     * {@code sub} that {@code Authentication.getName()} gives) — otherwise the creator could enable
     * their own report.
     */
    private String currentUser() {
        return auditorAware.getCurrentAuditor().orElse(null);
    }

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
}
