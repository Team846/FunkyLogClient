package com.funkylogclient;

import javafx.beans.value.ChangeListener;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import javafx.stage.Stage;

public class SidebarOtherSettings {
    public static VBox getSidebarOtherSettings(ChangeListener<Boolean> autoScrollChangeListener, Stage primaryStage) {
        VBox otherSettingsBox = new VBox(10);
        otherSettingsBox.setPadding(new Insets(12, 0, 12, 0));

        HBox autoScrollBox = new HBox(12);
        autoScrollBox.setAlignment(Pos.CENTER_LEFT);
        autoScrollBox.setPadding(new Insets(6, 10, 6, 10));
        autoScrollBox.setStyle("-fx-background-color: #2A2F35; -fx-background-radius: 6px;");

        autoScrollBox.setOnMouseEntered(e -> autoScrollBox.setStyle("-fx-background-color: #323840; -fx-background-radius: 6px;"));
        autoScrollBox.setOnMouseExited(e -> autoScrollBox.setStyle("-fx-background-color: #2A2F35; -fx-background-radius: 6px;"));

        CheckBox autoScrollCheckBox = new CheckBox();
        autoScrollCheckBox.setSelected(true);
        autoScrollCheckBox.setStyle(Styles.CHECKBOX_STYLE);
        autoScrollCheckBox.selectedProperty().addListener(autoScrollChangeListener);

        Text autoScrollLabel = new Text("Auto-scroll");
        autoScrollLabel.setStyle("-fx-font-size: 13px; -fx-fill: " + Styles.TEXT_PRIMARY + "; -fx-font-family: " + Styles.FONT_FAMILY + ";");

        autoScrollBox.getChildren().addAll(autoScrollCheckBox, autoScrollLabel);

        autoScrollBox.setOnMouseClicked(e -> {
            if (e.getTarget() != autoScrollCheckBox) {
                autoScrollCheckBox.setSelected(!autoScrollCheckBox.isSelected());
            }
        });

        otherSettingsBox.getChildren().add(autoScrollBox);

        Region midSpacing = new Region();
        midSpacing.setMinHeight(4);
        otherSettingsBox.getChildren().add(midSpacing);

        VBox buttonsContainer = new VBox(8);

        Button clearLogsButton = new Button("Clear Logs");
        clearLogsButton.setStyle(Styles.BUTTON_STYLE);
        clearLogsButton.setMaxWidth(Double.MAX_VALUE);
        clearLogsButton.setOnAction((ev) -> {
            FunkyLogSorter.clear();
            FunkyLogSorter.makeNewLogFile();
        });

        clearLogsButton.setOnMouseEntered(e -> clearLogsButton.setStyle(Styles.BUTTON_HOVER_STYLE));
        clearLogsButton.setOnMouseExited(e -> clearLogsButton.setStyle(Styles.BUTTON_STYLE));
        clearLogsButton.setOnMousePressed(e -> clearLogsButton.setStyle(Styles.BUTTON_PRESSED_STYLE));
        clearLogsButton.setOnMouseReleased(e -> clearLogsButton.setStyle(Styles.BUTTON_HOVER_STYLE));

        HBox.setHgrow(clearLogsButton, Priority.ALWAYS);
        buttonsContainer.getChildren().add(clearLogsButton);

        Button fileSelectionDialog = new Button("Open Log File");
        fileSelectionDialog.setStyle(Styles.BUTTON_STYLE);
        fileSelectionDialog.setMaxWidth(Double.MAX_VALUE);
        fileSelectionDialog.setOnAction((ev) -> {
            LogFileProcesser.selectFile(primaryStage);
        });

        fileSelectionDialog.setOnMouseEntered(e -> fileSelectionDialog.setStyle(Styles.BUTTON_HOVER_STYLE));
        fileSelectionDialog.setOnMouseExited(e -> fileSelectionDialog.setStyle(Styles.BUTTON_STYLE));
        fileSelectionDialog.setOnMousePressed(e -> fileSelectionDialog.setStyle(Styles.BUTTON_PRESSED_STYLE));
        fileSelectionDialog.setOnMouseReleased(e -> fileSelectionDialog.setStyle(Styles.BUTTON_HOVER_STYLE));

        HBox.setHgrow(fileSelectionDialog, Priority.ALWAYS);
        buttonsContainer.getChildren().add(fileSelectionDialog);

        otherSettingsBox.getChildren().add(buttonsContainer);

        return otherSettingsBox;
    }
}
