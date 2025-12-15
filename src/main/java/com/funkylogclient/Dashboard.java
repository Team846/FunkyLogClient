package com.funkylogclient;

import edu.wpi.first.networktables.NetworkTable;
import edu.wpi.first.networktables.NetworkTableEntry;
import edu.wpi.first.networktables.NetworkTableInstance;
import edu.wpi.first.networktables.NetworkTableValue;
import edu.wpi.first.networktables.NetworkTableType;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.layout.*;
import javafx.scene.layout.Priority;
import javafx.scene.text.Text;

import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;
import javafx.scene.input.ClipboardContent;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.annotation.JsonProperty;

public class Dashboard {
    private VBox dashboardContainer;
    private GridPane widgetGrid;
    private Map<String, DashboardWidget> widgets;
    private Map<String, GridPosition> widgetPositions = new HashMap<>(); // Track widget positions
    private Map<String, Long> lastUpdateTime = new HashMap<>();
    private ScheduledExecutorService updateExecutor;
    private Region[] gridSkeleton = null;
    private List<WidgetConfig> pendingWidgetsToLoad = new ArrayList<>();
    private ScheduledExecutorService loadRetryExecutor;

    // Helper class to track grid positions
    private static class GridPosition {
        int col;
        int row;

        GridPosition(int col, int row) {
            this.col = col;
            this.row = row;
        }
    }

    // Configuration classes for serialization
    private static class WidgetConfig {
        @JsonProperty
        public String key;
        @JsonProperty
        public String type;
        @JsonProperty
        public String title;
        @JsonProperty
        public int col;
        @JsonProperty
        public int row;

        public WidgetConfig() {
        }

        public WidgetConfig(String key, String type, String title, int col, int row) {
            this.key = key;
            this.type = type;
            this.title = title;
            this.col = col;
            this.row = row;
        }
    }

    private static class DashboardConfig {
        @JsonProperty
        public List<WidgetConfig> widgets = new ArrayList<>();

        public DashboardConfig() {
        }
    }

    public Dashboard() {
        widgets = new HashMap<>();
        createDashboard();
        loadConfiguration();
        startValueUpdateLoop();
        setupConnectionListener();
    }

    private void createDashboard() {
        dashboardContainer = new VBox(12);
        dashboardContainer.setPadding(new Insets(16, 20, 20, 20));
        dashboardContainer.setStyle("-fx-background-color: #1A1A1A;");

        HBox titleBar = new HBox(12);
        titleBar.setAlignment(Pos.CENTER_LEFT);
        titleBar.setPadding(new Insets(0, 0, 8, 0));

        Text title = new Text("Dashboard");
        title.setStyle(
                "-fx-font-size: 22px; -fx-fill: #FFFFFF; -fx-font-weight: bold; -fx-font-family: 'Segoe UI', 'Roboto', sans-serif;");

        Text subtitle = new Text("Drag items from the sidebar to add widgets");
        subtitle.setStyle(
                "-fx-font-size: 12px; -fx-fill: #808080; -fx-font-family: 'Segoe UI', 'Roboto', sans-serif;");

        VBox titleContainer = new VBox(2);
        titleContainer.getChildren().addAll(title, subtitle);

        HBox simControls = createSimulationControls();

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        titleBar.getChildren().addAll(titleContainer, spacer, simControls);

        widgetGrid = new GridPane();
        widgetGrid.setHgap(16);
        widgetGrid.setVgap(16);
        widgetGrid.setPadding(new Insets(16));
        widgetGrid.setStyle("-fx-background-color: #1A1A1A; -fx-background-radius: 12px;");
        widgetGrid.setMinSize(Region.USE_COMPUTED_SIZE, Region.USE_COMPUTED_SIZE);
        widgetGrid.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);

        for (int i = 0; i < 4; i++) {
            ColumnConstraints colConstraints = new ColumnConstraints();
            colConstraints.setMinWidth(200);
            colConstraints.setPrefWidth(250);
            colConstraints.setHgrow(Priority.ALWAYS);
            widgetGrid.getColumnConstraints().add(colConstraints);
        }

        RowConstraints defaultRowConstraints = new RowConstraints();
        defaultRowConstraints.setMinHeight(130);
        defaultRowConstraints.setPrefHeight(160);
        defaultRowConstraints.setMaxHeight(190);
        defaultRowConstraints.setVgrow(Priority.NEVER);
        for (int i = 0; i < 5; i++) {
            widgetGrid.getRowConstraints().add(defaultRowConstraints);
        }

        VBox.setVgrow(widgetGrid, Priority.ALWAYS);
        setupDragAndDrop(widgetGrid);

        dashboardContainer.getChildren().addAll(titleBar, widgetGrid);

    }

    private HBox createSimulationControls() {
        HBox controls = new HBox(5);
        controls.setAlignment(Pos.CENTER);
        controls.setVisible(false);
        controls.setManaged(false);

        String[] modes = { "Disabled", "Teleop", "Auto", "Test" };
        Button[] buttons = new Button[4];
        String[] currentMode = { null };

        updateControlsVisibility(controls);

        for (int i = 0; i < modes.length; i++) {
            final String mode = modes[i];
            Button btn = new Button(mode);

            String baseStyle = "-fx-font-size: 11px; " +
                    "-fx-font-weight: bold; " +
                    "-fx-font-family: 'Segoe UI', 'Roboto', sans-serif; " +
                    "-fx-text-fill: #FFFFFF; " +
                    "-fx-background-color: #FF8C00; " +
                    "-fx-border-color: #FF8C00; " +
                    "-fx-border-width: 1px; " +
                    "-fx-border-radius: 4px; " +
                    "-fx-background-radius: 4px; " +
                    "-fx-padding: 4px 8px; " +
                    "-fx-cursor: hand;";

            btn.setStyle(baseStyle);
            btn.setMinWidth(70);
            btn.setPrefWidth(70);
            btn.setMaxWidth(70);

            btn.setOnAction(e -> {
                setSimulationMode(mode.toLowerCase());
                currentMode[0] = mode.toLowerCase();

                for (Button b : buttons) {
                    if (b != null) {
                        b.setStyle(baseStyle);
                    }
                }

                String activeStyle = "-fx-font-size: 11px; " +
                        "-fx-font-weight: bold; " +
                        "-fx-font-family: 'Segoe UI', 'Roboto', sans-serif; " +
                        "-fx-text-fill: #000000; " +
                        "-fx-background-color: #FFB84D; " +
                        "-fx-border-color: #FFB84D; " +
                        "-fx-border-width: 1px; " +
                        "-fx-border-radius: 4px; " +
                        "-fx-background-radius: 4px; " +
                        "-fx-padding: 4px 8px; " +
                        "-fx-cursor: hand;";
                btn.setStyle(activeStyle);
            });

            btn.setOnMouseEntered(e -> {
                if (!mode.toLowerCase().equals(currentMode[0])) {
                    btn.setStyle("-fx-font-size: 11px; " +
                            "-fx-font-weight: bold; " +
                            "-fx-font-family: 'Segoe UI', 'Roboto', sans-serif; " +
                            "-fx-text-fill: #FFFFFF; " +
                            "-fx-background-color: #FFA500; " +
                            "-fx-border-color: #FFA500; " +
                            "-fx-border-width: 1px; " +
                            "-fx-border-radius: 4px; " +
                            "-fx-background-radius: 4px; " +
                            "-fx-padding: 4px 8px; " +
                            "-fx-cursor: hand;");
                }
            });

            btn.setOnMouseExited(e -> {
                if (!mode.toLowerCase().equals(currentMode[0])) {
                    btn.setStyle(baseStyle);
                }
            });

            buttons[i] = btn;
            controls.getChildren().add(btn);
        }

        updateSimulationButtons(buttons, currentMode, controls);

        ScheduledExecutorService visibilityExecutor = Executors.newSingleThreadScheduledExecutor();
        visibilityExecutor.scheduleAtFixedRate(() -> {
            updateControlsVisibility(controls);
        }, 0, 500, TimeUnit.MILLISECONDS);

        return controls;
    }

    private void updateControlsVisibility(HBox controls) {
        String currentIP = UDPClient.serverIP;
        boolean isLocalhost = currentIP.equals("127.0.0.1") || currentIP.equals("localhost");

        Platform.runLater(() -> {
            controls.setVisible(isLocalhost);
            controls.setManaged(isLocalhost);
        });
    }

    private void setSimulationMode(String mode) {
        try {
            NetworkTableInstance instance = NetworkTableInstance.getDefault();
            if (instance == null) {
                return;
            }

            int modeValue = 0;
            if (mode.equals("disabled")) {
                modeValue = 0;
            } else if (mode.equals("teleop")) {
                modeValue = 1;
            } else if (mode.equals("auto")) {
                modeValue = 2;
            } else if (mode.equals("test")) {
                modeValue = 3;
            }

            NetworkTable funkyFMSTable = instance.getTable("FunkyFMS");
            if (funkyFMSTable != null) {
                NetworkTableEntry controlModeEntry = funkyFMSTable.getEntry("controlMode");
                controlModeEntry.setInteger(modeValue);
                instance.flush();
            }
        } catch (Exception e) {
            System.err.println("Error setting simulation mode: " + e.getMessage());
        }
    }

    private void updateSimulationButtons(Button[] buttons, String[] currentMode, HBox controls) {
        ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();

        executor.scheduleAtFixedRate(() -> {
            try {
                String currentIP = UDPClient.serverIP;
                boolean isLocalhost = currentIP.equals("127.0.0.1") || currentIP.equals("localhost");

                if (!isLocalhost) {
                    Platform.runLater(() -> {
                        controls.setVisible(false);
                        controls.setManaged(false);
                    });
                    return;
                }

                Platform.runLater(() -> {
                    controls.setVisible(true);
                    controls.setManaged(true);
                });

                NetworkTableInstance instance = NetworkTableInstance.getDefault();
                if (instance == null || !NetworkTablesClient.isConnected()) {
                    return;
                }

                NetworkTable fmsTable = instance.getTable("FMSInfo");
                if (fmsTable == null) {
                    return;
                }

                NetworkTableEntry fmsControlDataEntry = fmsTable.getEntry("FMSControlData");
                String mode = "disabled";

                if (fmsControlDataEntry != null && fmsControlDataEntry.exists()) {
                    Number modeValueNumber = fmsControlDataEntry.getNumber(32.0);
                    int modeValue = modeValueNumber.intValue();

                    if (modeValue == 32) {
                        mode = "disabled";
                    } else if (modeValue == 35) {
                        mode = "auto";
                    } else if (modeValue == 33) {
                        mode = "teleop";
                    } else if (modeValue == 37) {
                        mode = "test";
                    }
                }

                final String finalMode = mode;
                Platform.runLater(() -> {
                    String baseStyle = "-fx-font-size: 11px; " +
                            "-fx-font-weight: bold; " +
                            "-fx-font-family: 'Segoe UI', 'Roboto', sans-serif; " +
                            "-fx-text-fill: #FFFFFF; " +
                            "-fx-background-color: #FF8C00; " +
                            "-fx-border-color: #FF8C00; " +
                            "-fx-border-width: 1px; " +
                            "-fx-border-radius: 4px; " +
                            "-fx-background-radius: 4px; " +
                            "-fx-padding: 4px 8px; " +
                            "-fx-cursor: hand;";

                    String activeStyle = "-fx-font-size: 11px; " +
                            "-fx-font-weight: bold; " +
                            "-fx-font-family: 'Segoe UI', 'Roboto', sans-serif; " +
                            "-fx-text-fill: #000000; " +
                            "-fx-background-color: #FFB84D; " +
                            "-fx-border-color: #FFB84D; " +
                            "-fx-border-width: 1px; " +
                            "-fx-border-radius: 4px; " +
                            "-fx-background-radius: 4px; " +
                            "-fx-padding: 4px 8px; " +
                            "-fx-cursor: hand;";

                    String[] modes = { "disabled", "teleop", "auto", "test" };
                    for (int i = 0; i < buttons.length; i++) {
                        if (buttons[i] != null) {
                            if (modes[i].equals(finalMode)) {
                                buttons[i].setStyle(activeStyle);
                                currentMode[0] = finalMode;
                            } else {
                                buttons[i].setStyle(baseStyle);
                            }
                        }
                    }
                });
            } catch (Exception e) {
            }
        }, 0, 2000, TimeUnit.MILLISECONDS);
    }

    private void addWidget(String title, String type, Double min, Double max, String unit) {
        String key = "/SmartDashboard/" + title.toLowerCase().replace(" ", "_");
        DashboardWidget widget = null;

        switch (type) {
            case "number":
                widget = new NumberWidget(title, key);
                break;
            case "text":
                widget = new TextWidget(title, key);
                break;
            case "boolean":
                widget = new BooleanWidget(title, key);
                break;
        }

        if (widget != null) {
            widgets.put(key, widget);
            updateWidgetGrid();
        }
    }

    private void updateWidgetGrid() {
        System.out.println("updateWidgetGrid called from thread: " + Thread.currentThread().getName());
        System.out.println("Updating widget grid with " + widgets.size() + " widgets");

        Platform.runLater(() -> {
            widgetGrid.getChildren().clear();

            for (Map.Entry<String, DashboardWidget> entry : widgets.entrySet()) {
                String key = entry.getKey();
                DashboardWidget widget = entry.getValue();
                GridPosition pos = widgetPositions.get(key);

                if (pos != null) {
                    System.out
                            .println("Adding widget: " + widget.getTitle() + " at col=" + pos.col + ", row=" + pos.row);

                    GridPane.setHgrow(widget.getContainer(), Priority.ALWAYS);
                    GridPane.setVgrow(widget.getContainer(), Priority.SOMETIMES);
                    if (widget instanceof GraphWidget) {
                        int spanCols = Math.min(2, 4 - pos.col);
                        int spanRows = 2;
                        widgetGrid.add(widget.getContainer(), pos.col, pos.row, spanCols, spanRows);
                    } else if (widget instanceof FieldViewWidget) {
                        int spanCols = Math.min(3, 4 - pos.col);
                        int spanRows = 2;
                        widgetGrid.add(widget.getContainer(), pos.col, pos.row, spanCols, spanRows);
                    } else if (widget instanceof AutoSelectorWidget) {
                        int spanCols = Math.min(2, 4 - pos.col);
                        int spanRows = 1;
                        widgetGrid.add(widget.getContainer(), pos.col, pos.row, spanCols, spanRows);
                    } else {
                        widgetGrid.add(widget.getContainer(), pos.col, pos.row);
                    }
                    setupWidgetDragAndDrop(widget.getContainer(), key);
                }
            }

            int maxRow = 0;
            for (GridPosition pos : widgetPositions.values()) {
                if (pos.row > maxRow) {
                    maxRow = pos.row;
                }
            }

            int requiredRowIndex = Math.max(maxRow, 4);
            while (widgetGrid.getRowConstraints().size() <= requiredRowIndex) {
                RowConstraints rowConstraints = new RowConstraints();
                rowConstraints.setMinHeight(120);
                rowConstraints.setPrefHeight(150);
                rowConstraints.setMaxHeight(180);
                rowConstraints.setVgrow(Priority.NEVER); // Prevent rows from growing
                widgetGrid.getRowConstraints().add(rowConstraints);
            }
            fillEmptyCellsWithPlaceholders(maxRow);

            System.out.println("Grid now has " + widgetGrid.getChildren().size() + " children");
        });
    }

    private void fillEmptyCellsWithPlaceholders(int maxRow) {
        int maxCols = 4;
        int rowsToFill = Math.max(maxRow + 1, 5);
        boolean[][] occupied = new boolean[rowsToFill][maxCols];
        for (Map.Entry<String, DashboardWidget> entry : widgets.entrySet()) {
            String key = entry.getKey();
            DashboardWidget widget = entry.getValue();
            GridPosition pos = widgetPositions.get(key);
            if (pos == null)
                continue;
            if (widget instanceof GraphWidget) {
                for (int r = 0; r < 2; r++) {
                    for (int c = 0; c < 2; c++) {
                        int rr = pos.row + r;
                        int cc = pos.col + c;
                        if (rr >= 0 && rr < rowsToFill && cc >= 0 && cc < maxCols) {
                            occupied[rr][cc] = true;
                        }
                    }
                }
            } else if (widget instanceof FieldViewWidget) {
                for (int r = 0; r < 2; r++) {
                    for (int c = 0; c < 3; c++) {
                        int rr = pos.row + r;
                        int cc = pos.col + c;
                        if (rr >= 0 && rr < rowsToFill && cc >= 0 && cc < maxCols) {
                            occupied[rr][cc] = true;
                        }
                    }
                }
            } else if (widget instanceof AutoSelectorWidget) {
                for (int c = 0; c < 2; c++) {
                    int cc = pos.col + c;
                    if (pos.row >= 0 && pos.row < rowsToFill && cc >= 0 && cc < maxCols) {
                        occupied[pos.row][cc] = true;
                    }
                }
            } else {
                if (pos.row >= 0 && pos.row < rowsToFill && pos.col >= 0 && pos.col < maxCols) {
                    occupied[pos.row][pos.col] = true;
                }
            }
        }

        for (int row = 0; row < rowsToFill; row++) {
            for (int col = 0; col < maxCols; col++) {
                if (!occupied[row][col]) {
                    Region placeholder = new Region();
                    placeholder.setMinSize(Region.USE_COMPUTED_SIZE, Region.USE_COMPUTED_SIZE);
                    placeholder.setPrefSize(Region.USE_COMPUTED_SIZE, Region.USE_COMPUTED_SIZE);
                    placeholder.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
                    placeholder.setOpacity(0.0);
                    placeholder.setMouseTransparent(true);
                    GridPane.setHgrow(placeholder, Priority.ALWAYS);
                    GridPane.setVgrow(placeholder, Priority.SOMETIMES);
                    widgetGrid.add(placeholder, col, row);
                }
            }
        }
    }

    private void startValueUpdateLoop() {
        if (updateExecutor != null) {
            return;
        }

        updateExecutor = Executors.newScheduledThreadPool(1);
        updateExecutor.scheduleAtFixedRate(() -> {
            if (NetworkTablesClient.isConnected()) {
                NetworkTableInstance instance = NetworkTableInstance.getDefault();
                if (instance == null) {
                    return;
                }

                long currentTime = System.currentTimeMillis();

                for (Map.Entry<String, DashboardWidget> entry : widgets.entrySet()) {
                    String key = entry.getKey();
                    DashboardWidget widget = entry.getValue();

                    long lastUpdate = lastUpdateTime.getOrDefault(key, 0L);
                    long timeSinceUpdate = currentTime - lastUpdate;

                    long updateInterval;
                    if (widget instanceof GraphWidget) {
                        updateInterval = 100;
                    } else if (widget instanceof FieldViewWidget) {
                        updateInterval = 50;
                    } else if (widget instanceof NumberWidget) {
                        updateInterval = 200;
                    } else {
                        updateInterval = 333;
                    }

                    if (timeSinceUpdate < updateInterval) {
                        continue;
                    }

                    try {
                        String tableName = key.split("/")[0];
                        NetworkTable table = instance.getTable(tableName);
                        if (table == null) {
                            System.err.println("Table does not exist: " + tableName);
                            continue;
                        }

                        String entryKey = key.substring(tableName.length() + 1);

                        if (widget instanceof AutoSelectorWidget) {
                            NetworkTable chooserTable = table.getSubTable(entryKey);
                            NetworkTableEntry activeEntry = chooserTable.getEntry("active");
                            if (activeEntry.exists()) {
                                NetworkTableValue ntValue = activeEntry.getValue();
                                if (ntValue != null) {
                                    Object value = ntValue.getValue();
                                    if (value != null) {
                                        if (timeSinceUpdate >= 500) {
                                            Platform.runLater(() -> widget.updateValue(value));
                                            lastUpdateTime.put(key, currentTime);
                                        }
                                    }
                                }
                            }
                        } else if (widget instanceof FieldViewWidget) {
                            NetworkTable fieldTable = table.getSubTable(entryKey);
                            NetworkTableEntry robotEntry = fieldTable.getEntry("Robot");
                            if (robotEntry.exists()) {
                                NetworkTableValue ntValue = robotEntry.getValue();
                                if (ntValue != null) {
                                    Object value = ntValue.getValue();
                                    if (value != null) {
                                        Platform.runLater(() -> widget.updateValue(value));
                                        lastUpdateTime.put(key, currentTime);
                                    }
                                }
                            }
                        } else {
                            NetworkTableEntry ntEntry = table.getEntry(entryKey);

                            if (ntEntry.exists()) {
                                NetworkTableValue ntValue = ntEntry.getValue();
                                if (ntValue != null) {
                                    Object value = ntValue.getValue();
                                    if (value != null) {
                                        Platform.runLater(() -> widget.updateValue(value));
                                        lastUpdateTime.put(key, currentTime);
                                    }
                                }
                            } else {
                                // Entry missing; skip without logging each loop
                            }
                        }
                    } catch (Exception e) {
                        System.err.println("Error updating widget " + key + ": " + e.getMessage());
                    }
                }
            }
        }, 0, 100, TimeUnit.MILLISECONDS);
    }

    public VBox getContainer() {
        return dashboardContainer;
    }

    public void shutdown() {
        saveConfiguration();
        for (DashboardWidget widget : widgets.values()) {
            if (widget instanceof AutoSelectorWidget) {
                ((AutoSelectorWidget) widget).shutdown();
            }
        }
        if (updateExecutor != null && !updateExecutor.isShutdown()) {
            updateExecutor.shutdown();
        }
        if (loadRetryExecutor != null && !loadRetryExecutor.isShutdown()) {
            loadRetryExecutor.shutdown();
        }
    }

    private void saveConfiguration() {
        try {
            DashboardConfig config = new DashboardConfig();

            for (Map.Entry<String, DashboardWidget> entry : widgets.entrySet()) {
                String key = entry.getKey();
                DashboardWidget widget = entry.getValue();
                GridPosition pos = widgetPositions.get(key);

                if (pos != null) {
                    String widgetType = getWidgetType(widget);
                    WidgetConfig widgetConfig = new WidgetConfig(
                            key,
                            widgetType,
                            widget.getTitle(),
                            pos.col,
                            pos.row);
                    config.widgets.add(widgetConfig);
                }
            }

            ObjectMapper mapper = new ObjectMapper();
            File configFile = new File("dash.conf846");
            mapper.writerWithDefaultPrettyPrinter().writeValue(configFile, config);
            System.out.println("Dashboard configuration saved to dash.conf846");
        } catch (IOException e) {
            System.err.println("Failed to save dashboard configuration: " + e.getMessage());
        }
    }

    private void loadConfiguration() {
        try {
            File configFile = new File("dash.conf846");
            if (!configFile.exists()) {
                System.out.println("No dashboard configuration found, starting with empty dashboard");
                return;
            }

            ObjectMapper mapper = new ObjectMapper();
            DashboardConfig config = mapper.readValue(configFile, DashboardConfig.class);

            System.out.println(
                    "Loading dashboard configuration from dash.conf846 - " + config.widgets.size() + " widgets");

            synchronized (pendingWidgetsToLoad) {
                pendingWidgetsToLoad.clear();
                pendingWidgetsToLoad.addAll(config.widgets);
            }

            attemptLoadPendingWidgets();

            startLoadRetryExecutor();

            System.out.println("Dashboard configuration loaded successfully");
        } catch (IOException e) {
            System.err.println("Failed to load dashboard configuration: " + e.getMessage());
        }
    }

    private void setupConnectionListener() {
        NetworkTablesClient.connectedProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue && !oldValue) {
                System.out.println("NetworkTables connected - attempting to load pending widgets");
                attemptLoadPendingWidgets();
            }
        });
    }

    private void startLoadRetryExecutor() {
        if (loadRetryExecutor != null && !loadRetryExecutor.isShutdown()) {
            loadRetryExecutor.shutdown();
        }

        loadRetryExecutor = Executors.newSingleThreadScheduledExecutor();

        loadRetryExecutor.scheduleAtFixedRate(() -> {
            synchronized (pendingWidgetsToLoad) {
                if (pendingWidgetsToLoad.isEmpty()) {
                    return;
                }
            }

            if (NetworkTablesClient.isConnected()) {
                attemptLoadPendingWidgets();
            }
        }, 1, 2, TimeUnit.SECONDS);
    }

    private void attemptLoadPendingWidgets() {
        synchronized (pendingWidgetsToLoad) {
            if (pendingWidgetsToLoad.isEmpty()) {
                return;
            }
        }

        if (!NetworkTablesClient.isConnected()) {
            return;
        }

        loadConfigWidgets(pendingWidgetsToLoad);
    }

    private void loadConfigWidgets(List<WidgetConfig> widgetsToLoad) {
        List<WidgetConfig> toRemove = new ArrayList<>();

        synchronized (widgetsToLoad) {
            if (widgetsToLoad.isEmpty()) {
                return;
            }

            NetworkTableInstance instance = NetworkTablesClient.getInstance();
            if (instance == null || !NetworkTablesClient.isConnected()) {
                return;
            }

            for (WidgetConfig widgetConfig : widgetsToLoad) {
                if (widgets.containsKey(widgetConfig.key)) {
                    toRemove.add(widgetConfig);
                    continue;
                }

                try {
                    String firstSegment = widgetConfig.key.split("/")[0];
                    NetworkTable table = instance.getTable(firstSegment);

                    if (table == null) {
                        continue;
                    }

                    String entryKey = widgetConfig.key.substring(firstSegment.length() + 1);

                    boolean shouldLoad = false;

                    if ("autoselector".equals(widgetConfig.type)) {
                        NetworkTable subTable = table.getSubTable(entryKey);
                        if (subTable != null) {
                            NetworkTableEntry activeEntry = subTable.getEntry("active");
                            if (activeEntry.exists()) {
                                shouldLoad = true;
                            }
                        }
                    } else if ("fieldview".equals(widgetConfig.type)) {
                        NetworkTable subTable = table.getSubTable(entryKey);
                        if (subTable != null) {
                            NetworkTableEntry robotEntry = subTable.getEntry("Robot");
                            if (robotEntry.exists()) {
                                shouldLoad = true;
                            }
                        }
                    } else {
                        NetworkTableEntry entry = table.getEntry(entryKey);
                        if (entry.exists()) {
                            shouldLoad = true;
                        } else {
                            NetworkTable subTable = table.getSubTable(entryKey);
                            if (subTable != null) {
                                shouldLoad = true;
                            }
                        }
                    }

                    if (shouldLoad) {
                        DashboardWidget widget = createWidgetFromType(
                                widgetConfig.type,
                                widgetConfig.key,
                                null);

                        if (widget != null) {
                            widgets.put(widgetConfig.key, widget);
                            widgetPositions.put(widgetConfig.key,
                                    new GridPosition(widgetConfig.col, widgetConfig.row));

                            if (widget instanceof NumberWidget) {
                                ((NumberWidget) widget).setRemoveCallback(() -> removeWidget(widgetConfig.key));
                            } else if (widget instanceof GraphWidget) {
                                ((GraphWidget) widget).setRemoveCallback(() -> removeWidget(widgetConfig.key));
                            } else if (widget instanceof FieldViewWidget) {
                                ((FieldViewWidget) widget)
                                        .setRemoveCallback(() -> removeWidget(widgetConfig.key));
                            } else if (widget instanceof AutoSelectorWidget) {
                                ((AutoSelectorWidget) widget)
                                        .setRemoveCallback(() -> removeWidget(widgetConfig.key));
                            }

                            setupWidgetDragAndDrop(widget.getContainer(), widgetConfig.key);
                            toRemove.add(widgetConfig);
                            System.out.println(
                                    "Successfully loaded widget: " + widgetConfig.key + " (" + widgetConfig.type + ")");
                        }
                    }
                } catch (Exception e) {
                }
            }

            widgetsToLoad.removeAll(toRemove);
        }

        if (!toRemove.isEmpty()) {
            int remaining = widgetsToLoad.size();
            System.out.println("Loaded " + toRemove.size() + " widget(s), " + remaining + " remaining");
            Platform.runLater(() -> updateWidgetGrid());
        }
    }

    private String getWidgetType(DashboardWidget widget) {
        if (widget instanceof GraphWidget) {
            return "graph";
        } else if (widget instanceof FieldViewWidget) {
            return "fieldview";
        } else if (widget instanceof AutoSelectorWidget) {
            return "autoselector";
        } else if (widget instanceof NumberWidget) {
            return "number";
        } else if (widget instanceof BooleanWidget) {
            return "boolean";
        } else if (widget instanceof TextWidget) {
            return "text";
        }
        return "text";
    }

    private void setupDragAndDrop(javafx.scene.Node target) {
        target.setOnDragEntered(event -> {
            widgetGrid.setStyle(
                    "-fx-background-color: #1A1A1A; -fx-background-radius: 12; -fx-border-color: #FF8C00; -fx-border-width: 2px; -fx-border-radius: 12;");
            showGridSkeleton();
        });

        target.setOnDragExited(event -> {
            widgetGrid.setStyle("-fx-background-color: #1A1A1A;");
            hideGridSkeleton();
        });

        target.setOnDragOver(event -> {
            if (event.getDragboard().hasString()) {
                event.acceptTransferModes(TransferMode.MOVE, TransferMode.COPY);
            }
            event.consume();
        });

        target.setOnDragDropped(event -> {
            Dragboard dragboard = event.getDragboard();
            boolean success = false;

            if (dragboard.hasString()) {
                String data = dragboard.getString();
                System.out.println("Drop received - data: '" + data + "'");

                double dropX = event.getX();
                double dropY = event.getY();
                GridPosition targetPosition = calculateGridPosition(dropX, dropY);
                System.out
                        .println("Calculated grid position: col=" + targetPosition.col + ", row=" + targetPosition.row);

                try {
                    if (data.startsWith("MOVE:")) {
                        String widgetKey = data.substring(5);
                        System.out.println("Moving widget: " + widgetKey);

                        if (widgets.containsKey(widgetKey)) {
                            DashboardWidget w = widgets.get(widgetKey);
                            GridPosition newPos = targetPosition;
                            int spanCols = 1, spanRows = 1;

                            if (w instanceof GraphWidget) {
                                spanCols = 2;
                                spanRows = 2;
                            } else if (w instanceof FieldViewWidget) {
                                spanCols = 3;
                                spanRows = 2;
                            } else if (w instanceof AutoSelectorWidget) {
                                spanCols = 2;
                                spanRows = 1;
                            }

                            int clampedCol = Math.max(0, Math.min(targetPosition.col, 4 - spanCols));
                            newPos = new GridPosition(clampedCol, targetPosition.row);

                            java.util.List<String> toRemove = new java.util.ArrayList<>();
                            for (Map.Entry<String, GridPosition> e : widgetPositions.entrySet()) {
                                String otherKey = e.getKey();
                                if (otherKey.equals(widgetKey))
                                    continue;
                                GridPosition p = e.getValue();
                                if (p.row >= newPos.row && p.row < newPos.row + spanRows && p.col >= newPos.col
                                        && p.col < newPos.col + spanCols) {
                                    toRemove.add(otherKey);
                                }
                            }
                            for (String k : toRemove) {
                                widgets.remove(k);
                                widgetPositions.remove(k);
                                lastUpdateTime.remove(k);
                            }

                            widgetPositions.put(widgetKey, newPos);
                            updateWidgetGrid();
                            saveConfiguration();
                            success = true;
                        }
                    } else {
                        String key = data;
                        NetworkTableInstance instance = NetworkTablesClient.getInstance();
                        if (instance == null) {
                            System.err.println("NetworkTables instance is null");
                            event.setDropCompleted(false);
                            hideGridSkeleton();
                            event.consume();
                            return;
                        }

                        String firstSegment = key.split("/")[0];
                        NetworkTable table = instance.getTable(firstSegment);

                        NetworkTableEntry entry = table.getEntry(key.substring(firstSegment.length() + 1));

                        System.out.println("Entry exists: " + entry.exists());

                        DashboardWidget widget = null;
                        try {
                            NetworkTableType entryType = entry.getType();
                            System.out.println("Entry type: " + entryType);

                            String keyBasedType = determineWidgetTypeFromKey(key);
                            if (keyBasedType != null) {
                                System.out.println("Widget type from key pattern: " + keyBasedType);
                                widget = createWidgetFromType(keyBasedType, key, null);
                                System.out.println("Widget successfully added!");
                            } else {
                                NetworkTableValue ntValue = entry.getValue();
                                if (ntValue != null) {
                                    Object value = ntValue.getValue();
                                    if (value != null) {
                                        System.out.println(
                                                "Entry value: " + value + " (type: " + value.getClass().getSimpleName()
                                                        + ")");
                                        String widgetType = determineWidgetType(value);
                                        System.out.println("Widget type determined: " + widgetType);
                                        widget = createWidgetFromType(widgetType, key, value);
                                        System.out.println("Widget successfully added!");
                                    } else {
                                        System.out.println("Value is null but entry type is available: " + entryType);
                                        String widgetType = determineWidgetTypeFromNetworkTableType(entryType);
                                        System.out.println("Widget type from entry type: " + widgetType);
                                        widget = createWidgetFromType(widgetType, key, null);
                                    }
                                } else {
                                    String widgetType = determineWidgetTypeFromNetworkTableType(entryType);
                                    System.out.println(
                                            "NetworkTableValue is null, using entry type to determine widget: "
                                                    + widgetType);
                                    widget = createWidgetFromType(widgetType, key, null);
                                }
                            }

                            if (widget == null) {
                                System.out.println("Widget creation returned null, creating text widget");
                                widget = new TextWidget(key.substring(key.lastIndexOf('/') + 1), key);
                            }
                        } catch (Exception valueError) {
                            System.err.println("Error reading entry value: " + valueError.getMessage());
                            System.out.println("Creating text widget with key name...");
                            widget = new TextWidget(key.substring(key.lastIndexOf('/') + 1), key);
                        }

                        if (widget != null) {
                            widgetPositions.put(widget.getKey(), targetPosition);
                            addWidgetToGrid(widget);
                            saveConfiguration();
                            success = true;

                            widgetGrid.setStyle(
                                    "-fx-background-color: #1A3A1A; -fx-background-radius: 12; -fx-border-color: #00FF00; -fx-border-width: 2px; -fx-border-radius: 12;");

                            Platform.runLater(() -> {
                                try {
                                    Thread.sleep(200);
                                    Platform.runLater(() -> {
                                        widgetGrid.setStyle("-fx-background-color: #1A1A1A;");
                                    });
                                } catch (InterruptedException e) {
                                    widgetGrid.setStyle("-fx-background-color: #1A1A1A;");
                                }
                            });
                        }
                    }
                } catch (Exception e) {
                    System.err.println("Error handling drop: " + e.getMessage());
                    e.printStackTrace();
                }
            } else {
                System.err.println("Dragboard does not contain string");
            }

            event.setDropCompleted(success);
            hideGridSkeleton();
            event.consume();
        });
    }

    private String determineWidgetType(Object value) {
        if (value == null) {
            return "text";
        }

        if (value instanceof Number) {
            return "number";
        }

        if (value instanceof Boolean) {
            return "boolean";
        }

        if (value instanceof String) {
            String strValue = ((String) value).trim().toLowerCase();
            if (strValue.equals("true") || strValue.equals("false")) {
                return "boolean";
            }
            try {
                Boolean.parseBoolean(strValue);
                return "boolean";
            } catch (Exception e) {
                return "text";
            }
        }

        return "text";
    }

    private String determineWidgetTypeFromKey(String key) {
        if (key.endsWith("/active")) {
            String parentPath = key.substring(0, key.length() - 7);
            try {
                NetworkTableInstance instance = NetworkTablesClient.getInstance();
                if (instance != null) {
                    NetworkTable table = instance.getTable(parentPath.split("/")[0]);
                    if (table != null) {
                        String subKey = parentPath.substring(parentPath.indexOf('/') + 1);
                        NetworkTable subTable = table.getSubTable(subKey);
                        var keys = subTable.getKeys();
                        for (String k : keys) {
                            if (k.equals("options")) {
                                return "autoselector";
                            }
                        }
                    }
                }
            } catch (Exception e) {
            }
        } else if (!key.contains("/")) {
            return null;
        } else {
            try {
                NetworkTableInstance instance = NetworkTablesClient.getInstance();
                if (instance != null) {
                    NetworkTable table = instance.getTable(key.split("/")[0]);
                    if (table != null) {
                        String subKey = key.substring(key.indexOf('/') + 1);
                        NetworkTable subTable = table.getSubTable(subKey);
                        var keys = subTable.getKeys();
                        boolean hasRobot = false;
                        boolean hasActive = false;
                        boolean hasOptions = false;
                        for (String k : keys) {
                            if (k.equals("robot") || k.contains("Robot")) {
                                hasRobot = true;
                            } else if (k.equals("active")) {
                                hasActive = true;
                            } else if (k.equals("options")) {
                                hasOptions = true;
                            }
                        }
                        if (hasRobot && !(hasActive && hasOptions)) {
                            return "fieldview";
                        }
                    }
                }
            } catch (Exception e) {
            }
        }
        return null;
    }

    private String determineWidgetTypeFromNetworkTableType(NetworkTableType type) {
        if (type == NetworkTableType.kBoolean) {
            return "boolean";
        } else if (type == NetworkTableType.kDouble || type == NetworkTableType.kInteger
                || type == NetworkTableType.kFloat) {
            return "number";
        } else if (type == NetworkTableType.kString || type == NetworkTableType.kUnassigned) {
            return "text";
        }
        return "text";
    }

    private DashboardWidget createWidgetFromType(String type, String key, Object value) {
        String title = key.contains("/") ? key.substring(key.lastIndexOf('/') + 1) : key;

        switch (type) {
            case "number":
                NumberWidget numWidget = new NumberWidget(title, key);
                numWidget.setContextMenuCallback(() -> convertWidgetToGraph(key));
                return numWidget;
            case "graph":
                GraphWidget graphWidget = new GraphWidget(title, key);
                graphWidget.setContextMenuCallback(() -> convertWidgetToNormal(key));
                return graphWidget;
            case "boolean":
                return new BooleanWidget(title, key);
            case "autoselector":
                String parentKey = key.endsWith("/active") ? key.substring(0, key.length() - 7) : key;
                String parentTitle = parentKey.contains("/") ? parentKey.substring(parentKey.lastIndexOf('/') + 1)
                        : parentKey;
                AutoSelectorWidget autoWidget = new AutoSelectorWidget(parentTitle, parentKey);
                return autoWidget;
            case "fieldview":
                FieldViewWidget fieldWidget = new FieldViewWidget(title, key);
                return fieldWidget;
            case "text":
            default:
                return new TextWidget(title, key);
        }
    }

    private void convertWidgetToGraph(String key) {
        DashboardWidget currentWidget = widgets.get(key);
        if (currentWidget instanceof NumberWidget) {
            if (key.startsWith("Preferences/")) {
                return;
            }
            String title = currentWidget.getTitle();
            GridPosition base = widgetPositions.get(key);
            if (base == null) {
                base = findNextAvailablePosition();
            }
            int clampedCol = Math.max(0, Math.min(base.col, 4 - 2));
            GridPosition graphPos = new GridPosition(clampedCol, base.row);

            java.util.List<String> toRemove = new java.util.ArrayList<>();
            for (Map.Entry<String, GridPosition> e : widgetPositions.entrySet()) {
                String otherKey = e.getKey();
                if (otherKey.equals(key))
                    continue;
                GridPosition p = e.getValue();
                if (p.row >= graphPos.row && p.row < graphPos.row + 2 && p.col >= graphPos.col
                        && p.col < graphPos.col + 2) {
                    toRemove.add(otherKey);
                }
            }
            for (String k : toRemove) {
                widgets.remove(k);
                widgetPositions.remove(k);
                lastUpdateTime.remove(k);
            }

            GraphWidget graphWidget = new GraphWidget(title, key);
            graphWidget.setContextMenuCallback(() -> convertWidgetToNormal(key));
            graphWidget.setRemoveCallback(() -> removeWidget(key));
            widgets.put(key, graphWidget);
            widgetPositions.put(key, graphPos);
            updateWidgetGrid();
            saveConfiguration();
        }
    }

    private void convertWidgetToNormal(String key) {
        DashboardWidget currentWidget = widgets.get(key);
        if (currentWidget instanceof GraphWidget) {
            String title = currentWidget.getTitle();

            NumberWidget numWidget = new NumberWidget(title, key);
            numWidget.setContextMenuCallback(() -> convertWidgetToGraph(key));
            widgets.put(key, numWidget);
            updateWidgetGrid();
            saveConfiguration();
        }
    }

    private void addWidgetToGrid(DashboardWidget widget) {
        String key = widget.getKey();
        System.out.println("AWG: Adding widget to grid: " + key);

        if (!widgets.containsKey(key)) {
            widgets.put(key, widget);
        }

        if (!widgetPositions.containsKey(key)) {
            widgetPositions.put(key, findNextAvailablePosition());
        }

        setupWidgetDragAndDrop(widget.getContainer(), key);
        if (widget instanceof NumberWidget) {
            ((NumberWidget) widget).setRemoveCallback(() -> removeWidget(key));
        } else if (widget instanceof GraphWidget) {
            ((GraphWidget) widget).setRemoveCallback(() -> removeWidget(key));
        } else if (widget instanceof FieldViewWidget) {
            ((FieldViewWidget) widget).setRemoveCallback(() -> removeWidget(key));
        } else if (widget instanceof AutoSelectorWidget) {
            ((AutoSelectorWidget) widget).setRemoveCallback(() -> removeWidget(key));
        }
        updateWidgetGrid();
        saveConfiguration();
    }

    private GridPosition findNextAvailablePosition() {
        int maxCols = 4;
        int row = 0;

        while (true) {
            for (int col = 0; col < maxCols; col++) {
                boolean positionOccupied = false;
                for (GridPosition pos : widgetPositions.values()) {
                    if (pos.col == col && pos.row == row) {
                        positionOccupied = true;
                        break;
                    }
                }
                if (!positionOccupied) {
                    return new GridPosition(col, row);
                }
            }
            row++;
        }
    }

    private void setupWidgetDragAndDrop(javafx.scene.Node widgetContainer, String widgetKey) {
        widgetContainer.setOnDragDetected(event -> {
            System.out.println("Drag detected for widget: " + widgetKey);
            Dragboard dragboard = widgetContainer.startDragAndDrop(TransferMode.MOVE);
            ClipboardContent content = new ClipboardContent();
            content.putString("MOVE:" + widgetKey);
            dragboard.setContent(content);
            event.consume();
        });

        widgetContainer.setOnDragDone(event -> {
            System.out.println("Drag done for widget: " + widgetKey);
            event.consume();
        });
    }

    private void removeWidget(String key) {
        DashboardWidget widget = widgets.get(key);
        if (widget instanceof AutoSelectorWidget) {
            ((AutoSelectorWidget) widget).shutdown();
        } else if (widget instanceof GraphWidget) {
            ((GraphWidget) widget).stop();
        }
        widgets.remove(key);
        widgetPositions.remove(key);
        lastUpdateTime.remove(key);
        updateWidgetGrid();
        saveConfiguration();
    }

    private void showGridSkeleton() {
        if (gridSkeleton != null) {
            hideGridSkeleton();
        }

        gridSkeleton = new Region[20];
        for (int i = 0; i < 20; i++) {
            Region cell = new Region();
            cell.setStyle(
                    "-fx-background-color: rgba(128, 128, 128, 0.2); -fx-border-color: rgba(128, 128, 128, 0.4); -fx-border-width: 1px; -fx-background-radius: 8px; -fx-border-radius: 8px;");
            cell.setMinSize(180, 140);
            cell.setMaxSize(320, 240);
            cell.setPrefSize(180, 140);
            gridSkeleton[i] = cell;

            int col = i % 4;
            int row = i / 4;
            widgetGrid.add(cell, col, row);
        }
    }

    private void hideGridSkeleton() {
        if (gridSkeleton != null) {
            for (Region cell : gridSkeleton) {
                widgetGrid.getChildren().remove(cell);
            }
            gridSkeleton = null;
        }
    }

    private GridPosition calculateGridPosition(double x, double y) {
        int maxCols = 4;

        double leftPadding = 15;
        double topPadding = 15;
        double hgap = 15;
        double vgap = 15;

        double cellWidth = 250 + hgap;
        double cellHeight = 150 + vgap;

        int col = (int) ((x - leftPadding) / cellWidth);
        int row = (int) ((y - topPadding) / cellHeight);

        col = Math.max(0, Math.min(col, maxCols - 1));
        row = Math.max(0, row);

        return new GridPosition(col, row);
    }
}
