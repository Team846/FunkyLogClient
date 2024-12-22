package com.funkylogclient;

import java.util.LinkedList;

import javafx.application.Platform;
import javafx.geometry.*;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

public class SavedFunkyLogs {

    private static VBox messageZone;

    private static Stage popupStage;

    public static void displaySavedLogs(LinkedList<Message> messages, Stage primaryStage, String fileName) {
        popupStage = new Stage();
        popupStage.setTitle("Log File Reader");

        BorderPane root = new BorderPane();
        root.getStyleClass().add("root");

        VBox center = new VBox();
        center.setStyle(Styles.CENTER);
        center.setPadding(new Insets(10, 10, 10, 10));

        messageZone = new VBox();
        messageZone.setPrefSize(100000, 100000);
        messageZone.setPadding(new Insets(5, 20, 5, 20));
        messageZone.setSpacing(2.0);
        messageZone.setStyle("-fx-background-color: rgb(50, 50, 50);");

        ScrollPane mScrollPane = new ScrollPane(messageZone);
        mScrollPane.setFitToWidth(true);
        mScrollPane.setFitToHeight(true);

        mScrollPane.setStyle("-fx-background-color: rgb(50, 50, 50);");

        center.getChildren().add(mScrollPane);

        root.setCenter(center);

        Button closeButton = new Button("Close");
        closeButton.setOnAction(event -> popupStage.close());
        closeButton.setStyle("-fx-background-color: red; -fx-text-fill: white;");

        Label fileNameLabel = new Label(fileName);
        fileNameLabel.setStyle("-fx-text-fill: white;");

        StackPane topPane = new StackPane();
        StackPane.setAlignment(closeButton, Pos.TOP_CENTER);
        StackPane.setAlignment(fileNameLabel, Pos.TOP_LEFT);
        topPane.setPadding(new Insets(10));
        topPane.getChildren().addAll(closeButton, fileNameLabel);

        root.setTop(topPane);

        Scene scene = new Scene(root, Color.TRANSPARENT);
        popupStage.initStyle(StageStyle.TRANSPARENT);
        popupStage.setScene(scene);
        popupStage.requestFocus();
        popupStage.show();
        popupStage.toFront();

        setStageSize(popupStage);

        root.setStyle("-fx-background-radius: 10; -fx-background-color:rgb(50, 50, 50);");

        displayMessages(messages);

        primaryStage.setOnCloseRequest(event -> popupStage.close());
    }

    private static void displayMessages(LinkedList<Message> messages) {
        Platform.runLater(() -> {
            for (Message msg : messages) {
                messageZone.getChildren().add(msg.getComponent());
            }
        });
    }

    private static void setStageSize(Stage stage) {
        stage.setX(200);
        stage.setY(150);

        stage.setWidth(700);
        stage.setHeight(500);
    }
}
