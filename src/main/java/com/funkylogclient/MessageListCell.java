package com.funkylogclient;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.ListCell;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;

public class MessageListCell extends ListCell<Message> {
    private VBox container;
    private HBox topBox;
    private HBox bodyBox;
    private Text timeText;
    private Text periodText;
    private Text periodTimeText;
    private Text senderText;
    private Text contentText;
    private Message lastMessage;

    public MessageListCell() {
        container = new VBox(4);
        container.setPadding(new Insets(8, 12, 8, 12));

        topBox = new HBox(10);
        topBox.setAlignment(Pos.CENTER_LEFT);

        timeText = new Text();
        timeText.setStyle("-fx-font-size: 11px; -fx-fill: #808080; -fx-font-family: 'Segoe UI', 'Roboto', sans-serif;");

        periodText = new Text();
        periodText.setStyle("-fx-font-size: 10px; -fx-font-weight: bold; -fx-font-family: 'Segoe UI', 'Roboto', sans-serif;");

        periodTimeText = new Text();
        periodTimeText.setStyle("-fx-font-size: 10px; -fx-fill: #606060; -fx-font-family: 'Segoe UI', 'Roboto', sans-serif;");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        senderText = new Text();
        senderText.setStyle("-fx-font-size: 12px; -fx-font-weight: bold; -fx-font-family: 'Segoe UI', 'Roboto', sans-serif;");

        topBox.getChildren().addAll(timeText, periodText, periodTimeText, spacer, senderText);

        bodyBox = new HBox();
        bodyBox.setPadding(new Insets(2, 0, 0, 0));

        contentText = new Text();
        contentText.setStyle("-fx-font-size: 13px; -fx-fill: #E0E0E0; -fx-font-family: 'Segoe UI', 'Roboto', sans-serif;");
        contentText.setWrappingWidth(Region.USE_COMPUTED_SIZE);

        bodyBox.getChildren().add(contentText);

        container.getChildren().addAll(topBox, bodyBox);

        setStyle("-fx-background-color: transparent; -fx-padding: 2 4 2 4;");
    }

    @Override
    protected void updateItem(Message msg, boolean empty) {
        super.updateItem(msg, empty);

        if (empty || msg == null) {
            setGraphic(null);
            lastMessage = null;
            return;
        }

        if (msg == lastMessage) {
            return;
        }
        lastMessage = msg;

        String baseStyle = "-fx-border-width: 0 0 0 4px; -fx-border-radius: 0; -fx-background-radius: 8px;";

        if (msg.isError()) {
            baseStyle += "-fx-border-color: " + Styles.ACCENT_ERROR + ";";
            baseStyle += "-fx-background-color: linear-gradient(to right, #2D1F1F, " + Styles.BG_DARK + ");";
            senderText.setStyle("-fx-font-size: 12px; -fx-fill: " + Styles.ACCENT_ERROR + "; -fx-font-weight: bold; -fx-font-family: " + Styles.FONT_FAMILY + ";");
        } else if (msg.isWarning()) {
            baseStyle += "-fx-border-color: " + Styles.ACCENT_WARNING + ";";
            baseStyle += "-fx-background-color: linear-gradient(to right, #2D2814, " + Styles.BG_DARK + ");";
            senderText.setStyle("-fx-font-size: 12px; -fx-fill: " + Styles.ACCENT_WARNING + "; -fx-font-weight: bold; -fx-font-family: " + Styles.FONT_FAMILY + ";");
        } else {
            baseStyle += "-fx-border-color: " + Styles.BORDER_DARK + ";";
            baseStyle += "-fx-background-color: " + Styles.BG_DARK + ";";
            senderText.setStyle("-fx-font-size: 12px; -fx-fill: " + Styles.TEXT_PRIMARY + "; -fx-font-weight: bold; -fx-font-family: " + Styles.FONT_FAMILY + ";");
        }

        container.setStyle(baseStyle);

        double time = msg.getTime();
        timeText.setText(String.format("%.1fs", time));

        String periodName = msg.getPeriodName();
        periodText.setText(periodName);
        String periodColor = "#808080";
        if (msg.getPeriod() == 1) periodColor = "#FF8C00";
        else if (msg.getPeriod() == 2) periodColor = "#FFD700";
        periodText.setStyle("-fx-font-size: 10px; -fx-fill: " + periodColor + "; -fx-font-weight: bold; -fx-font-family: 'Segoe UI', 'Roboto', sans-serif;");

        periodTimeText.setText(String.format("%.0fms", msg.getPeriodTimestamp()));

        senderText.setText(msg.getSender());
        contentText.setText(msg.getContent());

        setGraphic(container);
    }
}

