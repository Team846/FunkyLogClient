package com.funkylogclient;

import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

public class RoboRIOLogFetcher {

    private static Stage popupStage;

    public static void showDialog(Stage parentStage) {
        popupStage = new Stage();
        popupStage.setTitle("Fetch Logs from RoboRIO");
        popupStage.initModality(Modality.APPLICATION_MODAL);
        popupStage.initOwner(parentStage);

        BorderPane root = new BorderPane();
        root.setStyle("-fx-background-color: " + Styles.BG_DARKEST + "; -fx-border-color: " + Styles.BORDER_DARK + "; -fx-border-width: 1px;");

        VBox content = new VBox(12);
        content.setPadding(new Insets(16));
        content.setAlignment(Pos.TOP_CENTER);

        Label titleLabel = new Label("RoboRIO Log Files");
        titleLabel.setStyle("-fx-text-fill: white; -fx-font-size: 16px; -fx-font-weight: bold;");

        ListView<String> fileListView = new ListView<>();
        fileListView.setStyle("-fx-background-color: " + Styles.BG_DARK + "; -fx-text-fill: " + Styles.TEXT_PRIMARY + ";");
        VBox.setVgrow(fileListView, Priority.ALWAYS);

        HBox addressBox = new HBox(8);
        addressBox.setAlignment(Pos.CENTER_LEFT);
        Label ipLabel = new Label("Target IP/Hostname:");
        ipLabel.setStyle("-fx-text-fill: " + Styles.TEXT_SECONDARY + ";");
        TextField ipField = new TextField(UDPClient.serverIP);
        ipField.setStyle(Styles.SEARCH_BAR_STYLE);
        Button refreshButton = new Button("Refresh Files");
        refreshButton.setStyle(Styles.BUTTON_STYLE);
        
        addressBox.getChildren().addAll(ipLabel, ipField, refreshButton);

        refreshButton.setOnAction(e -> {
            fetchFileList(ipField.getText(), fileListView, refreshButton);
        });

        HBox matchBox = new HBox(8);
        matchBox.setAlignment(Pos.CENTER_LEFT);
        Label matchLabel = new Label("Match Number (optional):");
        matchLabel.setStyle("-fx-text-fill: " + Styles.TEXT_SECONDARY + ";");
        TextField matchField = new TextField();
        matchField.setStyle(Styles.SEARCH_BAR_STYLE);
        matchField.setPromptText("e.g. 12");
        matchBox.getChildren().addAll(matchLabel, matchField);

        HBox buttonBox = new HBox(12);
        buttonBox.setAlignment(Pos.CENTER_RIGHT);
        Button downloadButton = new Button("Download & Open");
        downloadButton.setStyle(Styles.BUTTON_STYLE);
        downloadButton.setDisable(true);
        Button cancelButton = new Button("Cancel");
        cancelButton.setStyle(Styles.BUTTON_STYLE);

        fileListView.getSelectionModel().selectedItemProperty().addListener((obs, oldV, newV) -> {
            downloadButton.setDisable(newV == null);
        });

        downloadButton.setOnAction(e -> {
            String selectedFile = fileListView.getSelectionModel().getSelectedItem();
            if (selectedFile != null) {
                downloadFile(ipField.getText(), selectedFile, matchField.getText(), parentStage);
                popupStage.close();
            }
        });

        cancelButton.setOnAction(e -> popupStage.close());

        buttonBox.getChildren().addAll(cancelButton, downloadButton);

        content.getChildren().addAll(titleLabel, addressBox, fileListView, matchBox, buttonBox);
        root.setCenter(content);

        Scene scene = new Scene(root, 500, 400);
        popupStage.setScene(scene);
        
        fetchFileList(ipField.getText(), fileListView, refreshButton);
        
        popupStage.showAndWait();
    }

    private static void fetchFileList(String ip, ListView<String> listView, Button refreshBtn) {
        refreshBtn.setDisable(true);
        listView.getItems().clear();
        listView.getItems().add("Loading...");

        Task<List<String>> task = new Task<>() {
            @Override
            protected List<String> call() throws Exception {
                List<String> files = new ArrayList<>();
                try {
                    ProcessBuilder pb = new ProcessBuilder(
                        "ssh", 
                        "-o", "StrictHostKeyChecking=no",
                        "-o", "ConnectTimeout=5",
                        "-o", "BatchMode=yes",
                        "lvuser@" + ip, 
                        "ls -1 /home/lvuser/foresting/*.log846"
                    );
                    
                    Process process = pb.start();
                    java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.InputStreamReader(process.getInputStream()));
                    String line;
                    while ((line = reader.readLine()) != null) {
                        String[] parts = line.split("/");
                        files.add(parts[parts.length - 1]);
                    }
                    
                    files.sort(java.util.Collections.reverseOrder());
                    
                    process.waitFor();
                } catch (Exception e) {
                    throw new RuntimeException("SSH ls failed", e);
                }
                return files;
            }
        };

        task.setOnSucceeded(e -> {
            listView.getItems().clear();
            listView.getItems().addAll(task.getValue());
            refreshBtn.setDisable(false);
        });

        task.setOnFailed(e -> {
            listView.getItems().clear();
            listView.getItems().add("Error connecting to " + ip + ": " + task.getException().getMessage());
            refreshBtn.setDisable(false);
        });

        new Thread(task).start();
    }

    private static void downloadFile(String ip, String remoteFilename, String matchNum, Stage parentStage) {
        Task<File> task = new Task<>() {
            @Override
            protected File call() throws Exception {
                File targetFile = null;
                try {
                    String remotePath = "/home/lvuser/foresting/" + remoteFilename;
                    String localFilename = remoteFilename;
                    if (matchNum != null && !matchNum.trim().isEmpty()) {
                        localFilename = "Match_" + matchNum.trim() + "_" + remoteFilename;
                    }

                    File localDir = new File(FunkyLogSorter.getLogFileDirectory());
                    if (!localDir.exists()) localDir.mkdirs();
                    targetFile = new File(localDir, localFilename);

                    ProcessBuilder pb = new ProcessBuilder(
                        "scp",
                        "-o", "StrictHostKeyChecking=no",
                        "-o", "ConnectTimeout=5",
                        "-o", "BatchMode=yes",
                        "lvuser@" + ip + ":" + remotePath,
                        targetFile.getAbsolutePath()
                    );
                    
                    Process process = pb.start();
                    int exitCode = process.waitFor();
                    if (exitCode != 0) {
                        throw new RuntimeException("SCP failed with exit code " + exitCode);
                    }
                } catch (Exception e) {
                    throw new RuntimeException("SCP transfer failed", e);
                }
                return targetFile;
            }
        };

        task.setOnSucceeded(e -> {
            File result = task.getValue();
            if (result != null && result.exists()) {
                LogFileProcesser.readFile(result, parentStage);
            }
        });

        task.setOnFailed(e -> {
            System.err.println("SCP transfer failed: " + task.getException().getMessage());
        });

        new Thread(task).start();
    }
}
