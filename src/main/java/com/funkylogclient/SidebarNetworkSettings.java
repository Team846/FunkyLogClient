package com.funkylogclient;

import java.util.ArrayList;

import javafx.beans.value.ChangeListener;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.TextField;
import javafx.scene.layout.Region;
import javafx.scene.text.Text;

public class SidebarNetworkSettings {
    public static ArrayList<Node> getSidebarNetworkSettings(ChangeListener<? super String> addrFieldChangeListener, 
        ChangeListener<? super String> portFieldChangeListener, EventHandler<ActionEvent> confirmButtonListener) {
        ArrayList<Node> networkSettings = new ArrayList<Node>();

        Region topLinedSpacing = new Region();
        topLinedSpacing.setMinHeight(15);
        topLinedSpacing.setStyle("-fx-border-width: 1px; -fx-border-color: transparent transparent #aaa transparent");
        networkSettings.add(topLinedSpacing);

        Region topSpacing = new Region();
        topSpacing.setMinHeight(15);
        networkSettings.add(topSpacing);

        Text nwSettingsLabel = new Text("Server address, port: ");
        nwSettingsLabel.setStyle(Styles.TEXT_GMED);
        networkSettings.add(nwSettingsLabel);

        TextField addressField = new TextField();
        addressField.setText(UDPClient.serverIP);
        addressField.textProperty().addListener(addrFieldChangeListener);
        networkSettings.add(addressField);

        TextField portField = new TextField();
        portField.setText("" + UDPClient.port);

        portField.textProperty().addListener(portFieldChangeListener);
        networkSettings.add(portField);

        Button confirmAddrButton = new Button("Confirm");
        confirmAddrButton.setOnAction(confirmButtonListener);
        networkSettings.add(confirmAddrButton);

        return networkSettings;
    }
}
