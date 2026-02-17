package com.funkylogclient;

import edu.wpi.first.networktables.NetworkTable;
import edu.wpi.first.networktables.NetworkTableEntry;
import edu.wpi.first.networktables.NetworkTableInstance;
import javafx.application.Platform;
import javafx.scene.control.ComboBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class SidebarJoystickInput {
    private static Text sourceText;
    private static Text axis0Text;
    private static Text axis1Text;
    private static Text axis2Text;
    private static ScheduledExecutorService pollExecutor;
    private static ScheduledExecutorService deviceListExecutor;
    private static ComboBox<String> sourceComboRef;

    public static VBox getJoystickInputSection() {
        VBox section = new VBox(6);
        section.setStyle("-fx-background-color: " + Styles.BG_DARKEST + "; -fx-background-radius: 8px; -fx-padding: 12px;");

        Region topSpacing = new Region();
        topSpacing.setMinHeight(8);
        section.getChildren().add(topSpacing);

        Text title = new Text("Joystick input");
        title.setStyle(Styles.SECTION_HEADER_STYLE);
        section.getChildren().add(title);

        Region afterTitle = new Region();
        afterTitle.setMinHeight(6);
        section.getChildren().add(afterTitle);

        Text sourceLabel = new Text("Input source");
        sourceLabel.setStyle("-fx-font-size: 11px; -fx-fill: " + Styles.TEXT_MUTED + "; -fx-font-family: " + Styles.FONT_FAMILY + ";");
        section.getChildren().add(sourceLabel);

        ComboBox<String> sourceCombo = new ComboBox<>();
        sourceComboRef = sourceCombo;
        refreshSourceComboItems(sourceCombo);
        String current = SimDriverStationInput.getPreferredInputSource();
        if (sourceCombo.getItems().contains(current)) {
            sourceCombo.setValue(current);
        } else {
            sourceCombo.setValue(SimDriverStationInput.SOURCE_AUTO);
        }
        sourceCombo.setStyle(
                "-fx-font-size: 12px; -fx-text-fill: " + Styles.TEXT_PRIMARY + "; -fx-font-family: " + Styles.FONT_FAMILY + "; " +
                        "-fx-background-color: " + Styles.BG_MEDIUM + "; -fx-border-color: " + Styles.BORDER_MEDIUM + "; " +
                        "-fx-border-radius: 6px; -fx-background-radius: 6px; -fx-padding: 6 10; -fx-cursor: hand;");
        sourceCombo.setMaxWidth(Double.MAX_VALUE);
        sourceCombo.setOnAction(e -> {
            String sel = sourceCombo.getValue();
            if (sel != null) SimDriverStationInput.setPreferredInputSource(sel);
        });
        section.getChildren().add(sourceCombo);
        startDeviceListRefresh();

        Region afterCombo = new Region();
        afterCombo.setMinHeight(4);
        section.getChildren().add(afterCombo);

        sourceText = new Text("Source: --");
        sourceText.setStyle("-fx-font-size: 11px; -fx-fill: " + Styles.TEXT_MUTED + "; -fx-font-family: " + Styles.FONT_FAMILY + ";");
        section.getChildren().add(sourceText);

        axis0Text = new Text("Axis 0 (X): 0.00");
        axis0Text.setStyle("-fx-font-size: 12px; -fx-fill: " + Styles.TEXT_PRIMARY + "; -fx-font-family: " + Styles.FONT_FAMILY + ";");
        section.getChildren().add(axis0Text);

        axis1Text = new Text("Axis 1 (Y): 0.00");
        axis1Text.setStyle("-fx-font-size: 12px; -fx-fill: " + Styles.TEXT_PRIMARY + "; -fx-font-family: " + Styles.FONT_FAMILY + ";");
        section.getChildren().add(axis1Text);

        axis2Text = new Text("Axis 2 (J/L): 0.00");
        axis2Text.setStyle("-fx-font-size: 12px; -fx-fill: " + Styles.TEXT_PRIMARY + "; -fx-font-family: " + Styles.FONT_FAMILY + ";");
        section.getChildren().add(axis2Text);

        Text hint = new Text("Teleop: WASD + J(-1)/L(+1) if no joystick");
        hint.setStyle("-fx-font-size: 10px; -fx-fill: " + Styles.TEXT_MUTED + "; -fx-font-family: " + Styles.FONT_FAMILY + ";");
        section.getChildren().add(hint);

        startPolling();
        return section;
    }

    private static void refreshSourceComboItems(ComboBox<String> combo) {
        List<String> items = new ArrayList<>();
        items.add(SimDriverStationInput.SOURCE_AUTO);
        items.add(SimDriverStationInput.SOURCE_KEYBOARD);
        items.addAll(SimDriverStationInput.getDiscoveredJoystickDevices());
        String current = combo.getValue();
        combo.getItems().setAll(items);
        if (items.contains(current)) {
            combo.setValue(current);
        } else if (!items.isEmpty()) {
            combo.setValue(items.get(0));
        }
    }

    private static void startDeviceListRefresh() {
        if (deviceListExecutor != null) return;
        deviceListExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "SidebarJoystickDeviceList");
            t.setDaemon(true);
            return t;
        });
        deviceListExecutor.scheduleAtFixedRate(() -> {
            SimDriverStationInput.getDiscoveredJoystickDevices();
            Platform.runLater(() -> {
                if (sourceComboRef != null) refreshSourceComboItems(sourceComboRef);
            });
        }, 2000, 2000, TimeUnit.MILLISECONDS);
    }

    private static void startPolling() {
        if (pollExecutor != null) return;
        pollExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "SidebarJoystickInput");
            t.setDaemon(true);
            return t;
        });
        pollExecutor.scheduleAtFixedRate(SidebarJoystickInput::pollAndUpdateUI, 0, 100, TimeUnit.MILLISECONDS);
    }

    private static void pollAndUpdateUI() {
        String src = "--";
        double a0 = 0, a1 = 0, a2 = 0;
        if (NetworkTablesClient.isConnected()) {
            try {
                NetworkTableInstance inst = NetworkTableInstance.getDefault();
                NetworkTable stick = inst.getTable("FunkyFMS").getSubTable("SimDS").getSubTable("joystick0");
                NetworkTableEntry e;
                e = stick.getEntry("source"); if (e.exists()) src = e.getString("--");
                e = stick.getEntry("axis0"); if (e.exists()) a0 = e.getDouble(0);
                e = stick.getEntry("axis1"); if (e.exists()) a1 = e.getDouble(0);
                e = stick.getEntry("axis2"); if (e.exists()) a2 = e.getDouble(0);
            } catch (Exception ignored) { }
        }
        final String fSrc = src;
        final double fA0 = a0, fA1 = a1, fA2 = a2;
        Platform.runLater(() -> updateUI(fSrc, fA0, fA1, fA2));
    }

    private static void updateUI(String source, double a0, double a1, double a2) {
        if (sourceText != null) sourceText.setText("Source: " + source);
        if (axis0Text != null) axis0Text.setText(String.format("Axis 0 (X): %.2f", a0));
        if (axis1Text != null) axis1Text.setText(String.format("Axis 1 (Y): %.2f", a1));
        if (axis2Text != null) axis2Text.setText(String.format("Axis 2 (J/L): %.2f", a2));
    }

    public static void shutdown() {
        if (pollExecutor != null && !pollExecutor.isShutdown()) {
            pollExecutor.shutdown();
            try { pollExecutor.awaitTermination(500, TimeUnit.MILLISECONDS); } catch (InterruptedException ignored) { }
            pollExecutor = null;
        }
        if (deviceListExecutor != null && !deviceListExecutor.isShutdown()) {
            deviceListExecutor.shutdown();
            try { deviceListExecutor.awaitTermination(500, TimeUnit.MILLISECONDS); } catch (InterruptedException ignored) { }
            deviceListExecutor = null;
        }
        sourceComboRef = null;
    }
}
