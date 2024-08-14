package com.funkylogclient;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.LinkedList;

import javafx.stage.FileChooser;
import javafx.stage.Stage;

public class FunkyLogSorter {
    private static int MAX_LEN = 1200;

    private static boolean allowErrors = true;
    private static boolean allowWarnings = true;
    private static boolean allowLogs = true;

    private static String searchTerm = "";

    public static LinkedList<Message> messages = new LinkedList<>();
    public static LinkedList<Message> filtered = new LinkedList<>();

    public static void clear() {
        messages.clear();
        filtered.clear();
    }

    public static void reFilter() {
        filtered.clear();

        for (Message m : messages) {
            if (!checkMessageBySearch(m))  {
                continue;
            } else if (allowLogs && m.isLog()) {
                filtered.add(m);
            } else if (allowWarnings && m.isWarning()) {
                filtered.add(m);
            } else if (allowErrors && m.isError()) {
                filtered.add(m);
            }
        }
    }

    private static boolean checkMessageBySearch(Message msg) {
        if (searchTerm.equals("")) return true;

        return msg.getSender().contains(searchTerm) || msg.getContent().contains(searchTerm);
    }

    public static void trimMessages() {
        int currentLength = messages.size();

        if (currentLength <= MAX_LEN)
            return;

        for (int i = 0; i <= currentLength - MAX_LEN; i++) {
            messages.removeFirst();
        }

        if (filtered.size() > MAX_LEN) {
            reFilter();
        }
    }

    public static void addMessage(Message m) {
        System.out.println(m);

        messages.add(m);

        if (!checkMessageBySearch(m))  {

        } else if (allowLogs && m.isLog()) {
            filtered.add(m);
        } else if (allowWarnings && m.isWarning()) {
            filtered.add(m);
        } else if (allowErrors && m.isError()) {
            filtered.add(m);
        }

        trimMessages();
    }

    public static void setErrorsAllowed(boolean allow) {
        allowErrors = allow;
        reFilter();
    }

    public static void setWarningsAllowed(boolean allow) {
        allowWarnings = allow;
        reFilter();
    }

    public static void setLogsAllowed(boolean allow) {
        allowLogs = allow;
        reFilter();
    }

    public static void changeSearchTerm(String term) {
        searchTerm = term;
        reFilter();
    }

    // Only for testing
    public static void main(String[] args) {
        // addMessage(new Message("0;1.0;a;abc"));
        // addMessage(new Message("1;2.0;a;cde"));
        // addMessage(new Message("2;2.0;a;cdef"));

        // logAllMessages();
    }

    public static void logAllMessages() {
        System.out.println("\nSTART");
        for (Message m : filtered) {
            System.out.println(m);
        }
        System.out.println("END\n");
    }

    public static String stringifyAllMessages() {
        StringBuilder result = new StringBuilder();
        for (Message m : messages) {
            result.append(m);
            result.append("\n");
        }
        return result.toString();
    }

    public static void saveToFile(Stage pstage) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Save Log File");

        LocalDateTime dateTime = LocalDateTime.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd-HH-mm-ss");
        String dateString = dateTime.format(formatter);

        fileChooser.setInitialFileName(dateString + ".log_846");

        FileChooser.ExtensionFilter extFilter = new FileChooser.ExtensionFilter("FunkyLog (*.log_846)", "*.log_846");
        fileChooser.getExtensionFilters().add(extFilter);

        File file = fileChooser.showSaveDialog(pstage);

        if (file != null) {
            try (FileWriter fileWriter = new FileWriter(file)) {
                fileWriter.write(stringifyAllMessages());
                System.out.println("File saved to: " + file.getAbsolutePath());
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

}
