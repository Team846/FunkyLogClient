package com.funkylogclient;

import java.io.File;
import java.util.LinkedList;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.geometry.*;
import javafx.scene.Scene;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.Popup;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.scene.Cursor;
import javafx.scene.text.*;

public class SavedFunkyLogs {

    private static VBox messageZone;

    private static boolean auto_scroll = true;

    private static Stage popupStage; 

    public static void displaySavedLogs(LinkedList<Message> messages) {
        popupStage = new Stage();
        popupStage.setTitle("Saved Logs");

        BorderPane root = new BorderPane();
        root.getStyleClass().add("root");

        VBox center = new VBox();
        center.setStyle(Styles.CENTER);
        center.setPadding(new Insets(10, 10, 10, 10));


        messageZone = new VBox();
        messageZone.setPrefSize(100000, 100000);
        messageZone.setPadding(new Insets(5, 20, 5, 20));
        messageZone.setSpacing(2.0);
        messageZone.setStyle(Styles.SCROLL_PANE_STYLE);

        ScrollPane mScrollPane = new ScrollPane(messageZone);
        mScrollPane.setFitToWidth(true);
        mScrollPane.setFitToHeight(true);
        messageZone.heightProperty().addListener((observable, oldValue, newValue) -> {
            if (SavedFunkyLogs.auto_scroll) mScrollPane.setVvalue(1.0);
        });
        mScrollPane.setStyle(Styles.SCROLL_PANE_STYLE);

        center.getChildren().add(mScrollPane);

        root.setCenter(center); 

        Scene scene = new Scene(root, Color.TRANSPARENT);
        popupStage.initStyle(StageStyle.TRANSPARENT);
        popupStage.setScene(scene);
        popupStage.show();

        SavedFunkyLogs.setStageSize(popupStage);

        root.setStyle("-fx-background-radius: 10; -fx-background-color: #1E1E1E;");

        displayMessages(messages);
    }

    private static void displayMessages(LinkedList<Message> messages){
        Platform.runLater(() -> {
            messageZone.getChildren().clear();            
            for (Message msg : messages) {
                messageZone.getChildren().add(msg.getComponent());
            }
        });
    }
    


    private static void setStageSize(Stage stage) {
        popupStage.setX(stage.getX() + stage.getWidth() / 2 - 400); 
        popupStage.setY(stage.getY() + stage.getHeight() / 2 - 300);

        stage.setWidth((800));
        stage.setHeight(600);
    }
}
