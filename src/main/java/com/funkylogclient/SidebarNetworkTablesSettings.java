package com.funkylogclient;

import java.util.ArrayList;

import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.text.Text;

public class SidebarNetworkTablesSettings {
    private static Circle statusDot;
    private static Text statusText;
    private static Text latencyText;

    public static ArrayList<Node> getSidebarNetworkTablesSettings(
            EventHandler<ActionEvent> connectButtonListener,
            EventHandler<ActionEvent> disconnectButtonListener) {

        ArrayList<Node> networkTablesSettings = new ArrayList<Node>();

        Region topDivider = new Region();
        topDivider.setMinHeight(1);
        topDivider.setPrefHeight(1);
        topDivider.setStyle("-fx-background-color: " + Styles.BORDER_DARK + ";");
        networkTablesSettings.add(topDivider);

        Region topSpacing = new Region();
        topSpacing.setMinHeight(12);
        networkTablesSettings.add(topSpacing);

        Text ntTitle = new Text("NetworkTables");
        ntTitle.setStyle(Styles.SECTION_HEADER_STYLE);
        networkTablesSettings.add(ntTitle);

        Region afterTitleSpace = new Region();
        afterTitleSpace.setMinHeight(8);
        networkTablesSettings.add(afterTitleSpace);

        VBox statusContainer = new VBox(6);
        statusContainer.setStyle("-fx-background-color: " + Styles.BG_DARKEST + "; -fx-background-radius: 8px; -fx-padding: 12px;");

        HBox statusRow = new HBox(10);
        statusRow.setAlignment(Pos.CENTER_LEFT);

        statusDot = new Circle(5, Color.web("#F85149"));
        statusDot.setStyle("-fx-effect: dropshadow(gaussian, rgba(248,81,73,0.4), 6, 0, 0, 0);");

        statusText = new Text("Disconnected");
        statusText.setStyle("-fx-font-size: 13px; -fx-fill: " + Styles.TEXT_PRIMARY + "; -fx-font-weight: bold; -fx-font-family: " + Styles.FONT_FAMILY + ";");

        statusRow.getChildren().addAll(statusDot, statusText);
        statusContainer.getChildren().add(statusRow);

        HBox statsRow = new HBox(16);
        statsRow.setAlignment(Pos.CENTER_LEFT);
        statsRow.setPadding(new Insets(4, 0, 0, 0));

        latencyText = new Text("Latency: --");
        latencyText.setStyle("-fx-font-size: 11px; -fx-fill: " + Styles.TEXT_MUTED + "; -fx-font-family: " + Styles.FONT_FAMILY + ";");

        Text serverText = new Text("→ " + UDPClient.serverIP);
        serverText.setStyle("-fx-font-size: 11px; -fx-fill: " + Styles.TEXT_MUTED + "; -fx-font-family: " + Styles.FONT_FAMILY + ";");

        statsRow.getChildren().addAll(latencyText, serverText);
        statusContainer.getChildren().add(statsRow);

        networkTablesSettings.add(statusContainer);

        Region buttonSpacing = new Region();
        buttonSpacing.setMinHeight(8);
        networkTablesSettings.add(buttonSpacing);

        HBox buttonContainer = new HBox(8);
        buttonContainer.setAlignment(Pos.CENTER);

        Button connectButton = new Button("Connect");
        connectButton.setStyle(Styles.ACCENT_BUTTON_STYLE);
        connectButton.setOnAction(connectButtonListener);
        connectButton.setOnMouseEntered(e -> connectButton.setStyle(Styles.ACCENT_BUTTON_HOVER_STYLE));
        connectButton.setOnMouseExited(e -> connectButton.setStyle(Styles.ACCENT_BUTTON_STYLE));
        HBox.setHgrow(connectButton, Priority.ALWAYS);
        connectButton.setMaxWidth(Double.MAX_VALUE);

        Button disconnectButton = new Button("Disconnect");
        disconnectButton.setStyle(Styles.SMALL_BUTTON_STYLE);
        disconnectButton.setOnAction(disconnectButtonListener);
        disconnectButton.setOnMouseEntered(e -> disconnectButton.setStyle(Styles.SMALL_BUTTON_HOVER_STYLE));
        disconnectButton.setOnMouseExited(e -> disconnectButton.setStyle(Styles.SMALL_BUTTON_STYLE));
        HBox.setHgrow(disconnectButton, Priority.ALWAYS);
        disconnectButton.setMaxWidth(Double.MAX_VALUE);

        buttonContainer.getChildren().addAll(connectButton, disconnectButton);
        networkTablesSettings.add(buttonContainer);

        return networkTablesSettings;
    }

    public static void updateStatus(boolean connected, String status, double latency) {
        if (statusDot != null) {
            if (connected) {
                statusDot.setFill(Color.web(Styles.ACCENT_YELLOW));
                statusDot.setStyle(Styles.STATUS_DOT_CONNECTED);
            } else {
                statusDot.setFill(Color.web(Styles.ACCENT_ERROR));
                statusDot.setStyle(Styles.STATUS_DOT_DISCONNECTED);
            }
        }
        if (statusText != null) {
            statusText.setText(status);
        }
        if (latencyText != null) {
            if (connected) {
                latencyText.setText(String.format("Latency: %.0fms", latency));
            } else {
                latencyText.setText("Latency: --");
            }
        }
    }
}
