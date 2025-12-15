package com.funkylogclient;

import java.net.URL;

import javafx.geometry.Pos;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.stage.Stage;

public class SidebarTopSection {
    public static HBox getTopSection(URL logoURL, URL exitImgURL, Stage primaryStage) {      
        ImageView logoImage = new ImageView(logoURL.toString());
        logoImage.setFitWidth(40);
        logoImage.setFitHeight(40);
        logoImage.setPreserveRatio(true);
        logoImage.setSmooth(true);

        HBox logoImgBox = new HBox(logoImage);
        logoImgBox.setAlignment(Pos.CENTER_LEFT);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox topSection = new HBox(10);
        topSection.setAlignment(Pos.CENTER_LEFT);

        topSection.getChildren().addAll(logoImgBox, spacer);

        return topSection;
    }
}
