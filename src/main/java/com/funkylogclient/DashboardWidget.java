package com.funkylogclient;

import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Rectangle;
import javafx.geometry.Insets;

public abstract class DashboardWidget {
    protected String title;
    protected String key;
    protected VBox container;
    protected VBox contentBox;
    protected Label titleLabel;
    protected Rectangle background;

    public DashboardWidget(String title, String key) {
        this.title = title;
        this.key = key;
        createWidget();
    }

    private void createWidget() {
        container = new VBox(0);
        container.setPadding(new Insets(0));
        container.setMinSize(javafx.scene.layout.Region.USE_COMPUTED_SIZE,
                javafx.scene.layout.Region.USE_COMPUTED_SIZE);
        container.setPrefSize(javafx.scene.layout.Region.USE_COMPUTED_SIZE,
                javafx.scene.layout.Region.USE_COMPUTED_SIZE);
        container.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        container.setStyle(
                "-fx-background-color: #21262D; -fx-background-radius: 8; -fx-border-color: #30363D; -fx-border-width: 1px; -fx-border-radius: 8; -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.3), 5, 0, 0, 1);");

        titleLabel = new Label(title);
        titleLabel.setPadding(new Insets(10, 12, 10, 12));
        titleLabel.setStyle(
                "-fx-background-color: #FF8C00; -fx-text-fill: #FFFFFF; -fx-font-size: 14px; -fx-font-weight: bold; -fx-font-family: 'Segoe UI', 'Roboto', sans-serif; -fx-background-radius: 8 8 0 0;");
        titleLabel.setMaxWidth(Double.MAX_VALUE);
        titleLabel.setAlignment(javafx.geometry.Pos.CENTER);

        this.contentBox = new VBox(8);
        this.contentBox.setPadding(new Insets(12));
        this.contentBox.setStyle("-fx-background-color: #21262D; -fx-background-radius: 0 0 8 8;");

        container.getChildren().addAll(titleLabel, this.contentBox);

        Rectangle clip = new Rectangle();
        clip.widthProperty().bind(container.widthProperty());
        clip.heightProperty().bind(container.heightProperty());
        clip.setArcWidth(12);
        clip.setArcHeight(12);
        container.setClip(clip);
    }

    public abstract void updateValue(Object value);

    public abstract Node getContent();

    public VBox getContainer() {
        return container;
    }

    public String getKey() {
        return key;
    }

    public String getTitle() {
        return title;
    }
}