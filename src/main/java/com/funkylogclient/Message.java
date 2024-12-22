package com.funkylogclient;

import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;

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

            isValid = true;
        } catch (Exception exc) {
            System.out.println("Error in parsing message: " + unparsed);

            content = new String();
            sender = new String("Unknown");
            time = 0.0;

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

        Text top = new Text(time + " [" + sender + "]");
        top.setStyle(Styles.TEXT_STYLE + Styles.TEXT_SMALL);

        HBox body = new HBox();
        body.setPadding(new Insets(5, 5, 5, 30));

        Text contentText = new Text(content);
        contentText.setStyle(Styles.TEXT_STYLE + Styles.TEXT_SMALL);
        contentText.setWrappingWidth(500);

        body.getChildren().add(contentText);

        box.getChildren().addAll(top, body);

        return box;
    }
};
