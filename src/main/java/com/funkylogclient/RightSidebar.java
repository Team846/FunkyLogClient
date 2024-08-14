package com.funkylogclient;

import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import java.net.URL;

import javafx.beans.value.ChangeListener;
import javafx.geometry.Insets;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import javafx.stage.Stage;

public class RightSidebar {
    public static VBox getRightSidebar(URL logoURL, URL exitImgURL, Stage primaryStage, ChangeListener<Boolean> autoScrollChangeListener,
        ChangeListener<? super String> addrFieldChangeListener, ChangeListener<? super String> portFieldChangeListener, 
            EventHandler<ActionEvent> confirmButtonListener) {
        VBox rightSidebar = new VBox();
        rightSidebar.setPadding(new Insets(5, 5, 10, 5));
        rightSidebar.setPrefWidth(200);
        rightSidebar.setMaxWidth(200);
        rightSidebar.setStyle(Styles.RIGHT_SIDEBAR_STYLE);

        rightSidebar.getChildren().add(SidebarTopSection.getTopSection(logoURL, exitImgURL, primaryStage));

        Text title = new Text("FunkyLogs");
        title.setStyle(Styles.TEXT_STYLE + Styles.BOLD_TEXT);
        rightSidebar.getChildren().add(title);

        rightSidebar.getChildren().addAll(SidebarFilters.getSidebarFilters());

        rightSidebar.getChildren().add(SidebarOtherSettings.getSidebarOtherSettings(autoScrollChangeListener, primaryStage));

        rightSidebar.getChildren().addAll(SidebarNetworkSettings.getSidebarNetworkSettings(addrFieldChangeListener, 
            portFieldChangeListener, confirmButtonListener));

        return rightSidebar;
    }
}
