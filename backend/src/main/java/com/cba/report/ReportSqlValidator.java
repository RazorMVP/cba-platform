package com.cba.report;

import com.cba.common.exception.CbaException;
import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.Statements;
import net.sf.jsqlparser.statement.select.ParenthesedSelect;
import net.sf.jsqlparser.statement.select.PlainSelect;
import net.sf.jsqlparser.statement.select.Select;
import net.sf.jsqlparser.statement.select.WithItem;
import net.sf.jsqlparser.util.TablesNamesFinder;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The shape check on report SQL (backlog 10, decision D5a) — a second layer: the boundary is the
 * read-only {@code cba_reporting} role, which can read nothing but the {@code reporting} views.
 * <ul>
 *   <li>exactly one statement, a {@code SELECT} (or {@code WITH … SELECT});</li>
 *   <li>no {@code SELECT … INTO}, no data-modifying CTE ({@code WITH x AS (DELETE …)});</li>
 *   <li>tables unqualified or {@code reporting.} — never {@code public.}, {@code pg_*} or {@code information_schema};</li>
 *   <li>none of the functions that read server state or switch identity ({@code set_config},
 *       {@code current_setting}, {@code pg_*}, {@code dblink*}, {@code lo_*}, the {@code *_to_xml} family).</li>
 * </ul>
 * SQL the parser can't read is refused ({@code 400 REPORT_SQL_NOT_SUPPORTED}) rather than run unchecked.
 */
@Component
public class ReportSqlValidator {

    /** Function calls refused by name; string literals and comments are stripped before matching. */
    private static final Pattern DENIED_FUNCTION = Pattern.compile(
            "\\b(set_config|current_setting|pg_[a-z0-9_]*|dblink[a-z0-9_]*|lo_[a-z0-9_]*|query_to_xml[a-z_]*|"
                    + "table_to_xml[a-z_]*|cursor_to_xml[a-z_]*|database_to_xml[a-z_]*|schema_to_xml[a-z_]*)\\s*\\(",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern LITERALS_AND_COMMENTS = Pattern.compile(
            "'(?:[^']|'')*'|\\$\\$.*?\\$\\$|--[^\\n]*|/\\*.*?\\*/", Pattern.DOTALL);

    /** Throws 400 unless {@code sql} has the allowed shape. */
    public void validate(String sql) {
        if (sql == null || sql.isBlank()) {
            throw CbaException.badRequest("INVALID_REPORT_SQL", "Report SQL is empty");
        }
        Statements statements;
        try {
            statements = CCJSqlParserUtil.parseStatements(sql);
        } catch (JSQLParserException | RuntimeException e) {
            throw CbaException.badRequest("REPORT_SQL_NOT_SUPPORTED",
                    "Report SQL could not be parsed; use a plain SELECT over the reporting views");
        }
        List<Statement> list = statements.stream().toList();
        if (list.size() != 1) {
            throw CbaException.badRequest("INVALID_REPORT_SQL", "Report SQL must be exactly one statement");
        }
        if (!(list.get(0) instanceof Select select)) {
            throw CbaException.badRequest("INVALID_REPORT_SQL", "Report SQL must be a SELECT");
        }
        if (select instanceof PlainSelect plain && plain.getIntoTables() != null && !plain.getIntoTables().isEmpty()) {
            throw CbaException.badRequest("INVALID_REPORT_SQL", "SELECT … INTO is not allowed");
        }
        if (select.getWithItemsList() != null) {
            for (WithItem<?> item : select.getWithItemsList()) {
                if (!(item.getParenthesedStatement() instanceof ParenthesedSelect)) {
                    throw CbaException.badRequest("INVALID_REPORT_SQL",
                            "A WITH clause may only contain SELECTs (no INSERT/UPDATE/DELETE)");
                }
            }
        }
        for (String table : new TablesNamesFinder<Void>().getTables((Statement) select)) {
            String t = table.toLowerCase(Locale.ROOT).replace("\"", "");
            boolean qualified = t.contains(".");
            if ((qualified && !t.startsWith("reporting.")) || t.startsWith("pg_") || t.contains(".pg_")
                    || t.startsWith("information_schema")) {
                throw CbaException.badRequest("INVALID_REPORT_SQL",
                        "Report SQL may read only the reporting views (found " + table + ")");
            }
        }
        Matcher denied = DENIED_FUNCTION.matcher(LITERALS_AND_COMMENTS.matcher(sql).replaceAll(" "));
        if (denied.find()) {
            throw CbaException.badRequest("INVALID_REPORT_SQL",
                    "Function " + denied.group(1) + "() is not allowed in report SQL");
        }
    }
}
