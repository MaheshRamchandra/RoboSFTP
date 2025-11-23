package org.robo.rag;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;

public final class RagScenarioExcelWriter {
    private RagScenarioExcelWriter() {
    }

    public static void write(File workbookFile, String sheetName, List<String> headers, List<List<String>> scenarios) throws IOException {
        if (workbookFile == null) throw new IllegalArgumentException("Workbook file is required");
        if (sheetName == null || sheetName.isBlank()) throw new IllegalArgumentException("Sheet name is required");
        if (headers == null || headers.isEmpty()) throw new IllegalArgumentException("Headers are required");

        Workbook wb = workbookFile.exists() ? load(workbookFile) : new XSSFWorkbook();
        try {
            int idx = wb.getSheetIndex(sheetName);
            if (idx >= 0) wb.removeSheetAt(idx);
            Sheet sheet = wb.createSheet(sheetName);

            writeRow(sheet, 0, headers);
            int rowIdx = 1;
            for (List<String> scenario : scenarios) {
                writeRow(sheet, rowIdx++, scenario);
            }
            for (int c = 0; c < headers.size(); c++) {
                sheet.autoSizeColumn(c);
            }

            if (workbookFile.getParentFile() != null && !workbookFile.getParentFile().exists()) {
                if (!workbookFile.getParentFile().mkdirs()) {
                    throw new IOException("Failed to create directory: " + workbookFile.getParent());
                }
            }
            try (FileOutputStream fos = new FileOutputStream(workbookFile)) {
                wb.write(fos);
            }
        } finally {
            wb.close();
        }
    }

    private static Workbook load(File f) throws IOException {
        try (FileInputStream fis = new FileInputStream(f)) {
            return new XSSFWorkbook(fis);
        }
    }

    private static void writeRow(Sheet sheet, int rowIndex, List<String> values) {
        Row row = sheet.createRow(rowIndex);
        for (int i = 0; i < values.size(); i++) {
            row.createCell(i).setCellValue(values.get(i));
        }
    }
}
