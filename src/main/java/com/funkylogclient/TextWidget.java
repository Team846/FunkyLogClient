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
        valueField = new TextField("--");
        valueField.setStyle(
                "-fx-font-size: 20px; -fx-font-weight: bold; -fx-font-family: " + Styles.FONT_FAMILY + "; -fx-text-fill: " + Styles.TEXT_WHITE + "; -fx-background-color: transparent; -fx-border-width: 0 0 2 0; -fx-border-color: " + Styles.BORDER_DARK + "; -fx-padding: 8px 8px 6px 8px; -fx-alignment: center;");

        valueField.setEditable(isEditable);
        valueField.setDisable(!isEditable);

        if (isEditable) {
            valueField.setStyle(
                    "-fx-font-size: 20px; -fx-font-weight: bold; -fx-font-family: " + Styles.FONT_FAMILY + "; -fx-text-fill: " + Styles.TEXT_WHITE + "; -fx-background-color: " + Styles.BG_MEDIUM + "; -fx-border-width: 0 0 2 0; -fx-border-color: " + Styles.ACCENT_PRIMARY + "; -fx-padding: 8px 8px 6px 8px; -fx-alignment: center; -fx-background-radius: 6px;");
        }

        confirmButton = new Button("✓");
        confirmButton.setStyle(Styles.CONFIRM_BUTTON_STYLE);
        confirmButton.setVisible(false);
        confirmButton.setOnAction(e -> writeValueBack());

        confirmButton.setOnMouseEntered(e -> confirmButton.setStyle(Styles.CONFIRM_BUTTON_HOVER_STYLE));
        confirmButton.setOnMouseExited(e -> confirmButton.setStyle(Styles.CONFIRM_BUTTON_STYLE));

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

        HBox inputContainer = new HBox(6);
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
                valueField.setText("--");
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
