package com.funkylogclient;

import edu.wpi.first.networktables.NetworkTable;
import edu.wpi.first.networktables.NetworkTableEntry;
import edu.wpi.first.networktables.NetworkTableInstance;
import javafx.scene.Scene;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

public class SimDriverStationInput {

    public static final String SOURCE_AUTO = "Auto";
    public static final String SOURCE_KEYBOARD = "Keyboard";

    private static volatile String preferredInputSource = SOURCE_AUTO;

    public static String getPreferredInputSource() {
        return preferredInputSource;
    }

    public static void setPreferredInputSource(String source) {
        if (source != null) preferredInputSource = source;
        clearCachedJoystickController();
    }

    public static void clearCachedJoystickController() {
        cachedJoystickController = null;
        lastJoystickEnumerationTime = 0;
        cachedJoystickList = null;
    }

    private static volatile List<String> discoveredDevices = Collections.emptyList();
    private static volatile long lastDiscoveryTime;
    private static final long DISCOVERY_INTERVAL_MS = 1500;
    private static volatile long lastControllerLogTime;
    private static final long CONTROLLER_LOG_INTERVAL_MS = 10000;

    private static Object getControllerEnvironment() {
        Object defaultEnv = null;
        try {
            Class<?> envClass = Class.forName("net.java.games.input.ControllerEnvironment");
            Method getDefault = envClass.getMethod("getDefaultEnvironment");
            getDefault.setAccessible(true);
            defaultEnv = getDefault.invoke(null);
        } catch (Throwable ignored) { }
        if (defaultEnv != null) {
            try {
                Method getControllers = defaultEnv.getClass().getMethod("getControllers");
                getControllers.setAccessible(true);
                Object[] controllers = (Object[]) getControllers.invoke(defaultEnv);
                if (countUsableControllers(defaultEnv, controllers) > 0)
                    return defaultEnv;
            } catch (Throwable ignored) { }
        }
        try {
            Class<?> xinputEnv = Class.forName("net.java.games.input.XInputEnvironmentPlugin");
            Object env = xinputEnv.getConstructor().newInstance();
            if (Boolean.TRUE.equals(xinputEnv.getMethod("isSupported").invoke(env)))
                return env;
        } catch (Throwable ignored) { }
        return defaultEnv;
    }

    public static void logAllDetectedControllersToConsole() {
        System.out.println("--- Controllers detection ---");
        try {
            Class<?> envClass = Class.forName("net.java.games.input.ControllerEnvironment");
            Method getDefault = envClass.getMethod("getDefaultEnvironment");
            getDefault.setAccessible(true);
            Object defaultEnv = getDefault.invoke(null);
            if (defaultEnv != null) {
                Method getControllers = defaultEnv.getClass().getMethod("getControllers");
                getControllers.setAccessible(true);
                Object[] arr = (Object[]) getControllers.invoke(defaultEnv);
                System.out.println("DirectInput (default env): " + (arr == null ? 0 : arr.length) + " total device(s)");
                if (arr != null) {
                    for (int i = 0; i < arr.length; i++) {
                        Object c = arr[i];
                        String typeStr = "?";
                        String nameStr = "?";
                        try {
                            Method getType = c.getClass().getMethod("getType");
                            getType.setAccessible(true);
                            Object type = getType.invoke(c);
                            typeStr = type != null ? type.toString() : "null";
                            Method getName = c.getClass().getMethod("getName");
                            getName.setAccessible(true);
                            nameStr = (String) getName.invoke(c);
                            if (nameStr == null) nameStr = "null";
                        } catch (Throwable t) {
                            typeStr = t.getMessage();
                        }
                        System.out.println("  [" + i + "] type=" + typeStr + " name=\"" + nameStr + "\"");
                    }
                }
            }
        } catch (Throwable t) {
            System.out.println("DirectInput: " + t.getClass().getSimpleName() + " " + t.getMessage());
        }
        try {
            Class<?> xinputEnv = Class.forName("net.java.games.input.XInputEnvironmentPlugin");
            Object xiEnv = xinputEnv.getConstructor().newInstance();
            boolean supported = Boolean.TRUE.equals(xinputEnv.getMethod("isSupported").invoke(xiEnv));
            System.out.println("XInput plugin: isSupported=" + supported);
            if (supported) {
                Object[] arr = (Object[]) xiEnv.getClass().getMethod("getControllers").invoke(xiEnv);
                System.out.println("  getControllers(): " + (arr == null ? 0 : arr.length) + " controller(s)");
                if (arr != null) {
                    for (int i = 0; i < arr.length; i++) {
                        String nameStr = "?";
                        try {
                            nameStr = (String) arr[i].getClass().getMethod("getName").invoke(arr[i]);
                        } catch (Throwable ignored) { }
                        System.out.println("  [" + i + "] \"" + nameStr + "\"");
                    }
                }
            }
        } catch (Throwable t) {
            System.out.println("XInput plugin: " + t.getClass().getSimpleName() + " " + t.getMessage());
        }
        try {
            Class<?> xi = Class.forName("de.ralleytn.wrapper.microsoft.xinput.XInput");
            Object instance = xi.getMethod("create").invoke(null);
            int flagGamepad = xi.getField("XINPUT_FLAG_GAMEPAD").getInt(null);
            int errSuccess = xi.getField("ERROR_SUCCESS").getInt(null);
            int errNotConnected = xi.getField("ERROR_DEVICE_NOT_CONNECTED").getInt(null);
            System.out.println("Direct XInput API (user indices 0-3):");
            for (int user = 0; user < 4; user++) {
                Object caps = Class.forName("de.ralleytn.wrapper.microsoft.xinput.XInputCapabilities").getConstructor().newInstance();
                int result = (int) xi.getMethod("XInputGetCapabilities", int.class, int.class, caps.getClass()).invoke(instance, user, flagGamepad, caps);
                String status = (result == errSuccess) ? "connected" : (result == errNotConnected ? "not connected" : "error " + result);
                System.out.println("  user " + user + ": " + status);
            }
        } catch (Throwable t) {
            System.out.println("Direct XInput API: " + t.getClass().getSimpleName() + " " + t.getMessage());
        }
        System.out.println("--- End controllers detection ---");
    }

    private static List<Object> getAllControllersFromBothEnvironments() {
        List<Object> list = new ArrayList<>();
        try {
            Class<?> envClass = Class.forName("net.java.games.input.ControllerEnvironment");
            Method getDefault = envClass.getMethod("getDefaultEnvironment");
            getDefault.setAccessible(true);
            Object defaultEnv = getDefault.invoke(null);
            if (defaultEnv != null) {
                Method getControllers = defaultEnv.getClass().getMethod("getControllers");
                getControllers.setAccessible(true);
                Object[] arr = (Object[]) getControllers.invoke(defaultEnv);
                if (arr != null) {
                    for (Object c : arr) {
                        try {
                            Method getType = c.getClass().getMethod("getType");
                            getType.setAccessible(true);
                            Object type = getType.invoke(c);
                            if (type != null && isControllerTypeUsable(type.toString().toUpperCase()))
                                list.add(c);
                        } catch (Throwable ignored) { }
                    }
                }
            }
        } catch (Throwable ignored) { }
        try {
            Class<?> xinputEnv = Class.forName("net.java.games.input.XInputEnvironmentPlugin");
            Object xiEnv = xinputEnv.getConstructor().newInstance();
            if (Boolean.TRUE.equals(xinputEnv.getMethod("isSupported").invoke(xiEnv))) {
                Object[] arr = (Object[]) xiEnv.getClass().getMethod("getControllers").invoke(xiEnv);
                if (arr != null) {
                    for (Object c : arr) list.add(c);
                }
            }
        } catch (Throwable ignored) { }
        List<Integer> directXinput = getConnectedXInputUserIndices();
        for (Integer userIndex : directXinput) {
            list.add(userIndex);
        }
        return list;
    }

    private static List<Integer> getConnectedXInputUserIndices() {
        List<Integer> out = new ArrayList<>();
        try {
            Class<?> xi = Class.forName("de.ralleytn.wrapper.microsoft.xinput.XInput");
            Object instance = xi.getMethod("create").invoke(null);
            int flagGamepad = xi.getField("XINPUT_FLAG_GAMEPAD").getInt(null);
            int errSuccess = xi.getField("ERROR_SUCCESS").getInt(null);
            for (int user = 0; user < 4; user++) {
                Object caps = Class.forName("de.ralleytn.wrapper.microsoft.xinput.XInputCapabilities").getConstructor().newInstance();
                int result = (int) xi.getMethod("XInputGetCapabilities", int.class, int.class, caps.getClass()).invoke(instance, user, flagGamepad, caps);
                if (result == errSuccess) out.add(user);
            }
        } catch (Throwable ignored) { }
        return out;
    }

    private static boolean isControllerTypeUsable(String typeStr) {
        if (typeStr == null) return false;
        switch (typeStr) {
            case "STICK":
            case "GAMEPAD":
                return true;
            case "UNKNOWN":
                return true;
            default:
                return false;
        }
    }

    private static int countUsableControllers(Object env, Object[] controllers) {
        if (controllers == null) return 0;
        int n = 0;
        try {
            for (Object c : controllers) {
                Method getType = c.getClass().getMethod("getType");
                getType.setAccessible(true);
                Object type = getType.invoke(c);
                if (type != null && isControllerTypeUsable(type.toString().toUpperCase())) n++;
            }
        } catch (Throwable ignored) { }
        return n;
    }

    public static List<String> getDiscoveredJoystickDevices() {
        long now = System.currentTimeMillis();
        if (!discoveredDevices.isEmpty() && now - lastDiscoveryTime < DISCOVERY_INTERVAL_MS)
            return new ArrayList<>(discoveredDevices);
        lastDiscoveryTime = now;
        if (now - lastControllerLogTime >= CONTROLLER_LOG_INTERVAL_MS) {
            lastControllerLogTime = now;
            logAllDetectedControllersToConsole();
        }
        List<String> out = new ArrayList<>();
        try {
            List<Object> controllers = getAllControllersFromBothEnvironments();
            int index = 0;
            for (Object controller : controllers) {
                String name = "Device " + index;
                if (controller instanceof Integer) {
                    name = "XInput " + controller + ": Xbox Controller";
                } else {
                    try {
                        String n = (String) controller.getClass().getMethod("getName").invoke(controller);
                        if (n != null && !n.isEmpty()) name = n;
                    } catch (Throwable ignored) { }
                }
                out.add(index + ": " + name);
                index++;
            }
            discoveredDevices = out;
        } catch (Throwable ignored) {
            discoveredDevices = out.isEmpty() ? Collections.emptyList() : out;
        }
        return new ArrayList<>(discoveredDevices);
    }

    static {
        Logger.getLogger("net.java.games.input").setLevel(Level.WARNING);
    }
    private static final int STICK_INDEX = 0;
    private static final int MAX_AXES = 6;
    private static final int MAX_BUTTONS = 12;

    private static final int POV_NONE = -1;

    private final Set<KeyCode> keysPressed = ConcurrentHashMap.newKeySet();
    private Scene scene;
    private ScheduledExecutorService executor;
    private volatile boolean running;

    private static volatile Object cachedJoystickController;
    private static volatile long lastJoystickEnumerationTime;
    private static final long ENUMERATION_INTERVAL_MS = 1000;

    public SimDriverStationInput() {
    }

    public void setScene(Scene scene) {
        if (this.scene != null) {
            this.scene.removeEventHandler(KeyEvent.KEY_PRESSED, this::onKeyPressed);
            this.scene.removeEventHandler(KeyEvent.KEY_RELEASED, this::onKeyReleased);
        }
        this.scene = scene;
        if (scene != null) {
            scene.addEventHandler(KeyEvent.KEY_PRESSED, this::onKeyPressed);
            scene.addEventHandler(KeyEvent.KEY_RELEASED, this::onKeyReleased);
        }
    }

    private void onKeyPressed(KeyEvent e) {
        KeyCode code = e.getCode();
        if (code == KeyCode.W || code == KeyCode.A || code == KeyCode.S || code == KeyCode.D
                || code == KeyCode.J || code == KeyCode.L) {
            keysPressed.add(code);
            e.consume();
        }
    }

    private void onKeyReleased(KeyEvent e) {
        KeyCode code = e.getCode();
        if (code == KeyCode.W || code == KeyCode.A || code == KeyCode.S || code == KeyCode.D
                || code == KeyCode.J || code == KeyCode.L) {
            keysPressed.remove(code);
            e.consume();
        }
    }

    public void start() {
        if (running) return;
        running = true;
        executor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "SimDriverStationInput");
            t.setDaemon(true);
            return t;
        });
        executor.scheduleAtFixedRate(this::tick, 50, 50, TimeUnit.MILLISECONDS);
    }

    public void stop() {
        running = false;
        cachedJoystickController = null;
        if (executor != null && !executor.isShutdown()) {
            executor.shutdown();
            try {
                executor.awaitTermination(500, TimeUnit.MILLISECONDS);
            } catch (InterruptedException ignored) { }
            executor = null;
        }
        setScene(null);
    }

    private boolean isTeleop() {
        try {
            if (!NetworkTablesClient.isConnected()) return false;
            NetworkTableInstance instance = NetworkTableInstance.getDefault();
            if (instance == null) return false;
            NetworkTable fmsTable = instance.getTable("FMSInfo");
            if (fmsTable == null) return false;
            NetworkTableEntry entry = fmsTable.getEntry("FMSControlData");
            if (entry == null || !entry.exists()) return false;
            Number n = entry.getNumber(32.0);
            int mode = n.intValue();
            return mode == 33;
        } catch (Exception e) {
            return false;
        }
    }

    private Object getControllerForCurrentSelection() {
        String pref = getPreferredInputSource();
        if (pref == null || SOURCE_KEYBOARD.equals(pref)) return null;
        if (SOURCE_AUTO.equals(pref)) return getOrAcquireFirstJoystickController();
        int index = parseDeviceIndex(pref);
        if (index < 0) return getOrAcquireFirstJoystickController();
        return getJoystickControllerByIndex(index);
    }

    private static int parseDeviceIndex(String selection) {
        if (selection == null) return -1;
        int colon = selection.indexOf(':');
        if (colon <= 0) return -1;
        try {
            return Integer.parseInt(selection.substring(0, colon).trim());
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private Object getOrAcquireFirstJoystickController() {
        Object c = cachedJoystickController;
        if (c != null) return c;
        long now = System.currentTimeMillis();
        if (now - lastJoystickEnumerationTime < ENUMERATION_INTERVAL_MS) return null;
        lastJoystickEnumerationTime = now;
        Object first = getJoystickControllerByIndex(0);
        if (first != null) cachedJoystickController = first;
        return first;
    }

    private static volatile Object[] cachedJoystickList;
    private static volatile long cachedJoystickListTime;

    private Object getJoystickControllerByIndex(int targetIndex) {
        long now = System.currentTimeMillis();
        if (cachedJoystickList == null || now - cachedJoystickListTime >= ENUMERATION_INTERVAL_MS) {
            cachedJoystickListTime = now;
            List<Object> list = getAllControllersFromBothEnvironments();
            cachedJoystickList = list.toArray();
        }
        Object[] arr = cachedJoystickList;
        if (arr == null || targetIndex < 0 || targetIndex >= arr.length) return null;
        return arr[targetIndex];
    }

    private boolean readXInputStateAndPublish(NetworkTable simDS, int userIndex) {
        try {
            Class<?> xi = Class.forName("de.ralleytn.wrapper.microsoft.xinput.XInput");
            Object instance = xi.getMethod("create").invoke(null);
            Object state = Class.forName("de.ralleytn.wrapper.microsoft.xinput.XInputState").getConstructor().newInstance();
            int result = (int) xi.getMethod("XInputGetState", int.class, state.getClass()).invoke(instance, userIndex, state);
            int errSuccess = xi.getField("ERROR_SUCCESS").getInt(null);
            if (result != errSuccess) return false;
            try {
                state.getClass().getMethod("read").invoke(state);
            } catch (Throwable ignored) { }
            Object gamepad = state.getClass().getField("Gamepad").get(state);
            short lx = gamepad.getClass().getField("sThumbLX").getShort(gamepad);
            short ly = gamepad.getClass().getField("sThumbLY").getShort(gamepad);
            short rx = gamepad.getClass().getField("sThumbRX").getShort(gamepad);
            short ry = gamepad.getClass().getField("sThumbRY").getShort(gamepad);
            int bt = 0, rtVal = 0;
            try {
                Object bLeft = gamepad.getClass().getField("bLeftTrigger").get(gamepad);
                Object bRight = gamepad.getClass().getField("bRightTrigger").get(gamepad);
                bt = bLeft instanceof Number ? ((Number) bLeft).intValue() & 0xFF : 0;
                rtVal = bRight instanceof Number ? ((Number) bRight).intValue() & 0xFF : 0;
            } catch (Throwable ignored) { }
            int wButtons = gamepad.getClass().getField("wButtons").getInt(gamepad);
            double lxNorm = lx / 32767.0;
            double lyNorm = ly / 32767.0;
            double rxNorm = rx / 32767.0;
            double ryNorm = -ry / 32767.0;
            boolean leftTrigger = bt > 128;
            boolean rightTrigger = rtVal > 128;
            int XINPUT_GAMEPAD_A = 0x1000, XINPUT_GAMEPAD_B = 0x2000, XINPUT_GAMEPAD_X = 0x4000, XINPUT_GAMEPAD_Y = 0x8000;
            int XINPUT_GAMEPAD_LEFT_SHOULDER = 0x0100, XINPUT_GAMEPAD_RIGHT_SHOULDER = 0x0200;
            int XINPUT_GAMEPAD_BACK = 0x0020, XINPUT_GAMEPAD_START = 0x0010;
            int XINPUT_GAMEPAD_LEFT_THUMB = 0x0040, XINPUT_GAMEPAD_RIGHT_THUMB = 0x0080;
            int XINPUT_GAMEPAD_DPAD_UP = 0x0001, XINPUT_GAMEPAD_DPAD_DOWN = 0x0002, XINPUT_GAMEPAD_DPAD_LEFT = 0x0004, XINPUT_GAMEPAD_DPAD_RIGHT = 0x0008;
            boolean a = (wButtons & XINPUT_GAMEPAD_A) != 0;
            boolean b = (wButtons & XINPUT_GAMEPAD_B) != 0;
            boolean x = (wButtons & XINPUT_GAMEPAD_X) != 0;
            boolean y = (wButtons & XINPUT_GAMEPAD_Y) != 0;
            boolean lb = (wButtons & XINPUT_GAMEPAD_LEFT_SHOULDER) != 0;
            boolean rb = (wButtons & XINPUT_GAMEPAD_RIGHT_SHOULDER) != 0;
            boolean back = (wButtons & XINPUT_GAMEPAD_BACK) != 0;
            boolean start = (wButtons & XINPUT_GAMEPAD_START) != 0;
            boolean lsb = (wButtons & XINPUT_GAMEPAD_LEFT_THUMB) != 0;
            boolean rsb = (wButtons & XINPUT_GAMEPAD_RIGHT_THUMB) != 0;
            int pov = POV_NONE;
            if ((wButtons & XINPUT_GAMEPAD_DPAD_UP) != 0) pov = (wButtons & XINPUT_GAMEPAD_DPAD_LEFT) != 0 ? 315 : (wButtons & XINPUT_GAMEPAD_DPAD_RIGHT) != 0 ? 45 : 0;
            else if ((wButtons & XINPUT_GAMEPAD_DPAD_DOWN) != 0) pov = (wButtons & XINPUT_GAMEPAD_DPAD_LEFT) != 0 ? 225 : (wButtons & XINPUT_GAMEPAD_DPAD_RIGHT) != 0 ? 135 : 180;
            else if ((wButtons & XINPUT_GAMEPAD_DPAD_LEFT) != 0) pov = 270;
            else if ((wButtons & XINPUT_GAMEPAD_DPAD_RIGHT) != 0) pov = 90;
            NetworkTable stickTable = simDS.getSubTable("joystick" + STICK_INDEX);
            stickTable.getEntry("source").setString("joystick");
            stickTable.getEntry("axis0").setDouble(lxNorm);
            stickTable.getEntry("axis1").setDouble(lyNorm);
            stickTable.getEntry("axis2").setDouble(rxNorm);
            stickTable.getEntry("axis3").setDouble(ryNorm);
            stickTable.getEntry("axis4").setDouble(leftTrigger ? 1.0 : 0.0);
            stickTable.getEntry("axis5").setDouble(rightTrigger ? 1.0 : 0.0);
            for (int i = 6; i < MAX_AXES; i++) stickTable.getEntry("axis" + i).setDouble(0.0);
            stickTable.getEntry("axisCount").setDouble(6);
            stickTable.getEntry("button1").setBoolean(a);
            stickTable.getEntry("button2").setBoolean(b);
            stickTable.getEntry("button3").setBoolean(x);
            stickTable.getEntry("button4").setBoolean(y);
            stickTable.getEntry("button5").setBoolean(lb);
            stickTable.getEntry("button6").setBoolean(rb);
            stickTable.getEntry("button7").setBoolean(back);
            stickTable.getEntry("button8").setBoolean(start);
            stickTable.getEntry("button9").setBoolean(lsb);
            stickTable.getEntry("button10").setBoolean(rsb);
            for (int i = 11; i <= MAX_BUTTONS; i++) stickTable.getEntry("button" + i).setBoolean(false);
            stickTable.getEntry("buttonCount").setDouble(10);
            publishXboxReadings(simDS, lxNorm, lyNorm, rxNorm, ryNorm, leftTrigger, rightTrigger, lb, rb, back, start, lsb, rsb, a, b, x, y, pov);
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    private void publishXboxReadings(NetworkTable simDS,
            double leftStickX, double leftStickY, double rightStickX, double rightStickY,
            boolean leftTrigger, boolean rightTrigger, boolean leftBumper, boolean rightBumper,
            boolean backButton, boolean startButton, boolean lsb, boolean rsb,
            boolean aButton, boolean bButton, boolean xButton, boolean yButton,
            int pov) {
        NetworkTable xbox = simDS.getSubTable("xbox0");
        xbox.getEntry("left_stick_x").setDouble(leftStickX);
        xbox.getEntry("left_stick_y").setDouble(leftStickY);
        xbox.getEntry("right_stick_x").setDouble(rightStickX);
        xbox.getEntry("right_stick_y").setDouble(rightStickY);
        xbox.getEntry("left_trigger").setBoolean(leftTrigger);
        xbox.getEntry("right_trigger").setBoolean(rightTrigger);
        xbox.getEntry("left_bumper").setBoolean(leftBumper);
        xbox.getEntry("right_bumper").setBoolean(rightBumper);
        xbox.getEntry("back_button").setBoolean(backButton);
        xbox.getEntry("start_button").setBoolean(startButton);
        xbox.getEntry("lsb").setBoolean(lsb);
        xbox.getEntry("rsb").setBoolean(rsb);
        xbox.getEntry("a_button").setBoolean(aButton);
        xbox.getEntry("b_button").setBoolean(bButton);
        xbox.getEntry("x_button").setBoolean(xButton);
        xbox.getEntry("y_button").setBoolean(yButton);
        xbox.getEntry("pov").setDouble(pov);
    }

    private boolean readJoystickAndPublish(NetworkTable simDS, Object controller) {
        if (controller == null) return false;
        if (controller instanceof Integer) {
            return readXInputStateAndPublish(simDS, (Integer) controller);
        }
        try {
            NetworkTable stickTable = simDS.getSubTable("joystick" + STICK_INDEX);
            controller.getClass().getMethod("poll").invoke(controller);
            Object[] components = (Object[]) controller.getClass().getMethod("getComponents").invoke(controller);
            if (components == null) return false;

            double[] axes = new double[MAX_AXES];
            boolean[] buttons = new boolean[16];
            int axisCount = 0;
            int buttonCount = 0;
            int pov = POV_NONE;

            for (Object comp : components) {
                Object id = comp.getClass().getMethod("getIdentifier").invoke(comp);
                if (id == null) continue;
                String name = id.toString().toLowerCase();
                float value = (Float) comp.getClass().getMethod("getPollData").invoke(comp);

                if (name.startsWith("ax") || name.equals("x") || name.equals("y") || name.startsWith("z")
                        || name.startsWith("rx") || name.startsWith("ry") || name.startsWith("rz")) {
                    int idx = axisCount++;
                    if (idx < MAX_AXES) axes[idx] = Math.max(-1, Math.min(1, value));
                } else if (name.startsWith("button") || name.startsWith("b")) {
                    int idx = buttonCount++;
                    if (idx < buttons.length) buttons[idx] = value > 0.5f;
                } else if (name.startsWith("pov") || name.contains("hat")) {
                    int angle = (int) value;
                    if (angle >= 0) {
                        if (angle <= 22 || angle > 338) pov = 0;
                        else if (angle <= 67) pov = 45;
                        else if (angle <= 112) pov = 90;
                        else if (angle <= 157) pov = 135;
                        else if (angle <= 202) pov = 180;
                        else if (angle <= 247) pov = 225;
                        else if (angle <= 292) pov = 270;
                        else pov = 315;
                    }
                }
            }

            for (int i = 0; i < MAX_AXES; i++) {
                stickTable.getEntry("axis" + i).setDouble(i < axisCount ? axes[i] : 0.0);
            }
            for (int i = 0; i < MAX_BUTTONS; i++) {
                stickTable.getEntry("button" + (i + 1)).setBoolean(i < buttonCount && buttons[i]);
            }
            stickTable.getEntry("axisCount").setDouble(axisCount);
            stickTable.getEntry("buttonCount").setDouble(buttonCount);

            double lx = axisCount > 0 ? axes[0] : 0, ly = axisCount > 1 ? -axes[1] : 0;
            double rx = axisCount > 2 ? axes[2] : 0, ry = axisCount > 3 ? axes[3] : 0;
            boolean lt = axisCount > 4 && axes[4] > 0.5;
            boolean rt = axisCount > 5 && axes[5] > 0.5;
            if (!lt && axisCount > 4 && axes[4] > 0) lt = true;
            if (!rt && axisCount > 5 && axes[5] > 0) rt = true;
            boolean lb = buttonCount > 4 && buttons[4];
            boolean rb = buttonCount > 5 && buttons[5];
            boolean back = buttonCount > 6 && buttons[6];
            boolean start = buttonCount > 7 && buttons[7];
            boolean l3 = buttonCount > 8 && buttons[8];
            boolean r3 = buttonCount > 9 && buttons[9];
            boolean a = buttonCount > 0 && buttons[0];
            boolean b = buttonCount > 1 && buttons[1];
            boolean x = buttonCount > 2 && buttons[2];
            boolean y = buttonCount > 3 && buttons[3];
            publishXboxReadings(simDS, lx, ly, rx, ry, lt, rt, lb, rb, back, start, l3, r3, a, b, x, y, pov);
            return true;
        } catch (Throwable t) {
            cachedJoystickController = null;
            return false;
        }
    }

    private void publishKeyboardFallback(NetworkTable simDS, String sourceSuffix) {
        NetworkTable stickTable = simDS.getSubTable("joystick" + STICK_INDEX);
        double axis0 = 0, axis1 = 0, axis2 = 0;
        if (keysPressed.contains(KeyCode.A)) axis0 -= 1.0;
        if (keysPressed.contains(KeyCode.D)) axis0 += 1.0;
        if (keysPressed.contains(KeyCode.S)) axis1 -= 1.0;
        if (keysPressed.contains(KeyCode.W)) axis1 += 1.0;
        if (keysPressed.contains(KeyCode.J)) axis2 -= 1.0;
        if (keysPressed.contains(KeyCode.L)) axis2 += 1.0;

        stickTable.getEntry("axis0").setDouble(axis0);
        stickTable.getEntry("axis1").setDouble(axis1);
        stickTable.getEntry("axis2").setDouble(axis2);
        for (int i = 3; i < MAX_AXES; i++) {
            stickTable.getEntry("axis" + i).setDouble(0.0);
        }
        for (int i = 1; i <= MAX_BUTTONS; i++) {
            stickTable.getEntry("button" + i).setBoolean(false);
        }
        stickTable.getEntry("axisCount").setDouble(3);
        stickTable.getEntry("buttonCount").setDouble(0);
        stickTable.getEntry("source").setString("keyboard" + sourceSuffix);

        publishXboxReadings(simDS,
                axis0, axis1, axis2, 0,
                false, false, false, false,
                false, false, false, false,
                false, false, false, false,
                POV_NONE);
    }

    private void tick() {
        if (!running) return;
        if (!isTeleop()) return;

        try {
            NetworkTableInstance instance = NetworkTableInstance.getDefault();
            if (instance == null) return;
            NetworkTable funkyFMS = instance.getTable("FunkyFMS");
            if (funkyFMS == null) return;
            NetworkTable simDS = funkyFMS.getSubTable("SimDS");
            NetworkTable stickTable = simDS.getSubTable("joystick" + STICK_INDEX);

            String pref = getPreferredInputSource();
            if (SOURCE_KEYBOARD.equals(pref)) {
                publishKeyboardFallback(simDS, "");
            } else {
                Object controller = getControllerForCurrentSelection();
                if (controller != null && readJoystickAndPublish(simDS, controller)) {
                    stickTable.getEntry("source").setString("joystick");
                } else {
                    String suffix = (controller != null) ? " (unavailable)" : "";
                    publishKeyboardFallback(simDS, suffix);
                }
            }
            instance.flush();
        } catch (Exception e) {
        }
    }
}
