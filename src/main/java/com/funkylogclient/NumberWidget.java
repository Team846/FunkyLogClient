package com.funkylogclient;

import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.layout.Priority;
import javafx.geometry.Pos;
import javafx.application.Platform;
import edu.wpi.first.networktables.NetworkTable;
import edu.wpi.first.networktables.NetworkTableEntry;
import edu.wpi.first.networktables.NetworkTableInstance;

public class NumberWidget extends DashboardWidget {
    private TextField valueField;
    private Label valueLabel;
    private Button confirmButton;
    private boolean isEditable;
    private boolean isUpdating = false;
    private ContextMenu contextMenu;
    private Runnable removeCallback;
    private String currentNTValue = null;

    public NumberWidget(String title, String key) {
        super(title, key);
        this.isEditable = key.startsWith("Preferences/");
        createNumberDisplay();
        setupContextMenu();
    }

    private void createNumberDisplay() {
        if (isEditable) {
            valueField = new TextField("0");
            valueField.setStyle(
                    "-fx-font-size: 30px; -fx-font-weight: bold; -fx-font-family: " + Styles.FONT_FAMILY + "; -fx-text-fill: " + Styles.TEXT_WHITE + "; -fx-background-color: transparent; -fx-border-width: 0; -fx-padding: 0; -fx-alignment: center;");
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
            
            VBox numberContainer = new VBox(0);
            numberContainer.setAlignment(Pos.CENTER);
            numberContainer.getChildren().add(inputContainer);
            VBox.setVgrow(numberContainer, Priority.ALWAYS);
            
            contentBox.getChildren().add(numberContainer);
        } else {
            valueLabel = new Label("0");
            valueLabel.setStyle(
                    "-fx-font-size: 40px; -fx-font-weight: normal; -fx-font-family: " + Styles.FONT_FAMILY + "; -fx-text-fill: " + Styles.TEXT_SECONDARY + "; -fx-background-color: transparent; -fx-padding: 0; -fx-alignment: center;");
            valueLabel.setAlignment(Pos.CENTER);
            valueLabel.setMaxWidth(Double.MAX_VALUE);
            HBox.setHgrow(valueLabel, Priority.ALWAYS);
            
            VBox numberContainer = new VBox(0);
            numberContainer.setAlignment(Pos.TOP_CENTER);
            numberContainer.setPadding(new javafx.geometry.Insets(-8, 0, 0, 0));
            numberContainer.getChildren().add(valueLabel);
            VBox.setVgrow(numberContainer, Priority.ALWAYS);
            
            contentBox.getChildren().add(numberContainer);
        }
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
    
    private String formatWithSignificantFigures(double value) {
        if (Double.isInfinite(value) || Double.isNaN(value)) {
            return String.valueOf(value);
        }
        
        if (value == 0.0) {
            return "0";
        }
        
        double absValue = Math.abs(value);
        int magnitude = (int) Math.floor(Math.log10(absValue));
        
        double scale = Math.pow(10, 3 - magnitude);
        double rounded = Math.round(value * scale) / scale;
        
        if (rounded == Math.floor(rounded) && Math.abs(rounded) < 1e10) {
            return String.valueOf((long) rounded);
        }
        
        int decimalPlaces = Math.max(0, 3 - magnitude);
        String formatted = String.format("%." + Math.min(decimalPlaces + 4, 15) + "f", rounded);
        
        formatted = formatted.replaceAll("0+$", "");
        formatted = formatted.replaceAll("\\.$", "");
        
        return formatted;
    }
    
    private String formatAllSignificantFigures(double value) {
        if (Double.isInfinite(value) || Double.isNaN(value)) {
            return String.valueOf(value);
        }
        
        if (value == 0.0) {
            return "0";
        }
        
        if (value == Math.floor(value) && Math.abs(value) < 1e15) {
            return String.valueOf((long) value);
        }
        
        String formatted = String.format("%.15g", value);
        
        if (formatted.contains("e") || formatted.contains("E")) {
            return formatted;
        }
        
        formatted = formatted.replaceAll("0+$", "");
        formatted = formatted.replaceAll("\\.$", "");
        
        return formatted;
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
                    currentNTValue = textValue;
                }
            } finally {
                isUpdating = false;
            }

            Platform.runLater(() -> updateButtonState());
        } catch (NumberFormatException e) {
            System.err.println("Invalid number format: " + valueField.getText());
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
                    if (value instanceof Double || value instanceof Float) {
                        double doubleValue = value instanceof Double ? (Double) value : (Float) value;
                        newValue = formatAllSignificantFigures(doubleValue);
                    } else if (value instanceof Number) {
                        double doubleValue = ((Number) value).doubleValue();
                        newValue = formatAllSignificantFigures(doubleValue);
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
                    if (value instanceof Double || value instanceof Float) {
                        double doubleValue = value instanceof Double ? (Double) value : (Float) value;
                        valueLabel.setText(formatWithSignificantFigures(doubleValue));
                    } else if (value instanceof Number) {
                        double doubleValue = ((Number) value).doubleValue();
                        valueLabel.setText(formatWithSignificantFigures(doubleValue));
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
