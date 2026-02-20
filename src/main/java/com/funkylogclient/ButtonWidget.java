package com.funkylogclient;

import javafx.scene.Node;
import javafx.scene.layout.VBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.HBox;
import javafx.scene.control.Button;
import edu.wpi.first.networktables.NetworkTable;
import edu.wpi.first.networktables.NetworkTableEntry;
import edu.wpi.first.networktables.NetworkTableInstance;
import javafx.geometry.Pos;
import javafx.application.Platform;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;

public class ButtonWidget extends DashboardWidget {
    private Button actionButton;
    private Runnable removeCallback;

    public ButtonWidget(String title, String key) {
        super(title, key);
        contentBox.setPadding(new javafx.geometry.Insets(6));
        createButtonDisplay();
        setupContextMenu();
    }

    private void setupContextMenu() {
        ContextMenu contextMenu = new ContextMenu();
        
        MenuItem removeItem = new MenuItem("Remove Widget");
        removeItem.setOnAction(e -> {
            if (removeCallback != null) {
                removeCallback.run();
            }
        });
        
        contextMenu.getItems().add(removeItem);
        
        container.setOnContextMenuRequested(event -> {
            contextMenu.show(container, event.getScreenX(), event.getScreenY());
        });
    }

    public void setRemoveCallback(Runnable callback) {
        this.removeCallback = callback;
    }

    private void createButtonDisplay() {
        actionButton = new Button(title);
        actionButton.setStyle(
                "-fx-font-size: 16px; " +
                "-fx-font-weight: bold; " +
                "-fx-font-family: 'Segoe UI', 'Roboto', sans-serif; " +
                "-fx-text-fill: #FFFFFF; " +
                "-fx-background-color: " + Styles.ACCENT_PRIMARY + "; " +
                "-fx-border-color: " + Styles.ACCENT_PRIMARY + "; " +
                "-fx-border-width: 1px; " +
                "-fx-border-radius: 6px; " +
                "-fx-background-radius: 6px; " +
                "-fx-padding: 8px 16px; " +
                "-fx-cursor: hand;"
        );

        actionButton.setOnMouseEntered(e -> {
            actionButton.setStyle(
                    "-fx-font-size: 16px; " +
                    "-fx-font-weight: bold; " +
                    "-fx-font-family: 'Segoe UI', 'Roboto', sans-serif; " +
                    "-fx-text-fill: #FFFFFF; " +
                    "-fx-background-color: " + Styles.ACCENT_HOVER + "; " +
                    "-fx-border-color: " + Styles.ACCENT_HOVER + "; " +
                    "-fx-border-width: 1px; " +
                    "-fx-border-radius: 6px; " +
                    "-fx-background-radius: 6px; " +
                    "-fx-padding: 8px 16px; " +
                    "-fx-cursor: hand;"
            );
        });

        actionButton.setOnMouseExited(e -> {
            actionButton.setStyle(
                    "-fx-font-size: 16px; " +
                    "-fx-font-weight: bold; " +
                    "-fx-font-family: 'Segoe UI', 'Roboto', sans-serif; " +
                    "-fx-text-fill: #FFFFFF; " +
                    "-fx-background-color: " + Styles.ACCENT_PRIMARY + "; " +
                    "-fx-border-color: " + Styles.ACCENT_PRIMARY + "; " +
                    "-fx-border-width: 1px; " +
                    "-fx-border-radius: 6px; " +
                    "-fx-background-radius: 6px; " +
                    "-fx-padding: 8px 16px; " +
                    "-fx-cursor: hand;"
            );
        });

        actionButton.setOnMousePressed(e -> {
            triggerAction(true);
        });
        
        actionButton.setOnMouseReleased(e -> {
            // NTAction/Command usually self-clears running = false when done, but we can also set to false
            triggerAction(false);
        });

        HBox h = new HBox(10);
        h.setAlignment(Pos.CENTER);
        h.setFillHeight(true);
        h.getChildren().add(actionButton);

        VBox booleanContainer = new VBox(4);
        booleanContainer.setAlignment(Pos.CENTER);
        booleanContainer.setFillWidth(true);
        booleanContainer.getChildren().add(h);
        VBox.setVgrow(booleanContainer, Priority.ALWAYS);
        contentBox.getChildren().add(booleanContainer);
    }

    private void triggerAction(boolean pressed) {
        try {
            NetworkTableInstance instance = NetworkTableInstance.getDefault();
            String[] parts = key.split("/");
            if (parts.length < 2) return;
            
            String tableName = parts[0];
            String entryKey = key.substring(tableName.length() + 1);
            NetworkTable table = instance.getTable(tableName);
            
            if (instance != null && table != null) {
                NetworkTable subTable = table.getSubTable(entryKey);
                if (subTable != null) {
                    NetworkTableEntry runningEntry = subTable.getEntry("running");
                    runningEntry.setBoolean(pressed);
                }
            }
        } catch (Exception e) {
            System.err.println("Error pressing button: " + e.getMessage());
        }
    }

    @Override
    public void updateValue(Object value) {
        boolean isRunning = false;
        if (value instanceof Boolean) {
            isRunning = (Boolean) value;
        } else if (value instanceof Number) {
            isRunning = ((Number) value).doubleValue() != 0;
        } else if (value instanceof String) {
            isRunning = Boolean.parseBoolean((String) value);
        }

        final boolean running = isRunning;
        Platform.runLater(() -> {
            if (running) {
                actionButton.setStyle(
                    "-fx-font-size: 16px; " +
                    "-fx-font-weight: bold; " +
                    "-fx-font-family: 'Segoe UI', 'Roboto', sans-serif; " +
                    "-fx-text-fill: #111111; " +
                    "-fx-background-color: #4CAF50; " +
                    "-fx-border-color: #4CAF50; " +
                    "-fx-border-width: 1px; " +
                    "-fx-border-radius: 6px; " +
                    "-fx-background-radius: 6px; " +
                    "-fx-padding: 8px 16px; " +
                    "-fx-cursor: hand;"
                );
                actionButton.setText("Running...");
            } else {
                actionButton.setStyle(
                    "-fx-font-size: 16px; " +
                    "-fx-font-weight: bold; " +
                    "-fx-font-family: 'Segoe UI', 'Roboto', sans-serif; " +
                    "-fx-text-fill: #FFFFFF; " +
                    "-fx-background-color: " + Styles.ACCENT_PRIMARY + "; " +
                    "-fx-border-color: " + Styles.ACCENT_PRIMARY + "; " +
                    "-fx-border-width: 1px; " +
                    "-fx-border-radius: 6px; " +
                    "-fx-background-radius: 6px; " +
                    "-fx-padding: 8px 16px; " +
                    "-fx-cursor: hand;"
                );
                actionButton.setText(title);
            }
        });
    }

    @Override
    public Node getContent() {
        return container;
    }
}
