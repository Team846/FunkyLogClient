package com.funkylogclient;

import java.util.Iterator;
import java.util.LinkedList;

import javafx.animation.PauseTransition;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.geometry.*;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.util.Duration;
import javafx.scene.Cursor;

public class FunkyLogs extends Application {

    public static final String APP_NAME = "FunkyLogs v1.1.4";

    private BorderPane root;

    private double xl = 100;
    private double xr = 1100;
    private double yu = 70;
    private double yd = 650;

    private static VBox messageZone;

    private static boolean auto_scroll = true;

    private static String serverIP = UDPClient.serverIP;
    private static int port = UDPClient.port;

    private static int num_open_alerts = 0;

    @Override
    public void start(Stage primaryStage) {
        UDPClient.start();
        FunkyLogSorter.makeNewLogFile();
        primaryStage.setTitle(APP_NAME);

        root = new BorderPane();
        root.getStyleClass().add("root");

        root.setTop(createUtilityBar(primaryStage));

        TabPane tabPane = new TabPane();
        tabPane.setStyle(
                "-fx-background-color: #1A1A1A; -fx-border-color: #30363D; -fx-border-width: 1px; -fx-tab-min-width: 80px; -fx-tab-min-height: 24px; -fx-tab-max-height: 24px; -fx-control-inner-background: #1A1A1A; -fx-background-insets: 0; -fx-tab-area-background: #1A1A1A; -fx-tab-header-background: #1A1A1A; -fx-tab-header-area-background: #1A1A1A; -fx-content-area-background: #1A1A1A; -fx-focus-color: transparent; -fx-faint-focus-color: transparent; -fx-tab-header-area-spacing: 20px;");
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

        messageZone = new VBox();
        messageZone.setPadding(new Insets(5, 20, 5, 20));
        messageZone.setSpacing(2.0);
        messageZone.setStyle(Styles.SCROLL_PANE_STYLE);

        ScrollPane mScrollPane = new ScrollPane(messageZone);
        mScrollPane.setFitToWidth(true);
        mScrollPane.setFitToHeight(true);
        mScrollPane.setStyle(Styles.SCROLL_PANE_STYLE);
        VBox.setVgrow(mScrollPane, Priority.ALWAYS);

        messageZone.heightProperty().addListener((observable, oldValue, newValue) -> {
            if (FunkyLogs.auto_scroll)
                mScrollPane.setVvalue(1.0);
        });

        logsContent.getChildren().add(mScrollPane);
        logsTab.setContent(logsContent);

        Tab dashboardTab = new Tab("Dashboard");
        dashboardTab.setClosable(false);
        dashboardTab.setStyle(
                "-fx-background-color: #404040; -fx-text-fill: #E0E0E0; -fx-padding: 0px 2px; -fx-font-size: 11px; -fx-font-family: 'Segoe UI', 'Roboto', sans-serif;");

        Dashboard dashboard = new Dashboard();
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

        Scene scene = new Scene(root, Color.TRANSPARENT);
        scene.getStylesheets().add(getClass().getResource("dark-theme.css").toExternalForm());
        primaryStage.initStyle(StageStyle.TRANSPARENT);
        primaryStage.setScene(scene);
        primaryStage.show();

        setStageSize(primaryStage);

        enableResizing(primaryStage, root);

        root.setStyle(
                "-fx-background-radius: 12; -fx-background-color: #1A1A1A; -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.3), 20, 0, 0, 0);");

        Task<Void> updateTask = new Task<Void>() {
            @Override
            protected Void call() throws Exception {
                for (;;) {
                    if (isCancelled()) {
                        break;
                    }
                    Thread.sleep(200);
                    try {
                        FunkyLogs.updateMessageZone(primaryStage);

                        Platform.runLater(() -> {
                            Iterator<Message> iterator = FunkyLogSorter.errors.iterator();
                            while (iterator.hasNext()) {
                                Message x = iterator.next();
                                if (num_open_alerts < 5) {
                                    Alert alert = new Alert(AlertType.ERROR);
                                    alert.setTitle("FunkyLogs Error Notification");
                                    alert.setHeaderText(x.getSender());
                                    alert.setContentText(x.getContent());

                                    alert.setX(alert.getX() + (num_open_alerts * 70));
                                    alert.setY(alert.getY() + (num_open_alerts * 70));

                                    alert.show();

                                    num_open_alerts += 1;

                                    PauseTransition delay = new PauseTransition(Duration.seconds(5));

                                    delay.setOnFinished(event -> {
                                        num_open_alerts--;
                                        alert.close();
                                    });

                                    delay.play();

                                    iterator.remove();
                                }
                            }
                        });
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

    private static void updateMessageZone(Stage stage) {
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
        final int borderWidth = 8;
        final int rightBorderWidth = 20;
        final double[] startX = new double[1];
        final double[] startY = new double[1];
        final double[] startWidth = new double[1];
        final double[] startHeight = new double[1];
        final double[] startStageX = new double[1];
        final double[] startStageY = new double[1];
        final boolean[] isResizing = new boolean[1];
        final Cursor[] resizeType = new Cursor[1];

        root.setOnMouseMoved(event -> {
            if (isResizing[0])
                return;

            double x = event.getX();
            double y = event.getY();
            double width = root.getWidth();
            double height = root.getHeight();

            if (x > width - rightBorderWidth && y > height - borderWidth) {
                root.setCursor(Cursor.SE_RESIZE);
            } else if (x > width - rightBorderWidth) {
                root.setCursor(Cursor.E_RESIZE);
            } else if (y > height - borderWidth) {
                root.setCursor(Cursor.S_RESIZE);
            } else {
                root.setCursor(Cursor.DEFAULT);
            }
        });

        root.setOnMousePressed(event -> {
            Cursor cursor = root.getCursor();
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
        });

        root.setOnMouseDragged(event -> {
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
                double newWidthSE = Math.max(minWidth, startWidth[0] + deltaX);
                double newHeightSE = Math.max(minHeight, startHeight[0] + deltaY);
                stage.setWidth(newWidthSE);
                stage.setHeight(newHeightSE);
            } else if (resizeType[0] == Cursor.E_RESIZE) {
                double newWidthE = Math.max(minWidth, startWidth[0] + deltaX);
                stage.setWidth(newWidthE);
            } else if (resizeType[0] == Cursor.S_RESIZE) {
                double newHeightS = Math.max(minHeight, startHeight[0] + deltaY);
                stage.setHeight(newHeightS);
            }
        });

        root.setOnMouseReleased(event -> {
            isResizing[0] = false;
            resizeType[0] = null;
        });

        root.setOnMouseExited(event -> {
            if (!isResizing[0]) {
                root.setCursor(Cursor.DEFAULT);
            }
        });
    }

    private HBox createUtilityBar(Stage primaryStage) {
        HBox utilityBar = new HBox(10);
        utilityBar.setPadding(new Insets(8, 15, 5, 15));
        utilityBar.setStyle(
                "-fx-background-color: #2A2A2A; -fx-background-radius: 12 12 0 0; -fx-border-color: transparent transparent #404040 transparent; -fx-border-width: 0 0 1px 0;");

        Circle closeButton = createUtilityButton(true, () -> System.exit(0));
        Circle minimizeButton = createUtilityButton(false, () -> primaryStage.setIconified(true));
        Circle maximizeButton = createMaximizeButton(primaryStage);

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

    private Circle createUtilityButton(boolean isCloseButton, Runnable action) {
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

    private Circle createMaximizeButton(Stage primaryStage) {
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
            if (primaryStage.isMaximized()) {
                primaryStage.setMaximized(false);
            } else {
                primaryStage.setMaximized(true);
            }
        });
        return button;
    }

    public static void main(String[] args) {
        launch(args);
    }
}
