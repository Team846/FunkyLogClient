package com.funkylogclient;

import edu.wpi.first.networktables.NetworkTable;
import edu.wpi.first.networktables.NetworkTableEntry;
import edu.wpi.first.networktables.NetworkTableInstance;
import edu.wpi.first.networktables.NetworkTableValue;
import edu.wpi.first.networktables.NetworkTableType;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.layout.*;
import javafx.scene.layout.Priority;
import javafx.scene.text.Text;

import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;
import javafx.scene.input.ClipboardContent;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class Dashboard {
    private VBox dashboardContainer;
    private GridPane widgetGrid;
    private Map<String, DashboardWidget> widgets;
    private Map<String, GridPosition> widgetPositions = new HashMap<>(); // Track widget positions
    private Map<String, Long> lastUpdateTime = new HashMap<>();
    private ScheduledExecutorService updateExecutor;
    private Region[] gridSkeleton = null;

    // Helper class to track grid positions
    private static class GridPosition {
        int col;
        int row;

        GridPosition(int col, int row) {
            this.col = col;
            this.row = row;
        }
    }

    public Dashboard() {
        widgets = new HashMap<>();
        createDashboard();
        startValueUpdateLoop();
    }

    private void createDashboard() {
        dashboardContainer = new VBox(10);
        dashboardContainer.setPadding(new Insets(15, 20, 20, 20));
        dashboardContainer.setStyle("-fx-background-color: #1A1A1A;");

        Text title = new Text("Dashboard");
        title.setStyle(
                "-fx-font-size: 24px; -fx-fill: #C9D1D9; -fx-font-weight: bold; -fx-font-family: 'Segoe UI', 'Roboto', sans-serif;");

        widgetGrid = new GridPane();
        widgetGrid.setHgap(15);
        widgetGrid.setVgap(15);
        widgetGrid.setPadding(new Insets(15));
        widgetGrid.setStyle("-fx-background-color: #1A1A1A;");
        widgetGrid.setMinSize(Region.USE_COMPUTED_SIZE, Region.USE_COMPUTED_SIZE);
        widgetGrid.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);

        // Set fixed column widths
        for (int i = 0; i < 4; i++) {
            ColumnConstraints colConstraints = new ColumnConstraints();
            colConstraints.setMinWidth(200);
            colConstraints.setPrefWidth(250);
            colConstraints.setHgrow(Priority.ALWAYS);
            widgetGrid.getColumnConstraints().add(colConstraints);
        }

        RowConstraints defaultRowConstraints = new RowConstraints();
        defaultRowConstraints.setMinHeight(120);
        defaultRowConstraints.setPrefHeight(150);
        defaultRowConstraints.setMaxHeight(180);
        defaultRowConstraints.setVgrow(Priority.NEVER);
        for (int i = 0; i < 5; i++) {
            widgetGrid.getRowConstraints().add(defaultRowConstraints);
        }

        VBox.setVgrow(widgetGrid, Priority.ALWAYS);
        setupDragAndDrop(widgetGrid);

        dashboardContainer.getChildren().addAll(title, widgetGrid);

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

            // Add widgets at their stored positions
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
                    } else {
                        widgetGrid.add(widget.getContainer(), pos.col, pos.row);
                    }
                }
            }

            // Ensure enough row constraints exist
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

                        NetworkTableEntry ntEntry = table.getEntry(key.substring(tableName.length() + 1));

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
        if (updateExecutor != null && !updateExecutor.isShutdown()) {
            updateExecutor.shutdown();
        }
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

                // Calculate grid position from drop coordinates
                double dropX = event.getX();
                double dropY = event.getY();
                GridPosition targetPosition = calculateGridPosition(dropX, dropY);
                System.out
                        .println("Calculated grid position: col=" + targetPosition.col + ", row=" + targetPosition.row);

                try {
                    // Check if this is a widget move operation
                    if (data.startsWith("MOVE:")) {
                        String widgetKey = data.substring(5); // Remove "MOVE:" prefix
                        System.out.println("Moving widget: " + widgetKey);

                        if (widgets.containsKey(widgetKey)) {
                            // Update the position of the existing widget
                            widgetPositions.put(widgetKey, targetPosition);
                            updateWidgetGrid();
                            success = true;
                        }
                    } else {
                        // This is a new widget from the sidebar
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
                            // Set the target position for the new widget
                            widgetPositions.put(widget.getKey(), targetPosition);
                            addWidgetToGrid(widget);
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
        }
    }

    private void addWidgetToGrid(DashboardWidget widget) {
        String key = widget.getKey();
        System.out.println("AWG: Adding widget to grid: " + key);

        // Only add to widgets map if not already there
        if (!widgets.containsKey(key)) {
            widgets.put(key, widget);
        }

        // Set initial position if not already set
        if (!widgetPositions.containsKey(key)) {
            widgetPositions.put(key, findNextAvailablePosition());
        }

        setupWidgetDragAndDrop(widget.getContainer(), key);
        if (widget instanceof NumberWidget) {
            ((NumberWidget) widget).setRemoveCallback(() -> removeWidget(key));
        } else if (widget instanceof GraphWidget) {
            ((GraphWidget) widget).setRemoveCallback(() -> removeWidget(key));
        }
        updateWidgetGrid();
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

    // removed: widget-level context menu injection handled inside widgets

    private void removeWidget(String key) {
        widgets.remove(key);
        widgetPositions.remove(key);
        lastUpdateTime.remove(key);
        updateWidgetGrid();
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

        // Account for padding and gaps (matching widgetGrid settings)
        double leftPadding = 15;
        double topPadding = 15;
        double hgap = 15;
        double vgap = 15;

        // Cell size based on column constraints (pref width 250 + gap 15)
        double cellWidth = 250 + hgap;
        double cellHeight = 150 + vgap; // Prefer height 150 + gap

        // Calculate position in grid
        int col = (int) ((x - leftPadding) / cellWidth);
        int row = (int) ((y - topPadding) / cellHeight);

        // Clamp to valid range
        col = Math.max(0, Math.min(col, maxCols - 1));
        row = Math.max(0, row);

        return new GridPosition(col, row);
    }
}
