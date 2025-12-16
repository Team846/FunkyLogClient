package com.funkylogclient;

import java.util.LinkedList;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.*;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Text;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.util.Callback;

public class SavedFunkyLogs {
    private static ListView<Message> messageListView;
    private static ObservableList<Message> messageList;
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

        VBox center = new VBox(8);
        center.setStyle(Styles.CENTER);
        center.setPadding(new Insets(10, 10, 10, 10));

        VBox searchBox = new VBox(8);
        searchBox.setStyle(Styles.SEARCH_CONTAINER_STYLE);
        HBox withLabelToo = new HBox(12);
        withLabelToo.setAlignment(Pos.CENTER_LEFT);
        Label searchLabel = new Label("Search:");
        searchLabel.setStyle("-fx-text-fill: " + Styles.TEXT_SECONDARY + "; -fx-font-size: 14px; -fx-font-weight: bold; -fx-font-family: " + Styles.FONT_FAMILY + ";");
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

        HBox filtersBox = new HBox(16);
        filtersBox.setPadding(new Insets(4, 0, 4, 0));
        filtersBox.setAlignment(Pos.CENTER_LEFT);
        Text filterByText = new Text("Filter:");
        filterByText.setStyle("-fx-font-size: 14px; -fx-fill: " + Styles.TEXT_SECONDARY + "; -fx-font-family: 'Segoe UI', 'Roboto', sans-serif; -fx-font-weight: bold;");
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
        filtersBox.getChildren().addAll(filterByText, errorsCheckBox, warningsCheckBox, logsCheckBox);

        messageList = FXCollections.observableArrayList();
        messageListView = new ListView<>(messageList);
        messageListView.setStyle("-fx-background-color: #1A1A1A; -fx-border-color: transparent;");
        messageListView.setFixedCellSize(-1);

        messageListView.setCellFactory(new Callback<ListView<Message>, ListCell<Message>>() {
            @Override
            public ListCell<Message> call(ListView<Message> listView) {
                return new MessageListCell();
            }
        });
        messageListView.setCache(true);
        messageListView.setCacheShape(true);

        VBox.setVgrow(messageListView, Priority.ALWAYS);

        center.getChildren().addAll(searchBox, filtersBox, messageListView);
        root.setCenter(center);
        
        StackPane rootStack = new StackPane();
        rootStack.getChildren().add(root);
        rootStack.setStyle(
                "-fx-background-radius: 8; -fx-background-color: " + Styles.BG_MEDIUM + "; -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.3), 20, 0, 0, 0);");
        
        javafx.scene.shape.Rectangle clip = new javafx.scene.shape.Rectangle();
        clip.widthProperty().bind(rootStack.widthProperty());
        clip.heightProperty().bind(rootStack.heightProperty());
        clip.setArcWidth(16);
        clip.setArcHeight(16);
        rootStack.setClip(clip);

        Scene scene = new Scene(rootStack, Color.TRANSPARENT);
        scene.getStylesheets()
                .add(SavedFunkyLogs.class.getResource("/com/funkylogclient/dark-theme.css").toExternalForm());
        popupStage.initStyle(StageStyle.TRANSPARENT);
        popupStage.setScene(scene);
        
        setStageSize(popupStage);
        
        popupStage.requestFocus();
        popupStage.show();
        popupStage.toFront();

        javafx.application.Platform.runLater(() -> reFilterMessages());
        primaryStage.setOnCloseRequest(event -> popupStage.close());
    }

    private static HBox createUtilityBar(Stage stage, String fileName) {
        HBox utilityBar = new HBox(0);
        utilityBar.setPadding(new Insets(0));
        utilityBar.setStyle(
                "-fx-background-color: " + Styles.BG_MEDIUM + "; -fx-background-radius: 8 8 0 0; -fx-border-color: transparent transparent " + Styles.BORDER_MEDIUM + " transparent; -fx-border-width: 0 0 1px 0;");

        HBox leftSection = new HBox(12);
        leftSection.setPadding(new Insets(10, 16, 10, 16));
        leftSection.setAlignment(Pos.CENTER_LEFT);
        leftSection.setStyle("-fx-background-color: transparent;");
        
        try {
            javafx.scene.image.ImageView logoView = new javafx.scene.image.ImageView(
                    new javafx.scene.image.Image(SavedFunkyLogs.class.getResource("logo846.png").toExternalForm()));
            logoView.setFitHeight(20);
            logoView.setFitWidth(20);
            logoView.setPreserveRatio(true);
            logoView.setSmooth(true);
            leftSection.getChildren().add(logoView);
        } catch (Exception e) {
        }
        
        Label fileNameLabel = new Label(fileName);
        fileNameLabel.setStyle(
                "-fx-text-fill: " + Styles.TEXT_WHITE + "; -fx-font-size: 14px; -fx-font-family: 'Segoe UI', 'Roboto', sans-serif; -fx-font-weight: 600;");
        
        leftSection.getChildren().add(fileNameLabel);

        Button closeButton = createWindowsButton("✕", true, () -> stage.close());
        Button minimizeButton = createWindowsButton("—", false, () -> stage.setIconified(true));
        Button maximizeButton = createMaximizeWindowsButton(stage);

        HBox rightSection = new HBox(0);
        rightSection.setAlignment(Pos.CENTER_RIGHT);
        rightSection.getChildren().addAll(minimizeButton, maximizeButton, closeButton);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        utilityBar.getChildren().addAll(leftSection, spacer, rightSection);

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

        HBox leftSection = (HBox) utilityBar.getChildren().get(0);
        leftSection.setOnMousePressed(event -> {
            xOffset[0] = event.getSceneX();
            yOffset[0] = event.getSceneY();
            isDragging[0] = false;
        });

        leftSection.setOnMouseDragged(event -> {
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

        leftSection.setOnMouseReleased(event -> {
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
    }

    private static Button createWindowsButton(String symbol, boolean isCloseButton, Runnable action) {
        Button button = new Button(symbol);
        button.setMinSize(46, 32);
        button.setMaxSize(46, 32);
        button.setPrefSize(46, 32);
        button.setAlignment(Pos.CENTER);
        button.setStyle(
            "-fx-background-color: transparent; " +
            "-fx-text-fill: " + Styles.TEXT_PRIMARY + "; " +
            "-fx-font-size: 14px; " +
            "-fx-font-family: 'Segoe UI Symbol', " + Styles.FONT_FAMILY + "; " +
            "-fx-font-weight: normal; " +
            "-fx-padding: 0; " +
            "-fx-border-width: 0; " +
            "-fx-cursor: hand; " +
            "-fx-content-display: center;"
        );

        button.setOnMouseEntered(e -> {
            if (isCloseButton) {
                button.setStyle(
                    "-fx-background-color: #E81123; " +
                    "-fx-text-fill: white; " +
                    "-fx-font-size: 14px; " +
                    "-fx-font-family: 'Segoe UI Symbol', " + Styles.FONT_FAMILY + "; " +
                    "-fx-font-weight: normal; " +
                    "-fx-padding: 0; " +
                    "-fx-border-width: 0; " +
                    "-fx-cursor: hand; " +
                    "-fx-content-display: center;"
                );
            } else {
                button.setStyle(
                    "-fx-background-color: " + Styles.BG_HOVER + "; " +
                    "-fx-text-fill: " + Styles.TEXT_PRIMARY + "; " +
                    "-fx-font-size: 14px; " +
                    "-fx-font-family: 'Segoe UI Symbol', " + Styles.FONT_FAMILY + "; " +
                    "-fx-font-weight: normal; " +
                    "-fx-padding: 0; " +
                    "-fx-border-width: 0; " +
                    "-fx-cursor: hand; " +
                    "-fx-content-display: center;"
                );
            }
        });

        button.setOnMouseExited(e -> {
            button.setStyle(
                "-fx-background-color: transparent; " +
                "-fx-text-fill: " + Styles.TEXT_PRIMARY + "; " +
                "-fx-font-size: 14px; " +
                "-fx-font-family: 'Segoe UI Symbol', " + Styles.FONT_FAMILY + "; " +
                "-fx-font-weight: normal; " +
                "-fx-padding: 0; " +
                "-fx-border-width: 0; " +
                "-fx-cursor: hand; " +
                "-fx-content-display: center;"
            );
        });

        button.setOnAction(e -> {
            e.consume();
            action.run();
        });

        return button;
    }

    private static Button createMaximizeWindowsButton(Stage stage) {
        Button button = new Button("□");
        button.setMinSize(46, 32);
        button.setMaxSize(46, 32);
        button.setPrefSize(46, 32);
        button.setAlignment(Pos.CENTER);
        button.setStyle(
            "-fx-background-color: transparent; " +
            "-fx-text-fill: " + Styles.TEXT_PRIMARY + "; " +
            "-fx-font-size: 14px; " +
            "-fx-font-family: 'Segoe UI Symbol', " + Styles.FONT_FAMILY + "; " +
            "-fx-font-weight: normal; " +
            "-fx-padding: 0; " +
            "-fx-border-width: 0; " +
            "-fx-cursor: hand; " +
            "-fx-content-display: center;"
        );

        button.setOnMouseEntered(e -> {
            button.setStyle(
                "-fx-background-color: " + Styles.BG_HOVER + "; " +
                "-fx-text-fill: " + Styles.TEXT_PRIMARY + "; " +
                "-fx-font-size: 14px; " +
                "-fx-font-family: 'Segoe UI Symbol', " + Styles.FONT_FAMILY + "; " +
                "-fx-font-weight: normal; " +
                "-fx-padding: 0; " +
                "-fx-border-width: 0; " +
                "-fx-cursor: hand; " +
                "-fx-content-display: center;"
            );
        });

        button.setOnMouseExited(e -> {
            button.setStyle(
                "-fx-background-color: transparent; " +
                "-fx-text-fill: " + Styles.TEXT_PRIMARY + "; " +
                "-fx-font-size: 14px; " +
                "-fx-font-family: 'Segoe UI Symbol', " + Styles.FONT_FAMILY + "; " +
                "-fx-font-weight: normal; " +
                "-fx-padding: 0; " +
                "-fx-border-width: 0; " +
                "-fx-cursor: hand; " +
                "-fx-content-display: center;"
            );
        });

        button.setOnAction(e -> {
            e.consume();
            if (stage.isMaximized()) {
                stage.setMaximized(false);
                button.setText("□");
            } else {
                stage.setMaximized(true);
                button.setText("❐");
            }
        });

        stage.maximizedProperty().addListener((obs, oldVal, newVal) -> {
            button.setText(newVal ? "❐" : "□");
        });

        return button;
    }

    private static void reFilterMessages() {
        if (allMessages == null)
            return;
        
        java.util.List<Message> filtered = new java.util.ArrayList<>(allMessages.size() / 2);
        String lowerSearch = searchTerm.toLowerCase();
        
        for (Message m : allMessages) {
            if (!checkMessageBySearch(m, lowerSearch))
                continue;
            if (allowErrors && m.isError())
                filtered.add(m);
            else if (allowWarnings && m.isWarning())
                filtered.add(m);
            else if (allowLogs && m.isLog())
                filtered.add(m);
        }
        
        messageList.setAll(filtered);
    }

    private static boolean checkMessageBySearch(Message msg, String lowerSearch) {
        if (lowerSearch.isEmpty())
            return true;
        String sender = msg.getSender();
        String content = msg.getContent();
        if (sender.length() < content.length()) {
            return sender.toLowerCase().contains(lowerSearch) 
                || content.toLowerCase().contains(lowerSearch);
        } else {
            return content.toLowerCase().contains(lowerSearch)
                || sender.toLowerCase().contains(lowerSearch);
        }
    }

    private static void setStageSize(Stage stage) {
        stage.setX(200);
        stage.setY(150);
        stage.setWidth(900);
        stage.setHeight(600);
    }
}
