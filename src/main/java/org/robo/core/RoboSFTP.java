package org.robo.core;

import javafx.application.Application;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.geometry.Insets;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.*;

import static javafx.application.Application.launch;

public class RoboSFTP extends Application{
    private Label statusLabel = new Label("Select an Excel file to start...");


    public void start(Stage stage) {
        Button selectBtn = new Button("Select Excel File");
        Button processBtn = new Button("Process File");
        processBtn.setDisable(true);

        final FileChooser fileChooser = new FileChooser();
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Excel Files", "*.xlsx"));

        final File[] selectedFile = new File[1];

        selectBtn.setOnAction(e -> {
            File file = fileChooser.showOpenDialog(stage);
            if (file != null) {
                selectedFile[0] = file;
                statusLabel.setText("Selected: " + file.getName());
                processBtn.setDisable(false);
            }
        });

        processBtn.setOnAction(e -> {
            if (selectedFile[0] != null) {
                try {
                    File output = processExcel(selectedFile[0]);
                    statusLabel.setText("Processed successfully! Saved as: " + output.getName());
                } catch (Exception ex) {
                    ex.printStackTrace();
                    statusLabel.setText("Error: " + ex.getMessage());
                }
            }
        });

        VBox layout = new VBox(15, selectBtn, processBtn, statusLabel);
        layout.setPadding(new Insets(20));
        stage.setScene(new Scene(layout, 400, 200));
        stage.setTitle("Excel Modifier App");
        stage.show();
    }

    private File processExcel(File inputFile) throws IOException {
        try (FileInputStream fis = new FileInputStream(inputFile);
             Workbook workbook = new XSSFWorkbook(fis)) {

            Sheet sheet = workbook.getSheetAt(0);
            Row header = sheet.getRow(0);
            Row mandatoryRow = sheet.getRow(1);

            for (int i = 0; i < header.getLastCellNum(); i++) {
                Cell nameCell = header.getCell(i);
                Cell mandatoryCell = mandatoryRow.getCell(i);

                if (mandatoryCell != null && "Y".equalsIgnoreCase(mandatoryCell.getStringCellValue().trim())) {
                    nameCell.setCellValue("M##" + nameCell.getStringCellValue());
                }
            }

            // Remove the mandatory row
            sheet.removeRow(mandatoryRow);

            File outputFile = new File(inputFile.getParent(), "Modified_" + System.currentTimeMillis() + ".xlsx");
            try (FileOutputStream fos = new FileOutputStream(outputFile)) {
                workbook.write(fos);
            }

            return outputFile;
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}

