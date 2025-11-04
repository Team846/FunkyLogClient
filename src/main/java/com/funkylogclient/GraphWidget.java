package com.funkylogclient;

import javafx.animation.AnimationTimer;
import javafx.scene.Node;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TextInputDialog;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.layout.StackPane;
import javafx.scene.text.Font;
import javafx.scene.text.TextAlignment;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

class DataPoint {
    double time;
    double value;

    DataPoint(double time, double value) {
        this.time = time;
        this.value = value;
    }
}

public class GraphWidget extends DashboardWidget {
    private Canvas canvas;
    private StackPane chartPane;
    private GraphicsContext gc;
    private List<DataPoint> data;
    private static final int MAX_DATA_POINTS = 700;
    private static final double DEFAULT_TIMEFRAME = 7.0;
    private long startTime = System.currentTimeMillis();
    private ContextMenu contextMenu;
    private Runnable removeCallback;
    private boolean autoScale = true;
    private double timeFrame = DEFAULT_TIMEFRAME;
    private double yMin = Double.NaN;
    private double yMax = Double.NaN;
    private DataPoint lastPoint = null;
    private DataPoint secondLastPoint = null;
    private long lastUpdateTime = 0;
    private AnimationTimer animationTimer;

    public GraphWidget(String title, String key) {
        super(title, key);
        createGraph();
        setupContextMenu();
    }

    private void createGraph() {
        contentBox.setStyle("-fx-background-color: transparent; -fx-background-radius: 0 0 8 8;");
        contentBox.setPadding(new javafx.geometry.Insets(0));

        canvas = new Canvas();
        canvas.setWidth(280);
        canvas.setHeight(190);
        gc = canvas.getGraphicsContext2D();
        data = new ArrayList<>();

        chartPane = new StackPane();
        chartPane.setMinHeight(190);
        chartPane.setMinWidth(200);
        chartPane.getChildren().add(canvas);
        canvas.widthProperty().bind(chartPane.widthProperty());
        canvas.heightProperty().bind(chartPane.heightProperty());
        canvas.widthProperty().addListener(e -> redraw());
        canvas.heightProperty().addListener(e -> redraw());

        contentBox.getChildren().add(chartPane);
        VBox.setVgrow(chartPane, javafx.scene.layout.Priority.ALWAYS);

        animationTimer = new AnimationTimer() {
            @Override
            public void handle(long now) {
                redraw();
            }
        };
        animationTimer.start();

        redraw();
    }

    private void redraw() {
        double width = canvas.getWidth();
        double height = canvas.getHeight();

        gc.clearRect(0, 0, width, height);
        gc.setFill(Color.web("#21262D"));
        gc.fillRect(0, 0, width, height);

        double currentTime = (System.currentTimeMillis() - startTime) / 1000.0;
        double oldestTime = Math.max(0, currentTime - timeFrame);

        List<DataPoint> visibleData = new ArrayList<>();
        for (DataPoint point : data) {
            if (point.time >= oldestTime && point.time <= currentTime) {
                visibleData.add(point);
            }
        }

        double minY = visibleData.isEmpty() ? 0 : visibleData.stream().mapToDouble(d -> d.value).min().orElse(0);
        double maxY = visibleData.isEmpty() ? 1 : visibleData.stream().mapToDouble(d -> d.value).max().orElse(0);

        if (autoScale) {
            double range = maxY - minY;
            double padding = range * 0.02;
            if (range == 0) {
                padding = 1.0;
            }
            yMin = minY - padding;
            yMax = maxY + padding;
        }

        double leftPadding = 41;
        double rightPadding = 14;
        double fontSize = 10.0;
        double labelHeight = fontSize * 1.5;
        double bottomPadding = labelHeight + 4;
        double topPadding = labelHeight / 2 + 4;

        double chartWidth = width - leftPadding - rightPadding;
        double chartHeight = height - bottomPadding - topPadding;

        gc.setStroke(Color.web("#30363D"));
        gc.setLineWidth(1);

        for (int i = 0; i <= 4; i++) {
            double y = topPadding + (chartHeight / 4) * i;
            gc.strokeLine(leftPadding, y, width - rightPadding, y);
        }

        for (int i = 0; i <= 10; i++) {
            double x = leftPadding + (chartWidth / 10) * i;
            gc.strokeLine(x, topPadding, x, height - bottomPadding);
        }

        gc.setFill(Color.web("#C9D1D9"));
        gc.setFont(new Font(10));

        for (int i = 0; i <= 4; i++) {
            double y = topPadding + (chartHeight / 4) * i;
            double value = yMax - (i * (yMax - yMin) / 4);
            gc.setTextAlign(TextAlignment.RIGHT);
            gc.fillText(String.format("%.1f", value), leftPadding - 5, y + 4);
        }

        for (int i = 0; i <= 5; i++) {
            double x = leftPadding + (chartWidth / 5) * i;
            double time = oldestTime + (i * timeFrame / 5);
            gc.setTextAlign(TextAlignment.CENTER);
            gc.fillText(String.format("%.1f", time), x, height - 5);
        }

        if (!visibleData.isEmpty()) {
            gc.setStroke(Color.web("#FF8C00"));
            gc.setLineWidth(2);
            for (int i = 0; i < visibleData.size() - 1; i++) {
                DataPoint p1 = visibleData.get(i);
                DataPoint p2 = visibleData.get(i + 1);
                double x1 = leftPadding + ((p1.time - oldestTime) / timeFrame) * chartWidth;
                double y1 = topPadding + chartHeight - ((p1.value - yMin) / (yMax - yMin)) * chartHeight;
                double x2 = leftPadding + ((p2.time - oldestTime) / timeFrame) * chartWidth;
                double y2 = topPadding + chartHeight - ((p2.value - yMin) / (yMax - yMin)) * chartHeight;
                gc.strokeLine(x1, y1, x2, y2);
            }

            if (lastPoint != null && lastPoint.time > currentTime - timeFrame) {
                long currentMillis = System.currentTimeMillis();
                long timeSinceUpdate = currentMillis - lastUpdateTime;

                double extrapolatedValue = lastPoint.value;
                if (secondLastPoint != null && timeSinceUpdate > 0) {
                    double dtLast = (lastPoint.time - secondLastPoint.time);
                    if (dtLast > 0) {
                        double dv = (lastPoint.value - secondLastPoint.value) / dtLast;
                        double extrapolationTime = Math.min(timeSinceUpdate / 1000.0, 0.5);
                        extrapolatedValue = lastPoint.value + dv * extrapolationTime;
                    }
                }

                double lastX = leftPadding + ((lastPoint.time - oldestTime) / timeFrame) * chartWidth;
                double lastY = topPadding + chartHeight - ((lastPoint.value - yMin) / (yMax - yMin)) * chartHeight;
                double extrapolatedX = leftPadding + ((currentTime - oldestTime) / timeFrame) * chartWidth;
                double extrapolatedY = topPadding + chartHeight
                        - ((extrapolatedValue - yMin) / (yMax - yMin)) * chartHeight;

                if (extrapolatedX > lastX && extrapolatedX <= width - rightPadding) {
                    gc.setStroke(Color.web("#FF8C00"));
                    gc.setLineWidth(2);
                    gc.setLineDashes(5, 5);
                    gc.strokeLine(lastX, lastY, extrapolatedX, extrapolatedY);
                    gc.setLineDashes(null);
                }
            }
        }
    }

    private void setupContextMenu() {
        contextMenu = new ContextMenu();

        MenuItem convertToNormal = new MenuItem("Show as Number");
        convertToNormal.setOnAction(e -> System.out.println("Convert to number requested for: " + key));

        MenuItem removeItem = new MenuItem("Remove Widget");
        removeItem.setOnAction(e -> {
            if (removeCallback != null)
                removeCallback.run();
        });

        MenuItem setYAxisRange = new MenuItem("Set Y-Axis Range");
        setYAxisRange.setOnAction(e -> showYAxisRangeDialog());

        MenuItem resetYAxis = new MenuItem("Auto-scale Y-Axis");
        resetYAxis.setOnAction(e -> {
            autoScale = true;
            yMin = Double.NaN;
            yMax = Double.NaN;
            redraw();
        });

        MenuItem setTimeFrame = new MenuItem("Set Timeframe");
        setTimeFrame.setOnAction(e -> showTimeFrameDialog());

        contextMenu.getItems().addAll(convertToNormal, setYAxisRange, resetYAxis, setTimeFrame, removeItem);

        container.setOnContextMenuRequested(event -> {
            contextMenu.show(container, event.getScreenX(), event.getScreenY());
        });
    }

    private void showYAxisRangeDialog() {
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("Set Y-Axis Range");
        dialog.setHeaderText("Enter min and max values (e.g., -100 to 100)");
        dialog.setContentText("Min:");

        Optional<String> minResult = dialog.showAndWait();
        if (minResult.isPresent()) {
            try {
                double min = Double.parseDouble(minResult.get());

                dialog.getEditor().clear();
                dialog.setContentText("Max:");
                Optional<String> maxResult = dialog.showAndWait();

                if (maxResult.isPresent()) {
                    double max = Double.parseDouble(maxResult.get());
                    yMin = min;
                    yMax = max;
                    autoScale = false;
                    redraw();
                }
            } catch (NumberFormatException e) {
                System.err.println("Invalid number format");
            }
        }
    }

    private void showTimeFrameDialog() {
        TextInputDialog dialog = new TextInputDialog(String.valueOf(timeFrame));
        dialog.setTitle("Set Timeframe");
        dialog.setHeaderText("Enter the timeframe in seconds");
        dialog.setContentText("Timeframe:");

        Optional<String> result = dialog.showAndWait();
        if (result.isPresent()) {
            try {
                timeFrame = Double.parseDouble(result.get());
                if (timeFrame > 0) {
                    long currentTime = System.currentTimeMillis();
                    double oldestTime = (currentTime - startTime) / 1000.0 - timeFrame;
                    data.removeIf(p -> p.time < oldestTime);
                    redraw();
                }
            } catch (NumberFormatException e) {
                System.err.println("Invalid number format");
            }
        }
    }

    public void setContextMenuCallback(Runnable callback) {
        if (contextMenu != null && !contextMenu.getItems().isEmpty()) {
            MenuItem convertItem = contextMenu.getItems().get(0);
            convertItem.setOnAction(e -> callback.run());
        }
    }

    public void setRemoveCallback(Runnable callback) {
        this.removeCallback = callback;
    }

    @Override
    public void updateValue(Object value) {
        if (value instanceof Number) {
            double numValue = ((Number) value).doubleValue();
            long currentMillis = System.currentTimeMillis();
            double currentTime = (currentMillis - startTime) / 1000.0;

            if (data.size() >= MAX_DATA_POINTS) {
                data.remove(0);
            }

            double oldestTime = Math.max(0, currentTime - timeFrame);
            data.removeIf(p -> p.time < oldestTime);

            DataPoint newPoint = new DataPoint(currentTime, numValue);
            data.add(newPoint);

            secondLastPoint = lastPoint;
            lastPoint = newPoint;
            lastUpdateTime = currentMillis;
        }
    }

    @Override
    public Node getContent() {
        return container;
    }
}
