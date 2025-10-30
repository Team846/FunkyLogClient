package com.funkylogclient;

import edu.wpi.first.networktables.NetworkTableInstance;
import edu.wpi.first.networktables.NetworkTable;
import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.StringProperty;
import javafx.beans.property.SimpleStringProperty;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class NetworkTablesClient {
    private static NetworkTableInstance ntInstance;
    private static NetworkTable table;
    private static ScheduledExecutorService executor;

    private static final BooleanProperty connected = new SimpleBooleanProperty(false);
    private static final DoubleProperty latency = new SimpleDoubleProperty(0.0);
    private static final StringProperty statusText = new SimpleStringProperty("Disconnected");

    private static boolean isRunning = false;
    private static long lastUpdateTime = 0;
    private static double measuredLatency = 0.0;

    public static BooleanProperty connectedProperty() {
        return connected;
    }

    public static DoubleProperty latencyProperty() {
        return latency;
    }

    public static StringProperty statusTextProperty() {
        return statusText;
    }

    public static void connect() {
        if (isRunning) {
            disconnect();
        }

        try {
            ntInstance = NetworkTableInstance.getDefault();
            ntInstance.setServer(UDPClient.serverIP);
            ntInstance.startClient3("FunkyLogClient");

            table = ntInstance.getTable("SmartDashboard");

            isRunning = true;

            startMonitoring();

            System.out.println("NetworkTables client connecting to: " + UDPClient.serverIP);

        } catch (Exception e) {
            System.err.println("Failed to start NetworkTables client: " + e.getMessage());
            Platform.runLater(() -> {
                connected.set(false);
                statusText.set("Connection Failed");
            });
        }
    }

    public static void disconnect() {
        isRunning = false;

        if (executor != null && !executor.isShutdown()) {
            executor.shutdown();
            try {
                if (!executor.awaitTermination(1, TimeUnit.SECONDS)) {
                    executor.shutdownNow();
                }
            } catch (InterruptedException e) {
                executor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }

        if (ntInstance != null) {
            ntInstance.stopClient();
            ntInstance = null;
        }

        Platform.runLater(() -> {
            connected.set(false);
            statusText.set("Disconnected");
            latency.set(0.0);
        });

        System.out.println("NetworkTables client disconnected");
    }

    private static void startMonitoring() {
        executor = Executors.newScheduledThreadPool(1);

        executor.scheduleAtFixedRate(() -> {
            if (!isRunning)
                return;

            try {
                boolean isConnected = ntInstance.isConnected();

                if (isConnected && ntInstance != null) {
                    java.util.OptionalLong rttOpt = ntInstance.getServerTimeOffset();

                    if (rttOpt.isPresent() && rttOpt.getAsLong() > 0) {
                        measuredLatency = Math.min(rttOpt.getAsLong() / 2000.0, 500.0);
                    } else if (lastUpdateTime > 0) {
                        long currentTime = System.currentTimeMillis();
                        long elapsed = currentTime - lastUpdateTime;
                        measuredLatency = Math.min(measuredLatency * 0.95 + elapsed * 0.05, 500.0);
                        lastUpdateTime = currentTime;
                    } else {
                        lastUpdateTime = System.currentTimeMillis();
                    }
                }

                final double currentLatency = measuredLatency;

                Platform.runLater(() -> {
                    connected.set(isConnected);
                    if (isConnected) {
                        statusText.set("Connected");
                        latency.set(currentLatency);
                    } else {
                        statusText.set("Disconnected");
                        latency.set(0.0);
                    }
                });

            } catch (Exception e) {
                Platform.runLater(() -> {
                    connected.set(false);
                    statusText.set("Error");
                    latency.set(0.0);
                });
            }
        }, 0, 100, TimeUnit.MILLISECONDS);
    }

    public static NetworkTable getTable() {
        return table;
    }

    public static NetworkTableInstance getInstance() {
        return ntInstance;
    }

    public static boolean isConnected() {
        return connected.get();
    }

    public static double getLatency() {
        return latency.get();
    }

    public static String getStatusText() {
        return statusText.get();
    }
}