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
                "-fx-font-size: 28px; -fx-font-weight: bold; -fx-font-family: 'Segoe UI', 'Roboto', sans-serif; -fx-text-fill: #C9D1D9; -fx-background-color: transparent; -fx-border-width: 0 0 2 0; -fx-border-color: #30363D; -fx-padding: 8px 8px 4px 8px;");

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
                    valueField.setText(String.valueOf(doubleValue));
                } else if (value instanceof Number) {
                    valueField.setText(value.toString());
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
