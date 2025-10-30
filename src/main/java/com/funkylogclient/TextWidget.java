package com.funkylogclient;

import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.geometry.Pos;
import javafx.application.Platform;
import edu.wpi.first.networktables.NetworkTableInstance;
import edu.wpi.first.networktables.NetworkTable;
import edu.wpi.first.networktables.NetworkTableEntry;

public class TextWidget extends DashboardWidget {
    private TextField valueField;
    private Button confirmButton;
    private boolean isEditable;
    private boolean isUpdating = false;

    public TextWidget(String title, String key) {
        super(title, key);
        this.isEditable = key.startsWith("Preferences/");
        createTextDisplay();
    }

    private void createTextDisplay() {
        valueField = new TextField("N/A");
        valueField.setStyle(
                "-fx-font-size: 24px; -fx-font-weight: bold; -fx-font-family: 'Segoe UI', 'Roboto', sans-serif; -fx-text-fill: #C9D1D9; -fx-background-color: transparent; -fx-border-width: 0 0 2 0; -fx-border-color: #30363D; -fx-padding: 8px 8px 4px 8px;");

        valueField.setEditable(isEditable);
        valueField.setDisable(!isEditable);

        confirmButton = new Button("✓");
        confirmButton.setStyle(
                "-fx-font-size: 14px; -fx-background-color: #238636; -fx-text-fill: white; -fx-padding: 4px 8px; -fx-background-radius: 4px;");
        confirmButton.setVisible(false);
        confirmButton.setOnAction(e -> writeValueBack());

        if (isEditable) {
            valueField.setOnAction(e -> {
                writeValueBack();
                Platform.runLater(() -> valueField.getParent().requestFocus());
            });

            valueField.focusedProperty().addListener((obs, wasFocused, isNowFocused) -> {
                confirmButton.setVisible(isNowFocused);
                if (wasFocused && !isNowFocused) {
                    writeValueBack();
                }
            });
        }

        HBox inputContainer = new HBox(4);
        inputContainer.setAlignment(Pos.CENTER);
        inputContainer.getChildren().addAll(valueField, confirmButton);

        VBox textContainer = new VBox(8);
        textContainer.setAlignment(Pos.CENTER);
        textContainer.getChildren().add(inputContainer);

        contentBox.getChildren().add(textContainer);
    }

    private void writeValueBack() {
        if (!isEditable || isUpdating) {
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
                }
            } finally {
                isUpdating = false;
            }

            Platform.runLater(() -> confirmButton.setVisible(false));
        } catch (Exception e) {
            System.err.println("Error writing value back: " + e.getMessage());
        }
    }

    @Override
    public void updateValue(Object value) {
        if (isUpdating || valueField.isFocused()) {
            return;
        }

        isUpdating = true;
        try {
            if (value != null) {
                if (value instanceof String) {
                    valueField.setText((String) value);
                } else {
                    valueField.setText(value.toString());
                }
            } else {
                valueField.setText("N/A");
            }
        } finally {
            isUpdating = false;
        }
    }

    @Override
    public Node getContent() {
        return container;
    }
}
