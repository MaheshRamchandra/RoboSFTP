package org.robo.core;



import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;

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
                    // keep your replacement behaviour if required
                    encoded = encoded.replaceAll("==", "=");
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

