package com.funkylogclient;

import java.math.BigDecimal;
import java.math.RoundingMode;

import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import javafx.scene.text.TextAlignment;

public class Message {
    private int type;

    private String content;
    private String sender;
    private double time;

    private double period_timestamp;
    private int period;

    private boolean isValid;

    public Message(String unparsed) {
        try {
            String[] split = unparsed.split(";");
            type = Integer.parseInt(split[0]);
            time = Double.parseDouble(split[3]);
            sender = split[1];
            content = split[2];

            period = Integer.parseInt(split[4]);
            period_timestamp = Double.parseDouble(split[5]);

            isValid = true;
        } catch (Exception exc) {
            System.out.println("Error in parsing message: " + unparsed);

            content = new String();
            sender = new String("Unknown");
            time = 0.0;

            period = 0;
            period_timestamp = 0.0;

            isValid = false;
        }
    }

    public Message(int type, String sender, String content, double time, int period, double period_timestamp) {
        this.type = type;
        this.sender = sender;
        this.content = content;
        this.time = time;
        this.period = period;
        this.period_timestamp = period_timestamp;
    }

    public Boolean getValid() {
        return isValid;
    }

    public String getContent() {
        return content;
    }

    public String getSender() {
        return sender;
    }

    public double getTime() {
        return time;
    }

    public double getPeriodTimestamp() {
        return period_timestamp;
    }

    public int getPeriod() {
        return period;
    }

    public String getPeriodName() {
        switch (getPeriod()) {
            case 1:
                return "TELE-OP";
            case 2:
                return "AUTON";
            default:
                return "DISABLED";
        }
    }

    public boolean isLog() {
        return type == 0;
    }

    public boolean isWarning() {
        return type == 1;
    }

    public boolean isError() {
        return type == 2;
    }

    @Override
    public String toString() {
        String output = "";
        output += type + ";" + sender + ";" + content + ";" + time + ";" + period + ";"
                + period_timestamp;

        return output;
    }

    public Node getComponent() {
        VBox box = new VBox();

        String msg_style = Styles.DEFAULT_MSG;

        if (isError()) {
            msg_style += "-fx-border-color: transparent transparent transparent #FF8272;";
            msg_style += "-fx-background-color: #302222";
        } else if (isWarning()) {
            msg_style += "-fx-border-color: transparent transparent transparent #FFCC19;";
            msg_style += "-fx-background-color: #302D22";
        } else {
            msg_style += "-fx-border-color: transparent transparent transparent transparent;";
            msg_style += "-fx-background-color: rgba(255, 255, 255, 0.00)";
        }

        box.setStyle(msg_style);

        box.setPadding(new Insets(5, 5, 5, 5));

        HBox topBox = new HBox();
        topBox.setPadding(new Insets(0, 0, 5, 0));
        topBox.setSpacing(20);

        Text top = new Text(
                "SYS " + new BigDecimal(this.time).setScale(1, RoundingMode.HALF_UP).toString());
        top.setStyle(Styles.TEXT_STYLE + Styles.TEXT_SMALLER);

        Text topMid = new Text(getPeriodName() + " " + new BigDecimal(this.period_timestamp)
                .setScale(0, RoundingMode.HALF_UP).toString());
        topMid.setStyle(Styles.TEXT_STYLE + Styles.TEXT_SMALLER);

        Text topRight = new Text(sender);
        topRight.setStyle(Styles.TEXT_STYLE + Styles.TEXT_SMALL);
        HBox.setHgrow(topRight, Priority.ALWAYS);
        topRight.setTextAlignment(TextAlignment.RIGHT);

        topBox.getChildren().addAll(top, topMid, topRight);

        HBox body = new HBox();
        body.setPadding(new Insets(5, 5, 5, 30));

        Text contentText = new Text(content);
        contentText.setStyle(Styles.TEXT_STYLE + Styles.TEXT_SMALL);
        contentText.setWrappingWidth(500);

        body.getChildren().add(contentText);

        box.getChildren().addAll(topBox, body);

        return box;
    }
};
