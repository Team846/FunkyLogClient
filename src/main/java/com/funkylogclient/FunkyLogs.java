package com.funkylogclient;

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
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.scene.Cursor;
import javafx.scene.text.*;

public class FunkyLogs extends Application {

    private BorderPane root;

    private double xl = 100;
    private double xr = 1100;
    private double yu = 70;
    private double yd = 650;

    private static VBox messageZone;

    private static boolean auto_scroll = true;

    private static String serverIP = UDPClient.serverIP;
    private static int port = UDPClient.port;

    @Override
    public void start(Stage primaryStage) {
        primaryStage.setTitle("FunkyLogs v1.0.0");

        root = new BorderPane();
        root.getStyleClass().add("root");

        VBox center = new VBox();
        center.setStyle(Styles.CENTER);
        center.setPadding(new Insets(10, 10, 10, 10));

        VBox centerSearch = new VBox();
        centerSearch.setPadding(new Insets(5, 5, 15, 5));
        HBox withLabelToo = new HBox(15);
        Text searchLabelText = new Text("Search: ");
        searchLabelText.setStyle(Styles.TEXT_GMED);
        withLabelToo.getChildren().add(searchLabelText);
        TextField searchBar = new TextField();

        searchBar.textProperty().addListener((observable, prevValue, newValue) -> {
            FunkyLogSorter.changeSearchTerm(newValue);
        });

        searchBar.setPrefWidth(450);
        withLabelToo.getChildren().add(searchBar);
        centerSearch.getChildren().add(withLabelToo);
        center.getChildren().add(centerSearch);

        messageZone = new VBox();
        messageZone.setPrefSize(100000, 100000);
        messageZone.setPadding(new Insets(5, 20, 5, 20));
        messageZone.setSpacing(2.0);
        messageZone.setStyle(Styles.SCROLL_PANE_STYLE);

        ScrollPane mScrollPane = new ScrollPane(messageZone);
        mScrollPane.setFitToWidth(true);
        mScrollPane.setFitToHeight(true);
        messageZone.heightProperty().addListener((observable, oldValue, newValue) -> {
            if (FunkyLogs.auto_scroll) mScrollPane.setVvalue(1.0);
        });
        mScrollPane.setStyle(Styles.SCROLL_PANE_STYLE);

        center.getChildren().add(mScrollPane);

        root.setCenter(center);

        root.setRight(RightSidebar.getRightSidebar(getClass().getResource("logo.png"), 
            getClass().getResource("exit.png"), primaryStage, 
            (observable, prev, value) -> { FunkyLogs.auto_scroll = value; }, 
            (observable, prev, value) -> { FunkyLogs.serverIP = value; },
            (observable, prev, value) -> { FunkyLogs.port = Integer.parseInt(value); },
            (ev) -> { 
                UDPClient.setConnectionAddress(FunkyLogs.serverIP, FunkyLogs.port); 
            }));

        Scene scene = new Scene(root, Color.TRANSPARENT);
        primaryStage.initStyle(StageStyle.TRANSPARENT);
        primaryStage.setScene(scene);
        primaryStage.show();

        setStageSize(primaryStage);

        enableResizing(primaryStage, root);

        root.setStyle("-fx-background-radius: 10; -fx-background-color: #1E1E1E;");

        Task<Void> updateTask = new Task<Void>() {
            @Override
            protected Void call() throws Exception {
                for (;;) {
                    if (isCancelled()) {
                        break;
                    }
                    Thread.sleep(200);
                    try {
                        FunkyLogs.updateMessageZone();
                    } catch (Exception exc) {
                        System.out.println(exc);
                    }
                }
                return null;
            }
        };

        Thread updateThread = new Thread(updateTask);
        updateThread.setDaemon(true);
        updateThread.start();
    }

    private static void updateMessageZone() {
        Platform.runLater(() -> {
            FunkyLogs.messageZone.getChildren().clear();
            @SuppressWarnings("unchecked")
            LinkedList<Message> fmessages_copy = (LinkedList<Message>) FunkyLogSorter.filtered.clone();
            for (Message msg : fmessages_copy) {
                FunkyLogs.messageZone.getChildren().add(msg.getComponent());
            }
        });
    }

    private void setStageSize(Stage stage) {
        stage.setX(xl);
        stage.setY(yu);

        stage.setWidth(Math.max(1000, xr - xl));
        stage.setHeight(Math.max(580, yd - yu));
    }

    private void enableResizing(Stage stage, BorderPane root) {
        final int borderWidth = 14;

        root.setOnMouseMoved(event -> {
            double mouseX = event.getX();
            double mouseY = event.getY();
            double width = root.getWidth();
            double height = root.getHeight();

            if (mouseX > width - borderWidth && mouseY < borderWidth) {
                root.setCursor(Cursor.NE_RESIZE);
            } else if (mouseX < borderWidth && mouseY < borderWidth) {
                root.setCursor(Cursor.NW_RESIZE);
            } else if (mouseX < borderWidth && mouseY > height - borderWidth) {
                root.setCursor(Cursor.SW_RESIZE);
            } else if (mouseX > width - borderWidth && mouseY > height - borderWidth) {
                root.setCursor(Cursor.SE_RESIZE);
            } else {
                root.setCursor(Cursor.CROSSHAIR);
            }
        });

        root.setOnMouseDragged(event -> {
            double mouseX = event.getScreenX();
            double mouseY = event.getScreenY();

            if (root.getCursor() == Cursor.NW_RESIZE) {
                xl = mouseX;
                yu = mouseY;
            } else if (root.getCursor() == Cursor.SW_RESIZE) {
                xl = mouseX;
                yd = mouseY;
            } else if (root.getCursor() == Cursor.NE_RESIZE) {
                xr = mouseX;
                yu = mouseY;
            } else if (root.getCursor() == Cursor.SE_RESIZE) {
                xr = mouseX;
                yd = mouseY;
            }
            setStageSize(stage);
        });
    }

    public static void main(String[] args) {
        // FunkyLogSorter.main(args);

        launch(args);
    }
}