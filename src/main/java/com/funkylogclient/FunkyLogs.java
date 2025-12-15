package com.funkylogclient;

import java.util.ArrayList;
import java.util.List;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.geometry.*;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.image.Image;
import javafx.scene.control.Button;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.scene.Cursor;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.util.Callback;
import javafx.animation.FadeTransition;
import javafx.animation.ScaleTransition;
import javafx.animation.ParallelTransition;
import javafx.util.Duration;

public class FunkyLogs extends Application {

    public static final String APP_NAME = "FunkyLogs v2.0.8";

    private StackPane rootStack;
    private BorderPane root;
    private static Dashboard dashboard;
    private double savedX, savedY, savedWidth, savedHeight;
    private boolean isAnimating = false;

    private double xl = 100;
    private double xr = 1100;
    private double yu = 70;
    private double yd = 650;

    private static ListView<Message> messageListView;
    private static ObservableList<Message> messageList;

    private static boolean auto_scroll = true;

    private static String serverIP = UDPClient.serverIP;
    private static int port = UDPClient.port;


    private static long lastKnownVersion = -1;

    @Override
    public void start(Stage primaryStage) {
        UDPClient.start();
        FunkyLogSorter.makeNewLogFile();
        primaryStage.setTitle(APP_NAME);
        
        savedX = primaryStage.getX();
        savedY = primaryStage.getY();
        savedWidth = primaryStage.getWidth();
        savedHeight = primaryStage.getHeight();
        
        primaryStage.iconifiedProperty().addListener((obs, wasIconified, isIconified) -> {
            if (!isIconified && wasIconified) {
                animateRestoreFromMinimized(primaryStage);
            }
            if (isIconified && !wasIconified) {
                if (!primaryStage.isMaximized()) {
                    savedX = primaryStage.getX();
                    savedY = primaryStage.getY();
                    savedWidth = primaryStage.getWidth();
                    savedHeight = primaryStage.getHeight();
                }
            }
        });
        
        primaryStage.maximizedProperty().addListener((obs, wasMaximized, isMaximized) -> {
            if (!isMaximized && wasMaximized) {
                if (savedX <= 0 || savedY <= 0 || savedWidth <= 0 || savedHeight <= 0) {
                    savedX = xl;
                    savedY = yu;
                    savedWidth = Math.max(1000, xr - xl);
                    savedHeight = Math.max(580, yd - yu);
                }
            }
        });

        try {
            Image logoImage = new Image(getClass().getResource("logo846.png").toExternalForm());
            primaryStage.getIcons().add(logoImage);
        } catch (Exception e) {
            System.err.println("Failed to load application icon: " + e.getMessage());
        }

        rootStack = new StackPane();
        rootStack.getStyleClass().add("root");
        
        root = new BorderPane();
        rootStack.getChildren().add(root);
        
        NotificationManager.getInstance().init(rootStack);

        root.setTop(createUtilityBar(primaryStage));

        TabPane tabPane = new TabPane();
        tabPane.setStyle(
                "-fx-background-color: " + Styles.BG_DARKEST + "; -fx-border-color: " + Styles.BORDER_DARK + "; -fx-border-width: 1px; -fx-tab-min-width: 80px; -fx-tab-min-height: 24px; -fx-tab-max-height: 24px; -fx-control-inner-background: " + Styles.BG_DARKEST + "; -fx-background-insets: 0; -fx-tab-area-background: " + Styles.BG_DARKEST + "; -fx-tab-header-background: " + Styles.BG_DARKEST + "; -fx-tab-header-area-background: " + Styles.BG_DARKEST + "; -fx-content-area-background: " + Styles.BG_DARKEST + "; -fx-focus-color: transparent; -fx-faint-focus-color: transparent; -fx-tab-header-area-spacing: 20px;");
        tabPane.getStyleClass().add("tab-pane");

        Tab logsTab = new Tab("Logs");
        logsTab.setClosable(false);
        logsTab.setStyle(
                "-fx-background-color: #404040; -fx-text-fill: #E0E0E0; -fx-padding: 0px 2px; -fx-font-size: 11px; -fx-font-family: 'Segoe UI', 'Roboto', sans-serif;");

        VBox logsContent = new VBox();
        logsContent.setStyle(Styles.CENTER);
        logsContent.setPadding(new Insets(10, 10, 10, 10));

        VBox centerSearch = new VBox();
        centerSearch.setStyle(Styles.SEARCH_CONTAINER_STYLE);

        HBox withLabelToo = new HBox(15);
        withLabelToo.setAlignment(Pos.CENTER_LEFT);

        Label searchLabel = new Label("Search:");
        searchLabel.setStyle(Styles.LABEL_MED + Styles.BOLD_TEXT);

        TextField searchBar = new TextField();
        searchBar.setStyle(Styles.SEARCH_BAR_STYLE);
        searchBar.setPromptText("Search logs...");

        searchBar.textProperty().addListener((observable, prevValue, newValue) -> {
            FunkyLogSorter.changeSearchTerm(newValue);
        });

        searchBar.focusedProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue) {
                searchBar.setStyle(Styles.SEARCH_BAR_FOCUSED_STYLE);
            } else {
                searchBar.setStyle(Styles.SEARCH_BAR_STYLE);
            }
        });

        Region searchSpacer = new Region();
        HBox.setHgrow(searchSpacer, Priority.ALWAYS);
        HBox.setHgrow(searchBar, Priority.ALWAYS);
        withLabelToo.getChildren().addAll(searchLabel, searchBar, searchSpacer);
        centerSearch.getChildren().add(withLabelToo);
        logsContent.getChildren().add(centerSearch);

        messageList = FXCollections.observableArrayList();
        messageListView = new ListView<>(messageList);
        messageListView.setStyle("-fx-background-color: " + Styles.BG_DARKEST + "; -fx-border-color: transparent;");
        messageListView.setFixedCellSize(-1);

        messageListView.setCellFactory(new Callback<ListView<Message>, ListCell<Message>>() {
            @Override
            public ListCell<Message> call(ListView<Message> listView) {
                return new MessageListCell();
            }
        });

        VBox.setVgrow(messageListView, Priority.ALWAYS);

        logsContent.getChildren().add(messageListView);
        logsTab.setContent(logsContent);

        Tab dashboardTab = new Tab("Dashboard");
        dashboardTab.setClosable(false);
        dashboardTab.setStyle(
                "-fx-background-color: #404040; -fx-text-fill: #E0E0E0; -fx-padding: 0px 2px; -fx-font-size: 11px; -fx-font-family: 'Segoe UI', 'Roboto', sans-serif;");

        dashboard = new Dashboard();
        dashboardTab.setContent(dashboard.getContainer());

        tabPane.getTabs().addAll(logsTab, dashboardTab);

        root.setCenter(tabPane);

        String[] activeTab = { "Logs" };
        VBox sidebar = RightSidebar.getRightSidebar(getClass().getResource("logo.png"),
                getClass().getResource("exit.png"), primaryStage,
                (observable, prev, value) -> {
                    FunkyLogs.auto_scroll = value;
                },
                (observable, prev, value) -> {
                    FunkyLogs.serverIP = value;
                },
                (observable, prev, value) -> {
                    FunkyLogs.port = Integer.parseInt(value);
                },
                (ev) -> {
                    UDPClient.setConnectionAddress(FunkyLogs.serverIP, FunkyLogs.port);
                }, activeTab[0]);

        tabPane.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue != null) {
                activeTab[0] = newValue.getText();
                VBox newSidebar = RightSidebar.getRightSidebar(getClass().getResource("logo.png"),
                        getClass().getResource("exit.png"), primaryStage,
                        (observable2, prev, value) -> {
                            FunkyLogs.auto_scroll = value;
                        },
                        (observable2, prev, value) -> {
                            FunkyLogs.serverIP = value;
                        },
                        (observable2, prev, value) -> {
                            FunkyLogs.port = Integer.parseInt(value);
                        },
                        (ev) -> {
                            UDPClient.setConnectionAddress(FunkyLogs.serverIP, FunkyLogs.port);
                        }, activeTab[0]);
                root.setRight(newSidebar);
            }
        });

        root.setRight(sidebar);

        Scene scene = new Scene(rootStack, Color.TRANSPARENT);
        scene.getStylesheets().add(getClass().getResource("dark-theme.css").toExternalForm());
        primaryStage.initStyle(StageStyle.TRANSPARENT);
        primaryStage.setScene(scene);
        primaryStage.setOnCloseRequest(event -> {
            if (dashboard != null) {
                dashboard.shutdown();
            }
        });
        primaryStage.show();

        setStageSize(primaryStage);

        rootStack.setStyle(
                "-fx-background-radius: 8; -fx-background-color: " + Styles.BG_DARKEST + "; -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.3), 20, 0, 0, 0);");
        
        javafx.scene.shape.Rectangle clip = new javafx.scene.shape.Rectangle();
        clip.widthProperty().bind(rootStack.widthProperty());
        clip.heightProperty().bind(rootStack.heightProperty());
        clip.setArcWidth(16);
        clip.setArcHeight(16);
        rootStack.setClip(clip);
        
        createResizeRegions(rootStack, primaryStage);

        Task<Void> updateTask = new Task<Void>() {
            @Override
            protected Void call() throws Exception {
                for (;;) {
                    if (isCancelled()) {
                        break;
                    }
                    Thread.sleep(150);
                    try {
                        long currentVersion = FunkyLogSorter.getFilterVersion();
                        if (currentVersion != lastKnownVersion) {
                            lastKnownVersion = currentVersion;
                            FunkyLogs.updateMessageList();
                        }

                        if (!FunkyLogSorter.errors.isEmpty()) {
                            List<Message> errorsCopy = new ArrayList<>(FunkyLogSorter.errors);
                            FunkyLogSorter.errors.clear();
                            Platform.runLater(() -> {
                                for (Message x : errorsCopy) {
                                    NotificationManager.getInstance().showError(x.getSender(), x.getContent());
                                }
                            });
                        }
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

    private static void updateMessageList() {
        Platform.runLater(() -> {
            java.util.List<Message> snapshot = FunkyLogSorter.getFilteredSnapshot();
            
            int currentSize = messageList.size();
            int newSize = snapshot.size();
            
            if (newSize == 0) {
                messageList.clear();
                return;
            }
            
            if (currentSize == 0 || newSize < currentSize) {
                messageList.setAll(snapshot);
            } else {
                for (int i = currentSize; i < newSize; i++) {
                    messageList.add(snapshot.get(i));
                }
            }
            
            if (auto_scroll && newSize > 0) {
                messageListView.scrollTo(newSize - 1);
            }
        });
    }

    private void setStageSize(Stage stage) {
        stage.setX(xl);
        stage.setY(yu);

        stage.setWidth(Math.max(1000, xr - xl));
        stage.setHeight(Math.max(580, yd - yu));
    }

    private void createResizeRegions(StackPane rootStack, Stage stage) {
        final int resizeZone = 8;
        final int scrollbarWidth = 15;
        final double[] startX = new double[1];
        final double[] startY = new double[1];
        final double[] startWidth = new double[1];
        final double[] startHeight = new double[1];
        final double[] startStageX = new double[1];
        final double[] startStageY = new double[1];
        final boolean[] isResizing = new boolean[1];
        final Cursor[] resizeType = new Cursor[1];

        Region topEdge = new Region();
        topEdge.setStyle("-fx-background-color: transparent;");
        topEdge.setMinHeight(resizeZone);
        topEdge.setMaxHeight(resizeZone);
        topEdge.setPickOnBounds(true);
        topEdge.setMouseTransparent(false);
        topEdge.setCursor(Cursor.N_RESIZE);
        topEdge.prefWidthProperty().bind(rootStack.widthProperty());

        Region bottomEdge = new Region();
        bottomEdge.setStyle("-fx-background-color: transparent;");
        bottomEdge.setMinHeight(resizeZone);
        bottomEdge.setMaxHeight(resizeZone);
        bottomEdge.setPickOnBounds(true);
        bottomEdge.setMouseTransparent(false);
        bottomEdge.setCursor(Cursor.S_RESIZE);
        bottomEdge.prefWidthProperty().bind(rootStack.widthProperty());

        Region leftEdge = new Region();
        leftEdge.setStyle("-fx-background-color: transparent;");
        leftEdge.setMinWidth(resizeZone);
        leftEdge.setMaxWidth(resizeZone);
        leftEdge.setPickOnBounds(true);
        leftEdge.setMouseTransparent(false);
        leftEdge.setCursor(Cursor.W_RESIZE);
        leftEdge.prefHeightProperty().bind(rootStack.heightProperty().subtract(resizeZone * 2));
        leftEdge.setMinHeight(0);

        Region rightEdge = new Region();
        rightEdge.setStyle("-fx-background-color: transparent;");
        rightEdge.setMinWidth(resizeZone);
        rightEdge.setMaxWidth(resizeZone);
        rightEdge.setPrefWidth(resizeZone);
        rightEdge.setPickOnBounds(true);
        rightEdge.setMouseTransparent(false);
        rightEdge.setCursor(Cursor.E_RESIZE);
        rightEdge.prefHeightProperty().bind(rootStack.heightProperty().subtract(resizeZone * 2));
        rightEdge.setMinHeight(0);
        rightEdge.setMaxHeight(Double.MAX_VALUE);
        rightEdge.setOnMouseMoved(event -> {
            double sceneX = event.getSceneX();
            double stageWidth = stage.getWidth();
            if (sceneX > stageWidth - scrollbarWidth) {
                rightEdge.setCursor(Cursor.DEFAULT);
            } else {
                rightEdge.setCursor(Cursor.E_RESIZE);
            }
        });

        Region topLeftCorner = new Region();
        topLeftCorner.setStyle("-fx-background-color: transparent;");
        topLeftCorner.setMinSize(resizeZone, resizeZone);
        topLeftCorner.setMaxSize(resizeZone, resizeZone);
        topLeftCorner.setPickOnBounds(true);
        topLeftCorner.setMouseTransparent(false);
        topLeftCorner.setCursor(Cursor.NW_RESIZE);

        Region topRightCorner = new Region();
        topRightCorner.setStyle("-fx-background-color: transparent;");
        topRightCorner.setMinSize(resizeZone, resizeZone);
        topRightCorner.setMaxSize(resizeZone, resizeZone);
        topRightCorner.setPickOnBounds(true);
        topRightCorner.setMouseTransparent(false);
        topRightCorner.setCursor(Cursor.NE_RESIZE);

        Region bottomLeftCorner = new Region();
        bottomLeftCorner.setStyle("-fx-background-color: transparent;");
        bottomLeftCorner.setMinSize(resizeZone, resizeZone);
        bottomLeftCorner.setMaxSize(resizeZone, resizeZone);
        bottomLeftCorner.setPickOnBounds(true);
        bottomLeftCorner.setMouseTransparent(false);
        bottomLeftCorner.setCursor(Cursor.SW_RESIZE);

        Region bottomRightCorner = new Region();
        bottomRightCorner.setStyle("-fx-background-color: transparent;");
        bottomRightCorner.setMinSize(resizeZone, resizeZone);
        bottomRightCorner.setMaxSize(resizeZone, resizeZone);
        bottomRightCorner.setPickOnBounds(true);
        bottomRightCorner.setMouseTransparent(false);
        bottomRightCorner.setCursor(Cursor.SE_RESIZE);
        bottomRightCorner.setOnMouseMoved(event -> {
            double x = event.getX();
            double width = bottomRightCorner.getWidth();
            if (x > width - scrollbarWidth) {
                bottomRightCorner.setCursor(Cursor.DEFAULT);
            } else {
                bottomRightCorner.setCursor(Cursor.SE_RESIZE);
            }
        });

        javafx.scene.layout.HBox topBox = new javafx.scene.layout.HBox();
        topBox.getChildren().addAll(topLeftCorner, topEdge, topRightCorner);
        topBox.setAlignment(Pos.TOP_LEFT);

        javafx.scene.layout.HBox bottomBox = new javafx.scene.layout.HBox();
        bottomBox.getChildren().addAll(bottomLeftCorner, bottomEdge, bottomRightCorner);
        bottomBox.setAlignment(Pos.BOTTOM_LEFT);

        javafx.scene.layout.VBox leftBox = new javafx.scene.layout.VBox();
        leftBox.getChildren().addAll(leftEdge);
        leftBox.setAlignment(Pos.CENTER_LEFT);
        leftBox.setPadding(new Insets(resizeZone, 0, resizeZone, 0));

        javafx.scene.layout.VBox rightBox = new javafx.scene.layout.VBox();
        rightBox.getChildren().addAll(rightEdge);
        rightBox.setAlignment(Pos.CENTER_RIGHT);
        rightBox.setPadding(new Insets(resizeZone, 0, resizeZone, 0));
        VBox.setVgrow(rightEdge, Priority.ALWAYS);

        javafx.scene.layout.BorderPane resizePane = new javafx.scene.layout.BorderPane();
        resizePane.setTop(topBox);
        resizePane.setBottom(bottomBox);
        resizePane.setLeft(leftBox);
        resizePane.setRight(rightBox);
        resizePane.setMouseTransparent(true);
        resizePane.setPickOnBounds(false);
        resizePane.prefWidthProperty().bind(rootStack.widthProperty());
        resizePane.prefHeightProperty().bind(rootStack.heightProperty());
        resizePane.setManaged(false);
        
        topBox.setMouseTransparent(false);
        bottomBox.setMouseTransparent(false);
        leftBox.setMouseTransparent(false);
        rightBox.setMouseTransparent(false);

        rootStack.getChildren().add(resizePane);
        Platform.runLater(() -> {
            resizePane.toFront();
        });

        javafx.event.EventHandler<javafx.scene.input.MouseEvent> setupResizeHandler = (event) -> {
            Cursor cursor = ((Region) event.getSource()).getCursor();
            if (cursor != Cursor.DEFAULT) {
                startX[0] = event.getSceneX();
                startY[0] = event.getSceneY();
                startWidth[0] = stage.getWidth();
                startHeight[0] = stage.getHeight();
                startStageX[0] = stage.getX();
                startStageY[0] = stage.getY();
                resizeType[0] = cursor;
                isResizing[0] = true;
                event.consume();
            }
        };

        topEdge.setOnMousePressed(setupResizeHandler);
        bottomEdge.setOnMousePressed(setupResizeHandler);
        leftEdge.setOnMousePressed(setupResizeHandler);
        rightEdge.setOnMousePressed(e -> {
            double sceneX = e.getSceneX();
            double stageWidth = stage.getWidth();
            if (sceneX <= stageWidth - scrollbarWidth) {
                setupResizeHandler.handle(e);
            }
        });
        topLeftCorner.setOnMousePressed(setupResizeHandler);
        topRightCorner.setOnMousePressed(setupResizeHandler);
        bottomLeftCorner.setOnMousePressed(setupResizeHandler);
        bottomRightCorner.setOnMousePressed(e -> {
            double sceneX = e.getSceneX();
            double stageWidth = stage.getWidth();
            if (sceneX <= stageWidth - scrollbarWidth) {
                setupResizeHandler.handle(e);
            }
        });

        javafx.event.EventHandler<javafx.scene.input.MouseEvent> dragHandler = (event) -> {
            if (!isResizing[0])
                return;

            event.consume();
            double currentX = event.getSceneX();
            double currentY = event.getSceneY();
            double deltaX = currentX - startX[0];
            double deltaY = currentY - startY[0];

            double minWidth = 1000;
            double minHeight = 580;

            if (resizeType[0] == Cursor.SE_RESIZE) {
                double newWidth = Math.max(minWidth, startWidth[0] + deltaX);
                double newHeight = Math.max(minHeight, startHeight[0] + deltaY);
                stage.setWidth(newWidth);
                stage.setHeight(newHeight);
            } else if (resizeType[0] == Cursor.SW_RESIZE) {
                double newWidth = Math.max(minWidth, startWidth[0] - deltaX);
                double newHeight = Math.max(minHeight, startHeight[0] + deltaY);
                if (newWidth != startWidth[0]) {
                    stage.setX(startStageX[0] + (startWidth[0] - newWidth));
                    stage.setWidth(newWidth);
                }
                if (newHeight != startHeight[0]) {
                    stage.setHeight(newHeight);
                }
            } else if (resizeType[0] == Cursor.NE_RESIZE) {
                double newWidth = Math.max(minWidth, startWidth[0] + deltaX);
                double newHeight = Math.max(minHeight, startHeight[0] - deltaY);
                if (newWidth != startWidth[0]) {
                    stage.setWidth(newWidth);
                }
                if (newHeight != startHeight[0]) {
                    stage.setY(startStageY[0] + (startHeight[0] - newHeight));
                    stage.setHeight(newHeight);
                }
            } else if (resizeType[0] == Cursor.NW_RESIZE) {
                double newWidth = Math.max(minWidth, startWidth[0] - deltaX);
                double newHeight = Math.max(minHeight, startHeight[0] - deltaY);
                if (newWidth != startWidth[0]) {
                    stage.setX(startStageX[0] + (startWidth[0] - newWidth));
                    stage.setWidth(newWidth);
                }
                if (newHeight != startHeight[0]) {
                    stage.setY(startStageY[0] + (startHeight[0] - newHeight));
                    stage.setHeight(newHeight);
                }
            } else if (resizeType[0] == Cursor.E_RESIZE) {
                double newWidth = Math.max(minWidth, startWidth[0] + deltaX);
                stage.setWidth(newWidth);
            } else if (resizeType[0] == Cursor.W_RESIZE) {
                double newWidth = Math.max(minWidth, startWidth[0] - deltaX);
                if (newWidth != startWidth[0]) {
                    stage.setX(startStageX[0] + (startWidth[0] - newWidth));
                    stage.setWidth(newWidth);
                }
            } else if (resizeType[0] == Cursor.S_RESIZE) {
                double newHeight = Math.max(minHeight, startHeight[0] + deltaY);
                stage.setHeight(newHeight);
            } else if (resizeType[0] == Cursor.N_RESIZE) {
                double newHeight = Math.max(minHeight, startHeight[0] - deltaY);
                if (newHeight != startHeight[0]) {
                    stage.setY(startStageY[0] + (startHeight[0] - newHeight));
                    stage.setHeight(newHeight);
                }
            }
        };

        javafx.event.EventHandler<javafx.scene.input.MouseEvent> releaseHandler = (event) -> {
            if (isResizing[0]) {
                isResizing[0] = false;
                resizeType[0] = null;
            }
        };

        topEdge.setOnMouseDragged(dragHandler);
        bottomEdge.setOnMouseDragged(dragHandler);
        leftEdge.setOnMouseDragged(dragHandler);
        rightEdge.setOnMouseDragged(dragHandler);
        topLeftCorner.setOnMouseDragged(dragHandler);
        topRightCorner.setOnMouseDragged(dragHandler);
        bottomLeftCorner.setOnMouseDragged(dragHandler);
        bottomRightCorner.setOnMouseDragged(dragHandler);

        topEdge.setOnMouseReleased(releaseHandler);
        bottomEdge.setOnMouseReleased(releaseHandler);
        leftEdge.setOnMouseReleased(releaseHandler);
        rightEdge.setOnMouseReleased(releaseHandler);
        topLeftCorner.setOnMouseReleased(releaseHandler);
        topRightCorner.setOnMouseReleased(releaseHandler);
        bottomLeftCorner.setOnMouseReleased(releaseHandler);
        bottomRightCorner.setOnMouseReleased(releaseHandler);
        
        rootStack.setOnMouseDragged(dragHandler);
        rootStack.setOnMouseReleased(releaseHandler);
    }

    private HBox createUtilityBar(Stage primaryStage) {
        HBox utilityBar = new HBox(10);
        utilityBar.setPadding(new Insets(8, 15, 5, 15));
        utilityBar.setStyle(
                "-fx-background-color: #2A2A2A; -fx-background-radius: 8 8 0 0; -fx-border-color: transparent transparent #404040 transparent; -fx-border-width: 0 0 1px 0;");

        Button closeButton = createWindowsButton("✕", true, () -> {
            if (dashboard != null) {
                dashboard.shutdown();
            }
            animateClose(primaryStage);
        });
        Button minimizeButton = createWindowsButton("—", false, () -> animateMinimize(primaryStage));
        Button maximizeButton = createMaximizeWindowsButton(primaryStage);

        Label appNameLabel = new Label(APP_NAME);
        appNameLabel.setStyle(
                "-fx-text-fill: #E0E0E0; -fx-font-size: 15px; -fx-font-family: 'Segoe UI', 'Roboto', sans-serif; -fx-font-weight: bold;");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        utilityBar.getChildren().addAll(appNameLabel, spacer, minimizeButton, maximizeButton, closeButton);

        enableDragging(primaryStage, utilityBar);

        return utilityBar;
    }

    private void enableDragging(Stage stage, HBox utilityBar) {
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

        Label appNameLabel = (Label) utilityBar.getChildren().get(0);
        appNameLabel.setOnMousePressed(event -> {
            xOffset[0] = event.getSceneX();
            yOffset[0] = event.getSceneY();
            isDragging[0] = false;
        });

        appNameLabel.setOnMouseDragged(event -> {
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

        appNameLabel.setOnMouseReleased(event -> {
            isDragging[0] = false;
        });
    }

    private Button createWindowsButton(String symbol, boolean isCloseButton, Runnable action) {
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

    private Button createMaximizeWindowsButton(Stage primaryStage) {
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
            if (primaryStage.isMaximized()) {
                animateRestore(primaryStage, button);
            } else {
                animateMaximize(primaryStage, button);
            }
        });
        
        primaryStage.maximizedProperty().addListener((obs, wasMaximized, isMaximized) -> {
            button.setText(isMaximized ? "❐" : "□");
        });

        return button;
    }

    private void animateMinimize(Stage stage) {
        if (isAnimating) return;
        isAnimating = true;
        
        savedX = stage.getX();
        savedY = stage.getY();
        savedWidth = stage.getWidth();
        savedHeight = stage.getHeight();
        
        FadeTransition fadeOut = new FadeTransition(Duration.millis(200), rootStack);
        fadeOut.setFromValue(1.0);
        fadeOut.setToValue(0.0);
        
        ScaleTransition scaleDown = new ScaleTransition(Duration.millis(200), rootStack);
        scaleDown.setFromX(1.0);
        scaleDown.setFromY(1.0);
        scaleDown.setToX(0.85);
        scaleDown.setToY(0.85);
        
        ParallelTransition minimizeAnim = new ParallelTransition(fadeOut, scaleDown);
        minimizeAnim.setOnFinished(e -> {
            stage.setIconified(true);
            rootStack.setOpacity(1.0);
            rootStack.setScaleX(1.0);
            rootStack.setScaleY(1.0);
            isAnimating = false;
        });
        minimizeAnim.play();
    }

    private void animateRestore(Stage stage, Button button) {
        if (isAnimating) return;
        isAnimating = true;
        
        if (savedX <= 0 || savedY <= 0 || savedWidth <= 0 || savedHeight <= 0) {
            savedX = xl;
            savedY = yu;
            savedWidth = Math.max(1000, xr - xl);
            savedHeight = Math.max(580, yd - yu);
        }
        
        double endX = savedX;
        double endY = savedY;
        double endWidth = savedWidth;
        double endHeight = savedHeight;
        
        Region fadeOverlay = new Region();
        fadeOverlay.setStyle("-fx-background-color: rgba(0, 0, 0, 0.2);");
        fadeOverlay.setMouseTransparent(true);
        fadeOverlay.prefWidthProperty().bind(rootStack.widthProperty());
        fadeOverlay.prefHeightProperty().bind(rootStack.heightProperty());
        rootStack.getChildren().add(fadeOverlay);
        fadeOverlay.setOpacity(0.0);
        
        FadeTransition fadeIn = new FadeTransition(Duration.millis(30), fadeOverlay);
        fadeIn.setFromValue(0.0);
        fadeIn.setToValue(1.0);
        fadeIn.setInterpolator(javafx.animation.Interpolator.EASE_OUT);
        
        fadeIn.setOnFinished(e -> {
            stage.setMaximized(false);
            Platform.runLater(() -> {
                stage.setX(endX);
                stage.setY(endY);
                stage.setWidth(endWidth);
                stage.setHeight(endHeight);
                
                FadeTransition fadeOut = new FadeTransition(Duration.millis(120), fadeOverlay);
                fadeOut.setFromValue(1.0);
                fadeOut.setToValue(0.0);
                fadeOut.setInterpolator(javafx.animation.Interpolator.EASE_IN);
                fadeOut.setOnFinished(e2 -> {
                    rootStack.getChildren().remove(fadeOverlay);
                    isAnimating = false;
                });
                fadeOut.play();
            });
        });
        fadeIn.play();
    }
    
    private void animateMaximize(Stage stage, Button button) {
        if (isAnimating) return;
        isAnimating = true;
        
        if (!stage.isMaximized()) {
            savedX = stage.getX();
            savedY = stage.getY();
            savedWidth = stage.getWidth();
            savedHeight = stage.getHeight();
        }
        
        Region fadeOverlay = new Region();
        fadeOverlay.setStyle("-fx-background-color: rgba(0, 0, 0, 0.2);");
        fadeOverlay.setMouseTransparent(true);
        fadeOverlay.prefWidthProperty().bind(rootStack.widthProperty());
        fadeOverlay.prefHeightProperty().bind(rootStack.heightProperty());
        rootStack.getChildren().add(fadeOverlay);
        fadeOverlay.setOpacity(0.0);
        
        FadeTransition fadeIn = new FadeTransition(Duration.millis(30), fadeOverlay);
        fadeIn.setFromValue(0.0);
        fadeIn.setToValue(1.0);
        fadeIn.setInterpolator(javafx.animation.Interpolator.EASE_OUT);
        
        fadeIn.setOnFinished(e -> {
            stage.setMaximized(true);
            
            Platform.runLater(() -> {
                FadeTransition fadeOut = new FadeTransition(Duration.millis(120), fadeOverlay);
                fadeOut.setFromValue(1.0);
                fadeOut.setToValue(0.0);
                fadeOut.setInterpolator(javafx.animation.Interpolator.EASE_IN);
                fadeOut.setOnFinished(e2 -> {
                    rootStack.getChildren().remove(fadeOverlay);
                    isAnimating = false;
                });
                fadeOut.play();
            });
        });
        fadeIn.play();
    }

    private void animateRestoreFromMinimized(Stage stage) {
        if (isAnimating) return;
        isAnimating = true;
        
        rootStack.setOpacity(0.0);
        rootStack.setScaleX(0.85);
        rootStack.setScaleY(0.85);
        
        FadeTransition fadeIn = new FadeTransition(Duration.millis(200), rootStack);
        fadeIn.setFromValue(0.0);
        fadeIn.setToValue(1.0);
        
        ScaleTransition scaleUp = new ScaleTransition(Duration.millis(200), rootStack);
        scaleUp.setFromX(0.85);
        scaleUp.setFromY(0.85);
        scaleUp.setToX(1.0);
        scaleUp.setToY(1.0);
        
        ParallelTransition restoreAnim = new ParallelTransition(fadeIn, scaleUp);
        restoreAnim.setOnFinished(e -> {
            isAnimating = false;
        });
        restoreAnim.play();
    }

    private void animateClose(Stage stage) {
        if (isAnimating) return;
        isAnimating = true;
        
        FadeTransition fadeOut = new FadeTransition(Duration.millis(200), rootStack);
        fadeOut.setFromValue(1.0);
        fadeOut.setToValue(0.0);
        
        ScaleTransition scaleDown = new ScaleTransition(Duration.millis(200), rootStack);
        scaleDown.setFromX(1.0);
        scaleDown.setFromY(1.0);
        scaleDown.setToX(0.9);
        scaleDown.setToY(0.9);
        
        ParallelTransition closeAnim = new ParallelTransition(fadeOut, scaleDown);
        closeAnim.setOnFinished(e -> {
            if (dashboard != null) {
                dashboard.shutdown();
            }
            System.exit(0);
        });
        closeAnim.play();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
