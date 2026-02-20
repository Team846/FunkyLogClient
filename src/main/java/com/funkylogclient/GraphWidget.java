package com.funkylogclient;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.scene.Node;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TextInputDialog;
import javafx.scene.layout.VBox;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;
import javafx.scene.layout.StackPane;
import javafx.scene.text.Font;
import javafx.scene.text.TextAlignment;
import javafx.util.Duration;

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
    private static final int MAX_DATA_POINTS = 500;
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
    private Timeline timeline;
    private volatile boolean needsRedraw = true;
    private double cachedMinY = 0;
    private double cachedMaxY = 1;

    public GraphWidget(String title, String key) {
        super(title, key);
        this.title = title;
        this.key = key;
        createGraph();
        setupContextMenu();
    }

    @Override
    public int getColSpan() { return 2; }

    @Override
    public int getRowSpan() { return 2; }

    private void createGraph() {
        contentBox.setStyle("-fx-background-color: transparent; -fx-background-radius: 0 0 8 8;");
        contentBox.setPadding(new javafx.geometry.Insets(8));

        canvas = new Canvas();
        gc = canvas.getGraphicsContext2D();
        gc.setImageSmoothing(true);
        data = new ArrayList<>(MAX_DATA_POINTS);

        chartPane = new StackPane();
        chartPane.setMinSize(0, 0);
        chartPane.setPrefSize(Region.USE_COMPUTED_SIZE, Region.USE_COMPUTED_SIZE);
        chartPane.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        chartPane.getChildren().add(canvas);
        
        chartPane.widthProperty().addListener((obs, oldVal, newVal) -> {
            double val = newVal.doubleValue();
            if (val > 0 && Math.abs(val - canvas.getWidth()) > 0.5) {
                canvas.setWidth(val);
                needsRedraw = true;
            }
        });
        chartPane.heightProperty().addListener((obs, oldVal, newVal) -> {
            double val = newVal.doubleValue();
            if (val > 0 && Math.abs(val - canvas.getHeight()) > 0.5) {
                canvas.setHeight(val);
                needsRedraw = true;
            }
        });

        contentBox.getChildren().add(chartPane);
        VBox.setVgrow(chartPane, javafx.scene.layout.Priority.ALWAYS);

        timeline = new Timeline(new KeyFrame(Duration.millis(33), e -> {
            if (needsRedraw || data.size() > 0) {
                redraw();
                needsRedraw = false;
            }
        }));
        timeline.setCycleCount(Timeline.INDEFINITE);
        timeline.play();

        redraw();
    }

    public void stop() {
        if (timeline != null) {
            timeline.stop();
        }
    }

    private void redraw() {
        double width = canvas.getWidth();
        double height = canvas.getHeight();
        
        if (width <= 0 || height <= 0) {
            return;
        }

        gc.clearRect(0, 0, width, height);
        gc.setFill(Color.web(Styles.BG_DARK));
        gc.fillRect(0, 0, width, height);

        double currentTime = (System.currentTimeMillis() - startTime) / 1000.0;
        double oldestTime = Math.max(0, currentTime - timeFrame);

        int visibleCount = 0;
        double minY = Double.MAX_VALUE;
        double maxY = Double.MIN_VALUE;

        for (int i = 0; i < data.size(); i++) {
            DataPoint point = data.get(i);
            if (point.time >= oldestTime && point.time <= currentTime) {
                visibleCount++;
                if (point.value < minY) minY = point.value;
                if (point.value > maxY) maxY = point.value;
            }
        }

        if (visibleCount == 0) {
            minY = cachedMinY;
            maxY = cachedMaxY;
        } else {
            cachedMinY = minY;
            cachedMaxY = maxY;
        }

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

        gc.setStroke(Color.web(Styles.BORDER_DARK));
        gc.setLineWidth(1);

        for (int i = 0; i <= 4; i++) {
            double y = topPadding + (chartHeight / 4) * i;
            gc.strokeLine(leftPadding, y, width - rightPadding, y);
        }

        for (int i = 0; i <= 10; i++) {
            double x = leftPadding + (chartWidth / 10) * i;
            gc.strokeLine(x, topPadding, x, height - bottomPadding);
        }

        gc.setFill(Color.web(Styles.TEXT_PRIMARY));
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

        if (visibleCount > 0) {
            gc.setStroke(Color.web("#FF8C00"));
            gc.setLineWidth(2.0);
            gc.setLineCap(javafx.scene.shape.StrokeLineCap.ROUND);
            gc.setLineJoin(javafx.scene.shape.StrokeLineJoin.ROUND);
            
            DataPoint prevPoint = null;
            for (int i = 0; i < data.size(); i++) {
                DataPoint point = data.get(i);
                if (point.time >= oldestTime && point.time <= currentTime) {
                    if (prevPoint != null) {
                        double x1 = leftPadding + ((prevPoint.time - oldestTime) / timeFrame) * chartWidth;
                        double y1 = topPadding + chartHeight - ((prevPoint.value - yMin) / (yMax - yMin)) * chartHeight;
                        double x2 = leftPadding + ((point.time - oldestTime) / timeFrame) * chartWidth;
                        double y2 = topPadding + chartHeight - ((point.value - yMin) / (yMax - yMin)) * chartHeight;
                        
                        if (x1 >= leftPadding && x1 <= width - rightPadding && 
                            x2 >= leftPadding && x2 <= width - rightPadding) {
                            gc.strokeLine(x1, y1, x2, y2);
                        }
                    }
                    prevPoint = point;
                }
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
            stop();
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
            needsRedraw = true;
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
                    needsRedraw = true;
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
                    needsRedraw = true;
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
            needsRedraw = true;
        }
    }

    @Override
    public Node getContent() {
        return container;
    }
}
