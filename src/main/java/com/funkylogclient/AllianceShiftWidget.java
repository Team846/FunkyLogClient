package com.funkylogclient;

import edu.wpi.first.networktables.NetworkTable;
import edu.wpi.first.networktables.NetworkTableInstance;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.TextField;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

public class AllianceShiftWidget extends DashboardWidget {
    private Runnable removeCallback;
    private TextField valueField;
    private boolean our_hub_active;
    private boolean won_auto;
    private double shift;
    private double until_flip;
    private double our_time_left;
    private String allianceColor;
    private String oppositeAllianceColor;

    public AllianceShiftWidget(String title, String key) {
        super(title, key);
        our_hub_active = false;
        won_auto = false;
        shift = 0.0;
        until_flip = 0.0;
        our_time_left = 0.0;
        allianceColor = Styles.TEXT_RED;
        oppositeAllianceColor = Styles.TEXT_BLUE;
        contentBox.setPadding(new javafx.geometry.Insets(6));
        createNumberDisplay();
        setupContextMenu();
    }

    public void setRemoveCallback(Runnable callback) {
        this.removeCallback = callback;
    }

    private void setupContextMenu() {
        javafx.scene.control.ContextMenu contextMenu = new javafx.scene.control.ContextMenu();
        javafx.scene.control.MenuItem removeItem = new javafx.scene.control.MenuItem("Remove Widget");
        removeItem.setOnAction(e -> {
            if (removeCallback != null) {
                removeCallback.run();
            }
        });
        contextMenu.getItems().add(removeItem);
        container.setOnContextMenuRequested(event -> {
            contextMenu.show(container, event.getScreenX(), event.getScreenY());
        });
    }

    private static String valueFieldStyle(String textFill) {
        
        return "-fx-font-size: 30px; -fx-font-weight: bold; -fx-font-family: " + Styles.FONT_FAMILY
                + "; -fx-text-fill: " + textFill
                + "; -fx-background-color: transparent; -fx-border-width: 0; -fx-padding: 0; -fx-alignment: center;";

    }

    private void createNumberDisplay() {
        valueField = new TextField("0");
        valueField.setStyle(valueFieldStyle(Styles.TEXT_WHITE));
        valueField.setEditable(false);

        VBox numberContainer = new VBox(0);
        numberContainer.setAlignment(Pos.CENTER);
        numberContainer.getChildren().add(valueField);
        VBox.setVgrow(numberContainer, Priority.ALWAYS);
        contentBox.getChildren().add(numberContainer);
    }

    @Override
    public Node getContent() {
        return container;
    }

    @Override
    public void updateValue(Object value) {
        retrieveData();
        if (our_hub_active) {
            valueField.setText(Double.toString(our_time_left));
        } else {
            valueField.setText(Double.toString(until_flip));
        }
        valueField.setStyle(valueFieldStyle(findColor()));
    }

    public String findColor()
    {
        if (shift == 0 || shift == 1 || shift == 6) return Styles.TEXT_MUTED;
        if (won_auto) {
            return (((int) Math.round(shift)) % 2 == 1 ? allianceColor : oppositeAllianceColor);
        } else {
            return (((int) Math.round(shift)) % 2 == 0 ? allianceColor : oppositeAllianceColor);
        }
    }

    private void retrieveData() {
        try {
            NetworkTableInstance instance = NetworkTableInstance.getDefault();
            NetworkTable table = instance.getTable("SmartDashboard");
            if (table != null) {
                NetworkTable goingIn = table.getSubTable("Robot");
                String color = goingIn.getEntry("AllianceColor").getString("");
                allianceColor = ("Blue".equals(color) ? Styles.TEXT_BLUE : Styles.TEXT_RED);
                oppositeAllianceColor = ("Blue".equals(color) ? Styles.TEXT_RED : Styles.TEXT_BLUE);
                
                NetworkTable allianceShift = goingIn.getSubTable("game_data");
                our_hub_active = allianceShift.getEntry("shift_active").getBoolean(false);
                won_auto = allianceShift.getEntry("active_first").getBoolean(false);
                shift = allianceShift.getEntry("shift").getDouble(0.0);
                until_flip = allianceShift.getEntry("until_flip").getDouble(0.0);
                our_time_left = allianceShift.getEntry("our_time_left").getDouble(0.0);
            }
        } catch (Exception e) {
            System.err.println("Unable to retrieve alliance shift data");
        }
    }
}
