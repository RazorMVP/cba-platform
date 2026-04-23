package com.cba.report;

import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

@DisplayName("ReportExportService — unit tests")
class ReportExportServiceTest {

    private final ReportExportService service = new ReportExportService();

    private Map<String, Object> row(Object... keyValues) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            m.put(keyValues[i].toString(), keyValues[i + 1]);
        }
        return m;
    }

    @Nested
    @DisplayName("CSV Export")
    class CsvExport {

        @Test
        @DisplayName("exportToCsv returns 'No data' marker for empty list")
        void emptyList_returnsNoData() {
            byte[] bytes = service.exportToCsv(List.of());
            assertThat(new String(bytes, StandardCharsets.UTF_8)).contains("No data");
        }

        @Test
        @DisplayName("exportToCsv writes header row from map keys")
        void writesHeaderRow() {
            List<Map<String, Object>> rows = List.of(
                row("name", "Alice", "amount", 1000)
            );
            String csv = new String(service.exportToCsv(rows), StandardCharsets.UTF_8);
            assertThat(csv).contains("name");
            assertThat(csv).contains("amount");
        }

        @Test
        @DisplayName("exportToCsv writes data rows with quoted values")
        void writesDataRows() {
            List<Map<String, Object>> rows = List.of(
                row("name", "Alice Smith", "amount", 5000)
            );
            String csv = new String(service.exportToCsv(rows), StandardCharsets.UTF_8);
            assertThat(csv).contains("\"Alice Smith\"");
            assertThat(csv).contains("\"5000\"");
        }

        @Test
        @DisplayName("exportToCsv escapes double-quotes inside values")
        void escapesDoubleQuotes() {
            List<Map<String, Object>> rows = List.of(
                row("description", "He said \"hello\"")
            );
            String csv = new String(service.exportToCsv(rows), StandardCharsets.UTF_8);
            // CSV standard: double-quote escaped as two double-quotes
            assertThat(csv).contains("He said \"\"hello\"\"");
        }

        @Test
        @DisplayName("exportToCsv handles null values as empty string")
        void handlesNullValues() {
            List<Map<String, Object>> rows = List.of(
                row("name", "Alice", "notes", null)
            );
            String csv = new String(service.exportToCsv(rows), StandardCharsets.UTF_8);
            assertThat(csv).contains("\"\"");  // empty quoted cell for null
        }

        @Test
        @DisplayName("exportToCsv writes multiple data rows")
        void multipleRows() {
            List<Map<String, Object>> rows = List.of(
                row("id", 1, "name", "Alice"),
                row("id", 2, "name", "Bob"),
                row("id", 3, "name", "Carol")
            );
            String csv = new String(service.exportToCsv(rows), StandardCharsets.UTF_8);
            String[] lines = csv.trim().split("\n");
            assertThat(lines).hasSize(4); // 1 header + 3 data rows
        }
    }

    @Nested
    @DisplayName("XLSX Export")
    class XlsxExport {

        @Test
        @DisplayName("exportToXlsx produces valid XLSX bytes")
        void producesValidXlsx() throws IOException {
            List<Map<String, Object>> rows = List.of(
                row("name", "Alice", "balance", 10000.00)
            );
            byte[] bytes = service.exportToXlsx("Test Report", rows);
            assertThat(bytes).isNotEmpty();
            // Validate it can be parsed back as a workbook
            try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
                assertThat(wb.getNumberOfSheets()).isEqualTo(1);
                assertThat(wb.getSheetAt(0).getRow(0).getCell(0).getStringCellValue())
                    .isEqualTo("name");
                assertThat(wb.getSheetAt(0).getRow(1).getCell(0).getStringCellValue())
                    .isEqualTo("Alice");
            }
        }

        @Test
        @DisplayName("exportToXlsx handles empty row list")
        void emptyRows_createsHeaderOnlySheet() throws IOException {
            byte[] bytes = service.exportToXlsx("Empty Report", List.of());
            assertThat(bytes).isNotEmpty();
            try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
                assertThat(wb.getSheetAt(0).getLastRowNum()).isEqualTo(0);
            }
        }

        @Test
        @DisplayName("exportToXlsx truncates sheet name to 31 chars")
        void truncatesLongSheetName() throws IOException {
            String longName = "A".repeat(50);
            byte[] bytes = service.exportToXlsx(longName, List.of());
            try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
                assertThat(wb.getSheetAt(0).getSheetName()).hasSize(31);
            }
        }

        @Test
        @DisplayName("exportToXlsx writes numeric cells for Number values")
        void numericCells() throws IOException {
            List<Map<String, Object>> rows = List.of(
                row("amount", 9999.99, "count", 42)
            );
            byte[] bytes = service.exportToXlsx("Numbers", rows);
            try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
                double amount = wb.getSheetAt(0).getRow(1).getCell(0).getNumericCellValue();
                assertThat(amount).isEqualTo(9999.99);
            }
        }
    }

    @Nested
    @DisplayName("PDF Export")
    class PdfExport {

        @Test
        @DisplayName("exportToPdf produces non-empty PDF bytes")
        void producesNonEmptyPdf() throws IOException {
            List<Map<String, Object>> rows = List.of(
                row("customer", "Alice", "loan_status", "ACTIVE")
            );
            byte[] bytes = service.exportToPdf("Loan Report", rows);
            assertThat(bytes).isNotEmpty();
            // PDF magic bytes: %PDF
            assertThat(new String(bytes, 0, 4, StandardCharsets.ISO_8859_1)).isEqualTo("%PDF");
        }

        @Test
        @DisplayName("exportToPdf handles empty row list")
        void emptyRows_producesValidPdf() throws IOException {
            byte[] bytes = service.exportToPdf("Empty Report", List.of());
            assertThat(bytes).isNotEmpty();
            assertThat(new String(bytes, 0, 4, StandardCharsets.ISO_8859_1)).isEqualTo("%PDF");
        }

        @Test
        @DisplayName("exportToPdf handles many rows without exception")
        void manyRows_noException() throws IOException {
            List<Map<String, Object>> rows = new java.util.ArrayList<>();
            for (int i = 0; i < 100; i++) {
                rows.add(row("id", i, "name", "Customer " + i, "amount", i * 1000));
            }
            assertThatCode(() -> service.exportToPdf("Large Report", rows)).doesNotThrowAnyException();
        }
    }
}
