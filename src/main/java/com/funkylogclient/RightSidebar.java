package com.funkylogclient;

import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import java.net.URL;

import javafx.beans.value.ChangeListener;
import javafx.geometry.Insets;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import javafx.stage.Stage;
import javafx.application.Platform;

public class RightSidebar {
        public static VBox getRightSidebar(URL logoURL, URL exitImgURL, Stage primaryStage,
                        ChangeListener<Boolean> autoScrollChangeListener,
                        ChangeListener<? super String> addrFieldChangeListener,
                        ChangeListener<? super String> portFieldChangeListener,
                        EventHandler<ActionEvent> confirmButtonListener, String activeTab) {

                VBox sidebarContent = new VBox(8);
                sidebarContent.setPadding(new Insets(12, 12, 12, 12));
                sidebarContent.setStyle("-fx-background-color: " + Styles.BG_DARK + ";");

                sidebarContent.getChildren().add(SidebarTopSection.getTopSection(logoURL, exitImgURL, primaryStage));

                Text title = new Text("BananaBits".equals(activeTab) ? "BananaBits" : "Forestry");
                title.setStyle(Styles.LABEL_TITLE);
                sidebarContent.getChildren().add(title);

                Region sectionSpacer = new Region();
                sectionSpacer.setMinHeight(8);
                sidebarContent.getChildren().add(sectionSpacer);

                if ("BananaBits".equals(activeTab)) {
                        sidebarContent.getChildren()
                                        .addAll(SidebarNetworkTablesSettings.getSidebarNetworkTablesSettings(
                                                        (ev) -> {
                                                                NetworkTablesClient.connect();
                                                        },
                                                        (ev) -> {
                                                                NetworkTablesClient.disconnect();
                                                        }));

                        sidebarContent.getChildren().addAll(SidebarNetworkTablesChooser.getNetworkTablesChooser());

                        NetworkTablesClient.connectedProperty().addListener((observable, oldValue, newValue) -> {
                                Platform.runLater(() -> {
                                        SidebarNetworkTablesSettings.updateStatus(
                                                        newValue,
                                                        NetworkTablesClient.getStatusText(),
                                                        NetworkTablesClient.getLatency());
                                });
                        });

                        NetworkTablesClient.statusTextProperty().addListener((observable, oldValue, newValue) -> {
                                Platform.runLater(() -> {
                                        SidebarNetworkTablesSettings.updateStatus(
                                                        NetworkTablesClient.isConnected(),
                                                        newValue,
                                                        NetworkTablesClient.getLatency());
                                });
                        });

                        NetworkTablesClient.latencyProperty().addListener((observable, oldValue, newValue) -> {
                                Platform.runLater(() -> {
                                        SidebarNetworkTablesSettings.updateStatus(
                                                        NetworkTablesClient.isConnected(),
                                                        NetworkTablesClient.getStatusText(),
                                                        newValue.doubleValue());
                                });
                        });
                } else {
                        sidebarContent.getChildren().addAll(SidebarFilters.getSidebarFilters());

                        sidebarContent.getChildren()
                                        .add(SidebarOtherSettings.getSidebarOtherSettings(autoScrollChangeListener,
                                                        primaryStage));

                        sidebarContent.getChildren()
                                        .addAll(SidebarNetworkSettings.getSidebarNetworkSettings(
                                                        addrFieldChangeListener,
                                                        portFieldChangeListener, confirmButtonListener));
                }

                ScrollPane scrollPane = new ScrollPane(sidebarContent);
                scrollPane.setFitToWidth(true);
                scrollPane.setFitToHeight(true);
                scrollPane.setPadding(new Insets(0));
                scrollPane.setStyle(
                                "-fx-background-color: " + Styles.BG_DARK + "; -fx-border-color: transparent; -fx-control-inner-background: " + Styles.BG_DARK + "; -fx-background-radius: 0 8 8 0;");
                scrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
                scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
                VBox.setVgrow(scrollPane, Priority.ALWAYS);

                VBox rightSidebar = new VBox();
                if ("BananaBits".equals(activeTab)) {
                    rightSidebar.setMinWidth(260);
                    rightSidebar.setPrefWidth(300);
                    rightSidebar.setMaxWidth(420);
                } else {
                    rightSidebar.setMinWidth(200);
                    rightSidebar.setPrefWidth(240);
                    rightSidebar.setMaxWidth(300);
                }
                rightSidebar.setStyle(Styles.RIGHT_SIDEBAR_STYLE);
                rightSidebar.getChildren().add(scrollPane);


                return rightSidebar;
        }
}
