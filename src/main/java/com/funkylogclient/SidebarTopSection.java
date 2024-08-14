package com.funkylogclient;

import java.net.URL;

import javafx.geometry.Pos;
import javafx.scene.effect.ColorAdjust;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.stage.Stage;

public class SidebarTopSection {
    public static HBox getTopSection(URL logoURL, URL exitImgURL, Stage primaryStage) {      
        ImageView logoImage = new ImageView(logoURL.toString());
        logoImage.setStyle(Styles.LOGO_STYLE);

        HBox logoImgBox = new HBox(logoImage);
        logoImgBox.setAlignment(Pos.TOP_LEFT);
        logoImgBox.setPrefWidth(1000);

        HBox exitImgBox = new HBox();
        exitImgBox.setPrefWidth(30);
        exitImgBox.setPrefHeight(20);
        exitImgBox.setMaxHeight(20);

        ImageView exitImage = new ImageView(exitImgURL.toString());

        exitImgBox.getChildren().add(exitImage);

        ColorAdjust normalColorAdjust = new ColorAdjust();
        normalColorAdjust.setBrightness(-0.5);

        ColorAdjust hoverColorAdjust = new ColorAdjust();
        hoverColorAdjust.setBrightness(0.5);

        exitImgBox.setOnMouseClicked((MouseEvent e) -> {
            primaryStage.close();
        });
        exitImgBox.setOnMouseEntered((MouseEvent e) -> {
            exitImage.setEffect(hoverColorAdjust);
        });
        exitImgBox.setOnMouseExited((MouseEvent e) -> {
            exitImage.setEffect(normalColorAdjust);
        });

        HBox topSection = new HBox(10);
        topSection.setAlignment(Pos.TOP_RIGHT);

        topSection.getChildren().addAll(logoImgBox, exitImgBox);

        return topSection;
    }
}
