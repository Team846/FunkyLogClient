package com.funkylogclient;

import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import javafx.stage.Stage;

public class SidebarOtherSettings {
    public static VBox getSidebarOtherSettings(ChangeListener<Boolean> autoScrollChangeListener, Stage primaryStage) {
        VBox otherSettingsBox = new VBox();
        otherSettingsBox.setPadding(new Insets(25, 5, 5, 5));

        Text autoScrollLabel = new Text("Auto-scroll:");
        autoScrollLabel.setStyle(Styles.TEXT_MED);
        otherSettingsBox.getChildren().add(autoScrollLabel);

        CheckBox autoScrollCheckBox = new CheckBox();
        autoScrollCheckBox.setSelected(true);
        autoScrollCheckBox.selectedProperty().addListener(autoScrollChangeListener);
        otherSettingsBox.getChildren().add(autoScrollCheckBox);

        Region midSpacing = new Region();
        midSpacing.setMinHeight(20);
        otherSettingsBox.getChildren().add(midSpacing);

        Button clearLogsButton = new Button("Clear logs");
        clearLogsButton.setOnAction((ev) -> {FunkyLogSorter.clear();});

        otherSettingsBox.getChildren().add(clearLogsButton);


        Region buttonSpacing = new Region();
        buttonSpacing.setMinHeight(20);
        otherSettingsBox.getChildren().add(buttonSpacing);

        Button exportButton = new Button("Save logs");
        exportButton.setOnAction((ev) -> {FunkyLogSorter.saveToFile(primaryStage);});
        otherSettingsBox.getChildren().add(exportButton);

        Region fileSelectionDialogSpacing = new Region();
        fileSelectionDialogSpacing.setMinHeight(20);
        otherSettingsBox.getChildren().add(fileSelectionDialogSpacing);

        Button fileSelectionDialog = new Button("Select File");
        fileSelectionDialog.setOnAction((ev) -> {
            LogFileProcesser.selectFile(primaryStage);
        });
        otherSettingsBox.getChildren().add(fileSelectionDialog);

        Region testLogButtonspacing = new Region();
        testLogButtonspacing.setMinHeight(20);
        otherSettingsBox.getChildren().add(testLogButtonspacing);
  
        Button testLogButton = new Button("Test Log");
        testLogButton.setOnAction((ev) -> {
            FunkyLogSorter.createTestLog(primaryStage);
        });
        otherSettingsBox.getChildren().add(testLogButton);

        Button testWarningButton = new Button("Test Warning");
        testWarningButton.setOnAction((ev) -> {
            FunkyLogSorter.createTestWarning(primaryStage);
        });
        otherSettingsBox.getChildren().add(testWarningButton);

        Button testErrorButton = new Button("Test Error");
        testErrorButton.setOnAction((ev) -> {
            FunkyLogSorter.createTestError(primaryStage);
        });
        otherSettingsBox.getChildren().add(testErrorButton);

     


        return otherSettingsBox;
    }
}
