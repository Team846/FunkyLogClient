package com.funkylogclient;

import javafx.animation.AnimationTimer;
import javafx.scene.Node;
import javafx.scene.canvas.Canvas;
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
    private Canvas canvas;
    private StackPane canvasPane;
    private GraphicsContext gc;
    private List<Pose2D> trajectory;
    private static final int MAX_POINTS = 1000;
    private static final double FIELD_LENGTH = 17.548; // meters (2025 REEFSCAPE)
    private static final double FIELD_WIDTH = 8.052; // meters (2025 REEFSCAPE)
    private static final long TRAIL_DURATION_MS = 3000; // 3 seconds
    private Image fieldImage;
    private static final int[] FIELD_CORNERS = { 421, 91, 3352, 1437 }; // top-left x, y, bottom-right x, y
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

    public FieldViewWidget(String title, String key) {
        super(title, key);
        createFieldView();
        setupContextMenu();
    }

    private void createFieldView() {
        contentBox.setStyle("-fx-background-color: #0D0D0D; -fx-background-radius: 0 0 8 8;");
        contentBox.setPadding(new javafx.geometry.Insets(0));

        try {
            java.io.InputStream stream = getClass().getResourceAsStream("field-2025.png");
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

        canvas = new Canvas();
        gc = canvas.getGraphicsContext2D();
        trajectory = new ArrayList<>();

        canvasPane = new StackPane();
        canvasPane.setMinHeight(235);
        canvasPane.setMinWidth(200);
        canvasPane.getChildren().add(canvas);
        canvas.widthProperty().bind(canvasPane.widthProperty());
        canvas.heightProperty().bind(canvasPane.heightProperty());
        canvas.widthProperty().addListener(e -> redrawField());
        canvas.heightProperty().addListener(e -> redrawField());

        contentBox.getChildren().add(canvasPane);
        VBox.setVgrow(canvasPane, javafx.scene.layout.Priority.ALWAYS);

        animationTimer = new AnimationTimer() {
            @Override
            public void handle(long now) {
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

                        double extrapolationTime = Math.min(timeSinceUpdate / 1000.0, 0.5);
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

                redrawField();
            }
        };
        animationTimer.start();

        redrawField();
    }

    private void redrawField() {
        double width = canvas.getWidth();
        double height = canvas.getHeight();

        if (width <= 0 || height <= 0) {
            return;
        }

        gc.clearRect(0, 0, width, height);

        double fieldAspectRatio = FIELD_LENGTH / FIELD_WIDTH;
        double canvasAspectRatio = width / height;

        double displayWidth, displayHeight, offsetX, offsetY;

        if (canvasAspectRatio > fieldAspectRatio) {
            displayHeight = height;
            displayWidth = displayHeight * fieldAspectRatio;
            offsetX = (width - displayWidth) / 2;
            offsetY = 0;
        } else {
            displayWidth = width;
            displayHeight = displayWidth / fieldAspectRatio;
            offsetX = 0;
            offsetY = (height - displayHeight) / 2;
        }

        if (fieldImage != null) {
            int fieldAreaWidth = FIELD_CORNERS[2] - FIELD_CORNERS[0];
            int fieldAreaHeight = FIELD_CORNERS[3] - FIELD_CORNERS[1];

            gc.drawImage(fieldImage,
                    FIELD_CORNERS[0], FIELD_CORNERS[1], fieldAreaWidth, fieldAreaHeight,
                    offsetX, offsetY, displayWidth, displayHeight);
        } else {
            gc.setFill(Color.web("#182818"));
            gc.fillRect(offsetX, offsetY, displayWidth, displayHeight);
        }

        gc.setFill(Color.web("#C9D1D9"));
        gc.setFont(new Font(9));
        gc.setTextAlign(TextAlignment.CENTER);
        gc.fillText("0", offsetX + displayWidth / 2, Math.max(offsetY - 3, 9));
        gc.fillText(String.format("%.2fm", FIELD_LENGTH), offsetX + displayWidth / 2,
                Math.min(offsetY + displayHeight + 12, height - 3));

        if (trajectory.size() > 1) {
            gc.setStroke(Color.web("#58A6FF"));
            gc.setLineWidth(1.5);
            for (int i = 0; i < trajectory.size() - 1; i++) {
                Pose2D p1 = trajectory.get(i);
                Pose2D p2 = trajectory.get(i + 1);
                double x1 = offsetX + (p1.x / FIELD_LENGTH) * displayWidth;
                double y1 = offsetY + displayHeight - (p1.y / FIELD_WIDTH) * displayHeight;
                double x2 = offsetX + (p2.x / FIELD_LENGTH) * displayWidth;
                double y2 = offsetY + displayHeight - (p2.y / FIELD_WIDTH) * displayHeight;
                gc.strokeLine(x1, y1, x2, y2);
            }
        }

        double robotScreenX = offsetX + (displayX / FIELD_LENGTH) * displayWidth;
        double robotScreenY = offsetY + displayHeight - (displayY / FIELD_WIDTH) * displayHeight;
        double robotSize = (32.0 / 39.37) / FIELD_WIDTH * displayHeight;

        gc.save();

        gc.translate(robotScreenX, robotScreenY);
        gc.rotate(-Math.toDegrees(displayRotation));

        gc.setStroke(Color.web("#FFFFFF"));
        gc.setLineWidth(3);
        gc.strokeRect(-robotSize / 2, -robotSize / 2, robotSize, robotSize);

        gc.setFill(Color.web("#FF8C00"));
        gc.setLineWidth(2);
        double arrowSize = robotSize * 0.3;
        double[] xPoints = { robotSize / 2 - robotSize * 0.1, -arrowSize * 0.6, -arrowSize * 0.6 };
        double[] yPoints = { 0, -arrowSize, arrowSize };
        gc.fillPolygon(xPoints, yPoints, 3);
        gc.strokePolygon(xPoints, yPoints, 3);

        gc.restore();
    }

    private void setupContextMenu() {
        ContextMenu contextMenu = new ContextMenu();

        MenuItem toggleTrajectory = new MenuItem(showTrajectory ? "Hide Trajectory" : "Show Trajectory");
        toggleTrajectory.setOnAction(e -> {
            showTrajectory = !showTrajectory;
            toggleTrajectory.setText(showTrajectory ? "Hide Trajectory" : "Show Trajectory");
            redrawField();
        });

        MenuItem clearTrajectory = new MenuItem("Clear Trajectory");
        clearTrajectory.setOnAction(e -> {
            trajectory.clear();
            redrawField();
        });

        MenuItem removeItem = new MenuItem("Remove Widget");
        removeItem.setOnAction(e -> {
            if (removeCallback != null) {
                removeCallback.run();
            }
        });

        contextMenu.getItems().addAll(toggleTrajectory, clearTrajectory, removeItem);

        container.setOnContextMenuRequested(event -> {
            contextMenu.show(container, event.getScreenX(), event.getScreenY());
        });
    }

    public void setRemoveCallback(Runnable callback) {
        this.removeCallback = callback;
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

    @Override
    public Node getContent() {
        return container;
    }
}
