package com.funkylogclient;

import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.geometry.Pos;
import javafx.application.Platform;
import edu.wpi.first.networktables.NetworkTable;
import edu.wpi.first.networktables.NetworkTableEntry;
import edu.wpi.first.networktables.NetworkTableInstance;

public class NumberWidget extends DashboardWidget {
    private TextField valueField;
    private Button confirmButton;
    private boolean isEditable;
    private boolean isUpdating = false;
    private ContextMenu contextMenu;
    private Runnable removeCallback;

    public NumberWidget(String title, String key) {
        super(title, key);
        this.isEditable = key.startsWith("Preferences/");
        createNumberDisplay();
        setupContextMenu();
    }

    private void createNumberDisplay() {
        valueField = new TextField("0");
        valueField.setStyle(
                "-fx-font-size: 26px; -fx-font-weight: bold; -fx-font-family: " + Styles.FONT_FAMILY + "; -fx-text-fill: " + Styles.TEXT_WHITE + "; -fx-background-color: transparent; -fx-border-width: 0 0 2 0; -fx-border-color: " + Styles.BORDER_DARK + "; -fx-padding: 8px 8px 6px 8px; -fx-alignment: center;");

        valueField.setEditable(isEditable);
        valueField.setDisable(!isEditable);

        if (isEditable) {
            valueField.setStyle(
                    "-fx-font-size: 26px; -fx-font-weight: bold; -fx-font-family: " + Styles.FONT_FAMILY + "; -fx-text-fill: " + Styles.TEXT_WHITE + "; -fx-background-color: " + Styles.BG_MEDIUM + "; -fx-border-width: 0 0 2 0; -fx-border-color: " + Styles.ACCENT_PRIMARY + "; -fx-padding: 8px 8px 6px 8px; -fx-alignment: center; -fx-background-radius: 6px;");
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

        VBox numberContainer = new VBox(8);
        numberContainer.setAlignment(Pos.CENTER);
        numberContainer.getChildren().add(inputContainer);

        contentBox.getChildren().add(numberContainer);
    }

    private void setupContextMenu() {
        contextMenu = new ContextMenu();
        MenuItem removeItem = new MenuItem("Remove Widget");
        removeItem.setOnAction(e -> {
            if (removeCallback != null)
                removeCallback.run();
        });

        if (!isEditable) {
            MenuItem convertToGraph = new MenuItem("Show as Graph");
            convertToGraph.setOnAction(e -> {
                System.out.println("Convert to graph requested for: " + key);
            });
            contextMenu.getItems().addAll(convertToGraph, removeItem);
        } else {
            contextMenu.getItems().addAll(removeItem);
        }

        container.setOnContextMenuRequested(event -> {
            contextMenu.show(container, event.getScreenX(), event.getScreenY());
        });
    }

    public void setContextMenuCallback(Runnable callback) {
        if (!isEditable && contextMenu != null && !contextMenu.getItems().isEmpty()) {
            MenuItem convertItem = contextMenu.getItems().get(0);
            convertItem.setOnAction(e -> callback.run());
        }
    }

    public void setRemoveCallback(Runnable callback) {
        this.removeCallback = callback;
    }

    private void writeValueBack() {
        if (!isEditable || isUpdating) {
            return;
        }

        try {
            String textValue = valueField.getText();
            double doubleValue = Double.parseDouble(textValue);

            isUpdating = true;
            try {
                NetworkTableInstance instance = NetworkTableInstance.getDefault();
                NetworkTable table = instance.getTable(key.split("/")[0]);
                String secondPartKey = key.substring(key.split("/")[0].length() + 1);
                if (instance != null && table != null) {
                    NetworkTableEntry entry = table.getEntry(secondPartKey);
                    entry.setDouble(doubleValue);
                    System.out.println("Wrote value " + doubleValue + " back to " + key);
                }
            } finally {
                isUpdating = false;
            }

            Platform.runLater(() -> confirmButton.setVisible(false));
        } catch (NumberFormatException e) {
            System.err.println("Invalid number format: " + valueField.getText());
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
                if (value instanceof Double || value instanceof Float) {
                    double doubleValue = value instanceof Double ? (Double) value : (Float) value;
                    if (doubleValue == Math.floor(doubleValue) && !Double.isInfinite(doubleValue)) {
                        valueField.setText(String.valueOf((long) doubleValue));
                    } else {
                        valueField.setText(String.format("%.2f", doubleValue));
                    }
                } else if (value instanceof Number) {
                    valueField.setText(value.toString());
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
