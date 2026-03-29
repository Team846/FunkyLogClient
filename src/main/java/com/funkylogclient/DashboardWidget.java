package com.funkylogclient;

import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.Priority;
import javafx.scene.shape.Rectangle;
import javafx.geometry.Insets;
import javafx.scene.Cursor;
import javafx.application.Platform;

public abstract class DashboardWidget {
    protected String title;
    protected String key;
    protected VBox container;
    protected VBox contentBox;
    protected Label titleLabel;
    protected Rectangle background;
    protected Runnable resizeRequestCallback;

    public void setResizeRequestCallback(Runnable cb) {
        this.resizeRequestCallback = cb;
    }

    protected void requestResize() {
        if (resizeRequestCallback != null) {
            resizeRequestCallback.run();
        }
    }

    public int getColSpan() { return 1; }
    public int getRowSpan() { return 1; }

    public DashboardWidget(String title, String key) {
        this.title = title;
        this.key = key;
        createWidget();
    }

    private void createWidget() {
        container = new VBox(0);
        container.setPadding(new Insets(0));
        container.setMinSize(0, 0);
        container.setPrefSize(javafx.scene.layout.Region.USE_COMPUTED_SIZE,
                javafx.scene.layout.Region.USE_COMPUTED_SIZE);
        container.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        container.setStyle(Styles.WIDGET_CONTAINER_STYLE);
        container.setCursor(Cursor.HAND);

        container.setOnMouseEntered(e -> {
            container.setStyle(
                "-fx-background-color: " + Styles.BG_MEDIUM + "; " +
                "-fx-background-radius: 10px; " +
                "-fx-border-color: " + Styles.ACCENT_PRIMARY + "; " +
                "-fx-border-width: 1.5px; " +
                "-fx-border-radius: 10px; " +
                "-fx-effect: dropshadow(gaussian, rgba(255, 140, 0, 0.4), 12, 0, 0, 2);"
            );
        });
        container.setOnMouseExited(e -> {
            container.setStyle(Styles.WIDGET_CONTAINER_STYLE);
        });

        titleLabel = new Label(title);
        titleLabel.setPadding(new Insets(10, 14, 10, 14));
        titleLabel.setStyle(
                "-fx-background-color: " + Styles.ACCENT_PRIMARY + "; -fx-text-fill: " + Styles.TEXT_WHITE + "; -fx-font-size: 13px; -fx-font-weight: bold; -fx-font-family: " + Styles.FONT_FAMILY + "; -fx-background-radius: 10 10 0 0;");
        titleLabel.setMaxWidth(Double.MAX_VALUE);
        titleLabel.setAlignment(javafx.geometry.Pos.CENTER);

        this.contentBox = new VBox(8);
        this.contentBox.setPadding(new Insets(14));
        this.contentBox.setStyle("-fx-background-color: " + Styles.BG_DARK + "; -fx-background-radius: 0 0 10 10;");
        this.contentBox.setMinSize(0, 0);
        this.contentBox.setPrefSize(Region.USE_COMPUTED_SIZE, Region.USE_COMPUTED_SIZE);
        VBox.setVgrow(this.contentBox, Priority.ALWAYS);

        container.getChildren().addAll(titleLabel, this.contentBox);

        Rectangle clip = new Rectangle();
        clip.widthProperty().bind(container.widthProperty());
        clip.heightProperty().bind(container.heightProperty());
        clip.setArcWidth(16);
        clip.setArcHeight(16);
        container.setClip(clip);
    }

    public abstract void updateValue(Object value);

    public abstract Node getContent();

    public VBox getContainer() {
        return container;
    }

    public Node getDragHandle() {
        return titleLabel;
    }

    public String getKey() {
        return key;
    }

    public String getTitle() {
        return title;
    }
    
    public void setDisabled(boolean disabled) {
        Platform.runLater(() -> {
            if (disabled) {
                container.setOpacity(0.85);
                container.setDisable(true);
                titleLabel.setStyle(
                    "-fx-background-color: " + Styles.BG_DARK + "; -fx-text-fill: " + Styles.TEXT_MUTED + "; -fx-font-size: 13px; -fx-font-weight: bold; -fx-font-family: " + Styles.FONT_FAMILY + "; -fx-background-radius: 10 10 0 0;");
            } else {
                container.setOpacity(1.0);
                container.setDisable(false);
                titleLabel.setStyle(
                    "-fx-background-color: " + Styles.ACCENT_PRIMARY + "; -fx-text-fill: " + Styles.TEXT_WHITE + "; -fx-font-size: 13px; -fx-font-weight: bold; -fx-font-family: " + Styles.FONT_FAMILY + "; -fx-background-radius: 10 10 0 0;");
            }
        });
    }
}
