package com.funkylogclient;

import java.util.logging.Filter;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

public class Main {
    public static void main(String[] args) {
        Logger jinputRoot = Logger.getLogger("net.java.games.input");
        jinputRoot.setLevel(Level.WARNING);
        Logger jinputEnv = Logger.getLogger("net.java.games.input.ControllerEnvironment");
        jinputEnv.setLevel(Level.WARNING);
        Logger root = Logger.getLogger("");
        for (var h : root.getHandlers()) {
            Filter prev = h.getFilter();
            h.setFilter((LogRecord r) -> {
                if (r.getLoggerName() != null && r.getLoggerName().startsWith("net.java.games.input")
                        && r.getLevel().intValue() < Level.WARNING.intValue())
                    return false;
                return prev == null || prev.isLoggable(r);
            });
        }
        FunkyLogs.main(args);
    }
}
