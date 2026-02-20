package com.funkylogclient;

import javafx.scene.Node;
import javafx.scene.layout.VBox;
import javafx.scene.layout.Priority;
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

    private Runnable removeCallback;

    public void setRemoveCallback(Runnable callback) {
        this.removeCallback = callback;
    }

    private void setupContextMenu() {
        javafx.scene.control.ContextMenu contextMenu = new javafx.scene.control.ContextMenu();
        javafx.scene.control.MenuItem removeItem = new javafx.scene.control.MenuItem("Remove Widget");
        removeItem.setOnAction(e -> {
            if (removeCallback != null) removeCallback.run();
        });
        contextMenu.getItems().add(removeItem);
        container.setOnContextMenuRequested(event -> {
            contextMenu.show(container, event.getScreenX(), event.getScreenY());
        });
    }

    public BooleanWidget(String title, String key) {
        super(title, key);
        this.isEditable = key.startsWith("Preferences/");
        contentBox.setPadding(new javafx.geometry.Insets(6));
        createBooleanDisplay();
        setupContextMenu();
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
                    toggleLabel.setFill(isNow ? Color.web("#4CAF50") : Color.web("#F85149"));
            });

            toggleLabel = new Text("FALSE");
            toggleLabel.setFill(Color.web("#F85149"));
            toggleLabel.setStyle(
                    "-fx-font-size: 14px; -fx-font-weight: bold; -fx-font-family: 'Segoe UI', 'Roboto', sans-serif;");

            javafx.scene.layout.HBox h = new javafx.scene.layout.HBox(10);
            h.setAlignment(Pos.CENTER);
            h.setFillHeight(true);
            h.getChildren().addAll(toggle, toggleLabel);

            VBox booleanContainer = new VBox(4);
            booleanContainer.setAlignment(Pos.CENTER);
            booleanContainer.setFillWidth(true);
            booleanContainer.getChildren().add(h);
            VBox.setVgrow(booleanContainer, Priority.ALWAYS);
            contentBox.getChildren().add(booleanContainer);
        } else {
            indicator = new Rectangle(40, 40);
            indicator.setFill(Color.web(Styles.ACCENT_ERROR));
            indicator.setStroke(Color.web(Styles.BORDER_DARK));
            indicator.setStrokeWidth(1.5);
            indicator.setArcWidth(10);
            indicator.setArcHeight(10);

            statusText = new Text("FALSE");
            statusText.setFill(Color.WHITE);
            statusText.setStyle(
                    "-fx-font-size: 10px; -fx-font-weight: bold; -fx-font-family: 'Segoe UI', 'Roboto', sans-serif;");

            indicatorStack = new StackPane();
            indicatorStack.getChildren().addAll(indicator, statusText);

            indicatorStack.setOnMouseEntered(e -> {
                indicator.setStroke(Color.web(Styles.BORDER_LIGHT));
            });
            indicatorStack.setOnMouseExited(e -> {
                indicator.setStroke(Color.web(Styles.BORDER_DARK));
            });

            VBox booleanContainer = new VBox(4);
            booleanContainer.setAlignment(Pos.CENTER);
            booleanContainer.setFillWidth(true);
            booleanContainer.getChildren().addAll(indicatorStack);
            VBox.setVgrow(booleanContainer, Priority.ALWAYS);

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
                    toggleLabel.setFill(boolValue ? Color.web("#4CAF50") : Color.web("#F85149"));
                }
            }
            return;
        }

        if (boolValue) {
            indicator.setFill(Color.web("#4CAF50"));
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
