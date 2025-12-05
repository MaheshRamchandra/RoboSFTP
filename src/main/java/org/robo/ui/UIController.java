package org.robo.ui;



import com.fasterxml.jackson.databind.ObjectMapper;
import org.robo.core.CryptoUtil;
import org.robo.core.ExcelProcessor;
import org.robo.core.SftpUtil;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.robo.rag.LocalJsonVectorStore;
import org.robo.rag.RagExcelWriter;
import org.robo.rag.RagFieldRecord;
import org.robo.rag.RagScenarioExcelWriter;
import org.robo.rag.RagScenarioGenerator;
import org.robo.rag.RagService;
import org.robo.rag.RagSpecBuilder;
import org.robo.rag.DependencyRuleService;
import org.robo.rag.RagStringValidator;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.scene.Scene;
import javafx.scene.layout.VBox;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.util.StringConverter;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Locale;

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
    @FXML public Button btnRagGenerateSpec;
    @FXML public TextArea taRagOutput;
    @FXML public Button btnRagOpenSpecPreview;
    @FXML public TextArea taRagLog;
    @FXML public TextField tfRagWorkbook;
    @FXML public Button btnRagChooseWorkbook;
    @FXML public Button btnRagWriteSheet;
    @FXML public TextField tfRagScenarioCount;
    @FXML public Button btnRagWriteScenarios;
    @FXML public TextField tfRagScenarioMix;
    @FXML public ComboBox<String> cbRagAssessmentTool;
    @FXML public Button btnRagPreviewScenarios;
    @FXML public Button btnRagSavePreview;
    @FXML public Button btnRagOpenPreviewWindow;
    @FXML public Button btnRagResetPreview;
    @FXML public VBox vbRagScenarioPreviews;
    // Dependency tab
    @FXML public TextField tfDepRules;
    @FXML public Button btnDepChooseRules;
    @FXML public Button btnDepLoadRules;
    @FXML public TableView<DependencyRuleRow> tblDepRules;
    @FXML public TableColumn<DependencyRuleRow, String> colDepName;
    @FXML public TableColumn<DependencyRuleRow, String> colDepRequired;
    @FXML public TableColumn<DependencyRuleRow, String> colDepMandatoryWhen;
    @FXML public TableColumn<DependencyRuleRow, String> colDepOptionalWhen;
    @FXML public TableColumn<DependencyRuleRow, String> colDepRule;

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
    private final java.util.Map<String, PreviewBundle> previewBundles = new java.util.HashMap<>();
    private File depRulesFile;
    private final ObservableList<DependencyRuleRow> depRuleRows = FXCollections.observableArrayList();

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
        initDependencyTab();
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
                    validateAgainstKb(toDecode, preview);
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

    private void validateAgainstKb(String generatedString, StringBuilder previewBuilder) {
        if (!ragStore.isLoaded()) {
            ragLog("KB not loaded; skipping KB validation.");
            return;
        }
        String rdg = cbRagRdg != null ? cbRagRdg.getValue() : null;
        if (rdg == null || rdg.isBlank()) {
            ragLog("Select an RDG to run KB validation.");
            return;
        }
        try {
            List<RagFieldRecord> records = ragService.retrieve(rdg, ragStore.defaultSections());
            if (records == null || records.isEmpty()) {
                ragLog("No KB records found for " + rdg + " to validate against.");
                return;
            }
            List<RagFieldRecord> ordered = RagScenarioGenerator.ordered(records);
            List<String> tokens = java.util.Arrays.asList(generatedString.split("\\|", -1));
            RagStringValidator.ValidationResult res = RagStringValidator.validate(tokens, ordered);

            previewBuilder.append("\n\n--- KB Validation ---\n");
            if (res.errors().isEmpty() && res.warnings().isEmpty()) {
                previewBuilder.append("OK: All values match KB ordering and rules.\n");
            } else {
                for (String err : res.errors()) {
                    previewBuilder.append("ERROR: ").append(err).append("\n");
                }
                for (String warn : res.warnings()) {
                    previewBuilder.append("WARN: ").append(warn).append("\n");
                }
            }
            ragLog("KB validation completed for " + rdg + " (" + res.errors().size() + " error(s), " + res.warnings().size() + " warning(s)).");
        } catch (Exception ex) {
            ragLog("KB validation failed: " + ex.getMessage());
        }
    }

    // --- RAG TAB ---

    private void initRagTab() {
        if (cbRagRdg != null) {
            cbRagRdg.getItems().setAll("Stroke", "SCI", "Hip", "Amputation", "MSK", "Deconditioning");
            if (!cbRagRdg.getItems().isEmpty()) {
                cbRagRdg.getSelectionModel().select(0);
            }
            cbRagRdg.setOnAction(e -> updateButtonStates());
        }
        if (tfRagWorkbook != null) {
            File def = new File(System.getProperty("user.home"), "RDG_Specs.xlsx");
            ragWorkbookFile = def;
            tfRagWorkbook.setText(def.getAbsolutePath());
        }
        if (vbRagScenarioPreviews != null) {
            vbRagScenarioPreviews.getChildren().clear();
        }

        if (btnRagChooseKb != null) btnRagChooseKb.setOnAction(e -> chooseRagKb());
        if (btnRagLoadSample != null) btnRagLoadSample.setOnAction(e -> loadSampleRagKb());
        if (btnRagRetrieve != null) btnRagRetrieve.setOnAction(e -> retrieveRagRecords());
        if (btnRagGenerateSpec != null) btnRagGenerateSpec.setOnAction(e -> generateRagSpecOffline());
        if (btnRagChooseWorkbook != null) btnRagChooseWorkbook.setOnAction(e -> chooseRagWorkbook());
        if (btnRagWriteSheet != null) btnRagWriteSheet.setOnAction(e -> writeRagSheet());
        if (btnRagWriteScenarios != null) btnRagWriteScenarios.setOnAction(e -> writeRagScenarios());
        if (btnRagPreviewScenarios != null) btnRagPreviewScenarios.setOnAction(e -> previewRagScenarios());
        if (btnRagSavePreview != null) btnRagSavePreview.setOnAction(e -> saveCurrentPreview());
        if (btnRagOpenPreviewWindow != null) btnRagOpenPreviewWindow.setOnAction(e -> openPreviewWindow());
        if (btnRagResetPreview != null) btnRagResetPreview.setOnAction(e -> resetScenarioPreview());
        if (btnRagOpenSpecPreview != null) btnRagOpenSpecPreview.setOnAction(e -> openRagSpecPreview());
        if (cbRagAssessmentTool != null) {
            cbRagAssessmentTool.getItems().setAll("FIM", "MBI");
            cbRagAssessmentTool.getSelectionModel().select("FIM");
        }
        updateButtonStates();
    }

    private void initDependencyTab() {
        if (tblDepRules != null) {
            tblDepRules.setItems(depRuleRows);
            tblDepRules.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
            colDepName.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().name()));
            colDepRequired.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().required()));
            colDepMandatoryWhen.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().mandatoryWhen()));
            colDepOptionalWhen.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().optionalWhen()));
            colDepRule.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().rule()));
            tblDepRules.setPlaceholder(new Label("Load dependency rules JSON to view conditional mandatory logic."));
        }
        if (btnDepChooseRules != null) btnDepChooseRules.setOnAction(e -> chooseDepRules());
        if (btnDepLoadRules != null) btnDepLoadRules.setOnAction(e -> loadDepRules());

        // Default path
        File def = new File("vector-kb/dependency_fields.json");
        if (def.exists()) {
            depRulesFile = def;
            if (tfDepRules != null) tfDepRules.setText(def.getAbsolutePath());
        }
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
            clearAllScenarioPreviews();
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
            clearAllScenarioPreviews();
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
            ragLog("Retrieved " + lastRagRecords.size() + " field records for RDG " + rdg
                    + " from " + ragStore.sourceDescription());
        } catch (Exception ex) {
            ragLog("Failed to retrieve: " + ex.getMessage());
        }
        updateButtonStates();
    }

    private void generateRagSpecOffline() {
        String rdg = cbRagRdg != null ? cbRagRdg.getValue() : null;
        if (rdg == null || rdg.isBlank()) {
            ragLog("Select an RDG first.");
            return;
        }
        if (lastRagRecords == null || lastRagRecords.isEmpty()) {
            ragLog("Retrieve field records before generating.");
            return;
        }

        if (btnRagGenerateSpec != null) btnRagGenerateSpec.setDisable(true);
        progressBar.setProgress(-1);

        Task<Void> task = new Task<>() {
            @Override
            protected Void call() {
                try {
                    String output = ragService.offlineSpec(lastRagRecords);
                    Platform.runLater(() -> {
                        if (taRagOutput != null) taRagOutput.setText(output);
                    });
                    ragLog("Offline spec generated for RDG " + rdg + ".");
                } catch (Exception ex) {
                    ragLog("RAG generation failed: " + ex.getMessage());
                } finally {
                    Platform.runLater(() -> {
                        if (btnRagGenerateSpec != null) btnRagGenerateSpec.setDisable(false);
                        progressBar.setProgress(0);
                        updateButtonStates();
                    });
                }
                return null;
            }
        };
        new Thread(task).start();
    }

    private void previewRagScenarios() {
        String rdg = cbRagRdg != null ? cbRagRdg.getValue() : null;
        ScenarioData data = computeScenarioData();
        if (data == null || rdg == null || rdg.isBlank()) return;
        renderScenarioPreview(rdg, data);
        ragLog("Preview ready for " + rdg + " (+" + data.rows().size() + " row(s)): " + data.summary());
        updateButtonStates();
    }

    private void resetScenarioPreview() {
        String rdg = cbRagRdg != null ? cbRagRdg.getValue() : null;
        if (rdg == null || rdg.isBlank()) {
            ragLog("Select an RDG first.");
            return;
        }
        if (lastRagRecords == null || lastRagRecords.isEmpty()) {
            ragLog("Retrieve field records before resetting preview.");
            return;
        }
        ScenarioData data = computeScenarioData();
        if (data == null) return;
        renderScenarioPreview(rdg, data);
        ragLog("Preview reset for " + rdg + " (" + data.summary() + ").");
        updateButtonStates();
    }

    private ScenarioData computeScenarioData() {
        String rdg = cbRagRdg != null ? cbRagRdg.getValue() : null;
        if (rdg == null || rdg.isBlank()) {
            ragLog("Select an RDG first.");
            return null;
        }
        if (lastRagRecords == null || lastRagRecords.isEmpty()) {
            ragLog("Retrieve field records before generating scenarios.");
            return null;
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

        String mixText = tfRagScenarioMix != null ? tfRagScenarioMix.getText() : "";
        try {
            List<RagFieldRecord> rdgFiltered = RagScenarioGenerator.filterByRdgBlocks(lastRagRecords, rdg);
            if (rdgFiltered.isEmpty()) {
                ragLog("No fields available for RDG " + rdg + " after block filtering.");
                return null;
            }

            Map<String, Integer> mixCounts = parseMixCounts(mixText);
            if (!mixCounts.isEmpty()) {
                if (mixCounts.size() > 1) {
                    ragLog("Mixing multiple assessment tools in one sheet is not supported. Please run them separately: " + mixText);
                    return null;
                }
                Map.Entry<String, Integer> entry = mixCounts.entrySet().iterator().next();
                String tool = entry.getKey();
                int cnt = entry.getValue();
                tool = normalizeTool(tool);

                List<RagFieldRecord> filtered = RagScenarioGenerator.filterByAssessmentTool(rdgFiltered, tool);
                logMarkerWarnings(rdg, tool, filtered);
                List<RagFieldRecord> ordered = RagScenarioGenerator.ordered(filtered);
                List<String> headers = RagScenarioGenerator.buildHeaders(ordered);
                List<List<String>> scenarios = RagScenarioGenerator.generateScenarios(ordered, cnt);
                if (scenarios.isEmpty()) {
                    ragLog("No scenarios generated for " + rdg + " (" + tool + ").");
                    return null;
                }
                return new ScenarioData(headers, scenarios, "Tool: " + tool + " x " + cnt, tool);
            } else {
                String tool = cbRagAssessmentTool != null ? cbRagAssessmentTool.getValue() : "FIM";
                tool = normalizeTool(tool);
                List<RagFieldRecord> filtered = RagScenarioGenerator.filterByAssessmentTool(rdgFiltered, tool);
                logMarkerWarnings(rdg, tool, filtered);
                List<RagFieldRecord> ordered = RagScenarioGenerator.ordered(filtered);
                List<String> headers = RagScenarioGenerator.buildHeaders(ordered);
                List<List<String>> scenarios = RagScenarioGenerator.generateScenarios(ordered, scenarioCount);
                if (scenarios.isEmpty()) {
                    ragLog("No scenarios generated for " + rdg + ".");
                    return null;
                }
                return new ScenarioData(headers, scenarios, "Tool: " + tool + " x " + scenarioCount, tool);
            }
        } catch (Exception ex) {
            ragLog("Failed to build scenarios: " + ex.getMessage());
            return null;
        }
    }

    private LinkedHashMap<String, Integer> parseMixCounts(String mixText) {
        LinkedHashMap<String, Integer> counts = new LinkedHashMap<>();
        if (mixText == null || mixText.isBlank()) return counts;

        String[] parts = mixText.split(",");
        for (String part : parts) {
            if (part == null || part.isBlank()) continue;
            String[] kv = part.split(":");
            if (kv.length != 2) {
                ragLog("Invalid mix entry (use TOOL:count): " + part.trim());
                continue;
            }
            String tool = kv[0].trim().toUpperCase();
            if (tool.isEmpty()) continue;
            int cnt;
            try {
                cnt = Math.max(1, Integer.parseInt(kv[1].trim()));
            } catch (NumberFormatException ex) {
                ragLog("Invalid count for " + tool + ", skipping.");
                continue;
            }
            counts.merge(tool, cnt, Integer::sum);
        }
        return counts;
    }

    private void renderScenarioPreview(String rdg, ScenarioData data) {
        if (vbRagScenarioPreviews == null) return;
        String key = normalizeRdg(rdg);
        PreviewBundle bundle = previewBundles.get(key);
        boolean isNew = bundle == null;

        if (bundle == null) {
            TableView<Map<String, String>> tbl = new TableView<>();
            tbl.setEditable(true);
            tbl.setPrefHeight(260);
            tbl.setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY);
            ObservableList<Map<String, String>> rows = FXCollections.observableArrayList();
            Label lbl = new Label();
            bundle = new PreviewBundle(key, rdg, tbl, rows, lbl, new ArrayList<>(), "", normalizeTool(data.tool()));
            previewBundles.put(key, bundle);

            VBox wrapper = new VBox(4);
            wrapper.getChildren().addAll(lbl, tbl);
            wrapper.setFillWidth(true);
            vbRagScenarioPreviews.getChildren().add(wrapper);
        }

        bundle.headers.clear();
        bundle.headers.addAll(data.headers());
        bundle.summary = data.summary();
        bundle.tool = normalizeTool(data.tool());
        ensureColumns(bundle.table, bundle.headers);

        bundle.rows.clear();
        for (List<String> rowList : data.rows()) {
            Map<String, String> rowMap = FXCollections.observableHashMap();
            for (int i = 0; i < bundle.headers.size(); i++) {
                String header = bundle.headers.get(i);
                String value = i < rowList.size() ? rowList.get(i) : "";
                rowMap.put(header, value);
            }
            bundle.rows.add(rowMap);
        }

        bundle.table.setItems(bundle.rows);
        bundle.titleLabel.setText("Preview - " + rdg + " (" + bundle.rows.size() + " scenario(s); " + bundle.summary + ")");

        if (isNew) {
            bundle.table.refresh();
        }
    }

    private void ensureColumns(TableView<Map<String, String>> table, List<String> headers) {
        table.getColumns().clear();
        for (String header : headers) {
            TableColumn<Map<String, String>, String> col = new TableColumn<>(header);
            col.setCellValueFactory(cd -> {
                Map<String, String> row = cd.getValue();
                String val = row != null ? row.getOrDefault(header, "") : "";
                return new SimpleStringProperty(val);
            });
            col.setCellFactory(column -> createEditingCell());
            col.setOnEditCommit(event -> {
                Map<String, String> row = event.getRowValue();
                if (row != null) {
                    row.put(header, event.getNewValue());
                }
            });
            double width = columnWidth(header);
            col.setPrefWidth(width);
            col.setMinWidth(width);
            table.getColumns().add(col);
        }
    }

    private double columnWidth(String header) {
        if ("Name".equalsIgnoreCase(header)) return 180;
        if (header.startsWith("M##")) return 150;
        int len = header != null ? header.length() : 0;
        return Math.max(110, Math.min(220, len * 9.0));
    }

    private TextFieldTableCell<Map<String, String>, String> createEditingCell() {
        StringConverter<String> converter = new StringConverter<>() {
            @Override
            public String toString(String object) {
                return object == null ? "" : object;
            }

            @Override
            public String fromString(String string) {
                return string;
            }
        };
        return new TextFieldTableCell<>(converter) {
            @Override
            public void startEdit() {
                super.startEdit();
                TextField tf = (TextField) getGraphic();
                if (tf != null) {
                    tf.focusedProperty().addListener((obs, was, isNow) -> {
                        if (!isNow && isEditing()) {
                            commitEdit(tf.getText());
                        }
                    });
                }
            }
        };
    }

    private void openPreviewWindow() {
        commitScenarioEdits();
        PreviewBundle bundle = currentPreviewBundle();
        if (bundle == null) {
            ragLog("No preview available to open.");
            return;
        }
        TableView<Map<String, String>> tbl = new TableView<>();
        tbl.setEditable(true);
        tbl.setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY);
        ensureColumns(tbl, bundle.headers);

        ObservableList<Map<String, String>> copyRows = FXCollections.observableArrayList();
        for (Map<String, String> row : bundle.rows) {
            Map<String, String> copy = FXCollections.observableHashMap();
            copy.putAll(row);
            copyRows.add(copy);
        }
        tbl.setItems(copyRows);

        VBox content = new VBox(8);
        content.setPadding(new javafx.geometry.Insets(10));
        Label title = new Label("Preview - " + bundle.rdgDisplay + " (" + bundle.rows.size() + " scenario(s); " + bundle.summary + ")");
        Button saveBtn = new Button("Save");
        saveBtn.setOnAction(e -> {
            commitScenarioEdits(tbl);
            bundle.rows.clear();
            for (Map<String, String> row : tbl.getItems()) {
                Map<String, String> copy = FXCollections.observableHashMap();
                copy.putAll(row);
                bundle.rows.add(copy);
            }
            bundle.table.setItems(bundle.rows);
            ragLog("Preview saved to main view for " + bundle.rdgDisplay + ".");
        });
        content.getChildren().addAll(title, tbl, saveBtn);

        ScrollPane sp = new ScrollPane(content);
        sp.setFitToWidth(true);
        sp.setFitToHeight(true);

        Stage stage = new Stage();
        stage.setTitle("Preview - " + bundle.rdgDisplay);
        stage.setScene(new Scene(sp, 1200, 700));
        stage.show();
    }

    private List<List<String>> previewRowsAsLists() {
        PreviewBundle bundle = currentPreviewBundle();
        if (bundle == null) return List.of();
        List<List<String>> rows = new ArrayList<>();
        for (Map<String, String> map : bundle.rows) {
            List<String> row = new ArrayList<>();
            for (String header : bundle.headers) {
                row.add(map.getOrDefault(header, ""));
            }
            rows.add(row);
        }
        return rows;
    }

    private boolean hasScenarioPreview() {
        PreviewBundle bundle = currentPreviewBundle();
        return bundle != null && !bundle.headers.isEmpty() && !bundle.rows.isEmpty();
    }

    private void clearAllScenarioPreviews() {
        previewBundles.clear();
        if (vbRagScenarioPreviews != null) {
            vbRagScenarioPreviews.getChildren().clear();
        }
    }

    private void commitScenarioEdits() {
        PreviewBundle bundle = currentPreviewBundle();
        if (bundle != null && bundle.table.getEditingCell() != null) {
            bundle.table.edit(-1, null); // end editing, committing focus-lost edits via our cell factory
        }
    }

    private void commitScenarioEdits(TableView<Map<String, String>> table) {
        if (table != null && table.getEditingCell() != null) {
            table.edit(-1, null);
        }
    }

    private void saveCurrentPreview() {
        commitScenarioEdits();
        PreviewBundle bundle = currentPreviewBundle();
        if (bundle == null) {
            ragLog("No preview to save.");
            return;
        }
        bundle.table.refresh();
        ragLog("Preview saved for " + bundle.rdgDisplay + ".");
        updateButtonStates();
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

    private void chooseDepRules() {
        FileChooser fc = new FileChooser();
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("JSON Files", "*.json"));
        File f = fc.showOpenDialog(null);
        if (f != null) {
            depRulesFile = f;
            if (tfDepRules != null) tfDepRules.setText(f.getAbsolutePath());
        }
        updateButtonStates();
    }

    private void loadDepRules() {
        if (depRulesFile == null) {
            if (tfDepRules != null && tfDepRules.getText() != null && !tfDepRules.getText().isBlank()) {
                File f = new File(tfDepRules.getText().trim());
                if (f.exists()) depRulesFile = f;
            }
        }
        if (depRulesFile == null || !depRulesFile.exists()) {
            ragLog("Dependency rules file not found. Choose a JSON file.");
            return;
        }
        try {
            List<String> warnings = new ArrayList<>();
            List<DependencyRuleService.RuleEntry> rules = DependencyRuleService.load(depRulesFile, warnings);
            depRuleRows.clear();
            for (DependencyRuleService.RuleEntry ruleEntry : rules) {
                String mandatoryWhen = summarizeMandatory(ruleEntry.rule());
                String optionalWhen = summarizeOptional(ruleEntry.rule());
                depRuleRows.add(new DependencyRuleRow(ruleEntry.name(), ruleEntry.required(), mandatoryWhen, optionalWhen, ruleEntry.rule()));
            }
            ragLog("Loaded " + depRuleRows.size() + " dependency rules from " + depRulesFile.getAbsolutePath());
            for (String w : warnings) {
                ragLog("[Dependency] " + w);
            }
            if (depRuleRows.isEmpty()) {
                ragLog("No 'refer to rule' entries found in file.");
            }
        } catch (Exception ex) {
            ragLog("Failed to load dependency rules: " + ex.getMessage());
        }
        updateButtonStates();
    }

    private String summarizeMandatory(String rule) {
        if (rule == null || rule.isBlank()) return "Refer to rule text.";
        String[] parts = rule.split("\\.|\n");
        for (String p : parts) {
            String t = p.trim();
            if (!t.isEmpty()) {
                if (t.length() > 180) return t.substring(0, 177) + "...";
                return t;
            }
        }
        return rule.length() > 180 ? rule.substring(0, 177) + "..." : rule;
    }

    private String summarizeOptional(String rule) {
        if (rule == null || rule.isBlank()) return "Otherwise optional.";
        return "Otherwise optional (when condition above is not met).";
    }

    private String getString(Object obj) {
        return obj == null ? "" : obj.toString();
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
            String sheetName = sheetNameForSpec(rdg);
            RagExcelWriter.writeSpec(ragWorkbookFile, sheetName, records);
            ragLog("Wrote sheet '" + sheetName + "' to " + ragWorkbookFile.getAbsolutePath());
        } catch (Exception ex) {
            ragLog("Failed to write workbook: " + ex.getMessage());
        }
        updateButtonStates();
    }

    private void openRagSpecPreview() {
        String rdg = cbRagRdg != null ? cbRagRdg.getValue() : null;
        String payload = taRagOutput != null ? taRagOutput.getText() : "";
        if (payload == null || payload.isBlank()) {
            ragLog("Generate or paste a JSON spec before previewing.");
            return;
        }

        TextArea ta = new TextArea(payload);
        ta.setEditable(false);
        ta.setWrapText(true);
        ta.setPrefRowCount(40);
        ta.setStyle("-fx-font-family: 'SFMono-Regular', 'JetBrains Mono', 'Consolas', monospace; -fx-font-size: 12px;");

        VBox box = new VBox(10, new Label("Offline JSON spec" + (rdg != null && !rdg.isBlank() ? " for " + rdg : "")), ta);
        box.setPadding(new javafx.geometry.Insets(12));
        box.setFillWidth(true);

        ScrollPane sp = new ScrollPane(box);
        sp.setFitToWidth(true);
        sp.setFitToHeight(true);

        Stage stage = new Stage();
        stage.setTitle("Spec Preview" + (rdg != null && !rdg.isBlank() ? " - " + rdg : ""));
        stage.setScene(new Scene(sp, 1100, 700));
        stage.show();
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
        boolean hasRecords = lastRagRecords != null && !lastRagRecords.isEmpty();
        if (!hasRecords && !hasScenarioPreview()) {
            ragLog("Retrieve field records or build a preview before writing scenarios.");
            return;
        }

        try {
            boolean usingPreview = hasScenarioPreview();
            commitScenarioEdits();
            ScenarioData data = usingPreview
                    ? previewDataForCurrentRdg()
                    : computeScenarioData();

            if (data == null) return;

            String sheetName = sheetNameForData(rdg);
            String requestedTool = normalizeTool(data.tool());
            String existingTool = normalizeTool(detectExistingScenarioTool(ragWorkbookFile, sheetName));
            if (!requestedTool.isEmpty() && !existingTool.isEmpty() && !existingTool.equals(requestedTool)) {
                ragLog("Scenario sheet '" + sheetName + "' already contains " + existingTool
                        + " scenarios. Create a separate RDG/sheet for " + requestedTool + ".");
                return;
            }

            RagScenarioExcelWriter.write(ragWorkbookFile, sheetName, data.headers(), data.rows());
            ragLog("Scenario sheet '" + sheetName + "' written "
                    + (usingPreview ? "from preview edits" : "(" + data.summary() + ")")
                    + " to " + ragWorkbookFile.getAbsolutePath());
        } catch (Exception ex) {
            ragLog("Failed to write scenarios: " + ex.getMessage());
        }
        updateButtonStates();
    }

    private record ScenarioData(List<String> headers, List<List<String>> rows, String summary, String tool) {
    }

    private PreviewBundle currentPreviewBundle() {
        String key = normalizeRdg(cbRagRdg != null ? cbRagRdg.getValue() : null);
        if (key.isEmpty()) return null;
        return previewBundles.get(key);
    }

    private ScenarioData previewDataForCurrentRdg() {
        PreviewBundle bundle = currentPreviewBundle();
        if (bundle == null) return null;
        return new ScenarioData(new ArrayList<>(bundle.headers), previewRowsAsLists(), bundle.summary, bundle.tool);
    }

    private String sheetNameForSpec(String rdg) {
        return "RDG_" + safeSheetName(rdg) + "_specs";
    }

    private String sheetNameForData(String rdg) {
        return "RDG_" + safeSheetName(rdg);
    }

    private String safeSheetName(String rdg) {
        String base = rdg == null ? "unknown" : rdg.trim();
        base = base.replaceAll("[\\\\/*?:\\[\\]]", "_");
        base = base.replaceAll("\\s+", "_");
        return base;
    }

    private String normalizeRdg(String rdg) {
        if (rdg == null) return "";
        return rdg.trim().toLowerCase(java.util.Locale.ROOT);
    }

    private String normalizeTool(String tool) {
        if (tool == null) return "";
        return tool.trim().toUpperCase(Locale.ROOT);
    }

    private void logMarkerWarnings(String rdg, String tool, List<RagFieldRecord> filtered) {
        List<String> warnings = RagScenarioGenerator.validateAssessmentMarkers(filtered, tool);
        for (String w : warnings) {
            ragLog("[Marker] " + w + (rdg != null && !rdg.isBlank() ? " for " + rdg : ""));
        }
    }

    private String detectExistingScenarioTool(File workbookFile, String sheetName) {
        if (workbookFile == null || sheetName == null || sheetName.isBlank() || !workbookFile.exists()) return "";
        try (FileInputStream fis = new FileInputStream(workbookFile);
             Workbook wb = new XSSFWorkbook(fis)) {
            int idx = wb.getSheetIndex(sheetName);
            if (idx < 0) return "";
            Sheet sheet = wb.getSheetAt(idx);
            Row headerRow = sheet.getRow(0);
            if (headerRow == null) return "";
            List<String> headers = new ArrayList<>();
            for (Cell cell : headerRow) {
                if (cell != null) headers.add(cell.toString());
            }
            return RagScenarioGenerator.detectAssessmentTool(headers);
        } catch (Exception ex) {
            ragLog("Could not inspect existing scenario sheet: " + ex.getMessage());
            return "";
        }
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

    private static final class PreviewBundle {
        final String rdgKey;
        final String rdgDisplay;
        final TableView<Map<String, String>> table;
        final ObservableList<Map<String, String>> rows;
        final Label titleLabel;
        final List<String> headers;
        String summary;
        String tool;

        PreviewBundle(String rdgKey, String rdgDisplay, TableView<Map<String, String>> table,
                      ObservableList<Map<String, String>> rows, Label titleLabel,
                      List<String> headers, String summary, String tool) {
            this.rdgKey = rdgKey;
            this.rdgDisplay = rdgDisplay;
            this.table = table;
            this.rows = rows;
            this.titleLabel = titleLabel;
            this.headers = headers;
            this.summary = summary;
            this.tool = tool;
        }
    }

    private record DependencyRuleRow(String name, String required, String mandatoryWhen, String optionalWhen, String rule) {
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
        if (btnRagGenerateSpec != null) {
            boolean hasRecords = lastRagRecords != null && !lastRagRecords.isEmpty();
            btnRagGenerateSpec.setDisable(!hasRecords);
        }
        if (btnRagPreviewScenarios != null) {
            boolean hasRecords = lastRagRecords != null && !lastRagRecords.isEmpty();
            boolean hasRdg = cbRagRdg != null && cbRagRdg.getValue() != null && !cbRagRdg.getValue().isBlank();
            btnRagPreviewScenarios.setDisable(!(hasRecords && hasRdg));
        }
        if (btnRagResetPreview != null) {
            boolean hasRecords = lastRagRecords != null && !lastRagRecords.isEmpty();
            boolean hasRdg = cbRagRdg != null && cbRagRdg.getValue() != null && !cbRagRdg.getValue().isBlank();
            btnRagResetPreview.setDisable(!(hasRecords && hasRdg));
        }
        if (btnRagSavePreview != null) {
            btnRagSavePreview.setDisable(!hasScenarioPreview());
        }
        if (btnRagOpenPreviewWindow != null) {
            btnRagOpenPreviewWindow.setDisable(!hasScenarioPreview());
        }
        if (btnRagOpenSpecPreview != null) {
            boolean hasPayload = taRagOutput != null
                    && taRagOutput.getText() != null
                    && !taRagOutput.getText().isBlank();
            btnRagOpenSpecPreview.setDisable(!hasPayload);
        }
        if (btnRagWriteSheet != null) {
            boolean hasPayload = taRagOutput != null
                    && taRagOutput.getText() != null
                    && !taRagOutput.getText().isBlank();
            btnRagWriteSheet.setDisable(ragWorkbookFile == null || !hasPayload);
        }
        if (btnRagWriteScenarios != null) {
            boolean hasData = hasScenarioPreview() || (lastRagRecords != null && !lastRagRecords.isEmpty());
            btnRagWriteScenarios.setDisable(ragWorkbookFile == null || !hasData);
        }
        if (btnDepLoadRules != null) btnDepLoadRules.setDisable(false);
    }


    private void log(String msg) {
        Platform.runLater(() -> {
            taLog.appendText(msg + "\n");
        });
    }
}
