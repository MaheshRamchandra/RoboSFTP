package org.robo.rag;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Comparator;
import java.util.List;

public final class RagExcelWriter {
    private RagExcelWriter() {
    }

    public static void writeSpec(File workbookFile, String sheetName, List<RagFieldRecord> records) throws IOException {
        if (workbookFile == null) throw new IllegalArgumentException("Workbook file is required");
        if (sheetName == null || sheetName.isBlank()) throw new IllegalArgumentException("Sheet name is required");
        if (records == null || records.isEmpty()) throw new IllegalArgumentException("No records to write");

        Workbook wb = workbookFile.exists() ? loadWorkbook(workbookFile) : new XSSFWorkbook();
        try {
            int existingIndex = wb.getSheetIndex(sheetName);
            if (existingIndex >= 0) {
                wb.removeSheetAt(existingIndex);
            }
            Sheet sheet = wb.createSheet(sheetName);

            writeHeaderRow(sheet);
            writeRecords(sheet, records);

            if (workbookFile.getParentFile() != null && !workbookFile.getParentFile().exists()) {
                if (!workbookFile.getParentFile().mkdirs()) {
                    throw new IOException("Failed to create parent directory: " + workbookFile.getParent());
                }
            }

            try (FileOutputStream fos = new FileOutputStream(workbookFile)) {
                wb.write(fos);
            }
        } finally {
            wb.close();
        }
    }

    private static Workbook loadWorkbook(File workbookFile) throws IOException {
        try (FileInputStream fis = new FileInputStream(workbookFile)) {
            return new XSSFWorkbook(fis);
        }
    }

    private static void writeHeaderRow(Sheet sheet) {
        Row header = sheet.createRow(0);
        header.createCell(0).setCellValue("position");
        header.createCell(1).setCellValue("excel_header");
        header.createCell(2).setCellValue("mandatory");
        header.createCell(3).setCellValue("datatype");
        header.createCell(4).setCellValue("format");
        header.createCell(5).setCellValue("description");
        header.createCell(6).setCellValue("dummy_value");
    }

    private static void writeRecords(Sheet sheet, List<RagFieldRecord> records) {
        List<RagFieldRecord> sorted = records.stream()
                .sorted(Comparator.comparingInt(RagFieldRecord::getPosition))
                .toList();
        int rowIdx = 1;
        for (RagFieldRecord rec : sorted) {
            Row row = sheet.createRow(rowIdx++);
            row.createCell(0).setCellValue(rec.getPosition());
            row.createCell(1).setCellValue(safe(rec.getExcelHeader()));
            row.createCell(2).setCellValue(rec.isMandatory());
            row.createCell(3).setCellValue(safe(rec.getDatatype()));
            row.createCell(4).setCellValue(safe(rec.getFormat()));
            row.createCell(5).setCellValue(safe(rec.getDescription()));
            row.createCell(6).setCellValue(safe(rec.getDummyValue()));
        }
        for (int c = 0; c <= 6; c++) {
            sheet.autoSizeColumn(c);
        }
    }

    private static String safe(String val) {
        return val == null ? "" : val;
    }
}
