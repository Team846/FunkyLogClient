package com.funkylogclient;

import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import java.net.URL;

import javafx.beans.value.ChangeListener;
import javafx.geometry.Insets;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import javafx.stage.Stage;

public class RightSidebar {
        public static VBox getRightSidebar(URL logoURL, URL exitImgURL, Stage primaryStage,
                        ChangeListener<Boolean> autoScrollChangeListener,
                        ChangeListener<? super String> addrFieldChangeListener,
                        ChangeListener<? super String> portFieldChangeListener,
                        EventHandler<ActionEvent> confirmButtonListener) {

                VBox sidebarContent = new VBox();
                sidebarContent.setPadding(new Insets(2, 5, 5, 5));
                sidebarContent.setSpacing(2);
                sidebarContent.setStyle("-fx-background-color: #252525;");

                sidebarContent.getChildren().add(SidebarTopSection.getTopSection(logoURL, exitImgURL, primaryStage));

                Text title = new Text("FunkyLogs");
                title.setStyle(Styles.TEXT_STYLE + Styles.BOLD_TEXT);
                sidebarContent.getChildren().add(title);

                sidebarContent.getChildren().addAll(SidebarFilters.getSidebarFilters());

                sidebarContent.getChildren()
                                .add(SidebarOtherSettings.getSidebarOtherSettings(autoScrollChangeListener,
                                                primaryStage));

                sidebarContent.getChildren()
                                .addAll(SidebarNetworkSettings.getSidebarNetworkSettings(addrFieldChangeListener,
                                                portFieldChangeListener, confirmButtonListener));

                ScrollPane scrollPane = new ScrollPane(sidebarContent);
                scrollPane.setFitToWidth(true);
                scrollPane.setFitToHeight(true);
                scrollPane.setPadding(new Insets(0, 5, 5, 0));
                scrollPane.setStyle(
                                "-fx-background-color: #252525; -fx-border-color: transparent; -fx-control-inner-background: #252525; -fx-background-radius: 0 12 12 0;");
                scrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
                scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
                VBox.setVgrow(scrollPane, Priority.ALWAYS);

                VBox rightSidebar = new VBox();
                rightSidebar.setMinWidth(180);
                rightSidebar.setPrefWidth(220);
                rightSidebar.setMaxWidth(280);
                rightSidebar.setStyle(Styles.RIGHT_SIDEBAR_STYLE);
                rightSidebar.getChildren().add(scrollPane);

                rightSidebar.setOnMouseMoved(event -> {
                        double x = event.getX();
                        double width = rightSidebar.getWidth();
                        if (x > width - 20) {
                                rightSidebar.setCursor(javafx.scene.Cursor.E_RESIZE);
                        } else {
                                rightSidebar.setCursor(javafx.scene.Cursor.DEFAULT);
                        }
                });

                return rightSidebar;
        }
}
