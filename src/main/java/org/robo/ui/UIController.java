package org.robo.ui;



import org.robo.core.CryptoUtil;
import org.robo.core.ExcelProcessor;
import org.robo.core.SftpUtil;
import org.robo.core.Utils;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.FileChooser;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.format.DateTimeFormatter;
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

    private File excelFile;
    private File encodeFile;
    private File privateKeyFile;
    private File lastGeneratedFile;

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
        cbSheet.setOnAction(e -> populateScenarios());
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
    }


    private void log(String msg) {
        Platform.runLater(() -> {
            taLog.appendText(msg + "\n");
        });
    }
}
