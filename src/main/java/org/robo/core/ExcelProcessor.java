package org.robo.core;



import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.Locale;

public class ExcelProcessor {

    private static final String DATE_FORMAT = "yyyy-MM-dd";

    // List sheet names
    public static List<String> listSheetNames(File excel) throws IOException {
        try (FileInputStream fis = new FileInputStream(excel);
             Workbook wb = new XSSFWorkbook(fis)) {
            List<String> names = new ArrayList<>();
            for (int i = 0; i < wb.getNumberOfSheets(); i++) names.add(wb.getSheetName(i));
            return names;
        }
    }

    // List scenarios: read first column values excluding header row (row 0)
    public static List<String> listScenarios(File excel, String sheetName) throws IOException {
        try (FileInputStream fis = new FileInputStream(excel);
             Workbook wb = new XSSFWorkbook(fis)) {
            Sheet s = wb.getSheet(sheetName);
            if (s == null) return Collections.emptyList();
            List<String> scen = new ArrayList<>();
            int firstRow = s.getFirstRowNum();
            for (int r = firstRow + 1; r <= s.getLastRowNum(); r++) {
                Row row = s.getRow(r);
                if (row == null) continue;
                String first = getCellValue(row.getCell(0));
                if (first == null || first.isBlank()) continue;
                scen.add(first);
            }
            return scen;
        }
    }

    // Load encode fields from a text file (one field per line)
    public static Set<String> loadEncodeFields(File file) throws IOException {
        Set<String> fields = new HashSet<>();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (!line.isEmpty()) fields.add(line);
            }
        }
        return fields;
    }

    public static ExcelTemplate loadTemplate(File excelFile, String sheetName) throws IOException {
        Objects.requireNonNull(excelFile, "Excel file is required");
        Objects.requireNonNull(sheetName, "Sheet name is required");
        try (FileInputStream fis = new FileInputStream(excelFile);
             Workbook wb = new XSSFWorkbook(fis)) {

            Sheet sheet = wb.getSheet(sheetName);
            if (sheet == null) throw new IllegalArgumentException("Sheet not found: " + sheetName);

            Row headerRow = sheet.getRow(0);
            if (headerRow == null) throw new IllegalStateException("Missing header row");

            int lastCol = Math.max(1, headerRow.getLastCellNum());
            List<String> headers = new ArrayList<>(lastCol);
            for (int c = 0; c < lastCol; c++) {
                headers.add(getCellValue(headerRow.getCell(c)));
            }

            List<ColumnInfo> columns = new ArrayList<>();
            for (int c = 1; c < lastCol; c++) {
                String rawHeader = headers.get(c);
                if (rawHeader == null) rawHeader = "";
                boolean mandatory = rawHeader.startsWith("M##");
                String cleanName = mandatory ? rawHeader.substring(3) : rawHeader;
                cleanName = cleanName == null ? "" : cleanName.trim();
                columns.add(new ColumnInfo(c, rawHeader, cleanName, mandatory));
            }

            return new ExcelTemplate(sheetName, headers, columns);
        }
    }

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("uuuu-MM-dd");

    public static List<DecodedColumn> decodeGeneratedString(String generatedString, ExcelTemplate template, Set<String> encodeFields) {
        Objects.requireNonNull(template, "Template is required");
        List<String> tokens = Arrays.asList((generatedString == null ? "" : generatedString).split("\\|", -1));
        List<DecodedColumn> decoded = new ArrayList<>();
        List<ColumnInfo> columns = template.getColumns();

        if (tokens.size() > columns.size()) {
            throw new IllegalArgumentException("Generated string contains more values than the template defines");
        }

        for (int i = 0; i < columns.size(); i++) {
            ColumnInfo column = columns.get(i);
            String token = i < tokens.size() ? tokens.get(i) : "";
            boolean isEncoded = encodeFields != null && encodeFields.contains(column.getCleanName());
            if (!isEncoded && !token.isEmpty() && (token.startsWith(" ") || token.endsWith(" "))) {
                throw new IllegalArgumentException("Column '" + column.getCleanName()
                        + "' contains unexpected whitespace in token '" + token
                        + "'. Prefix: '" + buildPrefixUpToToken(tokens, i) + "'");
            }
            if (!isEncoded && (token.matches(".*\\s-.*") || token.matches(".*-\\s.*"))) {
                throw new IllegalArgumentException("Column '" + column.getCleanName()
                        + "' contains misplaced whitespace near '-' in token '" + token
                        + "'. Prefix: '" + buildPrefixUpToToken(tokens, i) + "'");
            }
            String decodedValue = token;
            if (encodeFields != null && encodeFields.contains(column.getCleanName())) {
                try {
                    decodedValue = new String(Base64.getDecoder().decode(token), StandardCharsets.UTF_8);
                } catch (IllegalArgumentException e) {
                    throw new IllegalArgumentException("Failed to decode column '" + column.getCleanName()
                            + "' using token '" + token + "'. Prefix: '" + buildPrefixUpToToken(tokens, i) + "'", e);
                }
            }
            if (!decodedValue.isEmpty() && isDateColumn(column)) {
                validateDateFormat(decodedValue, column, tokens, i);
            }
            decoded.add(new DecodedColumn(column, token, decodedValue));
        }

        if (tokens.size() < columns.size()) {
            for (int i = tokens.size(); i < columns.size(); i++) {
                ColumnInfo column = columns.get(i);
                if (column.isMandatory()) {
                    throw new IllegalArgumentException("Missing data for mandatory column: " + column.getCleanName());
                }
            }
        }

        return decoded;
    }

    private static void validateDateFormat(String value, ColumnInfo column, List<String> tokens, int index) {
        try {
            LocalDate.parse(value, DATE_FORMATTER);
        } catch (DateTimeParseException ex) {
            throw new IllegalArgumentException("Column '" + column.getCleanName()
                    + "' expects yyyy-MM-dd, but got '" + value
                    + "'. Prefix: '" + buildPrefixUpToToken(tokens, index) + "'", ex);
        }
    }

    private static boolean isDateColumn(ColumnInfo column) {
        if (column == null || column.getCleanName() == null) return false;
        String lower = column.getCleanName().toLowerCase(Locale.ROOT);
        return lower.contains("date") || lower.contains("dob") || lower.contains("expiry") || lower.contains("valid");
    }

    private static String buildPrefixUpToToken(List<String> tokens, int index) {
        if (tokens == null || tokens.isEmpty() || index < 0) return "";
        int end = Math.min(index, tokens.size() - 1);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i <= end; i++) {
            if (i > 0) sb.append("|");
            sb.append(tokens.get(i));
        }
        return sb.toString();
    }

    public static void appendDecodedRow(File excelFile, ExcelTemplate template,
                                        String scenarioName, List<DecodedColumn> decodedColumns) throws IOException {
        Objects.requireNonNull(excelFile, "Excel file is required");
        Objects.requireNonNull(template, "Template is required");

        Workbook workbook = null;
        try (FileInputStream fis = new FileInputStream(excelFile)) {
            workbook = new XSSFWorkbook(fis);
        }

        try {
            Sheet sheet = workbook.getSheet(template.getSheetName());
            if (sheet == null) throw new IllegalArgumentException("Sheet not found: " + template.getSheetName());

            int newRowIndex = sheet.getLastRowNum() + 1;
            Row scenarioRow = sheet.createRow(newRowIndex);
            scenarioRow.createCell(0).setCellValue(scenarioName);
            for (DecodedColumn decoded : decodedColumns) {
                scenarioRow.createCell(decoded.getColumn().getColumnIndex())
                        .setCellValue(decoded.getDecodedValue());
            }

            try (FileOutputStream fos = new FileOutputStream(excelFile)) {
                workbook.write(fos);
            }
        } finally {
            if (workbook != null) {
                workbook.close();
            }
        }
    }

    public static final class ExcelTemplate {
        private final String sheetName;
        private final List<String> headerValues;
        private final List<ColumnInfo> columns;

        public ExcelTemplate(String sheetName, List<String> headerValues, List<ColumnInfo> columns) {
            this.sheetName = sheetName;
            this.headerValues = Collections.unmodifiableList(new ArrayList<>(headerValues));
            this.columns = Collections.unmodifiableList(new ArrayList<>(columns));
        }

        public String getSheetName() {
            return sheetName;
        }

        public List<String> getHeaderValues() {
            return headerValues;
        }

        public List<ColumnInfo> getColumns() {
            return columns;
        }
    }

    public static final class ColumnInfo {
        private final int columnIndex;
        private final String rawHeader;
        private final String cleanName;
        private final boolean mandatory;

        public ColumnInfo(int columnIndex, String rawHeader, String cleanName, boolean mandatory) {
            this.columnIndex = columnIndex;
            this.rawHeader = rawHeader == null ? "" : rawHeader;
            this.cleanName = cleanName == null ? "" : cleanName;
            this.mandatory = mandatory;
        }

        public int getColumnIndex() {
            return columnIndex;
        }

        public String getRawHeader() {
            return rawHeader;
        }

        public String getCleanName() {
            return cleanName;
        }

        public boolean isMandatory() {
            return mandatory;
        }
    }

    public static final class DecodedColumn {
        private final ColumnInfo column;
        private final String token;
        private final String decodedValue;

        public DecodedColumn(ColumnInfo column, String token, String decodedValue) {
            this.column = column;
            this.token = token == null ? "" : token;
            this.decodedValue = decodedValue == null ? "" : decodedValue;
        }

        public ColumnInfo getColumn() {
            return column;
        }

        public String getToken() {
            return token;
        }

        public String getDecodedValue() {
            return decodedValue;
        }
    }

    // Main processing: header contains M## prefix for mandatory columns.
    // It returns a single string where values (or placeholders) are separated by '|'
    public static String processExcel(File excelFile, String sheetName, String scenarioName, Set<String> encodeFields) throws IOException {
        try (FileInputStream fis = new FileInputStream(excelFile);
             Workbook wb = new XSSFWorkbook(fis)) {

            Sheet sheet = wb.getSheet(sheetName);
            if (sheet == null) throw new IllegalArgumentException("Sheet not found: " + sheetName);

            Row headerRow = sheet.getRow(0);
            if (headerRow == null) throw new IllegalStateException("Missing header row");

            // Find scenario row
            Row scenarioRow = null;
            for (int r = sheet.getFirstRowNum() + 1; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                if (row == null) continue;
                String first = getCellValue(row.getCell(0));
                if (scenarioName.equalsIgnoreCase(first)) {
                    scenarioRow = row;
                    break;
                }
            }
            if (scenarioRow == null) throw new IllegalStateException("Scenario '" + scenarioName + "' not found");

            StringBuilder sb = new StringBuilder();

            // loop columns starting from 1 (assuming col 0 is Name label)
            int lastCol = Math.max(1, headerRow.getLastCellNum());
            for (int c = 1; c < lastCol; c++) {
                String rawHeader = getCellValue(headerRow.getCell(c));
                if (rawHeader == null) rawHeader = "";
                boolean mandatory = rawHeader.startsWith("M##");
                String cleanName = mandatory ? rawHeader.substring(3) : rawHeader;

                if (!mandatory) {
                    // maintain delimiter position
                    sb.append("|");
                    continue;
                }

                String value = getCellValue(scenarioRow.getCell(c));
                if ("NRIC/FIN".equalsIgnoreCase(cleanName)) {
                    value = Utils.generateNRIC();
                }

                if (encodeFields.contains(cleanName)) {
                    String encoded = Base64.getEncoder().encodeToString(value.trim().getBytes(StandardCharsets.UTF_8));
                    sb.append(encoded);
                } else {
                    sb.append(value);
                }

                sb.append("|");
            }

            // remove trailing delimiter if present
            if (sb.length() > 0 && sb.charAt(sb.length() - 1) == '|') sb.setLength(sb.length() - 1);
            return sb.toString();
        }
    }

    // robust cell reader: handles formula, dates, numbers, boolean
    public static String getCellValue(Cell cell) {
        if (cell == null) return "";
        DataFormatter df = new DataFormatter();
        try {
            if (cell.getCellType() == CellType.FORMULA) {
                FormulaEvaluator ev = cell.getSheet().getWorkbook().getCreationHelper().createFormulaEvaluator();
                CellValue cv = ev.evaluate(cell);
                if (cv == null) return "";
                switch (cv.getCellType()) {
                    case STRING: return cv.getStringValue().trim();
                    case BOOLEAN: return String.valueOf(cv.getBooleanValue());
                    case NUMERIC:
                        if (DateUtil.isCellDateFormatted(cell)) {
                            return new SimpleDateFormat(DATE_FORMAT).format(DateUtil.getJavaDate(cv.getNumberValue()));
                        } else {
                            return df.formatCellValue(cell, ev).trim();
                        }
                    default: return "";
                }
            } else if (cell.getCellType() == CellType.NUMERIC) {
                if (DateUtil.isCellDateFormatted(cell)) {
                    return new SimpleDateFormat(DATE_FORMAT).format(cell.getDateCellValue());
                } else {
                    return df.formatCellValue(cell).trim();
                }
            } else {
                return df.formatCellValue(cell).trim();
            }
        } catch (Exception e) {
            return df.formatCellValue(cell).trim();
        }
    }
}
