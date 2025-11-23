package org.robo.ui;



import com.fasterxml.jackson.databind.ObjectMapper;
import org.robo.core.CryptoUtil;
import org.robo.core.ExcelProcessor;
import org.robo.core.SftpUtil;
import org.robo.core.Utils;
import org.robo.rag.LocalJsonVectorStore;
import org.robo.rag.OpenAiLlmClient;
import org.robo.rag.OllamaLlmClient;
import org.robo.rag.RagExcelWriter;
import org.robo.rag.RagFieldRecord;
import org.robo.rag.RagScenarioExcelWriter;
import org.robo.rag.RagScenarioGenerator;
import org.robo.rag.RagService;
import org.robo.rag.RagSpecBuilder;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.FileChooser;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class UIController {

    @FXML public TextField tfExcel;
    @FXML public Button btnChooseExcel;
    @FXML public ComboBox<String> cbSheet;
    @FXML public ComboBox<String> cbScenario;

    @FXML public TextField tfEncode;
    @FXML public Button btnChooseEncode;

    @FXML public Button btnGenerate;
    @FXML public Button btnEncrypt;
    @FXML public PasswordField pfAesKey;
    @FXML public TextField tfSecretKey; // AES Key input
    @FXML public TextField tfIv;        // IV input


    @FXML public TextField tfHost, tfPort, tfUser, tfPrivateKey, tfRemoteDir;
    @FXML public PasswordField pfPassword;
    @FXML public Button btnChooseKey, btnUpload;

    @FXML public TextField tfOutput;
    @FXML public Button btnOpenFolder;

    @FXML public ProgressBar progressBar;
    @FXML public TextArea taLog;
    @FXML public TextArea taValidationString;
    @FXML public TextArea taValidationPreview;
    @FXML public Button btnValidateString;
    @FXML public Button btnClearScreens;
    @FXML public TextField tfValidationScenario;
    @FXML public TextField tfPreviewSearch;
    @FXML public Button btnPreviewSearch;

    // RAG Tab controls
    @FXML public TextField tfRagKb;
    @FXML public Button btnRagChooseKb;
    @FXML public Button btnRagLoadSample;
    @FXML public ComboBox<String> cbRagRdg;
    @FXML public Button btnRagRetrieve;
    @FXML public TextArea taRagRetrieved;
    @FXML public TextArea taRagOutput;
    @FXML public TextArea taRagLog;
    @FXML public TextField tfRagApiBase;
    @FXML public TextField tfRagModel;
    @FXML public PasswordField pfRagApiKey;
    @FXML public Button btnRagGenerate;
    @FXML public TextField tfRagWorkbook;
    @FXML public Button btnRagChooseWorkbook;
    @FXML public Button btnRagWriteSheet;
    @FXML public TextField tfRagScenarioCount;
    @FXML public Button btnRagWriteScenarios;
    @FXML public TextField tfRagScenarioMix;
    @FXML public ComboBox<String> cbRagAssessmentTool;

    private File excelFile;
    private File encodeFile;
    private File privateKeyFile;
    private File lastGeneratedFile;
    private final List<Integer> previewMatchIndices = new ArrayList<>();
    private int previewMatchCursor = -1;
    private String lastPreviewSearchTerm;
    private File ragKbFile;
    private File ragWorkbookFile;
    private final LocalJsonVectorStore ragStore = new LocalJsonVectorStore();
    private final RagService ragService = new RagService(ragStore);
    private final ObjectMapper ragMapper = new ObjectMapper();
    private List<RagFieldRecord> lastRagRecords = new ArrayList<>();

    @FXML
    public void initialize() {
        btnGenerate.setDisable(true);
        btnEncrypt.setDisable(true);
        btnUpload.setDisable(true);
        progressBar.setProgress(0);

        btnChooseExcel.setOnAction(e -> {
            chooseExcel();
            updateButtonStates();
        });

        btnChooseEncode.setOnAction(e -> {
            chooseEncode();
            updateButtonStates();
        });

        cbSheet.setOnAction(e -> {
            populateScenarios();
            updateButtonStates();
        });

        cbScenario.setOnAction(e -> updateButtonStates());

        btnChooseKey.setOnAction(e -> choosePrivateKey());
        btnGenerate.setOnAction(e -> generate());
        btnEncrypt.setOnAction(e -> encrypt());
        btnUpload.setOnAction(e -> upload());
        btnValidateString.setOnAction(e -> validateString());
        btnClearScreens.setOnAction(e -> clearScreens());
        btnPreviewSearch.setOnAction(e -> searchPreview());
        tfPreviewSearch.setOnAction(e -> searchPreview());
        updateButtonStates();
        btnOpenFolder.setOnAction(e -> {
            if (lastGeneratedFile != null) {
                try {
                    File parentDir = lastGeneratedFile.getParentFile();
                    if (parentDir.exists()) {
                        java.awt.Desktop.getDesktop().open(parentDir);
                    } else {
                        log("Folder does not exist: " + parentDir.getAbsolutePath());
                    }
                } catch (Exception ex) {
                    log("Failed to open folder: " + ex.getMessage());
                    ex.printStackTrace();
                }
            } else {
                log("No generated file to open folder for.");
            }
        });
        initRagTab();
    }

    private void chooseExcel() {
        FileChooser fc = new FileChooser();
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("Excel Files", "*.xlsx"));
        File f = fc.showOpenDialog(null);
        if (f != null) {
            excelFile = f;
            tfExcel.setText(f.getAbsolutePath());
            log("Selected Excel: " + f.getName());
            populateSheets();
        }
    }

    private void populateSheets() {
        try {
            List<String> sheets = ExcelProcessor.listSheetNames(excelFile);
            cbSheet.getItems().clear();
            cbSheet.getItems().addAll(sheets);
            if (!sheets.isEmpty()) cbSheet.getSelectionModel().select(0);
            populateScenarios();
        } catch (Exception ex) {
            log("Error reading sheets: " + ex.getMessage());
            ex.printStackTrace();
        }
    }

    private void populateScenarios() {
        cbScenario.getItems().clear();
        String sheet = cbSheet.getValue();
        if (sheet == null || excelFile == null) return;
        try {
            List<String> scenarios = ExcelProcessor.listScenarios(excelFile, sheet);
            cbScenario.getItems().addAll(scenarios);
            if (!scenarios.isEmpty()) cbScenario.getSelectionModel().select(0);
        } catch (Exception ex) {
            log("Error listing scenarios: " + ex.getMessage());
            ex.printStackTrace();
        }
    }

    private void chooseEncode() {
        FileChooser fc = new FileChooser();
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("Text files", "*.txt"));
        File f = fc.showOpenDialog(null);
        if (f != null) {
            encodeFile = f;
            tfEncode.setText(f.getAbsolutePath());
            log("Selected encode fields: " + f.getName());
        }
    }

    private void choosePrivateKey() {
        FileChooser fc = new FileChooser();
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("Private Key", "*"));
        File f = fc.showOpenDialog(null);
        if (f != null) {
            privateKeyFile = f;
            tfPrivateKey.setText(f.getAbsolutePath());
            log("Selected private key: " + f.getName());
        }
    }

    private void validateString() {
        if (excelFile == null || cbSheet.getValue() == null || encodeFile == null) {
            log("Select Excel, sheet and encode fields before validating.");
            return;
        }
        String payload = taValidationString.getText();
        if (payload == null || payload.isBlank()) {
            log("Enter the generated string to validate.");
            return;
        }

        String scenarioName = tfValidationScenario.getText().trim();
        if (scenarioName.isEmpty()) {
            log("Provide a scenario name for the decoded Excel.");
            return;
        }

        String toDecode = payload.trim();

        btnValidateString.setDisable(true);
        progressBar.setProgress(-1);

        Task<Void> task = new Task<>() {
            @Override
            protected Void call() throws Exception {
                try {
                    Set<String> enc = ExcelProcessor.loadEncodeFields(encodeFile);
                    ExcelProcessor.ExcelTemplate template = ExcelProcessor.loadTemplate(excelFile, cbSheet.getValue());
                    List<ExcelProcessor.DecodedColumn> decoded = ExcelProcessor.decodeGeneratedString(toDecode, template, enc);
                    StringBuilder preview = new StringBuilder();
                    for (ExcelProcessor.DecodedColumn col : decoded) {
                        String name = col.getColumn().getCleanName();
                        preview.append(name.isEmpty() ? "<Unnamed>" : name)
                                .append(": ")
                                .append(col.getDecodedValue())
                                .append("\n");
                    }
                    if (preview.length() > 0) preview.setLength(preview.length() - 1);
                    ExcelProcessor.appendDecodedRow(excelFile, template, scenarioName, decoded);
                    Platform.runLater(() -> {
                        taValidationPreview.setText(preview.toString());
                        resetPreviewSearchState();
                        log("Appended decoded row to: " + excelFile.getAbsolutePath());
                    });
                } catch (Exception ex) {
                    ex.printStackTrace();
                    Platform.runLater(() -> log("Validation error: " + ex.getMessage()));
                } finally {
                    Platform.runLater(() -> {
                        btnValidateString.setDisable(false);
                        progressBar.setProgress(0);
                    });
                }
                return null;
            }
        };
        new Thread(task).start();
    }

    // --- RAG TAB ---

    private void initRagTab() {
        if (cbRagRdg != null) {
            cbRagRdg.getItems().setAll("Stroke", "SCI", "Hip", "Amputation", "MSK", "Deconditioning");
            if (!cbRagRdg.getItems().isEmpty()) {
                cbRagRdg.getSelectionModel().select(0);
            }
        }
        if (tfRagApiBase != null) tfRagApiBase.setText("https://api.openai.com/v1");
        if (tfRagModel != null) tfRagModel.setText("gpt-4o-mini");
        if (tfRagWorkbook != null) {
            File def = new File(System.getProperty("user.home"), "RDG_Specs.xlsx");
            ragWorkbookFile = def;
            tfRagWorkbook.setText(def.getAbsolutePath());
        }

        if (btnRagChooseKb != null) btnRagChooseKb.setOnAction(e -> chooseRagKb());
        if (btnRagLoadSample != null) btnRagLoadSample.setOnAction(e -> loadSampleRagKb());
        if (btnRagRetrieve != null) btnRagRetrieve.setOnAction(e -> retrieveRagRecords());
        if (btnRagGenerate != null) btnRagGenerate.setOnAction(e -> generateRagSpec());
        if (btnRagChooseWorkbook != null) btnRagChooseWorkbook.setOnAction(e -> chooseRagWorkbook());
        if (btnRagWriteSheet != null) btnRagWriteSheet.setOnAction(e -> writeRagSheet());
        if (btnRagWriteScenarios != null) btnRagWriteScenarios.setOnAction(e -> writeRagScenarios());
        if (cbRagAssessmentTool != null) {
            cbRagAssessmentTool.getItems().setAll("FIM", "MBI");
            cbRagAssessmentTool.getSelectionModel().select("FIM");
        }
        updateButtonStates();
    }

    private void chooseRagKb() {
        FileChooser fc = new FileChooser();
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("JSON Files", "*.json"));
        File f = fc.showOpenDialog(null);
        if (f != null) {
            try {
                ragStore.refresh(f);
                ragKbFile = f;
                if (tfRagKb != null) tfRagKb.setText(f.getAbsolutePath());
                lastRagRecords = new ArrayList<>();
                if (taRagRetrieved != null) taRagRetrieved.clear();
                ragLog("Loaded knowledge base: " + f.getName());
            } catch (IOException ex) {
                ragLog("Failed to load KB: " + ex.getMessage());
            }
        }
        updateButtonStates();
    }

    private void loadSampleRagKb() {
        try {
            ragStore.refreshFromClasspath("/rag/sample_rdg_fields.json");
            ragKbFile = null;
            if (tfRagKb != null) tfRagKb.setText("classpath:/rag/sample_rdg_fields.json");
            lastRagRecords = new ArrayList<>();
            if (taRagRetrieved != null) taRagRetrieved.clear();
            ragLog("Loaded bundled sample knowledge base.");
        } catch (IOException ex) {
            ragLog("Failed to load sample KB: " + ex.getMessage());
        }
        updateButtonStates();
    }

    private void retrieveRagRecords() {
        String rdg = cbRagRdg != null ? cbRagRdg.getValue() : null;
        if (rdg == null || rdg.isBlank()) {
            ragLog("Select an RDG first.");
            return;
        }
        if (!ragStore.isLoaded()) {
            ragLog("Load a knowledge base JSON before retrieving.");
            return;
        }
        try {
            lastRagRecords = ragService.retrieve(rdg, ragStore.defaultSections());
            String json = ragMapper.writerWithDefaultPrettyPrinter()
                    .writeValueAsString(lastRagRecords.stream().map(RagFieldRecord::toSpecMap).toList());
            if (taRagRetrieved != null) taRagRetrieved.setText(json);
            ragLog("Retrieved " + lastRagRecords.size() + " field records for RDG " + rdg
                    + " from " + ragStore.sourceDescription());
        } catch (Exception ex) {
            ragLog("Failed to retrieve: " + ex.getMessage());
        }
        updateButtonStates();
    }

    private void generateRagSpec() {
        String rdg = cbRagRdg != null ? cbRagRdg.getValue() : null;
        if (rdg == null || rdg.isBlank()) {
            ragLog("Select an RDG first.");
            return;
        }
        if (lastRagRecords == null || lastRagRecords.isEmpty()) {
            ragLog("Retrieve field records before generating.");
            return;
        }

        String apiKey = pfRagApiKey != null ? pfRagApiKey.getText() : "";
        boolean useLlm = apiKey != null && !apiKey.isBlank();

        if (btnRagGenerate != null) btnRagGenerate.setDisable(true);
        progressBar.setProgress(-1);

        Task<Void> task = new Task<>() {
            @Override
            protected Void call() {
                try {
                    String output;
                    if (useLlm) {
                        String apiBase = tfRagApiBase.getText().trim();
                        String model = tfRagModel.getText().trim().isEmpty() ? "gpt-4o-mini" : tfRagModel.getText().trim();
                        boolean useOllama = apiBase.toLowerCase().contains("ollama") || apiBase.contains("/api/generate");
                        if (useOllama) {
                            OllamaLlmClient client = new OllamaLlmClient(apiBase, apiKey.trim(), model);
                            output = ragService.callLlm(client, rdg, lastRagRecords);
                        } else {
                            OpenAiLlmClient client = new OpenAiLlmClient(apiBase, apiKey.trim(), model);
                            output = ragService.callLlm(client, rdg, lastRagRecords);
                        }
                    } else {
                        output = ragService.offlineSpec(lastRagRecords);
                    }
                    String finalOutput = output;
                    Platform.runLater(() -> {
                        if (taRagOutput != null) taRagOutput.setText(finalOutput);
                    });
                    ragLog((useLlm ? "LLM" : "Offline") + " spec generated for RDG " + rdg + ".");
                } catch (Exception ex) {
                    ragLog("RAG generation failed: " + ex.getMessage());
                } finally {
                    Platform.runLater(() -> {
                        if (btnRagGenerate != null) btnRagGenerate.setDisable(false);
                        progressBar.setProgress(0);
                        updateButtonStates();
                    });
                }
                return null;
            }
        };
        new Thread(task).start();
    }

    private void chooseRagWorkbook() {
        FileChooser fc = new FileChooser();
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("Excel Workbook", "*.xlsx"));
        File f = fc.showSaveDialog(null);
        if (f != null) {
            ragWorkbookFile = ensureXlsx(f);
            if (tfRagWorkbook != null) tfRagWorkbook.setText(ragWorkbookFile.getAbsolutePath());
            ragLog("Workbook set to: " + ragWorkbookFile.getName());
        }
        updateButtonStates();
    }

    private File ensureXlsx(File f) {
        if (f.getName().toLowerCase().endsWith(".xlsx")) return f;
        return new File(f.getParentFile(), f.getName() + ".xlsx");
    }

    private void writeRagSheet() {
        String rdg = cbRagRdg != null ? cbRagRdg.getValue() : null;
        if (rdg == null || rdg.isBlank()) {
            ragLog("Select an RDG first.");
            return;
        }
        if (ragWorkbookFile == null) {
            ragLog("Choose an output workbook path.");
            return;
        }
        String payload = taRagOutput != null ? taRagOutput.getText() : null;
        if (payload == null || payload.isBlank()) {
            ragLog("Generate or paste a JSON spec before writing.");
            return;
        }
        try {
            List<RagFieldRecord> records = RagSpecBuilder.parse(payload);
            String sheetName = "RDG_" + rdg.replaceAll("\\s+", "");
            RagExcelWriter.writeSpec(ragWorkbookFile, sheetName, records);
            ragLog("Wrote sheet '" + sheetName + "' to " + ragWorkbookFile.getAbsolutePath());
        } catch (Exception ex) {
            ragLog("Failed to write workbook: " + ex.getMessage());
        }
        updateButtonStates();
    }

    private void writeRagScenarios() {
        String rdg = cbRagRdg != null ? cbRagRdg.getValue() : null;
        if (rdg == null || rdg.isBlank()) {
            ragLog("Select an RDG first.");
            return;
        }
        if (ragWorkbookFile == null) {
            ragLog("Choose an output workbook path.");
            return;
        }
        if (lastRagRecords == null || lastRagRecords.isEmpty()) {
            ragLog("Retrieve field records before writing scenarios.");
            return;
        }
        int scenarioCount = 1;
        try {
            if (tfRagScenarioCount != null && tfRagScenarioCount.getText() != null && !tfRagScenarioCount.getText().isBlank()) {
                scenarioCount = Math.max(1, Integer.parseInt(tfRagScenarioCount.getText().trim()));
            }
        } catch (NumberFormatException ex) {
            ragLog("Invalid scenario count, defaulting to 1.");
            scenarioCount = 1;
        }

        try {
            String mixText = tfRagScenarioMix != null ? tfRagScenarioMix.getText() : "";
            if (mixText != null && !mixText.isBlank()) {
                List<RagFieldRecord> rdgFiltered = RagScenarioGenerator.filterByRdgBlocks(lastRagRecords, rdg);
                List<RagFieldRecord> masterOrdered = RagScenarioGenerator.ordered(rdgFiltered);
                List<String> headers = RagScenarioGenerator.buildHeaders(masterOrdered);
                List<List<String>> allRows = new ArrayList<>();

                String[] parts = mixText.split(",");
                int scenarioIdx = 1;
                for (String part : parts) {
                    String[] kv = part.split(":");
                    if (kv.length != 2) continue;
                    String tool = kv[0].trim().toUpperCase();
                    int cnt;
                    try {
                        cnt = Math.max(1, Integer.parseInt(kv[1].trim()));
                    } catch (NumberFormatException ex) {
                        ragLog("Invalid count for " + tool + ", skipping.");
                        continue;
                    }
                    List<RagFieldRecord> filtered = RagScenarioGenerator.filterByAssessmentTool(rdgFiltered, tool);
                    List<RagFieldRecord> ordered = RagScenarioGenerator.ordered(filtered);
                    List<List<String>> scenarios = RagScenarioGenerator.generateScenarios(ordered, cnt);

                    for (List<String> row : scenarios) {
                        List<String> merged = new ArrayList<>();
                        merged.add("Scenario" + scenarioIdx + "-" + tool);
                        for (int i = 1; i < headers.size(); i++) {
                            String h = headers.get(i);
                            int idx = findIndexByHeader(ordered, h);
                            merged.add(idx >= 0 && idx < row.size() ? row.get(idx) : "");
                        }
                        allRows.add(merged);
                        scenarioIdx++;
                    }
                }
                String sheetName = "RDG_" + rdg.replaceAll("\\s+", "");
                RagScenarioExcelWriter.write(ragWorkbookFile, sheetName, headers, allRows);
                ragLog("Scenario sheet '" + sheetName + "' written with mix '" + mixText + "' to " + ragWorkbookFile.getAbsolutePath());
            } else {
                List<RagFieldRecord> rdgFiltered = RagScenarioGenerator.filterByRdgBlocks(lastRagRecords, rdg);
                List<RagFieldRecord> filtered = RagScenarioGenerator.filterByAssessmentTool(
                        rdgFiltered,
                        cbRagAssessmentTool != null ? cbRagAssessmentTool.getValue() : "FIM"
                );
                List<RagFieldRecord> ordered = RagScenarioGenerator.ordered(filtered);
                List<String> headers = RagScenarioGenerator.buildHeaders(ordered);
                List<List<String>> scenarios = RagScenarioGenerator.generateScenarios(ordered, scenarioCount);
                String sheetName = "RDG_" + rdg.replaceAll("\\s+", "");
                RagScenarioExcelWriter.write(ragWorkbookFile, sheetName, headers, scenarios);
                ragLog("Scenario sheet '" + sheetName + "' written with " + scenarioCount + " scenario(s) using "
                        + (cbRagAssessmentTool != null ? cbRagAssessmentTool.getValue() : "FIM")
                        + " to " + ragWorkbookFile.getAbsolutePath());
            }
        } catch (Exception ex) {
            ragLog("Failed to write scenarios: " + ex.getMessage());
        }
        updateButtonStates();
    }

    private int findIndexByHeader(List<RagFieldRecord> ordered, String header) {
        if (ordered == null) return -1;
        for (int i = 0; i < ordered.size(); i++) {
            boolean conditional = org.robo.rag.RagScenarioGenerator.parseCondition(ordered.get(i).getFormat()) != null;
            String h = (!conditional && ordered.get(i).isMandatory()) ? "M##" + ordered.get(i).getExcelHeader() : ordered.get(i).getExcelHeader();
            if (h.equals(header)) return i + 1; // +1 because scenario rows include Name at index 0
        }
        return -1;
    }

    private void ragLog(String msg) {
        Platform.runLater(() -> {
            if (taRagLog != null) {
                taRagLog.appendText(msg + "\n");
            }
        });
    }

    private void clearScreens() {
        taValidationString.clear();
        taValidationPreview.clear();
        taLog.clear();
        tfOutput.clear();
        resetPreviewSearchState();
    }

    private void searchPreview() {
        String query = tfPreviewSearch.getText();
        if (query == null || query.isEmpty()) {
            taValidationPreview.deselect();
            return;
        }

        String preview = taValidationPreview.getText();
        if (preview == null || preview.isEmpty()) {
            log("Decoded preview is empty.");
            return;
        }

        if (!query.equals(lastPreviewSearchTerm)) {
            previewMatchIndices.clear();
            previewMatchCursor = -1;
            lastPreviewSearchTerm = query;
            int idx = preview.indexOf(query);
            while (idx >= 0) {
                previewMatchIndices.add(idx);
                idx = preview.indexOf(query, idx + query.length());
            }
        }

        if (previewMatchIndices.isEmpty()) {
            log("Preview text not found for '" + query + "'.");
            taValidationPreview.deselect();
            return;
        }

        previewMatchCursor = (previewMatchCursor + 1) % previewMatchIndices.size();
        int start = previewMatchIndices.get(previewMatchCursor);
        taValidationPreview.selectRange(start, start + query.length());
        taValidationPreview.requestFocus();
        log("Match " + (previewMatchCursor + 1) + "/" + previewMatchIndices.size() + " for '" + query + "'.");
    }

    private void resetPreviewSearchState() {
        previewMatchIndices.clear();
        previewMatchCursor = -1;
        lastPreviewSearchTerm = null;
    }

    private void generate() {
        if (excelFile == null || cbSheet.getValue() == null || cbScenario.getValue() == null || encodeFile == null) {
            log("Please select Excel, sheet, scenario and encode fields file first.");
            return;
        }
        btnGenerate.setDisable(true);
        progressBar.setProgress(-1);

        Task<Void> task = new Task<>() {
            @Override
            protected Void call() throws Exception {
                try {
                    Set<String> enc = ExcelProcessor.loadEncodeFields(encodeFile);
                    String result = ExcelProcessor.processExcel(excelFile, cbSheet.getValue(), cbScenario.getValue(), enc);

                    String name = "SFTP_" + cbScenario.getValue().replaceAll("\\s+", "_") + "_" + System.currentTimeMillis() + ".txt";
                    File out = new File(excelFile.getParentFile(), name);
                    Files.write(out.toPath(), result.getBytes(StandardCharsets.UTF_8));
                    lastGeneratedFile = out;
                    Platform.runLater(() -> {
                        tfOutput.setText(out.getAbsolutePath());
                        log("Generated TXT: " + out.getAbsolutePath());
                        btnEncrypt.setDisable(false);
                        btnUpload.setDisable(false);
                    });
                } catch (Exception ex) {
                    ex.printStackTrace();
                    log("Error generating file: " + ex.getMessage());
                } finally {
                    Platform.runLater(() -> {
                        btnGenerate.setDisable(false);
                        progressBar.setProgress(0);
                    });
                }
                return null;
            }
        };
        new Thread(task).start();
    }

    private void encrypt() {
        if (lastGeneratedFile == null) {
            log("Generate a TXT file first.");
            return;
        }
        btnEncrypt.setDisable(true);
        progressBar.setProgress(-1);

        Task<Void> task = new Task<>() {
            @Override
            protected Void call() throws Exception {
                try {
                    String secretKeyStr = tfSecretKey.getText().trim(); // user enters secret key
                    String ivStr = tfIv.getText().trim(); // user enters IV (for CBC)
                    File outEnc = new File(lastGeneratedFile.getParent(), lastGeneratedFile.getName() + ".enc");

                    if (secretKeyStr.isEmpty()) {
                        Platform.runLater(() -> log("Secret key is required."));
                        return null;
                    }

                    byte[] keyBytes = secretKeyStr.getBytes(StandardCharsets.UTF_8);

                    // Validate key length (must match selected key size)
                    if (keyBytes.length != 32) { // AES-256
                        Platform.runLater(() -> log("AES key must be 256 bits (32 chars)."));
                        return null;
                    }

                    // CBC mode requires IV
                    byte[] ivBytes = null;
                    if (!ivStr.isEmpty()) {
                        ivBytes = ivStr.getBytes(StandardCharsets.UTF_8);
                        if (ivBytes.length != 16) {
                            Platform.runLater(() -> log("IV must be exactly 16 bytes (16 chars)."));
                            return null;
                        }
                    }

                    // Encrypt
                    CryptoUtil.encryptFileAES_CBC(keyBytes, ivBytes, lastGeneratedFile, outEnc);

                    lastGeneratedFile = outEnc;
                    Platform.runLater(() -> {
                        tfOutput.setText(outEnc.getAbsolutePath());
                        log("Encrypted file created: " + outEnc.getAbsolutePath());
                    });

                } catch (Exception ex) {
                    ex.printStackTrace();
                    Platform.runLater(() -> log("Encryption error: " + ex.getMessage()));
                } finally {
                    Platform.runLater(() -> {
                        btnEncrypt.setDisable(false);
                        progressBar.setProgress(0);
                    });
                }
                return null;
            }
        };
        new Thread(task).start();
    }



    private void upload() {
        if (lastGeneratedFile == null) {
            log("No file to upload. Generate (and optionally encrypt) first.");
            return;
        }
        if (tfHost.getText().isBlank() || tfUser.getText().isBlank()) {
            log("Enter SFTP host and username.");
            return;
        }
        btnUpload.setDisable(true);
        progressBar.setProgress(-1);

        Task<Void> task = new Task<>() {
            @Override
            protected Void call() throws Exception {
                try {
                    String host = tfHost.getText().trim();
                    int port = Integer.parseInt(tfPort.getText().trim());
                    String user = tfUser.getText().trim();
                    String pass = pfPassword.getText();
                    String remoteDir = tfRemoteDir.getText().trim();
                    if (pass != null && !pass.isEmpty()) {
                        SftpUtil.uploadWithPassword(host, port, user, pass, lastGeneratedFile, remoteDir, lastGeneratedFile.getName());
                    } else if (privateKeyFile != null) {
                        SftpUtil.uploadWithPrivateKey(host, port, user, privateKeyFile, null, lastGeneratedFile, remoteDir, lastGeneratedFile.getName());
                    } else {
                        Platform.runLater(() -> log("Enter password or choose private key for SFTP."));
                        return null;
                    }
                    Platform.runLater(() -> log("Upload completed successfully."));
                } catch (Exception ex) {
                    ex.printStackTrace();
                    log("Upload error: " + ex.getMessage());
                } finally {
                    btnUpload.setDisable(false);
                    progressBar.setProgress(0);
                }
                return null;
            }
        };
        new Thread(task).start();
    }

    private void updateButtonStates() {
        boolean canGenerate = excelFile != null
                && cbSheet.getValue() != null
                && cbScenario.getValue() != null
                && encodeFile != null;

        btnGenerate.setDisable(!canGenerate);

        // Encrypt only enabled after TXT generated
        btnEncrypt.setDisable(lastGeneratedFile == null);
        btnValidateString.setDisable(!(excelFile != null && cbSheet.getValue() != null && encodeFile != null));

        if (btnRagRetrieve != null) {
            boolean canRetrieveRag = ragStore.isLoaded()
                    && cbRagRdg != null
                    && cbRagRdg.getValue() != null
                    && !cbRagRdg.getValue().isBlank();
            btnRagRetrieve.setDisable(!canRetrieveRag);
        }
        if (btnRagGenerate != null) {
            boolean hasRecords = lastRagRecords != null && !lastRagRecords.isEmpty();
            btnRagGenerate.setDisable(!hasRecords);
        }
        if (btnRagWriteSheet != null) {
            boolean hasPayload = taRagOutput != null
                    && taRagOutput.getText() != null
                    && !taRagOutput.getText().isBlank();
            btnRagWriteSheet.setDisable(ragWorkbookFile == null || !hasPayload);
        }
        if (btnRagWriteScenarios != null) {
            boolean hasRecords = lastRagRecords != null && !lastRagRecords.isEmpty();
            btnRagWriteScenarios.setDisable(ragWorkbookFile == null || !hasRecords);
        }
    }


    private void log(String msg) {
        Platform.runLater(() -> {
            taLog.appendText(msg + "\n");
        });
    }
}
