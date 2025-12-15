package com.funkylogclient;

import javafx.scene.Node;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Text;
import javafx.scene.layout.StackPane;
import javafx.scene.control.CheckBox;
import edu.wpi.first.networktables.NetworkTable;
import edu.wpi.first.networktables.NetworkTableEntry;
import edu.wpi.first.networktables.NetworkTableInstance;
import javafx.geometry.Pos;

public class BooleanWidget extends DashboardWidget {
    private Rectangle indicator;
    private Text statusText;
    private StackPane indicatorStack;
    private CheckBox toggle;
    private boolean isEditable;
    private boolean isUpdating = false;
    private Text toggleLabel;

    public BooleanWidget(String title, String key) {
        super(title, key);
        this.isEditable = key.startsWith("Preferences/");
        createBooleanDisplay();
    }

    private void createBooleanDisplay() {
        if (isEditable) {
            toggle = new CheckBox();
            toggle.setAllowIndeterminate(false);
            toggle.setStyle("-fx-font-size: 16px; -fx-text-fill: " + Styles.TEXT_PRIMARY + ";");
            toggle.selectedProperty().addListener((obs, was, isNow) -> {
                writeValueBack(isNow);
                if (toggleLabel != null)
                    toggleLabel.setText(isNow ? "TRUE" : "FALSE");
                if (toggleLabel != null)
                    toggleLabel.setFill(isNow ? Color.web("#FFD700") : Color.web("#F85149"));
            });

            toggleLabel = new Text("FALSE");
            toggleLabel.setFill(Color.web("#F85149"));
            toggleLabel.setStyle(
                    "-fx-font-size: 14px; -fx-font-weight: bold; -fx-font-family: 'Segoe UI', 'Roboto', sans-serif;");

            javafx.scene.layout.HBox h = new javafx.scene.layout.HBox(10);
            h.setAlignment(Pos.CENTER);
            h.getChildren().addAll(toggle, toggleLabel);

            VBox booleanContainer = new VBox(12);
            booleanContainer.setAlignment(Pos.CENTER);
            booleanContainer.getChildren().add(h);
            contentBox.getChildren().add(booleanContainer);
        } else {
            indicator = new Rectangle(56, 56);
            indicator.setFill(Color.web(Styles.ACCENT_ERROR));
            indicator.setStroke(Color.web(Styles.BORDER_DARK));
            indicator.setStrokeWidth(2);
            indicator.setArcWidth(14);
            indicator.setArcHeight(14);

            statusText = new Text("FALSE");
            statusText.setFill(Color.WHITE);
            statusText.setStyle(
                    "-fx-font-size: 12px; -fx-font-weight: bold; -fx-font-family: 'Segoe UI', 'Roboto', sans-serif;");

            indicatorStack = new StackPane();
            indicatorStack.getChildren().addAll(indicator, statusText);

            indicatorStack.setOnMouseEntered(e -> {
                indicator.setStroke(Color.web(Styles.BORDER_LIGHT));
            });
            indicatorStack.setOnMouseExited(e -> {
                indicator.setStroke(Color.web(Styles.BORDER_DARK));
            });

            VBox booleanContainer = new VBox(12);
            booleanContainer.setAlignment(Pos.CENTER);
            booleanContainer.getChildren().addAll(indicatorStack);

            contentBox.getChildren().add(booleanContainer);
        }
    }

    private void writeValueBack(boolean value) {
        if (!isEditable || isUpdating) {
            return;
        }
        isUpdating = true;
        try {
            NetworkTableInstance instance = NetworkTableInstance.getDefault();
            NetworkTable table = instance.getTable(key.split("/")[0]);
            String secondPartKey = key.substring(key.split("/")[0].length() + 1);
            if (instance != null && table != null) {
                NetworkTableEntry entry = table.getEntry(secondPartKey);
                entry.setBoolean(value);
            }
        } finally {
            isUpdating = false;
        }
    }

    @Override
    public void updateValue(Object value) {
        boolean boolValue = false;
        if (value instanceof Boolean) {
            boolValue = (Boolean) value;
        } else if (value instanceof Number) {
            boolValue = ((Number) value).doubleValue() != 0;
        } else if (value instanceof String) {
            boolValue = Boolean.parseBoolean((String) value);
        }

        if (isEditable) {
            if (toggle != null && !isUpdating) {
                toggle.setSelected(boolValue);
                if (toggleLabel != null) {
                    toggleLabel.setText(boolValue ? "TRUE" : "FALSE");
                    toggleLabel.setFill(boolValue ? Color.web("#FFD700") : Color.web("#F85149"));
                }
            }
            return;
        }

        if (boolValue) {
            indicator.setFill(Color.web("#FFD700"));
            statusText.setText("TRUE");
        } else {
            indicator.setFill(Color.web("#F85149"));
            statusText.setText("FALSE");
        }
    }

    @Override
    public Node getContent() {
        return container;
    }
}
