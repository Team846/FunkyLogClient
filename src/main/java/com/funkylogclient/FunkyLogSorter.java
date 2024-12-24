package com.funkylogclient;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import javafx.stage.FileChooser;
import javafx.stage.Stage;

public class FunkyLogSorter {
    private static int MAX_LEN = 1200;

    private static boolean allowErrors = true;
    private static boolean allowWarnings = true;
    private static boolean allowLogs = true;

    public static LinkedList<Message> errors = new LinkedList<>();

    private static String searchTerm = "";

    public static LinkedList<Message> messages = new LinkedList<>();
    public static LinkedList<Message> filtered = new LinkedList<>();

    public static String log_file_directory = System.getProperty("user.dir") + "/logs846";
    public static FileWriter log_file;

    private static final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

    static {
        scheduler.scheduleAtFixedRate(() -> {
            if (log_file != null) {
                try {
                    log_file.flush();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }, 3000, 1000, TimeUnit.MILLISECONDS);
    }

    public static void clear() {
        messages.clear();
        filtered.clear();
    }

    public static void reFilter() {
        filtered.clear();

        for (Message m : messages) {
            if (!checkMessageBySearch(m)) {
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
        if (searchTerm.equals(""))
            return true;

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
        messages.add(m);

        if (log_file != null) {
            try {
                log_file.write(m.toString() + "\n");
            } catch (IOException exc) {
                exc.printStackTrace();
            }
        } else {
            System.out.println("Log file not open");
        }

        if (m.isError()) {
            errors.add(m);
        }

        if (!checkMessageBySearch(m)) {

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

    public static void logAllMessages() {
        System.out.println("\nSTART");
        for (Message m : filtered) {
            System.out.println(m);
        }
        System.out.println("END\n");
    }

    public static String makeLogFileName() {
        LocalDateTime dateTime = LocalDateTime.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd-HH-mm-ss");
        String dateString = dateTime.format(formatter);
        return dateString + ".log846";
    }

    public static String getLogFileDirectory() {
        return log_file_directory;
    }

    public static void makeNewLogFile() {
        try {
            File directory = new File(log_file_directory);
            if (!directory.exists()) {
                directory.mkdir();
            }
            if (log_file != null)
                log_file.close();
            log_file = new FileWriter(log_file_directory + "/" + makeLogFileName());
            System.out.print("Log file created: ");
            System.out.println(log_file_directory + "/" + makeLogFileName());
        } catch (IOException exc) {
            exc.printStackTrace();
        }
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

        FileChooser.ExtensionFilter extFilter = new FileChooser.ExtensionFilter("FunkyLogs File", ".log846");
        fileChooser.getExtensionFilters().add(extFilter);

        LocalDateTime dateTime = LocalDateTime.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd-HH-mm-ss");
        String dateString = dateTime.format(formatter);

        fileChooser.setInitialFileName(dateString + ".log846");

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

    public static void createTestLog(Stage pstage) {
        addMessage(new Message("0;TestSender;This is a Log;0.0;0;0.0"));
        logAllMessages();
    }

    public static void createTestWarning(Stage pstage) {
        addMessage(new Message("1;TestSender;This is a Warning;0.0;0;0.0"));
        logAllMessages();
    }

    public static void createTestError(Stage pstage) {
        addMessage(new Message("2;TestSender;This is an Error;0.0;0;0.0"));
        logAllMessages();
    }
}
