package com.funkylogclient;

import javafx.animation.AnimationTimer;
import javafx.scene.CacheHint;
import javafx.scene.Node;
import javafx.scene.canvas.Canvas;
import javafx.scene.shape.Rectangle;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.image.Image;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.TextAlignment;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import edu.wpi.first.networktables.NetworkTableEntry;
import edu.wpi.first.networktables.NetworkTableInstance;
import edu.wpi.first.networktables.NetworkTable;

class Pose2D {
    double x;
    double y;
    double rotation;
    long timestamp;

    Pose2D(double x, double y, double rotation) {
        this.x = x;
        this.y = y;
        this.rotation = rotation;
        this.timestamp = System.currentTimeMillis();
    }
}

public class FieldViewWidget extends DashboardWidget {
    private Canvas backgroundCanvas;
    private Canvas foregroundCanvas;
    private StackPane canvasPane;
    private GraphicsContext gcBackground;
    private GraphicsContext gcForeground;
    private List<Pose2D> trajectory;
    private static final int MAX_POINTS = 1000;
    private static final double FIELD_LENGTH = 17.548;
    private static final double FIELD_WIDTH = 8.052;
    private static final double POSITION_OFFSET_X_M = 0.85 / 2.0;
    private static final double POSITION_OFFSET_Y_M = 0.85 / 2.0;
    private static final long TRAIL_DURATION_MS = 3000;
    private Image fieldImage;
    private double robotX = 0;
    private double robotY = 0;
    private double robotRotation = 0;
    private double displayX = 0;
    private double displayY = 0;
    private double displayRotation = 0;
    private Pose2D lastPose = null;
    private Pose2D secondLastPose = null;
    private long lastUpdateTime = 0;
    private AnimationTimer animationTimer;
    private boolean showTrajectory = false;
    private Runnable removeCallback;
    private Map<String, double[]> fieldObjects = new HashMap<>();
    private static final String FIELD_OBJECTS_COLOR = "#00BFFF";
    private static final long MIN_FRAME_INTERVAL_NANOS = 33_000_000;
    private long lastDrawNanos = 0;
    private static final double EDGE_PADDING = 10;
    private int fieldRotationIndex = 0;

    public FieldViewWidget(String title, String key) {
        super(title, key);
        createFieldView();
        setupContextMenu();
    }

    @Override
    public int getColSpan() {
        return (fieldRotationIndex % 2 != 0) ? 2 : 3;
    }

    @Override
    public int getRowSpan() {
        return (fieldRotationIndex % 2 != 0) ? 4 : 3;
    }
    private void collectMouseCoords()
    {
        canvasPane.setOnMouseDragged(event -> {
            double xRatio = canvasPane.getWidth()/fieldImage.getWidth();
            double yRatio = canvasPane.getHeight()/fieldImage.getHeight();
            double x_dist = xRatio * 651.22 * event.getX() / canvasPane.getWidth(); // x dist in inches 
            double y_dist = yRatio * 317.69 * event.getY() / canvasPane.getHeight(); // y dist in inches

            writeCoordinateBack(new double[]{x_dist, y_dist});
        });
    }

    private void createFieldView() {
        contentBox.setStyle("-fx-background-color: #0D0D0D; -fx-background-radius: 0 0 8 8;");
        contentBox.setPadding(new javafx.geometry.Insets(EDGE_PADDING));

        try {
            java.io.InputStream stream = getClass().getResourceAsStream("field26.png");
            if (stream != null) {
                fieldImage = new Image(stream);
            } else {
                System.err.println("Field image stream is null");
                fieldImage = null;
            }
        } catch (Exception e) {
            System.err.println("Failed to load field image: " + e.getMessage());
            fieldImage = null;
        }

        backgroundCanvas = new Canvas();
        foregroundCanvas = new Canvas();
        backgroundCanvas.setCache(true);
        backgroundCanvas.setCacheHint(CacheHint.SPEED);
        gcBackground = backgroundCanvas.getGraphicsContext2D();
        gcForeground = foregroundCanvas.getGraphicsContext2D();
        trajectory = new ArrayList<>();

        canvasPane = new StackPane();
        canvasPane.setMinSize(200, 235);
        canvasPane.setPrefSize(0, 0);
        canvasPane.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        Rectangle paneClip = new Rectangle();
        paneClip.widthProperty().bind(canvasPane.widthProperty());
        paneClip.heightProperty().bind(canvasPane.heightProperty());
        canvasPane.setClip(paneClip);
        canvasPane.getChildren().addAll(backgroundCanvas, foregroundCanvas);
        javafx.beans.value.ChangeListener<Number> resizeListener = (obs, oldVal, newVal) -> {
            double paneW = canvasPane.getWidth();
            double paneH = canvasPane.getHeight();
            if (paneW <= 0 || paneH <= 0) return;
            double aspect = getFieldImageAspectRatio();
            double cw;
            double ch;
            if (paneW / paneH > aspect) {
                ch = paneH;
                cw = paneH * aspect;
            } else {
                cw = paneW;
                ch = paneW / aspect;
            }
            backgroundCanvas.setWidth(cw);
            backgroundCanvas.setHeight(ch);
            foregroundCanvas.setWidth(cw);
            foregroundCanvas.setHeight(ch);
            redrawBackground();
            redrawDynamicContent();
        };
        canvasPane.widthProperty().addListener(resizeListener);
        canvasPane.heightProperty().addListener(resizeListener);
        backgroundCanvas.widthProperty().addListener(e -> redrawBackground());
        backgroundCanvas.heightProperty().addListener(e -> redrawBackground());

        contentBox.getChildren().add(canvasPane);
        VBox.setVgrow(canvasPane, javafx.scene.layout.Priority.ALWAYS);

        animationTimer = new AnimationTimer() {
            @Override
            public void handle(long now) {
                if (now - lastDrawNanos < MIN_FRAME_INTERVAL_NANOS) {
                    return;
                }
                lastDrawNanos = now;

                long currentTime = System.currentTimeMillis();
                long timeSinceUpdate = currentTime - lastUpdateTime;

                if (lastPose != null && secondLastPose != null && timeSinceUpdate > 0) {
                    double dtLast = (lastPose.timestamp - secondLastPose.timestamp) / 1000.0;
                    if (dtLast > 0) {
                        double dx = (lastPose.x - secondLastPose.x) / dtLast;
                        double dy = (lastPose.y - secondLastPose.y) / dtLast;

                        double angleDiff = lastPose.rotation - secondLastPose.rotation;
                        while (angleDiff > Math.PI)
                            angleDiff -= 2 * Math.PI;
                        while (angleDiff < -Math.PI)
                            angleDiff += 2 * Math.PI;
                        double dtheta = angleDiff / dtLast;

                        double extrapolationTime = 0.0; //Math.min(timeSinceUpdate / 1000.0 * 0.25, 0.12);
                        displayX = lastPose.x + dx * extrapolationTime;
                        displayY = lastPose.y + dy * extrapolationTime;
                        displayRotation = lastPose.rotation + dtheta * extrapolationTime;
                        
                        while (displayRotation > Math.PI)
                            displayRotation -= 2 * Math.PI;
                        while (displayRotation < -Math.PI)
                            displayRotation += 2 * Math.PI;
                    } else {
                        displayX = lastPose.x;
                        displayY = lastPose.y;
                        displayRotation = lastPose.rotation;
                    }
                } else if (lastPose != null) {
                    displayX = lastPose.x;
                    displayY = lastPose.y;
                    displayRotation = lastPose.rotation;
                }

                redrawDynamicContent();
            }
        };
        animationTimer.start();

        redrawBackground();
        redrawDynamicContent();
    }

    private double getFieldImageAspectRatio() {
        boolean rotated = (fieldRotationIndex % 2 != 0);
        double sourceAspect = FIELD_LENGTH / FIELD_WIDTH;
        if (fieldImage != null && fieldImage.getWidth() > 0 && fieldImage.getHeight() > 0) {
            sourceAspect = fieldImage.getWidth() / fieldImage.getHeight();
        }
        return rotated ? (1.0 / sourceAspect) : sourceAspect;
    }

    private static double[] layout(double width, double height) {
        return new double[] { width, height, 0, 0 };
    }

    private void redrawBackground() {
        double cw = backgroundCanvas.getWidth();
        double ch = backgroundCanvas.getHeight();
        if (cw <= 0 || ch <= 0) return;

        gcBackground.clearRect(0, 0, cw, ch);
        gcBackground.save();

        double fieldW = cw;
        double fieldH = ch;
        if (fieldRotationIndex % 2 != 0) {
            fieldW = ch;
            fieldH = cw;
        }

        gcBackground.translate(cw / 2, ch / 2);
        gcBackground.rotate(fieldRotationIndex * 90);
        gcBackground.translate(-fieldW / 2, -fieldH / 2);

        double displayWidth = fieldW, displayHeight = fieldH, offsetX = 0, offsetY = 0;

        if (fieldImage != null) {
            double srcW = fieldImage.getWidth();
            double srcH = fieldImage.getHeight();
            if (srcW > 0 && srcH > 0) {
                gcBackground.drawImage(fieldImage,
                        0, 0, srcW, srcH,
                        offsetX, offsetY, displayWidth, displayHeight);
            }
        }
        if (fieldImage == null || fieldImage.getWidth() <= 0 || fieldImage.getHeight() <= 0) {
            gcBackground.setFill(Color.web("#182818"));
            gcBackground.fillRect(offsetX, offsetY, displayWidth, displayHeight);
        }

        gcBackground.setFill(Color.web(Styles.TEXT_PRIMARY));
        gcBackground.setFont(new Font(9));
        gcBackground.setTextAlign(TextAlignment.CENTER);
        gcBackground.fillText("0", offsetX + displayWidth / 2, Math.max(offsetY - 3, 9));
        gcBackground.fillText(String.format("%.2fm", FIELD_LENGTH), offsetX + displayWidth / 2,
                Math.min(offsetY + displayHeight + 12, fieldH - 3));

        gcBackground.restore();
    }

    private void redrawDynamicContent() {
        collectMouseCoords();
        double cw = foregroundCanvas.getWidth();
        double ch = foregroundCanvas.getHeight();
        if (cw <= 0 || ch <= 0) return;

        gcForeground.clearRect(0, 0, cw, ch);
        gcForeground.save();

        double fieldW = cw;
        double fieldH = ch;
        if (fieldRotationIndex % 2 != 0) {
            fieldW = ch;
            fieldH = cw;
        }

        
        gcForeground.translate(cw / 2, ch / 2);
        gcForeground.rotate(fieldRotationIndex * 90);
        gcForeground.translate(-fieldW / 2, -fieldH / 2);

        double displayWidth = fieldW, displayHeight = fieldH, offsetX = 0, offsetY = 0;

        if (trajectory.size() > 1) {
            gcForeground.setStroke(Color.web("#FF8C00"));
            gcForeground.setLineWidth(1.5);
            for (int i = 0; i < trajectory.size() - 1; i++) {
                Pose2D p1 = trajectory.get(i);
                Pose2D p2 = trajectory.get(i + 1);
                double x1 = offsetX + ((p1.x + POSITION_OFFSET_X_M) / FIELD_LENGTH) * displayWidth;
                double y1 = offsetY + displayHeight - ((p1.y + POSITION_OFFSET_Y_M) / FIELD_WIDTH) * displayHeight;
                double x2 = offsetX + ((p2.x + POSITION_OFFSET_X_M) / FIELD_LENGTH) * displayWidth;
                double y2 = offsetY + displayHeight - ((p2.y + POSITION_OFFSET_Y_M) / FIELD_WIDTH) * displayHeight;
                gcForeground.strokeLine(x1, y1, x2, y2);
            }
        }

        double robotScreenX = offsetX + ((displayX + POSITION_OFFSET_X_M) / FIELD_LENGTH) * displayWidth;
        double robotScreenY = offsetY + displayHeight - ((displayY + POSITION_OFFSET_Y_M) / FIELD_WIDTH) * displayHeight;
        double robotSize = (32.0 / 39.37) / FIELD_WIDTH * displayHeight;

        gcForeground.save();
        gcForeground.translate(robotScreenX, robotScreenY);
        gcForeground.rotate(-Math.toDegrees(displayRotation));

        gcForeground.setStroke(Color.web("#FFFFFF"));
        gcForeground.setLineWidth(3);
        gcForeground.strokeRect(-robotSize / 2, -robotSize / 2, robotSize, robotSize);

        gcForeground.setFill(Color.web("#FF8C00"));
        gcForeground.setLineWidth(2);
        double arrowLen = robotSize * 0.8;
        double arrowHalfW = robotSize * 0.08;
        double[] xPoints = { arrowLen / 2, -arrowLen / 2, -arrowLen / 2 };
        double[] yPoints = { 0, arrowHalfW, -arrowHalfW };
        gcForeground.fillPolygon(xPoints, yPoints, 3);
        gcForeground.strokePolygon(xPoints, yPoints, 3);

        gcForeground.restore();

        for (Map.Entry<String, double[]> e : fieldObjects.entrySet()) {
            String name = e.getKey();
            if ("trajA".equals(name) || "trajB".equals(name)) continue;
            double[] pose = e.getValue();
            if (pose == null || pose.length < 3) continue;
            double ox = pose[0];
            double oy = pose[1];
            double orot = pose[2];
            double objScreenX = offsetX + ((ox + POSITION_OFFSET_X_M) / FIELD_LENGTH) * displayWidth;
            double objScreenY = offsetY + displayHeight - ((oy + POSITION_OFFSET_Y_M) / FIELD_WIDTH) * displayHeight;
            double objSize = (24.0 / 39.37) / FIELD_WIDTH * displayHeight;

            gcForeground.save();
            gcForeground.translate(objScreenX, objScreenY);
            gcForeground.rotate(-Math.toDegrees(orot));

            gcForeground.setFill(Color.web(FIELD_OBJECTS_COLOR));
            gcForeground.setStroke(Color.web("#00688B"));
            gcForeground.setLineWidth(2);
            gcForeground.fillOval(-objSize / 2, -objSize / 2, objSize, objSize);
            gcForeground.strokeOval(-objSize / 2, -objSize / 2, objSize, objSize);

            double objArrowLen = objSize * 0.75;
            double objArrowHalfW = objSize * 0.07;
            double[] objXPoints = { objArrowLen / 2, -objArrowLen / 2, -objArrowLen / 2 };
            double[] objYPoints = { 0, objArrowHalfW, -objArrowHalfW };
            gcForeground.setFill(Color.WHITE);
            gcForeground.setStroke(Color.web("#00688B"));
            gcForeground.setLineWidth(1);
            gcForeground.fillPolygon(objXPoints, objYPoints, 3);
            gcForeground.strokePolygon(objXPoints, objYPoints, 3);

            gcForeground.restore();
        }

        double[] trajA = fieldObjects.get("trajA");
        double[] trajB = fieldObjects.get("trajB");
        if (trajA != null && trajA.length >= 2) {
            System.out.println("trajA: " + java.util.Arrays.toString(trajA));
        }
        if (trajB != null && trajB.length >= 2) {
            System.out.println("trajB: " + java.util.Arrays.toString(trajB));
        }
        if (trajA != null && trajA.length >= 2 && trajB != null && trajB.length >= 2) {
            double x1 = offsetX + ((trajA[0] + POSITION_OFFSET_X_M) / FIELD_LENGTH) * displayWidth;
            double y1 = offsetY + displayHeight - ((trajA[1] + POSITION_OFFSET_Y_M) / FIELD_WIDTH) * displayHeight;
            double x2 = offsetX + ((trajB[0] + POSITION_OFFSET_X_M) / FIELD_LENGTH) * displayWidth;
            double y2 = offsetY + displayHeight - ((trajB[1] + POSITION_OFFSET_Y_M) / FIELD_WIDTH) * displayHeight;

            gcForeground.setStroke(Color.RED);
            gcForeground.setLineWidth(2);
            gcForeground.setLineDashes(3, 5);
            gcForeground.strokeLine(x1, y1, x2, y2);
            gcForeground.setLineDashes();

            double xSize = 5;
            gcForeground.strokeLine(x1 - xSize, y1 - xSize, x1 + xSize, y1 + xSize);
            gcForeground.strokeLine(x1 - xSize, y1 + xSize, x1 + xSize, y1 - xSize);
            gcForeground.strokeLine(x2 - xSize, y2 - xSize, x2 + xSize, y2 + xSize);
            gcForeground.strokeLine(x2 - xSize, y2 + xSize, x2 + xSize, y2 - xSize);
        }

        gcForeground.restore();
    }

    private static double[] parsePoseFromValue(Object value) {
        if (value instanceof double[]) {
            double[] pose = (double[]) value;
            if (pose.length >= 3) {
                double rot = pose[2];
                rot = Math.toRadians(rot);
                while (rot > Math.PI) rot -= 2 * Math.PI;
                while (rot < -Math.PI) rot += 2 * Math.PI;
                return new double[] { pose[0], pose[1], rot };
            }
        } else if (value instanceof String) {
            String s = ((String) value).trim();
            if (s.startsWith("[") && s.endsWith("]")) {
                s = s.substring(1, s.length() - 1);
                String[] parts = s.split(",");
                if (parts.length >= 3) {
                    try {
                        double x = Double.parseDouble(parts[0].trim());
                        double y = Double.parseDouble(parts[1].trim());
                        double r = Double.parseDouble(parts[2].trim());
                        r = Math.toRadians(r);
                        while (r > Math.PI) r -= 2 * Math.PI;
                        while (r < -Math.PI) r += 2 * Math.PI;
                        return new double[] { x, y, r };
                    } catch (NumberFormatException ignored) { }
                }
            }
        }
        return null;
    }

    public void setFieldObjects(Map<String, Object> raw) {
        Map<String, double[]> parsed = new HashMap<>();
        if (raw != null) {
            for (Map.Entry<String, Object> e : raw.entrySet()) {
                if ("Robot".equals(e.getKey())) continue;
                double[] pose = parsePoseFromValue(e.getValue());
                if (pose != null) parsed.put(e.getKey(), pose);
            }
        }
        this.fieldObjects = parsed;
    }

    private void setupContextMenu() {
        ContextMenu contextMenu = new ContextMenu();

        MenuItem toggleTrajectory = new MenuItem(showTrajectory ? "Hide Trajectory" : "Show Trajectory");
        toggleTrajectory.setOnAction(e -> {
            showTrajectory = !showTrajectory;
            toggleTrajectory.setText(showTrajectory ? "Hide Trajectory" : "Show Trajectory");
            redrawDynamicContent();
        });

        MenuItem clearTrajectory = new MenuItem("Clear Trajectory");
        clearTrajectory.setOnAction(e -> {
            trajectory.clear();
            redrawDynamicContent();
        });

        MenuItem rotateFieldMenu = new MenuItem("Rotate Field 90°");
        rotateFieldMenu.setOnAction(e -> {
            fieldRotationIndex = (fieldRotationIndex + 1) % 4;
            requestResize();
            // Force layout recalculation by triggering custom event code
            double paneW = canvasPane.getWidth();
            double paneH = canvasPane.getHeight();
            if (paneW > 0 && paneH > 0) {
                double aspect = getFieldImageAspectRatio();
                double cw, ch;
                if (paneW / paneH > aspect) {
                    ch = paneH;
                    cw = paneH * aspect;
                } else {
                    cw = paneW;
                    ch = paneW / aspect;
                }
                backgroundCanvas.setWidth(cw);
                backgroundCanvas.setHeight(ch);
                foregroundCanvas.setWidth(cw);
                foregroundCanvas.setHeight(ch);
            }
            redrawBackground();
            redrawDynamicContent();
        });

        MenuItem removeItem = new MenuItem("Remove Widget");
        removeItem.setOnAction(e -> {
            if (removeCallback != null) {
                removeCallback.run();
            }
        });

        contextMenu.getItems().addAll(toggleTrajectory, clearTrajectory, rotateFieldMenu, removeItem);

        container.setOnContextMenuRequested(event -> {
            contextMenu.show(container, event.getScreenX(), event.getScreenY());
        });
    }

    public void setRemoveCallback(Runnable callback) {
        this.removeCallback = callback;
    }

    @Override
    public void setDisabled(boolean disabled) {
        super.setDisabled(false);
    }

    @Override
    public void updateValue(Object value) {
        double newX = robotX;
        double newY = robotY;
        double newRotation = robotRotation;

        if (value instanceof String) {
            String strValue = (String) value;
            try {
                strValue = strValue.trim();
                if (strValue.startsWith("[") && strValue.endsWith("]")) {
                    strValue = strValue.substring(1, strValue.length() - 1);
                    String[] parts = strValue.split(",");
                    if (parts.length >= 3) {
                        newX = Double.parseDouble(parts[0].trim());
                        newY = Double.parseDouble(parts[1].trim());
                        newRotation = Double.parseDouble(parts[2].trim());
                    }
                }
            } catch (NumberFormatException e) {
                // Ignore malformed data
                return;
            }
        } else if (value instanceof double[]) {
            double[] pose = (double[]) value;
            if (pose.length >= 3) {
                newX = pose[0];
                newY = pose[1];
                newRotation = pose[2];

                newRotation = Math.toRadians(newRotation);

                while (newRotation > Math.PI)
                    newRotation -= 2 * Math.PI;
                while (newRotation < -Math.PI)
                    newRotation += 2 * Math.PI;
            }
        } else {
            return;
        }

        long currentTime = System.currentTimeMillis();

        trajectory.removeIf(p -> currentTime - p.timestamp > TRAIL_DURATION_MS);

        if (trajectory.size() >= MAX_POINTS) {
            trajectory.remove(0);
        }

        Pose2D newPose = new Pose2D(newX, newY, newRotation);
        trajectory.add(newPose);

        secondLastPose = lastPose;
        lastPose = newPose;
        lastUpdateTime = currentTime;

        robotX = newX;
        robotY = newY;
        robotRotation = newRotation;

        displayX = newX;
        displayY = newY;
        displayRotation = newRotation;
    }

    private void writeCoordinateBack(double[] distance) 
    {
        try
        {
            NetworkTableInstance instance = NetworkTableInstance.getDefault();
            NetworkTable prefs = instance.getTable("Preferences");
            NetworkTable location = prefs.getSubTable("location"); 

            location.getEntry("x_location").setDouble(distance[0]);
            location.getEntry("y_location").setDouble(distance[1]);

            System.out.println("X location in inches" + distance[0]);
            System.out.println("Y location in inches" + distance[1]);

        } 
        catch (Exception e)
        {
            System.err.println("Ntables did not work" + e.getMessage());
        }
    }

    @Override
    public Node getContent() {
        return container;
    }
}
