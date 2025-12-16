package com.funkylogclient;

import java.net.URL;

import javafx.scene.layout.HBox;
import javafx.stage.Stage;

public class SidebarTopSection {
    public static HBox getTopSection(URL logoURL, URL exitImgURL, Stage primaryStage) {
        HBox topSection = new HBox();
        topSection.setMinHeight(0);
        topSection.setPrefHeight(0);
        topSection.setMaxHeight(0);
        return topSection;
    }
}
