package com.funkylogclient;

import javafx.animation.FadeTransition;
import javafx.animation.ParallelTransition;
import javafx.animation.TranslateTransition;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

public class NotificationManager {
    private static final int MAX_VISIBLE = 3;
    private static final long MIN_INTERVAL_MS = 2500;
    private static final long FADE_DELAY_MS = 750;
    private static NotificationManager instance;
    private VBox notificationContainer;
    private LinkedList<NotificationItem> activeNotifications = new LinkedList<>();
    private Queue<NotificationData> pendingQueue = new ConcurrentLinkedQueue<>();
    private long lastNotificationTime = 0;
    private boolean isProcessing = false;

    private NotificationManager() {}

    public static NotificationManager getInstance() {
        if (instance == null) {
            instance = new NotificationManager();
        }
        return instance;
    }

    public void init(StackPane root) {
        notificationContainer = new VBox(8);
        notificationContainer.setPadding(new Insets(60, 0, 0, 16));
        notificationContainer.setMaxWidth(350);
        notificationContainer.setMaxHeight(Region.USE_PREF_SIZE);
        notificationContainer.setPickOnBounds(false);
        notificationContainer.setMouseTransparent(false);
        
        StackPane.setAlignment(notificationContainer, Pos.TOP_LEFT);
        root.getChildren().add(notificationContainer);
    }

    public void showError(String title, String message) {
        queueNotification(title, message, NotificationType.ERROR);
    }

    public void showWarning(String title, String message) {
        queueNotification(title, message, NotificationType.WARNING);
    }

    public void showInfo(String title, String message) {
        queueNotification(title, message, NotificationType.INFO);
    }

    private void queueNotification(String title, String message, NotificationType type) {
        pendingQueue.offer(new NotificationData(title, message, type));
        processQueue();
    }

    private synchronized void processQueue() {
        if (isProcessing) return;
        isProcessing = true;
        
        Platform.runLater(() -> {
            long now = System.currentTimeMillis();
            
            while (!pendingQueue.isEmpty()) {
                long timeSinceLast = now - lastNotificationTime;
                if (timeSinceLast < MIN_INTERVAL_MS && !activeNotifications.isEmpty()) {
                    scheduleNextProcess(MIN_INTERVAL_MS - timeSinceLast);
                    break;
                }
                
                NotificationData data = pendingQueue.poll();
                if (data != null) {
                    if (activeNotifications.size() >= MAX_VISIBLE) {
                        fadeOutOldestThenShow(data);
                    } else {
                        showNotificationNow(data.title, data.message, data.type);
                        lastNotificationTime = System.currentTimeMillis();
                    }
                    now = System.currentTimeMillis();
                }
            }
            
            isProcessing = false;
        });
    }

    private void fadeOutOldestThenShow(NotificationData data) {
        NotificationItem oldest = activeNotifications.peekFirst();
        if (oldest != null) {
            FadeTransition fadeOut = new FadeTransition(Duration.millis(FADE_DELAY_MS), oldest.node);
            fadeOut.setFromValue(oldest.node.getOpacity());
            fadeOut.setToValue(0);
            fadeOut.setOnFinished(e -> {
                NotificationItem removed = activeNotifications.pollFirst();
                if (removed != null) {
                    notificationContainer.getChildren().remove(removed.node);
                }
                showNotificationNow(data.title, data.message, data.type);
                lastNotificationTime = System.currentTimeMillis();
                isProcessing = false;
                processQueue();
            });
            fadeOut.play();
        } else {
            showNotificationNow(data.title, data.message, data.type);
            lastNotificationTime = System.currentTimeMillis();
        }
    }

    private void scheduleNextProcess(long delayMs) {
        new Thread(() -> {
            try {
                Thread.sleep(delayMs);
                isProcessing = false;
                processQueue();
            } catch (InterruptedException ignored) {}
        }).start();
    }

    private void showNotificationNow(String title, String message, NotificationType type) {
        while (activeNotifications.size() >= MAX_VISIBLE) {
            NotificationItem oldest = activeNotifications.pollFirst();
            if (oldest != null) {
                dismissImmediately(oldest);
            }
        }
        
        NotificationItem notification = createNotification(title, message, type);
        activeNotifications.add(notification);
        
        notification.node.setTranslateX(-400);
        notification.node.setOpacity(0);
        notificationContainer.getChildren().add(notification.node);
        
        TranslateTransition slideIn = new TranslateTransition(Duration.millis(200), notification.node);
        slideIn.setFromX(-400);
        slideIn.setToX(0);
        
        FadeTransition fadeIn = new FadeTransition(Duration.millis(200), notification.node);
        fadeIn.setFromValue(0);
        fadeIn.setToValue(1);
        
        ParallelTransition enterAnim = new ParallelTransition(slideIn, fadeIn);
        enterAnim.play();
    }

    private NotificationItem createNotification(String title, String message, NotificationType type) {
        VBox card = new VBox(6);
        card.setPadding(new Insets(12, 16, 12, 16));
        card.setMaxWidth(340);
        card.setMinWidth(280);
        
        String borderColor;
        String bgGradient;
        String titleColor;
        
        switch (type) {
            case ERROR:
                borderColor = Styles.ACCENT_ERROR;
                bgGradient = "linear-gradient(to right, #2D1F1F, " + Styles.BG_DARK + ")";
                titleColor = Styles.ACCENT_ERROR;
                break;
            case WARNING:
                borderColor = Styles.ACCENT_WARNING;
                bgGradient = "linear-gradient(to right, #2D2814, " + Styles.BG_DARK + ")";
                titleColor = Styles.ACCENT_WARNING;
                break;
            default:
                borderColor = Styles.ACCENT_PRIMARY;
                bgGradient = "linear-gradient(to right, #2D2210, " + Styles.BG_DARK + ")";
                titleColor = Styles.ACCENT_PRIMARY;
                break;
        }
        
        card.setStyle(
            "-fx-background-color: " + bgGradient + ";" +
            "-fx-background-radius: 10px;" +
            "-fx-border-color: " + borderColor + ";" +
            "-fx-border-width: 0 0 0 4px;" +
            "-fx-border-radius: 10px;" +
            "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.4), 12, 0, 0, 4);"
        );
        
        HBox header = new HBox(8);
        header.setAlignment(Pos.CENTER_LEFT);
        
        Label titleLabel = new Label(title);
        titleLabel.setStyle(
            "-fx-text-fill: " + titleColor + ";" +
            "-fx-font-size: 13px;" +
            "-fx-font-weight: bold;" +
            "-fx-font-family: " + Styles.FONT_FAMILY + ";"
        );
        titleLabel.setMaxWidth(240);
        titleLabel.setWrapText(true);
        
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        
        Button closeBtn = new Button("×");
        closeBtn.setStyle(
            "-fx-background-color: transparent;" +
            "-fx-text-fill: " + Styles.TEXT_MUTED + ";" +
            "-fx-font-size: 18px;" +
            "-fx-padding: 0 4 0 4;" +
            "-fx-cursor: hand;"
        );
        closeBtn.setOnMouseEntered(e -> closeBtn.setStyle(
            "-fx-background-color: transparent;" +
            "-fx-text-fill: " + Styles.TEXT_PRIMARY + ";" +
            "-fx-font-size: 18px;" +
            "-fx-padding: 0 4 0 4;" +
            "-fx-cursor: hand;"
        ));
        closeBtn.setOnMouseExited(e -> closeBtn.setStyle(
            "-fx-background-color: transparent;" +
            "-fx-text-fill: " + Styles.TEXT_MUTED + ";" +
            "-fx-font-size: 18px;" +
            "-fx-padding: 0 4 0 4;" +
            "-fx-cursor: hand;"
        ));
        
        header.getChildren().addAll(titleLabel, spacer, closeBtn);
        
        Label messageLabel = new Label(message);
        messageLabel.setStyle(
            "-fx-text-fill: " + Styles.TEXT_PRIMARY + ";" +
            "-fx-font-size: 12px;" +
            "-fx-font-family: " + Styles.FONT_FAMILY + ";"
        );
        messageLabel.setWrapText(true);
        messageLabel.setMaxWidth(300);
        
        card.getChildren().addAll(header, messageLabel);
        
        NotificationItem item = new NotificationItem(card);
        
        closeBtn.setOnAction(e -> {
            activeNotifications.remove(item);
            dismissWithAnimation(item);
        });
        
        return item;
    }

    private void dismissImmediately(NotificationItem item) {
        notificationContainer.getChildren().remove(item.node);
    }

    private void dismissWithAnimation(NotificationItem item) {
        TranslateTransition slideOut = new TranslateTransition(Duration.millis(150), item.node);
        slideOut.setToX(-400);
        
        FadeTransition fadeOut = new FadeTransition(Duration.millis(150), item.node);
        fadeOut.setToValue(0);
        
        ParallelTransition exitAnim = new ParallelTransition(slideOut, fadeOut);
        exitAnim.setOnFinished(e -> {
            notificationContainer.getChildren().remove(item.node);
        });
        exitAnim.play();
    }

    public void clearAll() {
        Platform.runLater(() -> {
            pendingQueue.clear();
            for (NotificationItem item : activeNotifications) {
                dismissImmediately(item);
            }
            activeNotifications.clear();
        });
    }

    private static class NotificationItem {
        VBox node;

        NotificationItem(VBox node) {
            this.node = node;
        }
    }

    private static class NotificationData {
        String title;
        String message;
        NotificationType type;

        NotificationData(String title, String message, NotificationType type) {
            this.title = title;
            this.message = message;
            this.type = type;
        }
    }

    public enum NotificationType {
        ERROR,
        WARNING,
        INFO
    }
}
