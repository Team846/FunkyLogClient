package com.funkylogclient;

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

            content = "";
            sender = "Unknown";
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
        this.isValid = true;
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
        switch (period) {
            case 1:
                return "TELEOP";
            case 2:
                return "AUTO";
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
        return type + ";" + sender + ";" + content + ";" + time + ";" + period + ";" + period_timestamp;
    }
}
