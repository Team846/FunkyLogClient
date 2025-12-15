package com.funkylogclient;

import java.util.ArrayList;

import javafx.beans.value.ChangeListener;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;

public class SidebarNetworkSettings {
    public static ArrayList<Node> getSidebarNetworkSettings(ChangeListener<? super String> addrFieldChangeListener,
            ChangeListener<? super String> portFieldChangeListener, EventHandler<ActionEvent> confirmButtonListener) {
        ArrayList<Node> networkSettings = new ArrayList<Node>();

        Region topDivider = new Region();
        topDivider.setMinHeight(1);
        topDivider.setPrefHeight(1);
        topDivider.setStyle("-fx-background-color: " + Styles.BORDER_DARK + ";");
        networkSettings.add(topDivider);

        Region topSpacing = new Region();
        topSpacing.setMinHeight(12);
        networkSettings.add(topSpacing);

        Text nwSettingsLabel = new Text("Server Connection");
        nwSettingsLabel.setStyle(Styles.SECTION_HEADER_STYLE);
        networkSettings.add(nwSettingsLabel);

        Region afterTitleSpace = new Region();
        afterTitleSpace.setMinHeight(8);
        networkSettings.add(afterTitleSpace);

        VBox fieldsContainer = new VBox(8);
        fieldsContainer.setPadding(new Insets(0));

        HBox ipRow = new HBox(8);
        ipRow.setAlignment(Pos.CENTER_LEFT);

        Text ipLabel = new Text("IP:");
        ipLabel.setStyle("-fx-font-size: 12px; -fx-fill: #808080; -fx-font-family: 'Segoe UI', 'Roboto', sans-serif;");
        ipLabel.setWrappingWidth(30);

        TextField addressField = new TextField();
        addressField.setText(UDPClient.serverIP);
        addressField.setStyle(Styles.TEXT_FIELD_STYLE);
        addressField.setPromptText("Server IP");
        addressField.textProperty().addListener(addrFieldChangeListener);
        HBox.setHgrow(addressField, Priority.ALWAYS);

        addressField.focusedProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue) {
                addressField.setStyle(Styles.TEXT_FIELD_FOCUSED_STYLE);
            } else {
                addressField.setStyle(Styles.TEXT_FIELD_STYLE);
            }
        });

        ipRow.getChildren().addAll(ipLabel, addressField);
        fieldsContainer.getChildren().add(ipRow);

        HBox portRow = new HBox(8);
        portRow.setAlignment(Pos.CENTER_LEFT);

        Text portLabel = new Text("Port:");
        portLabel.setStyle("-fx-font-size: 12px; -fx-fill: #808080; -fx-font-family: 'Segoe UI', 'Roboto', sans-serif;");
        portLabel.setWrappingWidth(30);

        TextField portField = new TextField();
        portField.setText("" + UDPClient.port);
        portField.setStyle(Styles.TEXT_FIELD_STYLE);
        portField.setPromptText("Port");
        portField.textProperty().addListener(portFieldChangeListener);
        HBox.setHgrow(portField, Priority.ALWAYS);

        portField.focusedProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue) {
                portField.setStyle(Styles.TEXT_FIELD_FOCUSED_STYLE);
            } else {
                portField.setStyle(Styles.TEXT_FIELD_STYLE);
            }
        });

        portRow.getChildren().addAll(portLabel, portField);
        fieldsContainer.getChildren().add(portRow);

        networkSettings.add(fieldsContainer);

        Region buttonSpacing = new Region();
        buttonSpacing.setMinHeight(8);
        networkSettings.add(buttonSpacing);

        HBox buttonRow = new HBox(8);
        buttonRow.setAlignment(Pos.CENTER_LEFT);

        Button localButton = new Button("Localhost");
        localButton.setStyle(Styles.SMALL_BUTTON_STYLE);
        localButton.setOnMouseEntered(e -> localButton.setStyle(Styles.SMALL_BUTTON_HOVER_STYLE));
        localButton.setOnMouseExited(e -> localButton.setStyle(Styles.SMALL_BUTTON_STYLE));
        localButton.setOnAction(e -> {
            addressField.setText("127.0.0.1");
        });

        Button confirmAddrButton = new Button("Connect");
        confirmAddrButton.setStyle("-fx-background-color: #FF8C00; -fx-text-fill: #FFFFFF; -fx-border-radius: 6px; -fx-background-radius: 6px; -fx-padding: 6 12 6 12; -fx-font-size: 12px; -fx-font-weight: bold; -fx-font-family: 'Segoe UI', 'Roboto', sans-serif; -fx-cursor: hand;");
        confirmAddrButton.setOnAction(confirmButtonListener);

        confirmAddrButton.setOnMouseEntered(e -> confirmAddrButton.setStyle("-fx-background-color: #FFA333; -fx-text-fill: #FFFFFF; -fx-border-radius: 6px; -fx-background-radius: 6px; -fx-padding: 6 12 6 12; -fx-font-size: 12px; -fx-font-weight: bold; -fx-font-family: 'Segoe UI', 'Roboto', sans-serif; -fx-cursor: hand;"));
        confirmAddrButton.setOnMouseExited(e -> confirmAddrButton.setStyle("-fx-background-color: #FF8C00; -fx-text-fill: #FFFFFF; -fx-border-radius: 6px; -fx-background-radius: 6px; -fx-padding: 6 12 6 12; -fx-font-size: 12px; -fx-font-weight: bold; -fx-font-family: 'Segoe UI', 'Roboto', sans-serif; -fx-cursor: hand;"));

        Region buttonSpacer = new Region();
        HBox.setHgrow(buttonSpacer, Priority.ALWAYS);

        buttonRow.getChildren().addAll(localButton, buttonSpacer, confirmAddrButton);
        networkSettings.add(buttonRow);

        return networkSettings;
    }
}
