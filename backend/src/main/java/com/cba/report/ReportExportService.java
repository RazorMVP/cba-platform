package com.cba.report;

import lombok.RequiredArgsConstructor;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ReportExportService {

    private static final float PAGE_MARGIN = 36f;
    private static final float ROW_HEIGHT = 14f;
    private static final float HEADER_SIZE = 9f;
    private static final float BODY_SIZE = 8f;

    public byte[] exportToCsv(List<Map<String, Object>> rows) {
        if (rows.isEmpty()) return "No data\n".getBytes();

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        PrintWriter pw = new PrintWriter(out);

        List<String> columns = new ArrayList<>(rows.get(0).keySet());
        pw.println(String.join(",", columns));
        for (Map<String, Object> row : rows) {
            List<String> vals = new ArrayList<>();
            for (String col : columns) {
                Object v = row.get(col);
                String s = v == null ? "" : v.toString().replace("\"", "\"\"");
                vals.add("\"" + s + "\"");
            }
            pw.println(String.join(",", vals));
        }
        pw.flush();
        return out.toByteArray();
    }

    public byte[] exportToXlsx(String reportName, List<Map<String, Object>> rows) throws IOException {
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            var sheet = wb.createSheet(reportName.length() > 31 ? reportName.substring(0, 31) : reportName);

            CellStyle headerStyle = wb.createCellStyle();
            headerStyle.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            var headerFont = wb.createFont();
            headerFont.setColor(IndexedColors.WHITE.getIndex());
            headerFont.setBold(true);
            headerStyle.setFont(headerFont);

            List<String> columns = rows.isEmpty() ? List.of() : new ArrayList<>(rows.get(0).keySet());

            var headerRow = sheet.createRow(0);
            for (int i = 0; i < columns.size(); i++) {
                var cell = headerRow.createCell(i);
                cell.setCellValue(columns.get(i));
                cell.setCellStyle(headerStyle);
                sheet.setColumnWidth(i, 4000);
            }

            for (int r = 0; r < rows.size(); r++) {
                var dataRow = sheet.createRow(r + 1);
                Map<String, Object> row = rows.get(r);
                for (int c = 0; c < columns.size(); c++) {
                    Object val = row.get(columns.get(c));
                    var cell = dataRow.createCell(c);
                    if (val instanceof Number n) {
                        cell.setCellValue(n.doubleValue());
                    } else {
                        cell.setCellValue(val == null ? "" : val.toString());
                    }
                }
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            wb.write(out);
            return out.toByteArray();
        }
    }

    public byte[] exportToPdf(String reportName, List<Map<String, Object>> rows) throws IOException {
        try (PDDocument doc = new PDDocument()) {
            List<String> columns = rows.isEmpty() ? List.of() : new ArrayList<>(rows.get(0).keySet());

            float pageWidth = PDRectangle.A4.getWidth();
            float pageHeight = PDRectangle.A4.getHeight();
            float usableWidth = pageWidth - 2 * PAGE_MARGIN;
            float colWidth = columns.isEmpty() ? usableWidth : usableWidth / columns.size();

            // All rows including header
            List<List<String>> allRows = new ArrayList<>();
            allRows.add(columns);
            for (Map<String, Object> row : rows) {
                List<String> r = new ArrayList<>();
                for (String col : columns) {
                    Object v = row.get(col);
                    r.add(v == null ? "" : v.toString());
                }
                allRows.add(r);
            }

            int rowsPerPage = (int) ((pageHeight - 2 * PAGE_MARGIN - 20) / ROW_HEIGHT);
            float y = pageHeight - PAGE_MARGIN - 20;
            PDPage page = new PDPage(PDRectangle.A4);
            doc.addPage(page);
            PDPageContentStream cs = new PDPageContentStream(doc, page);

            // Title
            cs.beginText();
            cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD), 11f);
            cs.newLineAtOffset(PAGE_MARGIN, y);
            cs.showText(reportName);
            cs.endText();
            y -= 16f;

            for (int i = 0; i < allRows.size(); i++) {
                if (i > 0 && (i % rowsPerPage) == 0) {
                    cs.close();
                    page = new PDPage(PDRectangle.A4);
                    doc.addPage(page);
                    cs = new PDPageContentStream(doc, page);
                    y = pageHeight - PAGE_MARGIN;
                }

                List<String> row = allRows.get(i);
                boolean isHeader = (i == 0);
                float fontSize = isHeader ? HEADER_SIZE : BODY_SIZE;
                var fontName = isHeader ? Standard14Fonts.FontName.HELVETICA_BOLD : Standard14Fonts.FontName.HELVETICA;

                for (int c = 0; c < row.size(); c++) {
                    String text = row.get(c);
                    if (text.length() > 20) text = text.substring(0, 17) + "...";
                    cs.beginText();
                    cs.setFont(new PDType1Font(fontName), fontSize);
                    cs.newLineAtOffset(PAGE_MARGIN + c * colWidth, y);
                    cs.showText(text);
                    cs.endText();
                }
                y -= ROW_HEIGHT;
            }

            cs.close();

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            doc.save(out);
            return out.toByteArray();
        }
    }
}
