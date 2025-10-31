package com.funkylogclient;

import java.util.LinkedList;

import javafx.geometry.*;
import javafx.scene.Cursor;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.text.Text;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

public class SavedFunkyLogs {
    private static VBox messageZone;
    private static Stage popupStage;
    private static LinkedList<Message> allMessages;
    private static boolean allowErrors = true;
    private static boolean allowWarnings = true;
    private static boolean allowLogs = true;
    private static String searchTerm = "";

    public static void displaySavedLogs(LinkedList<Message> messages, Stage primaryStage, String fileName) {
        allMessages = new LinkedList<>(messages);
        popupStage = new Stage();
        popupStage.setTitle("Log File Reader");

        BorderPane root = new BorderPane();
        root.getStyleClass().add("root");

        root.setTop(createUtilityBar(popupStage, fileName));

        VBox center = new VBox(10);
        center.setStyle(Styles.CENTER);
        center.setPadding(new Insets(10, 10, 10, 10));

        VBox searchBox = new VBox(8);
        searchBox.setStyle(Styles.SEARCH_CONTAINER_STYLE);
        HBox withLabelToo = new HBox(15);
        withLabelToo.setAlignment(Pos.CENTER_LEFT);
        Label searchLabel = new Label("Search:");
        searchLabel.setStyle(Styles.LABEL_MED + Styles.BOLD_TEXT);
        TextField searchBar = new TextField();
        searchBar.setStyle(Styles.SEARCH_BAR_STYLE);
        searchBar.setPromptText("Search logs...");
        searchBar.textProperty().addListener((obs, ov, nv) -> {
            searchTerm = nv;
            reFilterMessages();
        });
        searchBar.focusedProperty().addListener((obs, ov, nv) -> {
            if (nv) {
                searchBar.setStyle(Styles.SEARCH_BAR_FOCUSED_STYLE);
            } else {
                searchBar.setStyle(Styles.SEARCH_BAR_STYLE);
            }
        });
        Region searchSpacer = new Region();
        HBox.setHgrow(searchSpacer, Priority.ALWAYS);
        HBox.setHgrow(searchBar, Priority.ALWAYS);
        withLabelToo.getChildren().addAll(searchLabel, searchBar, searchSpacer);
        searchBox.getChildren().add(withLabelToo);

        VBox filtersBox = new VBox(5);
        filtersBox.setPadding(new Insets(5));
        Text filterByText = new Text("Filter by:");
        filterByText
                .setStyle("-fx-font-size: 16px; -fx-fill: #FFFFFF; -fx-font-family: 'Segoe UI', 'Roboto', sans-serif;");
        filtersBox.getChildren().add(filterByText);
        CheckBox errorsCheckBox = new CheckBox("Errors");
        errorsCheckBox.setSelected(true);
        errorsCheckBox.setStyle(Styles.CHECKBOX_STYLE);
        errorsCheckBox.selectedProperty().addListener((obs, ov, nv) -> {
            allowErrors = nv;
            reFilterMessages();
        });
        CheckBox warningsCheckBox = new CheckBox("Warnings");
        warningsCheckBox.setSelected(true);
        warningsCheckBox.setStyle(Styles.CHECKBOX_STYLE);
        warningsCheckBox.selectedProperty().addListener((obs, ov, nv) -> {
            allowWarnings = nv;
            reFilterMessages();
        });
        CheckBox logsCheckBox = new CheckBox("Logs");
        logsCheckBox.setSelected(true);
        logsCheckBox.setStyle(Styles.CHECKBOX_STYLE);
        logsCheckBox.selectedProperty().addListener((obs, ov, nv) -> {
            allowLogs = nv;
            reFilterMessages();
        });
        filtersBox.getChildren().addAll(errorsCheckBox, warningsCheckBox, logsCheckBox);

        messageZone = new VBox();
        messageZone.setPrefSize(100000, 100000);
        messageZone.setPadding(new Insets(5, 20, 5, 20));
        messageZone.setSpacing(2.0);
        messageZone.setStyle(Styles.SCROLL_PANE_STYLE);

        ScrollPane mScrollPane = new ScrollPane(messageZone);
        mScrollPane.setFitToWidth(true);
        mScrollPane.setFitToHeight(true);
        mScrollPane.setStyle(Styles.SCROLL_PANE_STYLE);
        VBox.setVgrow(mScrollPane, Priority.ALWAYS);

        center.getChildren().addAll(searchBox, filtersBox, mScrollPane);
        root.setCenter(center);

        Scene scene = new Scene(root, Color.TRANSPARENT);
        scene.getStylesheets()
                .add(SavedFunkyLogs.class.getResource("/com/funkylogclient/dark-theme.css").toExternalForm());
        popupStage.initStyle(StageStyle.TRANSPARENT);
        popupStage.setScene(scene);
        popupStage.requestFocus();
        popupStage.show();
        popupStage.toFront();

        setStageSize(popupStage);
        root.setStyle(
                "-fx-background-radius: 12; -fx-background-color: #242424; -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.3), 20, 0, 0, 0);");

        reFilterMessages();
        primaryStage.setOnCloseRequest(event -> popupStage.close());
    }

    private static HBox createUtilityBar(Stage stage, String fileName) {
        HBox utilityBar = new HBox(10);
        utilityBar.setPadding(new Insets(8, 15, 5, 15));
        utilityBar.setStyle(
                "-fx-background-color: #333333; -fx-background-radius: 12 12 0 0; -fx-border-color: transparent transparent #404040 transparent; -fx-border-width: 0 0 1px 0;");

        Circle closeButton = createUtilityButton(true, () -> stage.close());
        Circle minimizeButton = createUtilityButton(false, () -> stage.setIconified(true));
        Circle maximizeButton = createMaximizeButton(stage);

        Label fileNameLabel = new Label(fileName);
        fileNameLabel.setStyle(
                "-fx-text-fill: #E0E0E0; -fx-font-size: 15px; -fx-font-family: 'Segoe UI', 'Roboto', sans-serif; -fx-font-weight: bold;");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        utilityBar.getChildren().addAll(fileNameLabel, spacer, minimizeButton, maximizeButton, closeButton);

        enableDragging(stage, utilityBar);

        return utilityBar;
    }

    private static void enableDragging(Stage stage, HBox utilityBar) {
        final double[] xOffset = new double[1];
        final double[] yOffset = new double[1];
        final boolean[] isDragging = new boolean[1];

        utilityBar.setOnMousePressed(event -> {
            xOffset[0] = event.getSceneX();
            yOffset[0] = event.getSceneY();
            isDragging[0] = false;
        });

        utilityBar.setOnMouseDragged(event -> {
            if (!isDragging[0]) {
                double deltaX = Math.abs(event.getSceneX() - xOffset[0]);
                double deltaY = Math.abs(event.getSceneY() - yOffset[0]);
                if (deltaX > 3 || deltaY > 3) {
                    isDragging[0] = true;
                }
            }

            if (isDragging[0]) {
                stage.setX(event.getScreenX() - xOffset[0]);
                stage.setY(event.getScreenY() - yOffset[0]);
            }
        });

        utilityBar.setOnMouseReleased(event -> {
            isDragging[0] = false;
        });

        Region spacer = (Region) utilityBar.getChildren().get(1);
        spacer.setOnMousePressed(event -> {
            xOffset[0] = event.getSceneX();
            yOffset[0] = event.getSceneY();
            isDragging[0] = false;
        });

        spacer.setOnMouseDragged(event -> {
            if (!isDragging[0]) {
                double deltaX = Math.abs(event.getSceneX() - xOffset[0]);
                double deltaY = Math.abs(event.getSceneY() - yOffset[0]);
                if (deltaX > 3 || deltaY > 3) {
                    isDragging[0] = true;
                }
            }

            if (isDragging[0]) {
                stage.setX(event.getScreenX() - xOffset[0]);
                stage.setY(event.getScreenY() - yOffset[0]);
            }
        });

        spacer.setOnMouseReleased(event -> {
            isDragging[0] = false;
        });

        Label fileNameLabel = (Label) utilityBar.getChildren().get(0);
        fileNameLabel.setOnMousePressed(event -> {
            xOffset[0] = event.getSceneX();
            yOffset[0] = event.getSceneY();
            isDragging[0] = false;
        });

        fileNameLabel.setOnMouseDragged(event -> {
            if (!isDragging[0]) {
                double deltaX = Math.abs(event.getSceneX() - xOffset[0]);
                double deltaY = Math.abs(event.getSceneY() - yOffset[0]);
                if (deltaX > 3 || deltaY > 3) {
                    isDragging[0] = true;
                }
            }

            if (isDragging[0]) {
                stage.setX(event.getScreenX() - xOffset[0]);
                stage.setY(event.getScreenY() - yOffset[0]);
            }
        });

        fileNameLabel.setOnMouseReleased(event -> {
            isDragging[0] = false;
        });
    }

    private static Circle createUtilityButton(boolean isCloseButton, Runnable action) {
        Color color = isCloseButton ? Color.RED : Color.YELLOW;
        Circle button = new Circle(7, color);
        button.setStyle("-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.3), 3, 0, 0, 1);");

        button.setOnMouseEntered(e -> {
            button.setOpacity(0.8);
            button.setScaleX(1.1);
            button.setScaleY(1.1);
            button.setCursor(Cursor.DEFAULT);
        });
        button.setOnMouseExited(e -> {
            button.setOpacity(1.0);
            button.setScaleX(1.0);
            button.setScaleY(1.0);
        });
        button.setOnMousePressed(e -> {
            e.consume();
            button.setCursor(Cursor.DEFAULT);
        });
        button.setOnMouseDragged(e -> {
            e.consume();
            button.setCursor(Cursor.DEFAULT);
        });
        button.setOnMouseClicked(e -> {
            e.consume();
            action.run();
        });
        return button;
    }

    private static Circle createMaximizeButton(Stage stage) {
        Circle button = new Circle(7, Color.GREEN);
        button.setStyle("-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.3), 3, 0, 0, 1);");

        button.setOnMouseEntered(e -> {
            button.setOpacity(0.8);
            button.setScaleX(1.1);
            button.setScaleY(1.1);
            button.setCursor(Cursor.DEFAULT);
        });
        button.setOnMouseExited(e -> {
            button.setOpacity(1.0);
            button.setScaleX(1.0);
            button.setScaleY(1.0);
        });
        button.setOnMousePressed(e -> {
            e.consume();
            button.setCursor(Cursor.DEFAULT);
        });
        button.setOnMouseDragged(e -> {
            e.consume();
            button.setCursor(Cursor.DEFAULT);
        });
        button.setOnMouseClicked(e -> {
            e.consume();
            if (stage.isMaximized()) {
                stage.setMaximized(false);
            } else {
                stage.setMaximized(true);
            }
        });
        return button;
    }

    private static void reFilterMessages() {
        if (allMessages == null)
            return;
        messageZone.getChildren().clear();
        for (Message m : allMessages) {
            if (!checkMessageBySearch(m))
                continue;
            if (allowErrors && m.isError())
                messageZone.getChildren().add(m.getComponent());
            else if (allowWarnings && m.isWarning())
                messageZone.getChildren().add(m.getComponent());
            else if (allowLogs && m.isLog())
                messageZone.getChildren().add(m.getComponent());
        }
    }

    private static boolean checkMessageBySearch(Message msg) {
        if (searchTerm.isEmpty())
            return true;
        return msg.getSender().toLowerCase().contains(searchTerm.toLowerCase())
                || msg.getContent().toLowerCase().contains(searchTerm.toLowerCase());
    }

    private static void setStageSize(Stage stage) {
        stage.setX(200);
        stage.setY(150);
        stage.setWidth(900);
        stage.setHeight(600);
    }
}
