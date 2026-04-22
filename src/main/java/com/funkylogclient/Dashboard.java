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
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
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
    private final List<Runnable> uiUpdatesReuse = new ArrayList<>();
    private Region[] gridSkeleton = null;
    private List<WidgetConfig> pendingWidgetsToLoad = new ArrayList<>();
    private ScheduledExecutorService loadRetryExecutor;
    private SimDriverStationInput simDriverStationInput;

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

    /** Resolve dash config file so it works when run from project dir (dev) and when packaged (next to exe). */
    private static File getConfigFile() {
        File appDir = getApplicationDirectory();
        File configInAppDir = new File(appDir, "dash.conf846");
        if (configInAppDir.exists()) {
            return configInAppDir;
        }
        File configInCwd = new File("dash.conf846");
        if (configInCwd.exists()) {
            return configInCwd;
        }
        return configInAppDir;
    }

    /** Directory containing the app (JAR or exe); when not running from a JAR, use current working dir. */
    private static File getApplicationDirectory() {
        try {
            java.net.URL location = Dashboard.class.getProtectionDomain().getCodeSource().getLocation();
            String path = URLDecoder.decode(location.getPath(), StandardCharsets.UTF_8);
            if (path == null || path.isEmpty()) {
                return new File(System.getProperty("user.dir"));
            }
            File jarOrDir = new File(path);
            if (jarOrDir.isFile()) {
                return jarOrDir.getParentFile();
            }
            return new File(System.getProperty("user.dir"));
        } catch (Exception e) {
            return new File(System.getProperty("user.dir"));
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
        dashboardContainer.setNodeOrientation(javafx.geometry.NodeOrientation.LEFT_TO_RIGHT);
        dashboardContainer.setPadding(new Insets(16, 20, 20, 20));
        dashboardContainer.setStyle("-fx-background-color: " + Styles.BG_DARKEST + ";");

        HBox titleBar = new HBox(12);
        titleBar.setAlignment(Pos.CENTER_LEFT);
        titleBar.setPadding(new Insets(0, 0, 8, 0));

        Text title = new Text("Dashboard");
        title.setStyle(
                "-fx-font-size: 22px; -fx-fill: " + Styles.TEXT_WHITE + "; -fx-font-weight: bold; -fx-font-family: " + Styles.FONT_FAMILY + ";");

        Text subtitle = new Text("Drag items from the sidebar to add widgets");
        subtitle.setStyle(
                "-fx-font-size: 12px; -fx-fill: " + Styles.TEXT_MUTED + "; -fx-font-family: " + Styles.FONT_FAMILY + ";");

        VBox titleContainer = new VBox(2);
        titleContainer.getChildren().addAll(title, subtitle);

        HBox simControls = createSimulationControls();

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        titleBar.getChildren().addAll(titleContainer, spacer, simControls);

        StackPane gridContainer = new StackPane();
        gridContainer.setStyle("-fx-background-color: " + Styles.BG_DARKEST + "; -fx-background-radius: 12px;");
        gridContainer.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        
        createGridBackground(gridContainer);
        
        widgetGrid = new GridPane();
        widgetGrid.setHgap(16);
        widgetGrid.setVgap(16);
        widgetGrid.setPadding(new Insets(16));
        widgetGrid.setStyle("-fx-background-color: transparent;");
        widgetGrid.setMinSize(Region.USE_COMPUTED_SIZE, Region.USE_COMPUTED_SIZE);
        widgetGrid.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        
        gridContainer.getChildren().add(widgetGrid);
        
        Platform.runLater(() -> {
            widgetGrid.toFront();
        });

        currentCellWidth = 250.0;
        currentCellHeight = 160.0;
        currentPadding = 16.0;
        currentHgap = 16.0;
        currentVgap = 16.0;
        
        setupDynamicGridConstraints(gridContainer);

        VBox.setVgrow(gridContainer, Priority.ALWAYS);
        setupDragAndDrop(gridContainer);

        dashboardContainer.getChildren().addAll(titleBar, gridContainer);

        setupSimDriverStationInput();
    }
    
    private Region[] gridBackgroundCells = null;
    private GridPane backgroundGrid = null;
    private GridPane skeletonGrid = null;
    private StackPane gridContainerRef = null;
    private Region currentGlowingCell = null;
    private double currentCellWidth = 250.0;
    private double currentCellHeight = 160.0;
    private double currentPadding = 16.0;
    private double currentHgap = 16.0;
    private double currentVgap = 16.0;
    private boolean isDragging = false;
    
    private void createGridBackground(StackPane container) {
        this.gridContainerRef = container;
        int maxCols = 5;
        int maxRows = 6;
        gridBackgroundCells = new Region[maxCols * maxRows];
        
        backgroundGrid = new GridPane();
        backgroundGrid.setHgap(16);
        backgroundGrid.setVgap(16);
        backgroundGrid.setPadding(new Insets(16));
        backgroundGrid.setStyle("-fx-background-color: transparent;");
        backgroundGrid.setMouseTransparent(true);
        
        skeletonGrid = new GridPane();
        skeletonGrid.setHgap(16);
        skeletonGrid.setVgap(16);
        skeletonGrid.setPadding(new Insets(16));
        skeletonGrid.setStyle("-fx-background-color: transparent;");
        skeletonGrid.setMouseTransparent(true);
        skeletonGrid.setVisible(false);
        
        for (int row = 0; row < maxRows; row++) {
            for (int col = 0; col < maxCols; col++) {
                Region cell = new Region();
                cell.setStyle(
                    "-fx-background-color: transparent; " +
                    "-fx-border-color: rgba(128, 128, 128, 0.15); " +
                    "-fx-border-width: 1px;"
                );
                cell.setMouseTransparent(true);
                GridPane.setHgrow(cell, Priority.ALWAYS);
                GridPane.setVgrow(cell, Priority.NEVER);
                
                int index = row * maxCols + col;
                gridBackgroundCells[index] = cell;
                backgroundGrid.add(cell, col, row);
            }
        }
        
        container.getChildren().addAll(backgroundGrid, skeletonGrid);
        
        Platform.runLater(() -> {
            backgroundGrid.toBack();
            if (skeletonGrid != null) {
                skeletonGrid.toFront();
            }
        });
        
        setupDynamicGridConstraints(container);
    }
    
    private void setupDynamicGridConstraints(StackPane container) {
        container.widthProperty().addListener((obs, oldVal, newVal) -> updateGridConstraints());
        container.heightProperty().addListener((obs, oldVal, newVal) -> updateGridConstraints());
        
        updateGridConstraints();
    }
    
    private void updateGridConstraints() {
        if (gridContainerRef == null || isDragging) return;
        
        int maxCols = 5;
        double containerWidth = gridContainerRef.getWidth();
        double containerHeight = gridContainerRef.getHeight();
        
        if (containerWidth <= 0 || containerHeight <= 0) {
            containerWidth = 1000;
            containerHeight = 800;
        }
        
        double padding = 32;
        double hgap = 16;
        double vgap = 16;
        
        double availableWidth = containerWidth - padding;
        double availableHeight = containerHeight - padding - 64;
        
        double cellWidth = (availableWidth - (hgap * (maxCols - 1))) / maxCols;
        
        double minCellWidth = 200;
        double maxCellWidth = 400;
        cellWidth = Math.max(minCellWidth, Math.min(cellWidth, maxCellWidth));
        
        double minCellHeight = 100;
        double maxCellHeight = 200;
        
        double testCellHeight = (availableHeight - (vgap * 5)) / 6;
        double cellHeight = Math.max(minCellHeight, Math.min(testCellHeight, maxCellHeight));
        
        int fixedRows = 6;
        double actualGridHeight = (cellHeight * fixedRows) + (vgap * (fixedRows - 1)) + padding;
        
        if (actualGridHeight > containerHeight) {
            cellHeight = (availableHeight - (vgap * (fixedRows - 1))) / fixedRows;
            cellHeight = Math.max(minCellHeight, Math.min(cellHeight, maxCellHeight));
        }
        
        final double finalCellHeight = cellHeight;
        final double finalCellWidth = cellWidth;
        final int finalRowsNeeded = fixedRows;
        
        Platform.runLater(() -> {
            if (isDragging) return;
            
            currentCellWidth = finalCellWidth;
            currentCellHeight = finalCellHeight;
            currentPadding = 16.0;
            currentHgap = 16.0;
            currentVgap = 16.0;
            
            updateGridPaneConstraints(widgetGrid, finalCellWidth, finalCellHeight, maxCols, finalRowsNeeded);
            if (backgroundGrid != null) {
                updateBackgroundGrid(finalRowsNeeded, maxCols);
                updateGridPaneConstraints(backgroundGrid, finalCellWidth, finalCellHeight, maxCols, finalRowsNeeded);
            }
            if (skeletonGrid != null) {
                updateGridPaneConstraints(skeletonGrid, finalCellWidth, finalCellHeight, maxCols, finalRowsNeeded);
            }
        });
    }
    
    private void updateBackgroundGrid(int rowsNeeded, int maxCols) {
        if (backgroundGrid == null) return;
        
        int currentRows = gridBackgroundCells == null ? 0 : gridBackgroundCells.length / maxCols;
        
        if (rowsNeeded > currentRows) {
            Region[] newCells = new Region[maxCols * rowsNeeded];
            
            if (gridBackgroundCells != null) {
                System.arraycopy(gridBackgroundCells, 0, newCells, 0, gridBackgroundCells.length);
            }
            
            for (int row = currentRows; row < rowsNeeded; row++) {
                for (int col = 0; col < maxCols; col++) {
                    Region cell = new Region();
                    cell.setStyle(
                        "-fx-background-color: transparent; " +
                        "-fx-border-color: rgba(128, 128, 128, 0.15); " +
                        "-fx-border-width: 1px;"
                    );
                    cell.setMouseTransparent(true);
                    GridPane.setHgrow(cell, Priority.ALWAYS);
                    GridPane.setVgrow(cell, Priority.NEVER);
                    
                    int index = row * maxCols + col;
                    newCells[index] = cell;
                    backgroundGrid.add(cell, col, row);
                }
            }
            
            gridBackgroundCells = newCells;
        } else if (rowsNeeded < currentRows) {
            for (int row = rowsNeeded; row < currentRows; row++) {
                for (int col = 0; col < maxCols; col++) {
                    int index = row * maxCols + col;
                    if (index < gridBackgroundCells.length && gridBackgroundCells[index] != null) {
                        backgroundGrid.getChildren().remove(gridBackgroundCells[index]);
                        gridBackgroundCells[index] = null;
                    }
                }
            }
            
            Region[] newCells = new Region[maxCols * rowsNeeded];
            System.arraycopy(gridBackgroundCells, 0, newCells, 0, newCells.length);
            gridBackgroundCells = newCells;
        }
    }
    
    private void updateGridPaneConstraints(GridPane grid, double cellWidth, double cellHeight, int maxCols, int maxRows) {
        grid.getColumnConstraints().clear();
        grid.getRowConstraints().clear();
        
        for (int col = 0; col < maxCols; col++) {
            ColumnConstraints colConstraints = new ColumnConstraints();
            colConstraints.setMinWidth(cellWidth);
            colConstraints.setPrefWidth(cellWidth);
            colConstraints.setMaxWidth(cellWidth);
            colConstraints.setHgrow(Priority.ALWAYS);
            grid.getColumnConstraints().add(colConstraints);
        }
        
        for (int row = 0; row < maxRows; row++) {
            RowConstraints rowConstraints = new RowConstraints();
            rowConstraints.setMinHeight(cellHeight);
            rowConstraints.setPrefHeight(cellHeight);
            rowConstraints.setMaxHeight(cellHeight);
            rowConstraints.setVgrow(Priority.NEVER);
            grid.getRowConstraints().add(rowConstraints);
        }
    }
    
    private void updateGridBackgroundGlow(int col, int row) {
        if (gridBackgroundCells == null) return;
        
        Platform.runLater(() -> {
            if (currentGlowingCell != null) {
                currentGlowingCell.setStyle(
                    "-fx-background-color: transparent; -fx-border-color: rgba(128, 128, 128, 0.15); -fx-border-width: 1px; -fx-effect: null;"
                );
            }
            
            int maxCols = 5;
            int index = row * maxCols + col;
            if (index >= 0 && index < gridBackgroundCells.length) {
                currentGlowingCell = gridBackgroundCells[index];
                if (currentGlowingCell != null) {
                    currentGlowingCell.setStyle(
                        "-fx-background-color: rgba(255, 140, 0, 0.1); -fx-border-color: rgba(255, 140, 0, 0.5); -fx-border-width: 2px; -fx-effect: dropshadow(gaussian, rgba(255, 140, 0, 0.4), 10, 0, 0, 0);"
                    );
                }
            }
        });
    }
    
    private void clearGridBackgroundGlow() {
        if (currentGlowingCell != null) {
            Platform.runLater(() -> {
                currentGlowingCell.setStyle(
                    "-fx-background-color: transparent; -fx-border-color: rgba(128, 128, 128, 0.15); -fx-border-width: 1px; -fx-effect: null;"
                );
                currentGlowingCell = null;
            });
        }
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
        }, 0, 2000, TimeUnit.MILLISECONDS);

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
        if (!NetworkTablesClient.isConnected()) {
            NetworkTablesClient.connect();
            new Thread(() -> {
                try {
                    long start = System.currentTimeMillis();
                    while (!NetworkTablesClient.isConnected() && (System.currentTimeMillis() - start) < 1200) {
                        Thread.sleep(25);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                writeSimulationMode(mode);
            }, "FunkyLog-SetSimMode").start();
        } else {
            writeSimulationMode(mode);
        }
    }

    private void writeSimulationMode(String mode) {
        try {
            NetworkTableInstance instance = NetworkTableInstance.getDefault();
            if (instance == null) return;

            NetworkTable funkyFMSTable = instance.getTable("FunkyFMS");
            if (funkyFMSTable == null) return;
            long controlMode = 0;
            if ("teleop".equals(mode)) controlMode = 1;
            else if ("auto".equals(mode)) controlMode = 2;
            else if ("test".equals(mode)) controlMode = 3;
            funkyFMSTable.getEntry("controlMode").setDouble((double) controlMode);
            instance.flush();
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

                NetworkTable funkyFMS = instance.getTable("FunkyFMS");
                if (funkyFMS == null) {
                    return;
                }

                NetworkTableEntry controlModeEntry = funkyFMS.getEntry("controlMode");
                long cm = 0;
                if (controlModeEntry != null) {
                    cm = (long) controlModeEntry.getDouble(0.0);
                }

                String mode;
                if (cm == 1) mode = "teleop";
                else if (cm == 2) mode = "auto";
                else if (cm == 3) mode = "test";
                else mode = "disabled";

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
            widget.setResizeRequestCallback(() -> updateWidgetGrid());
            widgets.put(key, widget);
            updateWidgetGrid();
        }
    }

    private void updateWidgetGrid() {

        Platform.runLater(() -> {
            widgetGrid.getChildren().clear();
            widgetGrid.toFront();

            for (Map.Entry<String, DashboardWidget> entry : widgets.entrySet()) {
                String key = entry.getKey();
                DashboardWidget widget = entry.getValue();
                GridPosition pos = widgetPositions.get(key);

                if (pos != null) {
                    System.out
                            .println("Adding widget: " + widget.getTitle() + " at col=" + pos.col + ", row=" + pos.row);

                    GridPane.setHgrow(widget.getContainer(), Priority.ALWAYS);
                    GridPane.setVgrow(widget.getContainer(), Priority.NEVER);
                    widget.getContainer().setMaxHeight(Region.USE_COMPUTED_SIZE);
                    widget.getContainer().setMaxWidth(Region.USE_COMPUTED_SIZE);
                    int spanCols = Math.min(widget.getColSpan(), 5 - pos.col);
                    int spanRows = widget.getRowSpan();
                    if (spanCols > 1 || spanRows > 1) {
                        widgetGrid.add(widget.getContainer(), pos.col, pos.row, spanCols, spanRows);
                    } else {
                        widgetGrid.add(widget.getContainer(), pos.col, pos.row);
                    }
                    setupWidgetDragAndDrop(widget.getContainer(), key);
                }
            }

            fillEmptyCellsWithPlaceholders(6);
            if (!isDragging) {
                updateGridConstraints();
            }

        });
    }

    private void fillEmptyCellsWithPlaceholders(int maxRow) {
        int maxCols = 5;
        int rowsToFill = 6;
        boolean[][] occupied = new boolean[rowsToFill][maxCols];
        for (Map.Entry<String, DashboardWidget> entry : widgets.entrySet()) {
            String key = entry.getKey();
            DashboardWidget widget = entry.getValue();
            GridPosition pos = widgetPositions.get(key);
            if (pos == null)
                continue;
            int rSpan = widget.getRowSpan();
            int cSpan = widget.getColSpan();
            for (int r = 0; r < rSpan; r++) {
                for (int c = 0; c < cSpan; c++) {
                    int rr = pos.row + r;
                    int cc = pos.col + c;
                    if (rr >= 0 && rr < rowsToFill && cc >= 0 && cc < maxCols) {
                        occupied[rr][cc] = true;
                    }
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
            if (NetworkTablesClient.isConnected() && !widgets.isEmpty()) {
                NetworkTableInstance instance = NetworkTableInstance.getDefault();
                if (instance == null) {
                    return;
                }

                long currentTime = System.currentTimeMillis();
                uiUpdatesReuse.clear();

                for (Map.Entry<String, DashboardWidget> entry : widgets.entrySet()) {
                    String key = entry.getKey();
                    DashboardWidget widget = entry.getValue();

                    long lastUpdate = lastUpdateTime.getOrDefault(key, 0L);
                    long timeSinceUpdate = currentTime - lastUpdate;

                    long updateInterval;
                    if (widget instanceof GraphWidget) {
                        updateInterval = 100;
                    } else if (widget instanceof FieldViewWidget) {
                        updateInterval = 100;
                    } else if (widget instanceof NumberWidget) {
                        updateInterval = 250;
                    } else {
                        updateInterval = 400;
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
                                            final Object finalValue = value;
                                            uiUpdatesReuse.add(() -> widget.updateValue(finalValue));
                                            lastUpdateTime.put(key, currentTime);
                                        }
                                    }
                                }
                            }
                        } else if (widget instanceof FieldViewWidget) {
                            NetworkTable fieldTable = table.getSubTable(entryKey);
                            final Map<String, Object> allFieldObjects = new HashMap<>();
                            for (String fieldKey : fieldTable.getKeys()) {
                                NetworkTableEntry fieldEntry = fieldTable.getEntry(fieldKey);
                                if (!fieldEntry.exists()) continue;
                                NetworkTableValue ntValue = fieldEntry.getValue();
                                if (ntValue == null) continue;
                                Object val = ntValue.getValue();
                                if (val == null) continue;
                                allFieldObjects.put(fieldKey, val);
                            }
                            final Object robotValue = allFieldObjects.get("Robot");
                            final FieldViewWidget fvWidget = (FieldViewWidget) widget;
                            uiUpdatesReuse.add(() -> {
                                if (robotValue != null) {
                                    fvWidget.updateValue(robotValue);
                                }
                                fvWidget.setFieldObjects(allFieldObjects);
                            });
                            if (robotValue != null) {
                                lastUpdateTime.put(key, currentTime);
                            }
                        } else {
                            NetworkTableEntry ntEntry = table.getEntry(entryKey);

                            if (ntEntry.exists()) {
                                NetworkTableValue ntValue = ntEntry.getValue();
                                if (ntValue != null) {
                                    Object value = ntValue.getValue();
                                    if (value != null) {
                                        final Object finalValue = value;
                                        uiUpdatesReuse.add(() -> widget.updateValue(finalValue));
                                        lastUpdateTime.put(key, currentTime);
                                    }
                                }
                            }
                        }
                    } catch (Exception e) {
                    }
                }
                
                if (!uiUpdatesReuse.isEmpty()) {
                    final List<Runnable> runNow = new ArrayList<>(uiUpdatesReuse);
                    Platform.runLater(() -> {
                        for (Runnable update : runNow) {
                            update.run();
                        }
                    });
                }
            }
        }, 0, 100, TimeUnit.MILLISECONDS);
    }

    public VBox getContainer() {
        return dashboardContainer;
    }

    public void shutdown() {
        saveConfiguration();
        if (simDriverStationInput != null) {
            simDriverStationInput.stop();
            simDriverStationInput = null;
        }
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

    private void setupSimDriverStationInput() {
        simDriverStationInput = new SimDriverStationInput();
        dashboardContainer.sceneProperty().addListener((obs, oldScene, newScene) -> {
            Platform.runLater(() -> {
                if (simDriverStationInput != null) {
                    simDriverStationInput.setScene(newScene);
                    if (newScene != null) {
                        simDriverStationInput.start();
                    }
                }
            });
        });
        if (dashboardContainer.getScene() != null) {
            simDriverStationInput.setScene(dashboardContainer.getScene());
            simDriverStationInput.start();
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
            File configFile = getConfigFile();
            mapper.writerWithDefaultPrettyPrinter().writeValue(configFile, config);
            System.out.println("Dashboard configuration saved to " + configFile.getAbsolutePath());
        } catch (IOException e) {
            System.err.println("Failed to save dashboard configuration: " + e.getMessage());
        }
    }

    private void loadConfiguration() {
        try {
            File configFile = getConfigFile();
            if (!configFile.exists()) {
                System.out.println("No dashboard configuration found, starting with empty dashboard");
                return;
            }

            ObjectMapper mapper = new ObjectMapper();
            DashboardConfig config = mapper.readValue(configFile, DashboardConfig.class);

            System.out.println(
                    "Loading dashboard configuration from " + configFile.getAbsolutePath() + " - " + config.widgets.size() + " widgets");

            loadConfigWidgetsImmediately(config.widgets);

            startLoadRetryExecutor();

            System.out.println("Dashboard configuration loaded successfully");
        } catch (IOException e) {
            System.err.println("Failed to load dashboard configuration: " + e.getMessage());
        }
    }
    
    private void loadConfigWidgetsImmediately(List<WidgetConfig> widgetsToLoad) {
        List<WidgetConfig> toRemove = new ArrayList<>();

        synchronized (widgetsToLoad) {
            if (widgetsToLoad.isEmpty()) {
                return;
            }

            for (WidgetConfig widgetConfig : widgetsToLoad) {
                if (widgets.containsKey(widgetConfig.key)) {
                    toRemove.add(widgetConfig);
                    continue;
                }

                try {
                    DashboardWidget widget = createWidgetFromType(
                            widgetConfig.type,
                            widgetConfig.key,
                            null);

                    if (widget != null) {
                        widgets.put(widgetConfig.key, widget);
                        widgetPositions.put(widgetConfig.key,
                                new GridPosition(widgetConfig.col, widgetConfig.row));

                        boolean isConnected = NetworkTablesClient.isConnected();
                        boolean entryExists = false;
                        
                        if (isConnected) {
                            NetworkTableInstance instance = NetworkTablesClient.getInstance();
                            if (instance != null) {
                                String firstSegment = widgetConfig.key.split("/")[0];
                                NetworkTable table = instance.getTable(firstSegment);
                                
                                if (table != null) {
                                    String entryKey = widgetConfig.key.substring(firstSegment.length() + 1);
                                    
                                    if ("autoselector".equals(widgetConfig.type)) {
                                        NetworkTable subTable = table.getSubTable(entryKey);
                                        if (subTable != null) {
                                            NetworkTableEntry activeEntry = subTable.getEntry("active");
                                            entryExists = activeEntry.exists();
                                        }
                                    } else if ("fieldview".equals(widgetConfig.type)) {
                                        NetworkTable subTable = table.getSubTable(entryKey);
                                        if (subTable != null) {
                                            NetworkTableEntry robotEntry = subTable.getEntry("Robot");
                                            entryExists = robotEntry.exists();
                                        }
                                    } else if ("button".equals(widgetConfig.type)) {
                                        NetworkTableEntry runningEntry = table.getEntry(entryKey);
                                        entryExists = runningEntry.exists();
                                        if (!entryExists) {
                                            NetworkTable subTable = table.getSubTable(entryKey);
                                            entryExists = subTable != null && subTable.getEntry("running").exists();
                                        }
                                    } else {
                                        NetworkTableEntry entry = table.getEntry(entryKey);
                                        if (entry.exists()) {
                                            entryExists = true;
                                        } else {
                                            NetworkTable subTable = table.getSubTable(entryKey);
                                            entryExists = (subTable != null);
                                        }
                                    }
                                }
                            }
                        }
                        
                        widget.setDisabled(!isConnected || !entryExists);

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
                        } else if (widget instanceof ButtonWidget) {
                            ((ButtonWidget) widget).setRemoveCallback(() -> removeWidget(widgetConfig.key));
                        } else if (widget instanceof BooleanWidget) {
                            ((BooleanWidget) widget).setRemoveCallback(() -> removeWidget(widgetConfig.key));
                        }

                        setupWidgetDragAndDrop(widget.getContainer(), widgetConfig.key);
                        toRemove.add(widgetConfig);
                        System.out.println(
                                "Loaded widget: " + widgetConfig.key + " (" + widgetConfig.type + ") - " + 
                                (isConnected && entryExists ? "enabled" : "disabled"));
                    }
                } catch (Exception e) {
                    System.err.println("Error loading widget " + widgetConfig.key + ": " + e.getMessage());
                }
            }

            widgetsToLoad.removeAll(toRemove);
        }

        if (!toRemove.isEmpty()) {
            Platform.runLater(() -> updateWidgetGrid());
        }
    }

    private void setupConnectionListener() {
        NetworkTablesClient.connectedProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue && !oldValue) {
                System.out.println("NetworkTables connected - enabling widgets and checking entries");
                updateWidgetStates();
                attemptLoadPendingWidgets();
            } else if (!newValue && oldValue) {
                System.out.println("NetworkTables disconnected - disabling widgets");
                updateWidgetStates();
            }
        });
    }
    
    private void updateWidgetStates() {
        boolean isConnected = NetworkTablesClient.isConnected();
        
        for (Map.Entry<String, DashboardWidget> entry : widgets.entrySet()) {
            String key = entry.getKey();
            DashboardWidget widget = entry.getValue();
            
            boolean entryExists = false;
            
            if (isConnected) {
                NetworkTableInstance instance = NetworkTablesClient.getInstance();
                if (instance != null) {
                    try {
                        String firstSegment = key.split("/")[0];
                        NetworkTable table = instance.getTable(firstSegment);
                        
                        if (table != null) {
                            String entryKey = key.substring(firstSegment.length() + 1);
                            
                            if (widget instanceof AutoSelectorWidget) {
                                NetworkTable subTable = table.getSubTable(entryKey);
                                if (subTable != null) {
                                    NetworkTableEntry activeEntry = subTable.getEntry("active");
                                    entryExists = activeEntry.exists();
                                }
                            } else if (widget instanceof FieldViewWidget) {
                                NetworkTable subTable = table.getSubTable(entryKey);
                                if (subTable != null) {
                                    NetworkTableEntry robotEntry = subTable.getEntry("Robot");
                                    entryExists = robotEntry.exists();
                                }
                            } else if (widget instanceof ButtonWidget) {
                                NetworkTableEntry runningEntry = table.getEntry(entryKey);
                                entryExists = runningEntry.exists();
                                if (!entryExists) {
                                    NetworkTable subTable = table.getSubTable(entryKey);
                                    if (subTable != null) {
                                        entryExists = subTable.getEntry("running").exists();
                                    }
                                }
                            } else {
                                NetworkTableEntry ntEntry = table.getEntry(entryKey);
                                if (ntEntry.exists()) {
                                    entryExists = true;
                                } else {
                                    NetworkTable subTable = table.getSubTable(entryKey);
                                    entryExists = (subTable != null);
                                }
                            }
                        }
                    } catch (Exception e) {
                    }
                }
            }
            
            widget.setDisabled(!isConnected || !entryExists);
        }
    }

    private void startLoadRetryExecutor() {
        if (loadRetryExecutor != null && !loadRetryExecutor.isShutdown()) {
            loadRetryExecutor.shutdown();
        }

        loadRetryExecutor = Executors.newSingleThreadScheduledExecutor();

        loadRetryExecutor.scheduleAtFixedRate(() -> {
            synchronized (pendingWidgetsToLoad) {
                if (pendingWidgetsToLoad.isEmpty()) {
                    updateWidgetStates();
                    return;
                }
            }

            if (NetworkTablesClient.isConnected()) {
                attemptLoadPendingWidgets();
            }
            updateWidgetStates();
        }, 1, 2, TimeUnit.SECONDS);
    }

    private void attemptLoadPendingWidgets() {
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
                    } else if ("button".equals(widgetConfig.type)) {
                        NetworkTableEntry runningEntry = table.getEntry(entryKey);
                        if (runningEntry.exists()) {
                            shouldLoad = true;
                        } else {
                            NetworkTable subTable = table.getSubTable(entryKey);
                            if (subTable != null && subTable.getEntry("running").exists()) {
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
                            widget.setResizeRequestCallback(() -> updateWidgetGrid());
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
                            } else if (widget instanceof ButtonWidget) {
                                ((ButtonWidget) widget).setRemoveCallback(() -> removeWidget(widgetConfig.key));
                            } else if (widget instanceof BooleanWidget) {
                                ((BooleanWidget) widget).setRemoveCallback(() -> removeWidget(widgetConfig.key));
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
        } else if (widget instanceof ButtonWidget) {
            return "button";
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
            isDragging = true;
            showGridSkeleton();
        });

        target.setOnDragExited(event -> {
            isDragging = false;
            clearGridBackgroundGlow();
            hideGridSkeleton();
            updateGridConstraints();
        });

        target.setOnDragOver(event -> {
            if (event.getDragboard().hasString()) {
                event.acceptTransferModes(TransferMode.MOVE, TransferMode.COPY);
                
                double dropX = event.getX();
                double dropY = event.getY();
                GridPosition targetPosition = calculateGridPosition(dropX, dropY);
                updateGridBackgroundGlow(targetPosition.col, targetPosition.row);
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
                            int spanCols = w.getColSpan();
                            int spanRows = w.getRowSpan();

                            int clampedCol = Math.max(0, Math.min(targetPosition.col, 5 - spanCols));
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
                            String finalKey = widget.getKey();
                            if (widget instanceof NumberWidget) {
                                ((NumberWidget) widget).setRemoveCallback(() -> removeWidget(finalKey));
                            } else if (widget instanceof GraphWidget) {
                                ((GraphWidget) widget).setRemoveCallback(() -> removeWidget(finalKey));
                            } else if (widget instanceof FieldViewWidget) {
                                ((FieldViewWidget) widget).setRemoveCallback(() -> removeWidget(finalKey));
                            } else if (widget instanceof AutoSelectorWidget) {
                                ((AutoSelectorWidget) widget).setRemoveCallback(() -> removeWidget(finalKey));
                            } else if (widget instanceof ButtonWidget) {
                                ((ButtonWidget) widget).setRemoveCallback(() -> removeWidget(finalKey));
                            } else if (widget instanceof BooleanWidget) {
                                ((BooleanWidget) widget).setRemoveCallback(() -> removeWidget(finalKey));
                            }
                            widget.setResizeRequestCallback(() -> updateWidgetGrid());
                            widgetPositions.put(widget.getKey(), targetPosition);
                            addWidgetToGrid(widget);
                            saveConfiguration();
                            success = true;
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
            clearGridBackgroundGlow();
            hideGridSkeleton();
            isDragging = false;
            Platform.runLater(() -> {
                updateGridConstraints();
            });
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
        } else if (key.endsWith("/running")) {
            return "button";
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
                        
                        NetworkTableEntry typeEntry = subTable.getEntry(".type");
                        if (typeEntry.exists() && "Command".equals(typeEntry.getString(""))) {
                            return "button";
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
            case "button":
                String btnKey = key.endsWith("/running") ? key.substring(0, key.length() - 8) : key;
                String btnTitle = btnKey.contains("/") ? btnKey.substring(btnKey.lastIndexOf('/') + 1) : btnKey;
                return new ButtonWidget(btnTitle, btnKey);
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
            int clampedCol = Math.max(0, Math.min(base.col, 5 - 2));
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
        } else if (widget instanceof ButtonWidget) {
            ((ButtonWidget) widget).setRemoveCallback(() -> removeWidget(key));
        } else if (widget instanceof BooleanWidget) {
            ((BooleanWidget) widget).setRemoveCallback(() -> removeWidget(key));
        }
        updateWidgetGrid();
        saveConfiguration();
    }

    private GridPosition findNextAvailablePosition() {
        int maxCols = 5;
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
        updateGridConstraints();
        saveConfiguration();
    }

    private void showGridSkeleton() {
        if (skeletonGrid == null) return;
        
        hideGridSkeleton();

        int maxCols = 5;
        int maxRows = 6;
        gridSkeleton = new Region[maxCols * maxRows];
        for (int i = 0; i < maxCols * maxRows; i++) {
            Region cell = new Region();
            cell.setStyle(
                    "-fx-background-color: rgba(255, 140, 0, 0.08); -fx-border-color: rgba(255, 140, 0, 0.3); -fx-border-width: 1.5px; -fx-background-radius: 8px; -fx-border-radius: 8px; -fx-effect: dropshadow(gaussian, rgba(255, 140, 0, 0.2), 4, 0, 0, 0);");
            cell.setMouseTransparent(true);
            GridPane.setHgrow(cell, Priority.ALWAYS);
            GridPane.setVgrow(cell, Priority.SOMETIMES);
            gridSkeleton[i] = cell;

            int col = i % maxCols;
            int row = i / maxCols;
            skeletonGrid.add(cell, col, row);
        }
        skeletonGrid.setVisible(true);
    }

    private void hideGridSkeleton() {
        if (skeletonGrid != null) {
            skeletonGrid.getChildren().clear();
            skeletonGrid.setVisible(false);
        }
        if (gridSkeleton != null) {
            gridSkeleton = null;
        }
    }

    private GridPosition calculateGridPosition(double x, double y) {
        int maxCols = 5;

        double leftPadding = currentPadding;
        double topPadding = currentPadding;
        double hgap = currentHgap;
        double vgap = currentVgap;

        double cellWidthWithGap = currentCellWidth + hgap;
        double cellHeightWithGap = currentCellHeight + vgap;

        int col = (int) ((x - leftPadding) / cellWidthWithGap);
        int row = (int) ((y - topPadding) / cellHeightWithGap);

        col = Math.max(0, Math.min(col, maxCols - 1));
        row = Math.max(0, row);

        return new GridPosition(col, row);
    }
}
