package com.funkylogclient;

import java.util.ArrayList;

import javafx.beans.value.ChangeListener;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.text.Text;

public class SidebarNetworkSettings {
    public static ArrayList<Node> getSidebarNetworkSettings(ChangeListener<? super String> addrFieldChangeListener,
            ChangeListener<? super String> portFieldChangeListener, EventHandler<ActionEvent> confirmButtonListener) {
        ArrayList<Node> networkSettings = new ArrayList<Node>();

        Region topLinedSpacing = new Region();
        topLinedSpacing.setMinHeight(15);
        topLinedSpacing
                .setStyle("-fx-border-width: 1px; -fx-border-color: transparent transparent #404040 transparent");
        networkSettings.add(topLinedSpacing);

        Region topSpacing = new Region();
        topSpacing.setMinHeight(15);
        networkSettings.add(topSpacing);

        HBox serverLabelContainer = new HBox(8);
        serverLabelContainer.setAlignment(Pos.CENTER_LEFT);

        Text nwSettingsLabel = new Text("Server:");
        nwSettingsLabel
                .setStyle("-fx-font-size: 16px; -fx-fill: #FFFFFF; -fx-font-family: 'Segoe UI', 'Roboto', sans-serif;");

        Button localButton = new Button("local");
        localButton.setStyle(Styles.SMALL_BUTTON_STYLE);
        localButton.setOnMouseEntered(e -> localButton.setStyle(Styles.SMALL_BUTTON_HOVER_STYLE));
        localButton.setOnMouseExited(e -> localButton.setStyle(Styles.SMALL_BUTTON_STYLE));

        serverLabelContainer.getChildren().addAll(nwSettingsLabel, localButton);
        networkSettings.add(serverLabelContainer);

        TextField addressField = new TextField();
        addressField.setText(UDPClient.serverIP);
        addressField.setStyle(Styles.TEXT_FIELD_STYLE);
        addressField.setPromptText("Server IP");
        addressField.setMaxWidth(Double.MAX_VALUE);
        addressField.textProperty().addListener(addrFieldChangeListener);

        localButton.setOnAction(e -> {
            addressField.setText("127.0.0.1");
        });

        addressField.focusedProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue) {
                addressField.setStyle(Styles.TEXT_FIELD_FOCUSED_STYLE);
            } else {
                addressField.setStyle(Styles.TEXT_FIELD_STYLE);
            }
        });

        networkSettings.add(addressField);

        TextField portField = new TextField();
        portField.setText("" + UDPClient.port);
        portField.setStyle(Styles.TEXT_FIELD_STYLE);
        portField.setPromptText("Port");
        portField.setMaxWidth(Double.MAX_VALUE);

        portField.textProperty().addListener(portFieldChangeListener);

        portField.focusedProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue) {
                portField.setStyle(Styles.TEXT_FIELD_FOCUSED_STYLE);
            } else {
                portField.setStyle(Styles.TEXT_FIELD_STYLE);
            }
        });

        networkSettings.add(portField);

        Button confirmAddrButton = new Button("Confirm");
        confirmAddrButton.setStyle(Styles.BUTTON_STYLE);
        confirmAddrButton.setMaxWidth(Double.MAX_VALUE);
        confirmAddrButton.setOnAction(confirmButtonListener);

        confirmAddrButton.setOnMouseEntered(e -> confirmAddrButton.setStyle(Styles.BUTTON_HOVER_STYLE));
        confirmAddrButton.setOnMouseExited(e -> confirmAddrButton.setStyle(Styles.BUTTON_STYLE));

        networkSettings.add(confirmAddrButton);

        return networkSettings;
    }
}
