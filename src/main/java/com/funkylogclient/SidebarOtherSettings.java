package com.funkylogclient;

import javafx.beans.value.ChangeListener;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import javafx.stage.Stage;

public class SidebarOtherSettings {
    public static VBox getSidebarOtherSettings(ChangeListener<Boolean> autoScrollChangeListener, Stage primaryStage) {
        VBox otherSettingsBox = new VBox();
        otherSettingsBox.setPadding(new Insets(8, 5, 2, 5));

        HBox autoScrollBox = new HBox(10);
        Text autoScrollLabel = new Text("Auto Scroll:");
        autoScrollLabel
                .setStyle("-fx-font-size: 14px; -fx-fill: #FFFFFF; -fx-font-family: 'Segoe UI', 'Roboto', sans-serif;");

        CheckBox autoScrollCheckBox = new CheckBox();
        autoScrollCheckBox.setSelected(true);
        autoScrollCheckBox.setStyle(Styles.CHECKBOX_STYLE);
        autoScrollCheckBox.selectedProperty().addListener(autoScrollChangeListener);

        autoScrollBox.getChildren().addAll(autoScrollLabel, autoScrollCheckBox);
        otherSettingsBox.getChildren().add(autoScrollBox);

        Region midSpacing = new Region();
        midSpacing.setMinHeight(5);
        otherSettingsBox.getChildren().add(midSpacing);

        Button clearLogsButton = new Button("Clear Logs");
        clearLogsButton.setStyle(Styles.BUTTON_STYLE);
        clearLogsButton.setMaxWidth(Double.MAX_VALUE);
        clearLogsButton.setOnAction((ev) -> {
            FunkyLogSorter.clear();
            FunkyLogSorter.makeNewLogFile();
        });

        clearLogsButton.setOnMouseEntered(e -> clearLogsButton.setStyle(Styles.BUTTON_HOVER_STYLE));
        clearLogsButton.setOnMouseExited(e -> clearLogsButton.setStyle(Styles.BUTTON_STYLE));

        otherSettingsBox.getChildren().add(clearLogsButton);

        // Region buttonSpacing = new Region();
        // buttonSpacing.setMinHeight(20);
        // otherSettingsBox.getChildren().add(buttonSpacing);

        // Button exportButton = new Button("Save logs");
        // exportButton.setOnAction((ev) -> {
        // FunkyLogSorter.saveToFile(primaryStage);
        // });
        // otherSettingsBox.getChildren().add(exportButton);

        Region fileSelectionDialogSpacing = new Region();
        fileSelectionDialogSpacing.setMinHeight(5);
        otherSettingsBox.getChildren().add(fileSelectionDialogSpacing);

        Button fileSelectionDialog = new Button("Open Log File");
        fileSelectionDialog.setStyle(Styles.BUTTON_STYLE);
        fileSelectionDialog.setMaxWidth(Double.MAX_VALUE);
        fileSelectionDialog.setOnAction((ev) -> {
            LogFileProcesser.selectFile(primaryStage);
        });

        fileSelectionDialog.setOnMouseEntered(e -> fileSelectionDialog.setStyle(Styles.BUTTON_HOVER_STYLE));
        fileSelectionDialog.setOnMouseExited(e -> fileSelectionDialog.setStyle(Styles.BUTTON_STYLE));

        otherSettingsBox.getChildren().add(fileSelectionDialog);

        // Region testLogButtonspacing = new Region();
        // testLogButtonspacing.setMinHeight(20);
        // otherSettingsBox.getChildren().add(testLogButtonspacing);

        // Button testLogButton = new Button("Test Log");
        // testLogButton.setOnAction((ev) -> {
        // FunkyLogSorter.createTestLog(primaryStage);
        // });
        // otherSettingsBox.getChildren().add(testLogButton);

        // Button testWarningButton = new Button("Test Warning");
        // testWarningButton.setOnAction((ev) -> {
        // FunkyLogSorter.createTestWarning(primaryStage);
        // });
        // otherSettingsBox.getChildren().add(testWarningButton);

        // Button testErrorButton = new Button("Test Error");
        // testErrorButton.setOnAction((ev) -> {
        // FunkyLogSorter.createTestError(primaryStage);
        // });
        // otherSettingsBox.getChildren().add(testErrorButton);

        return otherSettingsBox;
    }
}
