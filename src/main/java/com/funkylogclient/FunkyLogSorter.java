package com.funkylogclient;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import javafx.stage.FileChooser;
import javafx.stage.Stage;

public class FunkyLogSorter {
    private static boolean allowErrors = true;
    private static boolean allowWarnings = true;
    private static boolean allowLogs = true;

    public static CopyOnWriteArrayList<Message> errors = new CopyOnWriteArrayList<>();

    private static String searchTerm = "";
    
    // Period Filters
    private static boolean allowTeleop = true;
    private static boolean allowAuto = true;
    private static boolean allowDisabled = true;

    public static List<Message> messages = new ArrayList<>(10000);
    public static List<Message> filtered = new ArrayList<>(10000);

    private static final AtomicLong filterVersion = new AtomicLong(0);
    private static volatile long lastFilteredVersion = -1;

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
        }, 3000, 2000, TimeUnit.MILLISECONDS);
    }

    public static long getFilterVersion() {
        return filterVersion.get();
    }

    public static boolean hasNewData() {
        long current = filterVersion.get();
        if (current != lastFilteredVersion) {
            lastFilteredVersion = current;
            return true;
        }
        return false;
    }

    public static void clear() {
        synchronized (messages) {
            messages.clear();
        }
        synchronized (filtered) {
            filtered.clear();
        }
        filterVersion.incrementAndGet();
    }

    public static void reFilter() {
        List<Message> newFiltered = new ArrayList<>(messages.size() / 2);
        synchronized (messages) {
            for (Message m : messages) {
                if (!checkMessageBySearch(m) || !checkPeriodFilter(m)) {
                    continue;
                }
                if (allowLogs && m.isLog()) {
                    newFiltered.add(m);
                } else if (allowWarnings && m.isWarning()) {
                    newFiltered.add(m);
                } else if (allowErrors && m.isError()) {
                    newFiltered.add(m);
                }
            }
        }
        synchronized (filtered) {
            filtered.clear();
            filtered.addAll(newFiltered);
        }
        filterVersion.incrementAndGet();
    }

    private static boolean checkMessageBySearch(Message msg) {
        if (searchTerm.isEmpty())
            return true;

        String sender = msg.getSender();
        String content = msg.getContent();
        String lowerSearch = searchTerm.toLowerCase();
        
        if (sender.length() < content.length()) {
            return sender.toLowerCase().contains(lowerSearch) 
                || content.toLowerCase().contains(lowerSearch);
        } else {
            return content.toLowerCase().contains(lowerSearch)
                || sender.toLowerCase().contains(lowerSearch);
        }
    }

    private static boolean checkPeriodFilter(Message msg) {
        String periodName = msg.getPeriodName();
        if (periodName.equals("TELEOP") && !allowTeleop) return false;
        if (periodName.equals("AUTO") && !allowAuto) return false;
        if (periodName.equals("DISABLED") && !allowDisabled) return false;
        return true;
    }

    public static void addMessage(Message m) {
        synchronized (messages) {
            messages.add(m);
        }

        if (log_file != null) {
            try {
                log_file.write(m.toString() + "\n");
            } catch (IOException exc) {
            }
        }

        if (m.isError()) {
            errors.add(m);
        }

        if (checkMessageBySearch(m) && checkPeriodFilter(m)) {
            boolean shouldAdd = false;
            if (allowLogs && m.isLog()) {
                shouldAdd = true;
            } else if (allowWarnings && m.isWarning()) {
                shouldAdd = true;
            } else if (allowErrors && m.isError()) {
                shouldAdd = true;
            }
            
            if (shouldAdd) {
                synchronized (filtered) {
                    filtered.add(m);
                }
                filterVersion.incrementAndGet();
            }
        }
    }

    public static List<Message> getFilteredSnapshot() {
        synchronized (filtered) {
            if (filtered.isEmpty()) {
                return java.util.Collections.emptyList();
            }
            return new ArrayList<>(filtered);
        }
    }

    public static int getFilteredSize() {
        synchronized (filtered) {
            return filtered.size();
        }
    }

    public static Message getFilteredAt(int index) {
        synchronized (filtered) {
            if (index >= 0 && index < filtered.size()) {
                return filtered.get(index);
            }
            return null;
        }
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

    public static void setTeleopAllowed(boolean allow) {
        allowTeleop = allow;
        reFilter();
    }

    public static void setAutoAllowed(boolean allow) {
        allowAuto = allow;
        reFilter();
    }

    public static void setDisabledAllowed(boolean allow) {
        allowDisabled = allow;
        reFilter();
    }

    public static void logAllMessages() {
        System.out.println("\nSTART");
        synchronized (filtered) {
            for (Message m : filtered) {
                System.out.println(m);
            }
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
        synchronized (messages) {
            for (Message m : messages) {
                result.append(m);
                result.append("\n");
            }
        }
        return result.toString();
    }

    public static void saveToFile(Stage pstage) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Save Log File");

        FileChooser.ExtensionFilter extFilter = new FileChooser.ExtensionFilter("ChimpCheck File", ".log846");
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
