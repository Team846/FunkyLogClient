package com.funkylogclient;

import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.geometry.Pos;
import javafx.application.Platform;
import edu.wpi.first.networktables.NetworkTableInstance;
import edu.wpi.first.networktables.NetworkTable;
import edu.wpi.first.networktables.NetworkTableEntry;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class AutoSelectorWidget extends DashboardWidget {
    private ComboBox<String> modeComboBox;
    private Button refreshButton;
    private List<String> autoModes;
    private boolean isUpdating = false;
    private Runnable removeCallback;
    private String lastUserSelection = null;
    private long lastWriteTime = 0;
    private static final long WRITE_COOLDOWN_MS = 2000;
    private ScheduledExecutorService writeExecutor;
    private NetworkTableEntry activeEntry;

    public AutoSelectorWidget(String title, String key) {
        super(title, key);
        createAutoSelector();
        setupContextMenu();
        startContinuousWrite();
    }

    private void createAutoSelector() {
        autoModes = new ArrayList<>();
        autoModes.add("None");

        modeComboBox = new ComboBox<>();
        modeComboBox.getItems().addAll(autoModes);
        modeComboBox.setValue("None");

        modeComboBox.setButtonCell(new ListCell<String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    setText(item);
                    setStyle(
                            "-fx-font-size: 16px; -fx-font-weight: bold; " +
                                    "-fx-font-family: 'Segoe UI', 'Roboto', sans-serif; " +
                                    "-fx-text-fill: #C9D1D9; " +
                                    "-fx-background-color: transparent; " +
                                    "-fx-padding: 0;");
                }
            }
        });

        modeComboBox.setStyle(
                "-fx-font-size: 16px; -fx-font-weight: bold; " +
                        "-fx-font-family: 'Segoe UI', 'Roboto', sans-serif; " +
                        "-fx-text-fill: #C9D1D9; " +
                        "-fx-background-color: #30363D; " +
                        "-fx-border-color: #454D56; " +
                        "-fx-border-width: 2px; " +
                        "-fx-border-radius: 6px; " +
                        "-fx-background-radius: 6px; " +
                        "-fx-padding: 8px 12px;" +
                        "-fx-cursor: hand;");

        modeComboBox.setMinWidth(200);
        modeComboBox.setMaxWidth(Double.MAX_VALUE);

        modeComboBox.sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (newScene != null) {
                Platform.runLater(() -> {
                    Node arrowButton = modeComboBox.lookup(".arrow-button");
                    if (arrowButton != null) {
                        arrowButton.setStyle(
                                "-fx-background-color: #21262D; " +
                                        "-fx-border-color: transparent; " +
                                        "-fx-background-radius: 0 6px 6px 0;");
                    }

                    Node arrow = modeComboBox.lookup(".arrow");
                    if (arrow != null) {
                        arrow.setStyle(
                                "-fx-background-color: #C9D1D9; " +
                                        "-fx-shape: 'M 0 4 L 8 4 L 4 8 Z';");
                    }
                });
            }
        });

        modeComboBox.setOnMouseEntered(e -> {
            modeComboBox.setStyle(
                    "-fx-font-size: 16px; -fx-font-weight: bold; " +
                            "-fx-font-family: 'Segoe UI', 'Roboto', sans-serif; " +
                            "-fx-text-fill: #E0E0E0; " +
                            "-fx-background-color: #30363D; " +
                            "-fx-border-color: #56A8F4; " +
                            "-fx-border-width: 2px; " +
                            "-fx-border-radius: 6px; " +
                            "-fx-background-radius: 6px; " +
                            "-fx-padding: 8px 12px;" +
                            "-fx-cursor: hand;");
            Node arrowButton = modeComboBox.lookup(".arrow-button");
            if (arrowButton != null) {
                arrowButton.setStyle(
                        "-fx-background-color: #30363D; " +
                                "-fx-border-color: transparent; " +
                                "-fx-background-radius: 0 6px 6px 0;");
            }
        });

        modeComboBox.setOnMouseExited(e -> {
            modeComboBox.setStyle(
                    "-fx-font-size: 16px; -fx-font-weight: bold; " +
                            "-fx-font-family: 'Segoe UI', 'Roboto', sans-serif; " +
                            "-fx-text-fill: #C9D1D9; " +
                            "-fx-background-color: #30363D; " +
                            "-fx-border-color: #454D56; " +
                            "-fx-border-width: 2px; " +
                            "-fx-border-radius: 6px; " +
                            "-fx-background-radius: 6px; " +
                            "-fx-padding: 8px 12px;" +
                            "-fx-cursor: hand;");
            Node arrowButton = modeComboBox.lookup(".arrow-button");
            if (arrowButton != null) {
                arrowButton.setStyle(
                        "-fx-background-color: #21262D; " +
                                "-fx-border-color: transparent; " +
                                "-fx-background-radius: 0 6px 6px 0;");
            }
        });

        modeComboBox.setOnAction(e -> {
            if (!isUpdating) {
                writeSelectedMode();
            }
        });

        refreshButton = new Button("⟳");
        refreshButton.setStyle(
                "-fx-font-size: 16px; " +
                        "-fx-background-color: #238636; " +
                        "-fx-text-fill: white; " +
                        "-fx-padding: 8px 12px; " +
                        "-fx-background-radius: 6px; " +
                        "-fx-cursor: hand;");
        refreshButton.setOnAction(e -> refreshAutoModes());

        refreshButton.setOnMouseEntered(e -> {
            refreshButton.setStyle(
                    "-fx-font-size: 16px; " +
                            "-fx-background-color: #2EA043; " +
                            "-fx-text-fill: white; " +
                            "-fx-padding: 8px 12px; " +
                            "-fx-background-radius: 6px; " +
                            "-fx-cursor: hand;");
        });

        refreshButton.setOnMouseExited(e -> {
            refreshButton.setStyle(
                    "-fx-font-size: 16px; " +
                            "-fx-background-color: #238636; " +
                            "-fx-text-fill: white; " +
                            "-fx-padding: 8px 12px; " +
                            "-fx-background-radius: 6px; " +
                            "-fx-cursor: hand;");
        });

        HBox selectorContainer = new HBox(8);
        selectorContainer.setAlignment(Pos.CENTER);
        selectorContainer.getChildren().addAll(modeComboBox, refreshButton);

        VBox autoContainer = new VBox(8);
        autoContainer.setAlignment(Pos.CENTER);
        autoContainer.getChildren().add(selectorContainer);

        contentBox.getChildren().add(autoContainer);

        refreshAutoModes();
    }

    private void refreshAutoModes() {
        try {
            NetworkTableInstance instance = NetworkTableInstance.getDefault();
            if (instance == null) {
                return;
            }

            String[] parts = key.split("/");
            if (parts.length < 2) {
                return;
            }

            String tableName = parts[0];
            String entryKey = key.substring(tableName.length() + 1);

            NetworkTable table = instance.getTable(tableName);
            if (table == null) {
                return;
            }

            NetworkTable chooserTable = table.getSubTable(entryKey);
            NetworkTableEntry optionsEntry = chooserTable.getEntry("options");

            if (optionsEntry.exists()) {
                String[] options = optionsEntry.getStringArray(new String[0]);
                if (options != null && options.length > 0) {
                    String currentActive = modeComboBox.getValue();

                    Platform.runLater(() -> {
                        autoModes.clear();
                        for (String mode : options) {
                            autoModes.add(mode.trim());
                        }

                        if (currentActive != null && !currentActive.isEmpty() && !autoModes.contains(currentActive)) {
                            autoModes.add(currentActive);
                        }

                        modeComboBox.getItems().clear();
                        modeComboBox.getItems().addAll(autoModes);

                        long timeSinceWrite = System.currentTimeMillis() - lastWriteTime;
                        if (timeSinceWrite > WRITE_COOLDOWN_MS && currentActive != null
                                && autoModes.contains(currentActive)) {
                            isUpdating = true;
                            try {
                                modeComboBox.setValue(currentActive);
                            } finally {
                                isUpdating = false;
                            }
                        }
                    });
                }
            }
        } catch (Exception e) {
            System.err.println("Error refreshing auto modes: " + e.getMessage());
        }
    }

    private void writeSelectedMode() {
        String selected = modeComboBox.getValue();
        if (selected == null || selected.isEmpty()) {
            return;
        }

        try {
            NetworkTableInstance instance = NetworkTableInstance.getDefault();
            if (instance == null) {
                return;
            }

            String[] parts = key.split("/");
            if (parts.length < 2) {
                return;
            }

            String tableName = parts[0];
            String entryKey = key.substring(tableName.length() + 1);
            NetworkTable table = instance.getTable(tableName);
            if (table == null) {
                return;
            }

            NetworkTable chooserTable = table.getSubTable(entryKey);
            if (chooserTable == null) {
                return;
            }

            NetworkTableEntry selectedEntry = chooserTable.getEntry("selected");
            if (selectedEntry != null) {
                selectedEntry.setString(selected);
                activeEntry = chooserTable.getEntry("active");

                NetworkTableInstance ntInstance = NetworkTableInstance.getDefault();
                if (ntInstance != null) {
                    ntInstance.flush();
                }

                lastUserSelection = selected;
                lastWriteTime = System.currentTimeMillis();
            }
        } catch (Exception e) {
            System.err.println("Error writing auto mode: " + e.getMessage());
        }
    }

    private void startContinuousWrite() {
        if (writeExecutor != null) {
            return;
        }

        writeExecutor = Executors.newSingleThreadScheduledExecutor();
        writeExecutor.scheduleAtFixedRate(() -> {
            try {
                if (lastUserSelection == null) {
                    return;
                }

                NetworkTableInstance instance = NetworkTableInstance.getDefault();
                if (instance == null) {
                    return;
                }

                String[] parts = key.split("/");
                if (parts.length < 2) {
                    return;
                }

                String tableName = parts[0];
                String entryKey = key.substring(tableName.length() + 1);
                NetworkTable table = instance.getTable(tableName);
                if (table == null) {
                    return;
                }

                NetworkTable chooserTable = table.getSubTable(entryKey);
                if (chooserTable == null) {
                    return;
                }

                NetworkTableEntry selectedEntry = chooserTable.getEntry("selected");
                if (selectedEntry != null && selectedEntry.exists()) {
                    String currentNetworkValue = selectedEntry.getString("");

                    if (!lastUserSelection.equals(currentNetworkValue)) {
                        selectedEntry.setString(lastUserSelection);

                        NetworkTableInstance ntInstance = NetworkTableInstance.getDefault();
                        if (ntInstance != null) {
                            ntInstance.flush();
                        }
                    }
                }
            } catch (Exception e) {
            }
        }, 0, 200, TimeUnit.MILLISECONDS);
    }

    public void shutdown() {
        if (writeExecutor != null && !writeExecutor.isShutdown()) {
            writeExecutor.shutdown();
        }
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

    @Override
    public void updateValue(Object value) {
        if (!(value instanceof String)) {
            return;
        }

        String strValue = (String) value;
        if (strValue == null || strValue.isEmpty()) {
            return;
        }

        long timeSinceWrite = System.currentTimeMillis() - lastWriteTime;

        if (lastUserSelection != null) {
            if (strValue.equals(lastUserSelection)) {
                return;
            }

            if (timeSinceWrite < WRITE_COOLDOWN_MS) {
                return;
            }

            long timeSinceLastWrite = System.currentTimeMillis() - lastWriteTime;
            if (timeSinceLastWrite < 5000 && !strValue.equals(lastUserSelection)) {
                Platform.runLater(() -> {
                    if (!isUpdating && modeComboBox.getValue() != null
                            && !modeComboBox.getValue().equals(lastUserSelection)) {
                        isUpdating = true;
                        try {
                            modeComboBox.setValue(lastUserSelection);
                        } finally {
                            isUpdating = false;
                        }
                    }
                });
                return;
            }
        }

        String currentValue = modeComboBox.getValue();
        if (isUpdating || strValue.equals(currentValue)) {
            return;
        }

        isUpdating = true;
        Platform.runLater(() -> {
            if (!modeComboBox.getItems().contains(strValue)) {
                modeComboBox.getItems().add(strValue);
            }
            modeComboBox.setValue(strValue);
            isUpdating = false;
        });
    }

    @Override
    public Node getContent() {
        return container;
    }
}
