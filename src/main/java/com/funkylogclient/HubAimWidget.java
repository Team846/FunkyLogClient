package com.funkylogclient;

import java.io.InputStream;

import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.image.PixelReader;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import edu.wpi.first.networktables.NetworkTableEntry;
import edu.wpi.first.networktables.NetworkTableInstance;
import edu.wpi.first.networktables.NetworkTable;
import javafx.scene.input.MouseEvent;
import javafx.event.EventHandler;

public class HubAimWidget extends DashboardWidget {
    private static final double DEFAULT_SIZE = 260.0;
    private static final double FUEL_SIZE_RATIO = 48.0 / 400.0;
    private static final double HUB_OPACITY_THRESHOLD = 0.1;
    private static final double FUEL_DRAG_PADDING = 14.0;
    private static final double HUB_SIZE = 41.73; // inches

    private final StackPane hubWrapper;
    private final Pane hubPane;
    private final Image baseHubImage;
    private final Image hexagonMaskImage;
    private final Image greenFuelImage;
    private final Image redFuelImage;
    private final PixelReader hexagonMaskPixelReader;
    private final ImageView baseHubView;
    private final ImageView fuelView;

    private final double[] hexagonXPoints;
    private final double[] hexagonYPoints;
    private double renderedHubSize = DEFAULT_SIZE;
    private double renderedHubOffsetX = 0.0;
    private double renderedHubOffsetY = 0.0;
    private double fuelCenterXRatio = 0.5;
    private double fuelCenterYRatio = 0.5;
    private double dragOffsetX;
    private double dragOffsetY;
    private boolean draggingFuel = false;
    private boolean fuelInsideHub = true;
    private Runnable removeCallback;

    private final EventHandler<MouseEvent> sceneReleaseHandler = event -> {
        if (draggingFuel)
        {
            endFuelDrag();
        }
    };

    public HubAimWidget(String title, String key) {
        super(title, key);
        // this.isEditable = key.startsWith("Preferences/");

        baseHubImage = loadImage("base_hub.png");
        hexagonMaskImage = loadImage("Hexagon.png");
        greenFuelImage = loadImage("green_fuel.png");
        redFuelImage = loadImage("red_fuel.png");
        hexagonMaskPixelReader = hexagonMaskImage.getPixelReader();
        double[] bounds = computeHexagonBounds();
        hexagonXPoints = new double[] {
                midpoint(bounds[0], bounds[2]),
                bounds[2],
                bounds[2],
                midpoint(bounds[0], bounds[2]),
                bounds[0],
                bounds[0]
        };
        hexagonYPoints = new double[] {
                bounds[1],
                bounds[1] + (bounds[3] - bounds[1]) * 0.25,
                bounds[1] + (bounds[3] - bounds[1]) * 0.75,
                bounds[3],
                bounds[1] + (bounds[3] - bounds[1]) * 0.75,
                bounds[1] + (bounds[3] - bounds[1]) * 0.25
        };

        baseHubView = new ImageView(baseHubImage);
        fuelView = new ImageView(greenFuelImage);

        hubPane = new Pane();
        hubWrapper = new StackPane(hubPane);

        createHubAimDisplay();
        setupDragging();
        setupContextMenu();
        container.sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (oldScene != null)
            {
                oldScene.removeEventFilter(MouseEvent.MOUSE_RELEASED, sceneReleaseHandler);
            }
            if (newScene != null)
            {
                newScene.addEventFilter(MouseEvent.MOUSE_RELEASED, sceneReleaseHandler);
            }
        });
    }

    @Override
    public int getColSpan() {
        return 2;
    }

    @Override
    public int getRowSpan() {
        return 4;
    }

    private void createHubAimDisplay() {
        contentBox.setAlignment(Pos.CENTER);
        contentBox.setStyle("-fx-background-color: " + Styles.BG_DARK + "; -fx-background-radius: 0 0 10 10;");

        hubWrapper.setAlignment(Pos.CENTER);
        hubWrapper.setMinSize(180, 180);
        hubWrapper.setPrefSize(DEFAULT_SIZE, DEFAULT_SIZE);
        hubWrapper.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);

        hubPane.setMinSize(0, 0);
        hubPane.setPrefSize(DEFAULT_SIZE, DEFAULT_SIZE);
        hubPane.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        hubPane.setPickOnBounds(true);

        baseHubView.setPreserveRatio(true);
        fuelView.setPreserveRatio(true);
        fuelView.setPickOnBounds(true);
        fuelView.setCursor(Cursor.HAND);

        hubPane.getChildren().addAll(baseHubView, fuelView);
        contentBox.getChildren().add(hubWrapper);
        VBox.setVgrow(hubWrapper, javafx.scene.layout.Priority.ALWAYS);

        hubWrapper.widthProperty().addListener((obs, oldVal, newVal) -> relayout());
        hubWrapper.heightProperty().addListener((obs, oldVal, newVal) -> relayout());

        relayout();
    }

    private void setupDragging() {
        fuelView.setOnMousePressed(event -> {
            beginFuelDrag(event.getX() + fuelView.getLayoutX(), event.getY() + fuelView.getLayoutY());
            event.consume();
        });

        hubPane.setOnMousePressed(event -> {
            if (!isWithinFuelHitTarget(event.getX(), event.getY())) {
                return;
            }

            beginFuelDrag(event.getX(), event.getY());
            if (event.getX() > 400 || event.getY() > 400)
            {
                lockToCenter();
                event.consume();
            }
            event.consume();

        });

        //ntable work starts here
        fuelView.setOnMouseReleased(event -> {
            writeCoordinateBack(getRelativeDistance());
            endFuelDrag();
            lockToCenter();
            event.consume();
        });

        hubPane.setOnMouseDragged(event -> {
            if (!draggingFuel) {
                return;
            }

            if (!event.isPrimaryButtonDown()) {
                endFuelDrag();
                return;
            }

            double size = getHubSize();
            if (size <= 0) {
                return;
            }

            double fuelSize = getFuelSize();
            double minX = 0;
            double minY = 0;
            double maxX = Math.max(0, size - fuelSize);
            double maxY = Math.max(0, size - fuelSize);

            double nextX = clamp(event.getX() - renderedHubOffsetX - fuelSize / 2.0 - dragOffsetX, minX, maxX);
            double nextY = clamp(event.getY() - renderedHubOffsetY - fuelSize / 2.0 - dragOffsetY, minY, maxY);

            fuelCenterXRatio = (nextX + fuelSize / 2.0) / size;
            fuelCenterYRatio = (nextY + fuelSize / 2.0) / size;

            positionFuel();
            updateFuelState();
            event.consume();
        });

        hubPane.setOnMouseReleased(event -> {
            if (!draggingFuel) {
                return;
            }
            endFuelDrag();
            event.consume();
        });

        hubPane.setOnMouseExited(event -> {
            if (draggingFuel && !event.isPrimaryButtonDown()) {
                endFuelDrag();
            }
            event.consume();
        });
    }

    private void setupContextMenu() {
        ContextMenu contextMenu = new ContextMenu();
        MenuItem recenterItem = new MenuItem("Center Fuel");
        recenterItem.setOnAction(e -> {
            fuelCenterXRatio = 0.5;
            fuelCenterYRatio = 0.5;
            positionFuel();
            updateFuelState();
        });

        MenuItem removeItem = new MenuItem("Remove Widget");
        removeItem.setOnAction(e -> {
            if (removeCallback != null) {
                removeCallback.run();
            }
        });

        contextMenu.getItems().addAll(recenterItem, removeItem);
        container.setOnContextMenuRequested(event -> {
            contextMenu.show(container, event.getScreenX(), event.getScreenY());
        });
    }

    private void beginFuelDrag(double paneX, double paneY) {
        dragOffsetX = paneX - (fuelView.getLayoutX() + fuelView.getFitWidth() / 2.0);
        dragOffsetY = paneY - (fuelView.getLayoutY() + fuelView.getFitHeight() / 2.0);
        draggingFuel = true;
        fuelView.setCursor(Cursor.CLOSED_HAND);
    }

    private void endFuelDrag() {
        draggingFuel = false;
        dragOffsetX = 0.0;
        dragOffsetY = 0.0;
        fuelView.setCursor(Cursor.HAND);
    }

    private void relayout() {
        double wrapperWidth = hubWrapper.getWidth();
        double wrapperHeight = hubWrapper.getHeight();
        if (wrapperWidth <= 0 || wrapperHeight <= 0) {
            return;
        }

        renderedHubSize = Math.min(wrapperWidth, wrapperHeight);
        renderedHubOffsetX = (wrapperWidth - renderedHubSize) / 2.0;
        renderedHubOffsetY = (wrapperHeight - renderedHubSize) / 2.0;

        baseHubView.setFitWidth(renderedHubSize);
        baseHubView.setFitHeight(renderedHubSize);
        baseHubView.relocate(renderedHubOffsetX, renderedHubOffsetY);

        fuelView.setFitWidth(getFuelSize());
        fuelView.setFitHeight(getFuelSize());

        positionFuel();
        updateFuelState();
    }

    private void positionFuel() {
        double size = getHubSize();
        double fuelSize = getFuelSize();
        double x = clamp(fuelCenterXRatio * size - fuelSize / 2.0, 0, Math.max(0, size - fuelSize));
        double y = clamp(fuelCenterYRatio * size - fuelSize / 2.0, 0, Math.max(0, size - fuelSize));
        fuelView.relocate(renderedHubOffsetX + x, renderedHubOffsetY + y);
    }

    private void updateFuelState() {
        boolean inside = isFuelCenterInsideHub();
        if (inside != fuelInsideHub) {
            fuelInsideHub = inside;
            fuelView.setImage(fuelInsideHub ? greenFuelImage : redFuelImage);
        } else if (fuelView.getImage() == null) {
            fuelView.setImage(fuelInsideHub ? greenFuelImage : redFuelImage);
        }
    }

    private boolean isWithinFuelHitTarget(double paneX, double paneY) {
        double fuelCenterX = fuelView.getLayoutX() + fuelView.getFitWidth() / 2.0;
        double fuelCenterY = fuelView.getLayoutY() + fuelView.getFitHeight() / 2.0;
        double radius = fuelView.getFitWidth() / 2.0 + FUEL_DRAG_PADDING;
        double dx = paneX - fuelCenterX;
        double dy = paneY - fuelCenterY;
        return dx * dx + dy * dy <= radius * radius;
    }

    private boolean isFuelCenterInsideHub() {
        if (hexagonMaskPixelReader == null) {
            return true;
        }

        double size = getHubSize();
        if (size <= 0) {
            return true;
        }

        double centerX = clamp(fuelCenterXRatio, 0, 1) * (hexagonMaskImage.getWidth() - 1);
        double centerY = clamp(fuelCenterYRatio, 0, 1) * (hexagonMaskImage.getHeight() - 1);
        return isPointInPolygon(centerX, centerY, hexagonXPoints, hexagonYPoints);
    }

    private double getHubSize() {
        double width = hubWrapper.getWidth();
        double height = hubWrapper.getHeight();
        if (width <= 0 || height <= 0) {
            return renderedHubSize > 0 ? renderedHubSize : DEFAULT_SIZE;
        }
        return renderedHubSize > 0 ? renderedHubSize : Math.min(width, height);
    }

    private double getFuelSize() {
        return getHubSize() * FUEL_SIZE_RATIO;
    }

    private Image loadImage(String resourceName) {
        try (InputStream stream = getClass().getResourceAsStream(resourceName)) {
            if (stream == null) {
                throw new IllegalStateException("Missing resource: " + resourceName);
            }
            return new Image(stream);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to load resource: " + resourceName, e);
        }
    }

    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private double[] computeHexagonBounds() {
        if (hexagonMaskPixelReader == null) {
            return new double[] { 48, 31, 351, 368 };
        }

        int minX = (int) hexagonMaskImage.getWidth();
        int minY = (int) hexagonMaskImage.getHeight();
        int maxX = 0;
        int maxY = 0;

        for (int y = 0; y < (int) hexagonMaskImage.getHeight(); y++) {
            for (int x = 0; x < (int) hexagonMaskImage.getWidth(); x++) {
                if (hexagonMaskPixelReader.getColor(x, y).getOpacity() > HUB_OPACITY_THRESHOLD) {
                    minX = Math.min(minX, x);
                    minY = Math.min(minY, y);
                    maxX = Math.max(maxX, x);
                    maxY = Math.max(maxY, y);
                }
            }
        }

        return new double[] { minX, minY, maxX, maxY };
    }

    private boolean isPointInPolygon(double x, double y, double[] xPoints, double[] yPoints) {
        boolean inside = false;
        for (int i = 0, j = xPoints.length - 1; i < xPoints.length; j = i++) {
            boolean intersects = ((yPoints[i] > y) != (yPoints[j] > y))
                    && (x < (xPoints[j] - xPoints[i]) * (y - yPoints[i]) / (yPoints[j] - yPoints[i]) + xPoints[i]);
            if (intersects) {
                inside = !inside;
            }
        }
        return inside;
    }

    private double midpoint(double a, double b) {
        return (a + b) / 2.0;
    }

    public void setRemoveCallback(Runnable callback) {
        this.removeCallback = callback;
    }

    @Override
    public void updateValue(Object value) {
        if (value instanceof double[]) {
            double[] position = (double[]) value;
            if (position.length >= 2) {
                fuelCenterXRatio = clamp(position[0], 0, 1);
                fuelCenterYRatio = clamp(position[1], 0, 1);
                positionFuel();
                updateFuelState();
            }
        } else if (value instanceof String) {
            String strValue = ((String) value).trim();
            if (strValue.startsWith("[") && strValue.endsWith("]")) {
                strValue = strValue.substring(1, strValue.length() - 1);
            }

            String[] parts = strValue.split(",");
            if (parts.length >= 2) {
                try {
                    fuelCenterXRatio = clamp(Double.parseDouble(parts[0].trim()), 0, 1);
                    fuelCenterYRatio = clamp(Double.parseDouble(parts[1].trim()), 0, 1);
                    positionFuel();
                    updateFuelState();
                } catch (NumberFormatException ignored) {
                }
            }
        }
    }

    @Override
    public Node getContent() {
        return container;
    }

    public double[] getRelativeDistance(double[] inputcoords) {
        double xFromCenter = HUB_SIZE * (inputcoords[0] - 200) / getHubSize();
        double yFromCenter = HUB_SIZE * (inputcoords[1] - 200) / getHubSize();
        return new double[] { xFromCenter, yFromCenter };
    }

    public double[] getRelativeDistance() {
        double xFromCenter = HUB_SIZE * (fuelCenterXRatio - 0.5);
        double yFromCenter = HUB_SIZE * (fuelCenterYRatio - 0.5);
        return new double[] { xFromCenter, yFromCenter };
    }


    //inputs human operator (misplaced) aim coords
    private void writeCoordinateBack(double[] distance) 
    {
        try
        {
            NetworkTableInstance instance = NetworkTableInstance.getDefault();
            NetworkTable prefs = instance.getTable("Preferences");
            NetworkTable hubAim = prefs.getSubTable("HubAim");

            hubAim.getEntry("x_offset").setDouble(distance[0]);
            hubAim.getEntry("y_offset").setDouble(distance[1]);

            System.out.println("X DIST: " + distance[0]); 
            System.out.println("Y DIST: " + distance[1]); 
        } 
        catch (Exception e)
        {
            System.err.println("Hub aim preference did not work: " + e.getMessage());
        }
    }

    private void lockToCenter() {
        fuelCenterXRatio = 0.5;
        fuelCenterYRatio = 0.5;
        positionFuel();
        updateFuelState();
    }

}
