package com.funkylogclient;

import java.util.ArrayList;

import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.layout.HBox;
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

        Region topLinedSpacing = new Region();
        topLinedSpacing.setMinHeight(15);
        topLinedSpacing
                .setStyle("-fx-border-width: 1px; -fx-border-color: transparent transparent #404040 transparent");
        networkTablesSettings.add(topLinedSpacing);

        Region topSpacing = new Region();
        topSpacing.setMinHeight(15);
        networkTablesSettings.add(topSpacing);

        Text ntTitle = new Text("NetworkTables:");
        ntTitle.setStyle("-fx-font-size: 16px; -fx-fill: #FFFFFF; -fx-font-family: 'Segoe UI', 'Roboto', sans-serif;");
        networkTablesSettings.add(ntTitle);

        VBox statusContainer = new VBox(5);
        statusContainer.setStyle("-fx-background-color: #1A1A1A; -fx-background-radius: 5; -fx-padding: 8;");

        HBox statusRow = new HBox(8);
        statusRow.setAlignment(Pos.CENTER_LEFT);

        statusDot = new Circle(6, Color.RED);
        statusDot.setStyle("-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.3), 2, 0, 0, 0);");

        statusText = new Text("Disconnected");
        statusText
                .setStyle("-fx-font-size: 12px; -fx-fill: #CCCCCC; -fx-font-family: 'Segoe UI', 'Roboto', sans-serif;");

        statusRow.getChildren().addAll(statusDot, statusText);
        statusContainer.getChildren().add(statusRow);

        latencyText = new Text("Latency: 0ms");
        latencyText
                .setStyle("-fx-font-size: 11px; -fx-fill: #AAAAAA; -fx-font-family: 'Segoe UI', 'Roboto', sans-serif;");
        statusContainer.getChildren().add(latencyText);

        Text serverText = new Text("Server: " + UDPClient.serverIP);
        serverText
                .setStyle("-fx-font-size: 11px; -fx-fill: #AAAAAA; -fx-font-family: 'Segoe UI', 'Roboto', sans-serif;");
        statusContainer.getChildren().add(serverText);

        networkTablesSettings.add(statusContainer);

        HBox buttonContainer = new HBox(5);
        buttonContainer.setAlignment(Pos.CENTER);

        Button connectButton = new Button("Connect");
        connectButton.setStyle(Styles.SMALL_BUTTON_STYLE);
        connectButton.setMaxWidth(Double.MAX_VALUE);
        connectButton.setOnAction(connectButtonListener);
        connectButton.setOnMouseEntered(e -> connectButton.setStyle(Styles.SMALL_BUTTON_HOVER_STYLE));
        connectButton.setOnMouseExited(e -> connectButton.setStyle(Styles.SMALL_BUTTON_STYLE));

        Button disconnectButton = new Button("Disconnect");
        disconnectButton.setStyle(Styles.SMALL_BUTTON_STYLE);
        disconnectButton.setMaxWidth(Double.MAX_VALUE);
        disconnectButton.setOnAction(disconnectButtonListener);
        disconnectButton.setOnMouseEntered(e -> disconnectButton.setStyle(Styles.SMALL_BUTTON_HOVER_STYLE));
        disconnectButton.setOnMouseExited(e -> disconnectButton.setStyle(Styles.SMALL_BUTTON_STYLE));

        buttonContainer.getChildren().addAll(connectButton, disconnectButton);
        networkTablesSettings.add(buttonContainer);

        return networkTablesSettings;
    }

    public static void updateStatus(boolean connected, String status, double latency) {
        if (statusDot != null) {
            statusDot.setFill(connected ? Color.GREEN : Color.RED);
        }
        if (statusText != null) {
            statusText.setText(status);
        }
        if (latencyText != null) {
            latencyText.setText(String.format("Latency: %.1fms", latency));
        }
    }
}