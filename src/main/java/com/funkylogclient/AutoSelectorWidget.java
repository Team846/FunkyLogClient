package com.funkylogclient;

import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Circle;
import javafx.geometry.Pos;
import javafx.application.Platform;
import javafx.scene.paint.Color;
import edu.wpi.first.networktables.NetworkTableInstance;
import edu.wpi.first.networktables.NetworkTable;
import edu.wpi.first.networktables.NetworkTableEntry;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.Arrays;

public class AutoSelectorWidget extends DashboardWidget {
    private String[] lastKnownOptions = new String[0];
    private ComboBox<String> modeComboBox;
    private Circle statusIndicator;
    private List<String> autoModes;
    private boolean isUpdating = false;
    private Runnable removeCallback;
    private String lastUserSelection = null;
    private String currentNetworkValue = null;
    private long lastWriteTime = 0;
    private static final long WRITE_COOLDOWN_MS = 2000;
    private ScheduledExecutorService writeExecutor;

    public AutoSelectorWidget(String title, String key) {
        super(title, key);
        createAutoSelector();
        setupContextMenu();
        startContinuousWrite();
    }

    @Override
    public int getColSpan() { return 2; }

    @Override
    public int getRowSpan() { return 1; }

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
                            "-fx-font-size: 14px; -fx-font-weight: bold; " +
                                    "-fx-font-family: 'Segoe UI', 'Roboto', sans-serif; " +
                                    "-fx-text-fill: #FFFFFF; " +
                                    "-fx-background-color: transparent; " +
                                    "-fx-padding: 0;");
                }
            }
        });

        modeComboBox.setStyle(
                "-fx-font-size: 14px; -fx-font-weight: bold; " +
                        "-fx-font-family: " + Styles.FONT_FAMILY + "; " +
                        "-fx-text-fill: " + Styles.TEXT_WHITE + "; " +
                        "-fx-background-color: " + Styles.BG_MEDIUM + "; " +
                        "-fx-border-color: " + Styles.BORDER_LIGHT + "; " +
                        "-fx-border-width: 1px; " +
                        "-fx-border-radius: 6px; " +
                        "-fx-background-radius: 6px; " +
                        "-fx-padding: 8px 12px;" +
                        "-fx-cursor: hand;");

        modeComboBox.setMinWidth(180);
        modeComboBox.setMaxWidth(Double.MAX_VALUE);

        modeComboBox.sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (newScene != null) {
                Platform.runLater(() -> {
                    Node arrowButton = modeComboBox.lookup(".arrow-button");
                    if (arrowButton != null) {
                        arrowButton.setStyle(
                                "-fx-background-color: " + Styles.BG_DARK + "; " +
                                        "-fx-border-color: transparent; " +
                                        "-fx-background-radius: 0 6px 6px 0;");
                    }

                    Node arrow = modeComboBox.lookup(".arrow");
                    if (arrow != null) {
                        arrow.setStyle(
                                "-fx-background-color: " + Styles.TEXT_PRIMARY + "; " +
                                        "-fx-shape: 'M 0 4 L 8 4 L 4 8 Z';");
                    }
                });
            }
        });

        modeComboBox.setOnMouseEntered(e -> {
            modeComboBox.setStyle(
                    "-fx-font-size: 14px; -fx-font-weight: bold; " +
                            "-fx-font-family: " + Styles.FONT_FAMILY + "; " +
                            "-fx-text-fill: " + Styles.TEXT_WHITE + "; " +
                            "-fx-background-color: " + Styles.BG_LIGHT + "; " +
                            "-fx-border-color: " + Styles.ACCENT_PRIMARY + "; " +
                            "-fx-border-width: 1px; " +
                            "-fx-border-radius: 6px; " +
                            "-fx-background-radius: 6px; " +
                            "-fx-padding: 8px 12px;" +
                            "-fx-cursor: hand;");
        });

        modeComboBox.setOnMouseExited(e -> {
            modeComboBox.setStyle(
                    "-fx-font-size: 14px; -fx-font-weight: bold; " +
                            "-fx-font-family: " + Styles.FONT_FAMILY + "; " +
                            "-fx-text-fill: " + Styles.TEXT_WHITE + "; " +
                            "-fx-background-color: " + Styles.BG_MEDIUM + "; " +
                            "-fx-border-color: " + Styles.BORDER_LIGHT + "; " +
                            "-fx-border-width: 1px; " +
                            "-fx-border-radius: 6px; " +
                            "-fx-background-radius: 6px; " +
                            "-fx-padding: 8px 12px;" +
                            "-fx-cursor: hand;");
        });

        modeComboBox.setOnAction(e -> {
            if (!isUpdating) {
                writeSelectedMode();
                updateStatusIndicator();
            }
        });

        statusIndicator = new Circle(6);
        statusIndicator.setFill(Color.web("#F85149"));
        statusIndicator.setStroke(Color.TRANSPARENT);

        HBox selectorContainer = new HBox(10);
        selectorContainer.setAlignment(Pos.CENTER);
        selectorContainer.getChildren().addAll(modeComboBox, statusIndicator);

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
                if (options != null) {
                    Platform.runLater(() -> updateOptionsList(options));
                }
            }
        } catch (Exception e) {
            System.err.println("Error refreshing auto modes: " + e.getMessage());
        }
    }

    private void updateOptionsList(String[] options) {
        String currentActive = modeComboBox.getValue();
        autoModes.clear();
        
        if (options == null || options.length == 0) {
            autoModes.add("None");
        } else {
            for (String mode : options) {
                autoModes.add(mode.trim());
            }
        }

        if (currentActive != null && !currentActive.isEmpty() && !autoModes.contains(currentActive)) {
            autoModes.add(currentActive);
        }

        modeComboBox.getItems().setAll(autoModes);

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

                NetworkTableEntry activeEntry = chooserTable.getEntry("active");
                if (activeEntry != null && activeEntry.exists()) {
                    String newNetworkValue = activeEntry.getString("");
                    if (newNetworkValue != null && !newNetworkValue.equals(currentNetworkValue)) {
                        currentNetworkValue = newNetworkValue;
                        updateStatusIndicator();
                    }
                }

                NetworkTableEntry optionsEntry = chooserTable.getEntry("options");
                if (optionsEntry != null && optionsEntry.exists()) {
                    String[] currentOptions = optionsEntry.getStringArray(new String[0]);
                    if (currentOptions != null && !Arrays.equals(currentOptions, lastKnownOptions)) {
                        String[] optionsToUpdate = currentOptions.clone();
                        lastKnownOptions = currentOptions;
                        Platform.runLater(() -> updateOptionsList(optionsToUpdate));
                    }
                }

                if (lastUserSelection != null) {
                    NetworkTableEntry selectedEntry = chooserTable.getEntry("selected");
                    if (selectedEntry != null && selectedEntry.exists()) {
                        String selectedValue = selectedEntry.getString("");

                        if (!lastUserSelection.equals(selectedValue)) {
                            selectedEntry.setString(lastUserSelection);

                            NetworkTableInstance ntInstance = NetworkTableInstance.getDefault();
                            if (ntInstance != null) {
                                ntInstance.flush();
                            }
                        }
                    }
                }
            } catch (Exception e) {
            }
        }, 0, 200, TimeUnit.MILLISECONDS);
    }
    
    private void updateStatusIndicator() {
        Platform.runLater(() -> {
            if (statusIndicator == null) {
                return;
            }
            
            String comboValue = modeComboBox.getValue();
            boolean matches = comboValue != null && comboValue.equals(currentNetworkValue);
            
            if (matches) {
                statusIndicator.setFill(Color.web("#4CAF50"));
            } else {
                statusIndicator.setFill(Color.web("#F85149"));
            }
        });
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

        currentNetworkValue = strValue;
        isUpdating = true;
        Platform.runLater(() -> {
            if (!modeComboBox.getItems().contains(strValue)) {
                modeComboBox.getItems().add(strValue);
            }
            modeComboBox.setValue(strValue);
            updateStatusIndicator();
            isUpdating = false;
        });
    }

    @Override
    public Node getContent() {
        return container;
    }
}
