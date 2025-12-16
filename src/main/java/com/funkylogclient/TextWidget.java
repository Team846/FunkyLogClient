package com.funkylogclient;

import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.layout.Priority;
import javafx.geometry.Pos;
import javafx.application.Platform;
import edu.wpi.first.networktables.NetworkTableInstance;
import edu.wpi.first.networktables.NetworkTable;
import edu.wpi.first.networktables.NetworkTableEntry;

public class TextWidget extends DashboardWidget {
    private TextField valueField;
    private Label valueLabel;
    private Button confirmButton;
    private boolean isEditable;
    private boolean isUpdating = false;
    private String currentNTValue = null;

    public TextWidget(String title, String key) {
        super(title, key);
        this.isEditable = key.startsWith("Preferences/");
        createTextDisplay();
    }

    private void createTextDisplay() {
        if (isEditable) {
            valueField = new TextField("--");
            valueField.setStyle(
                    "-fx-font-size: 24px; -fx-font-weight: bold; -fx-font-family: " + Styles.FONT_FAMILY + "; -fx-text-fill: " + Styles.TEXT_WHITE + "; -fx-background-color: transparent; -fx-border-width: 0; -fx-padding: 0; -fx-alignment: center;");
            valueField.setEditable(true);
            
            confirmButton = new Button("OK");
            confirmButton.setText("OK");
            confirmButton.setStyle(
                    "-fx-font-size: 12px; -fx-background-color: #CC7000; -fx-text-fill: " + Styles.TEXT_WHITE + "; -fx-padding: 6px 12px; -fx-background-radius: 4px; -fx-cursor: default; -fx-min-width: 50px; -fx-pref-width: 50px; -fx-max-width: 50px; -fx-min-height: 30px; -fx-pref-height: 30px; -fx-max-height: 30px; -fx-opacity: 0.6; -fx-text-overrun: clip;");
            confirmButton.setVisible(true);
            confirmButton.setDisable(true);
            confirmButton.setOnAction(e -> writeValueBack());
            confirmButton.setOnMouseEntered(e -> {
                if (!confirmButton.isDisabled()) {
                    confirmButton.setStyle(
                            "-fx-font-size: 12px; -fx-background-color: " + Styles.ACCENT_HOVER + "; -fx-text-fill: " + Styles.TEXT_WHITE + "; -fx-padding: 6px 12px; -fx-background-radius: 4px; -fx-cursor: hand; -fx-min-width: 50px; -fx-pref-width: 50px; -fx-max-width: 50px; -fx-min-height: 30px; -fx-pref-height: 30px; -fx-max-height: 30px;");
                }
            });
            confirmButton.setOnMouseExited(e -> updateButtonState());
            
            valueField.textProperty().addListener((obs, oldVal, newVal) -> {
                if (!isUpdating) {
                    updateButtonState();
                }
            });
            
            valueField.setOnAction(e -> {
                if (!confirmButton.isDisabled()) {
                    writeValueBack();
                }
            });
            
            HBox inputContainer = new HBox(12);
            inputContainer.setAlignment(Pos.CENTER);
            inputContainer.getChildren().addAll(valueField, confirmButton);
            
            VBox textContainer = new VBox(0);
            textContainer.setAlignment(Pos.CENTER);
            textContainer.getChildren().add(inputContainer);
            VBox.setVgrow(textContainer, Priority.ALWAYS);
            
            contentBox.getChildren().add(textContainer);
        } else {
            valueLabel = new Label("--");
            valueLabel.setStyle(
                    "-fx-font-size: 36px; -fx-font-weight: normal; -fx-font-family: " + Styles.FONT_FAMILY + "; -fx-text-fill: " + Styles.TEXT_SECONDARY + "; -fx-background-color: transparent; -fx-padding: 0; -fx-alignment: center;");
            valueLabel.setAlignment(Pos.CENTER);
            valueLabel.setMaxWidth(Double.MAX_VALUE);
            HBox.setHgrow(valueLabel, Priority.ALWAYS);
            
            VBox textContainer = new VBox(0);
            textContainer.setAlignment(Pos.TOP_CENTER);
            textContainer.setPadding(new javafx.geometry.Insets(-8, 0, 0, 0));
            textContainer.getChildren().add(valueLabel);
            VBox.setVgrow(textContainer, Priority.ALWAYS);
            
            contentBox.getChildren().add(textContainer);
        }
    }

    private void updateButtonState() {
        if (!isEditable || valueField == null || confirmButton == null) {
            return;
        }
        
        String fieldValue = valueField.getText();
        boolean hasChange = currentNTValue != null && !fieldValue.equals(currentNTValue);
        
        Platform.runLater(() -> {
            confirmButton.setDisable(!hasChange);
            confirmButton.setText("OK");
            if (hasChange) {
                confirmButton.setStyle(
                        "-fx-font-size: 12px; -fx-background-color: " + Styles.ACCENT_PRIMARY + "; -fx-text-fill: " + Styles.TEXT_WHITE + "; -fx-padding: 6px 12px; -fx-background-radius: 4px; -fx-cursor: hand; -fx-min-width: 50px; -fx-pref-width: 50px; -fx-max-width: 50px; -fx-min-height: 30px; -fx-pref-height: 30px; -fx-max-height: 30px; -fx-opacity: 1.0; -fx-text-overrun: clip;");
            } else {
                confirmButton.setStyle(
                        "-fx-font-size: 12px; -fx-background-color: #CC7000; -fx-text-fill: " + Styles.TEXT_WHITE + "; -fx-padding: 6px 12px; -fx-background-radius: 4px; -fx-cursor: default; -fx-min-width: 50px; -fx-pref-width: 50px; -fx-max-width: 50px; -fx-min-height: 30px; -fx-pref-height: 30px; -fx-max-height: 30px; -fx-opacity: 0.6; -fx-text-overrun: clip;");
            }
        });
    }
    
    private void writeValueBack() {
        if (!isEditable || isUpdating || confirmButton.isDisabled()) {
            return;
        }

        try {
            String textValue = valueField.getText();

            isUpdating = true;
            try {
                NetworkTableInstance instance = NetworkTableInstance.getDefault();
                NetworkTable table = instance.getTable(key.split("/")[0]);
                String secondPartKey = key.substring(key.split("/")[0].length() + 1);
                if (instance != null && table != null) {
                    NetworkTableEntry entry = table.getEntry(secondPartKey);
                    entry.setString(textValue);
                    System.out.println("Wrote value " + textValue + " back to " + key);
                    currentNTValue = textValue;
                }
            } finally {
                isUpdating = false;
            }

            Platform.runLater(() -> updateButtonState());
        } catch (Exception e) {
            System.err.println("Error writing value back: " + e.getMessage());
        }
    }

    @Override
    public void updateValue(Object value) {
        if (isEditable) {
            if (isUpdating || valueField.isFocused()) {
                return;
            }
            
            isUpdating = true;
            try {
                String newValue = null;
                if (value != null) {
                    if (value instanceof String) {
                        newValue = (String) value;
                    } else {
                        newValue = value.toString();
                    }
                } else {
                    newValue = "--";
                }
                
                valueField.setText(newValue);
                currentNTValue = newValue;
            } finally {
                isUpdating = false;
            }
            
            Platform.runLater(() -> updateButtonState());
        } else {
            if (isUpdating) {
                return;
            }
            
            isUpdating = true;
            try {
                if (value != null) {
                    if (value instanceof String) {
                        valueLabel.setText((String) value);
                    } else {
                        valueLabel.setText(value.toString());
                    }
                } else {
                    valueLabel.setText("--");
                }
            } finally {
                isUpdating = false;
            }
        }
    }

    @Override
    public Node getContent() {
        return container;
    }
}
