package com.funkylogclient;

import edu.wpi.first.networktables.NetworkTable;
import edu.wpi.first.networktables.NetworkTableEntry;
import edu.wpi.first.networktables.NetworkTableInstance;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.TextField;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;

import javafx.scene.input.ClipboardContent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;
import javafx.scene.control.TreeCell;
import javafx.scene.shape.SVGPath;
import javafx.scene.text.Font;
import javafx.scene.paint.Color;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class SidebarNetworkTablesChooser {
    private static TreeView<String> networkTablesTree;
    private static TextField searchField;
    private static Map<String, NetworkTableEntry> entries = new HashMap<>();
    private static Map<TreeItem<String>, String> itemToKeyMap = new HashMap<>();
    private static Set<String> sendableChooserPaths = new HashSet<>();
    private static Set<String> fieldPaths = new HashSet<>();
    private static volatile boolean isDragging = false;
    private static Thread updateThread = null;
    private static final long TREE_REFRESH_INTERVAL_MS = 3000;
    private static volatile long lastTreeUIRefreshTime = 0;
    private static int lastEntriesSignature = 0;

    public static ArrayList<javafx.scene.Node> getNetworkTablesChooser() {
        ArrayList<javafx.scene.Node> chooserElements = new ArrayList<>();

        Region topSpacing = new Region();
        topSpacing.setMinHeight(12);
        chooserElements.add(topSpacing);

        Text chooserLabel = new Text("NetworkTables Tree");
        chooserLabel.setStyle(Styles.SECTION_HEADER_STYLE);
        chooserElements.add(chooserLabel);

        Region afterTitleSpace = new Region();
        afterTitleSpace.setMinHeight(8);
        chooserElements.add(afterTitleSpace);

        HBox searchContainer = new HBox(10);
        searchContainer.setAlignment(Pos.CENTER_LEFT);
        searchContainer.setPadding(new Insets(0, 0, 8, 0));

        SVGPath searchIcon = new SVGPath();
        searchIcon.setContent(
                "M15.5 14h-.79l-.28-.27C15.41 12.59 16 11.11 16 9.5 16 5.91 13.09 3 9.5 3S3 5.91 3 9.5 5.91 16 9.5 16c1.61 0 3.09-.59 4.23-1.57l.27.28v.79l5 4.99L20.49 19l-4.99-5zm-6 0C7.01 14 5 11.99 5 9.5S7.01 5 9.5 5 14 7.01 14 9.5 11.99 14 9.5 14z");
        searchIcon.setFill(javafx.scene.paint.Color.valueOf("#808080"));
        searchIcon.setScaleX(0.8);
        searchIcon.setScaleY(0.8);

        searchField = new TextField();
        searchField.setPromptText("Search...");
        searchField.setStyle(Styles.SEARCH_BAR_STYLE);
        HBox.setHgrow(searchField, Priority.ALWAYS);

        searchField.focusedProperty().addListener((obs, old, focused) -> {
            if (focused) {
                searchField.setStyle(Styles.SEARCH_BAR_FOCUSED_STYLE);
                searchIcon.setFill(Color.web("#FF8C00"));
            } else {
                searchField.setStyle(Styles.SEARCH_BAR_STYLE);
                searchIcon.setFill(Color.web("#808080"));
            }
        });

        searchContainer.getChildren().addAll(searchIcon, searchField);
        chooserElements.add(searchContainer);

        networkTablesTree = new TreeView<>();
        networkTablesTree.setStyle(
                "-fx-background-color: " + Styles.BG_DARKEST + "; -fx-border-color: " + Styles.BORDER_DARK + "; -fx-border-width: 1px; -fx-border-radius: 8px; -fx-background-radius: 8px;");
        networkTablesTree.setRoot(createTreeRoot());
        networkTablesTree.setMinHeight(200);
        networkTablesTree.setPrefHeight(300);

        VBox treeContainer = new VBox();
        treeContainer.getChildren().add(networkTablesTree);
        VBox.setVgrow(networkTablesTree, javafx.scene.layout.Priority.ALWAYS);

        networkTablesTree.setCellFactory(param -> new TreeCell<String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setStyle(null);
                } else {
                    setText(item);

                    TreeItem<String> treeItem = getTreeItem();
                    boolean isEntry = treeItem != null && itemToKeyMap.containsKey(treeItem);

                    boolean isChooser = false;
                    boolean isField = false;
                    if (treeItem != null && !isEntry) {
                        String fullPath = buildFullPath(treeItem);
                        isChooser = sendableChooserPaths.contains(fullPath);
                        isField = fieldPaths.contains(fullPath);
                    }

                    if (isEntry) {
                        setStyle(Styles.TREE_CELL_VALUE_STYLE);
                    } else if (isChooser) {
                        setStyle(Styles.TREE_CELL_VALUE_STYLE.replace("normal", "bold"));
                    } else if (isField) {
                        setStyle(Styles.TREE_CELL_FIELD_STYLE);
                    } else {
                        setStyle(Styles.TREE_CELL_DEFAULT_STYLE);
                    }
                }
            }
        });

        setupDragAndDrop();
        chooserElements.add(treeContainer);

        searchField.textProperty().addListener((observable, oldValue, newValue) -> {
            filterTree(newValue);
        });

        startNetworkTablesUpdate();

        return chooserElements;
    }

    private static TreeItem<String> createTreeRoot() {
        TreeItem<String> root = new TreeItem<>("NetworkTables");
        root.setExpanded(true);
        return root;
    }

    private static void filterTree(String searchText) {
        lastTreeUIRefreshTime = System.currentTimeMillis();
        if (searchText == null || searchText.trim().isEmpty()) {
            TreeItem<String> oldRoot = networkTablesTree.getRoot();
            Set<String> expandedPaths = saveExpandedState(oldRoot);

            itemToKeyMap.clear();
            TreeItem<String> newRoot = createTreeRoot();
            populateTree(newRoot);
            restoreExpandedState(newRoot, expandedPaths);
            networkTablesTree.setRoot(newRoot);
        } else {
            TreeItem<String> oldRoot = networkTablesTree.getRoot();
            Set<String> expandedPaths = saveExpandedState(oldRoot);

            TreeItem<String> filteredRoot = createFilteredTree(searchText.toLowerCase());
            restoreExpandedState(filteredRoot, expandedPaths);
            networkTablesTree.setRoot(filteredRoot);
        }
    }

    private static TreeItem<String> createFilteredTree(String searchText) {
        TreeItem<String> root = new TreeItem<>("NetworkTables");
        root.setExpanded(true);

        synchronized (SidebarNetworkTablesChooser.class) {
            for (Map.Entry<String, NetworkTableEntry> entry : entries.entrySet()) {
                String key = entry.getKey().toLowerCase();
                boolean matchesKey = key.contains(searchText);

                if (matchesKey) {
                    addEntryToTree(root, entry.getKey(), entry.getValue());
                }
            }

            expandMatchingPaths(root, searchText);
        }

        return root;
    }

    private static void expandMatchingPaths(TreeItem<String> node, String searchText) {
        if (node == null)
            return;

        boolean hasMatchingDescendant = false;
        for (TreeItem<String> child : node.getChildren()) {
            expandMatchingPaths(child, searchText);

            String value = child.getValue();
            if (value.toLowerCase().contains(searchText)) {
                hasMatchingDescendant = true;
            }

            boolean childHasMatch = child.getChildren().stream()
                    .anyMatch(c -> c.getValue().toLowerCase().contains(searchText));

            if (childHasMatch) {
                hasMatchingDescendant = true;
            }
        }

        if (hasMatchingDescendant) {
            node.setExpanded(true);
        }
    }

    private static void addEntryToTree(TreeItem<String> root, String key, NetworkTableEntry entry) {
        String[] parts = key.split("/");
        TreeItem<String> current = root;

        for (int i = 0; i < parts.length; i++) {
            String part = parts[i];
            if (part.isEmpty())
                continue;

            boolean isLastPart = (i == parts.length - 1);

            if (isLastPart && entry.exists()) {
                TreeItem<String> entryItem = new TreeItem<>(part);
                itemToKeyMap.put(entryItem, key);
                current.getChildren().add(entryItem);
            } else {
                TreeItem<String> child = findChild(current, part);
                if (child == null) {
                    child = new TreeItem<>(part);
                    current.getChildren().add(child);
                }
                current = child;
            }
        }
    }

    private static TreeItem<String> findChild(TreeItem<String> parent, String name) {
        for (TreeItem<String> child : parent.getChildren()) {
            if (child.getValue().equals(name)) {
                return child;
            }
        }
        return null;
    }

    private static void populateTree(TreeItem<String> root) {
        if (!NetworkTablesClient.isConnected()) {
            TreeItem<String> disconnectedItem = new TreeItem<>("Not Connected");
            root.getChildren().add(disconnectedItem);
            return;
        }

        synchronized (SidebarNetworkTablesChooser.class) {
            for (Map.Entry<String, NetworkTableEntry> entry : entries.entrySet()) {
                addEntryToTree(root, entry.getKey(), entry.getValue());
            }
        }
    }

    private static int computeEntriesSignature(Map<String, NetworkTableEntry> entries,
            Set<String> chooserPaths, Set<String> fieldPaths) {
        int hash = 31 * chooserPaths.hashCode() + fieldPaths.hashCode();
        for (String key : entries.keySet()) {
            hash = 31 * hash + key.hashCode();
        }
        return hash;
    }

    private static void startNetworkTablesUpdate() {
        updateThread = new Thread(() -> {
            while (true) {
                try {
                    Thread.sleep(500);
                    
                    if (isDragging) {
                        continue;
                    }
                    
                    if (NetworkTablesClient.isConnected()) {
                        NetworkTableInstance instance = NetworkTablesClient.getInstance();
                        if (instance != null) {
                            Map<String, NetworkTableEntry> newEntries = new HashMap<>();

                            try {
                                String[] tableRoots = { "SmartDashboard", "Shuffleboard", "LiveWindow",
                                        "FMSInfo", "Preferences" };

                                Set<String> newChooserPaths = new HashSet<>();
                                Set<String> newFieldPaths = new HashSet<>();
                                
                                boolean skipUpdate = false;
                                synchronized (SidebarNetworkTablesChooser.class) {
                                    if (isDragging) {
                                        skipUpdate = true;
                                    } else {
                                        sendableChooserPaths = newChooserPaths;
                                        fieldPaths = newFieldPaths;
                                    }
                                }
                                
                                if (skipUpdate) {
                                    continue;
                                }

                                for (String tableRoot : tableRoots) {
                                    NetworkTable table = instance.getTable(tableRoot);
                                    fetchEntriesRecursive(table, tableRoot, newEntries);
                                }
                            } catch (Exception e) {
                                System.err.println("Error getting NetworkTables entries: " + e.getMessage());
                                e.printStackTrace();
                            }

                            boolean skipUpdate = false;
                            synchronized (SidebarNetworkTablesChooser.class) {
                                if (isDragging) {
                                    skipUpdate = true;
                                } else {
                                    entries = newEntries;
                                }
                            }
                            
                            if (skipUpdate) {
                                continue;
                            }

                            String searchText = searchField.getText();
                            boolean searchEmpty = searchText == null || searchText.trim().isEmpty();
                            if (searchEmpty) {
                                int newSignature = computeEntriesSignature(newEntries, sendableChooserPaths, fieldPaths);
                                long now = System.currentTimeMillis();
                                boolean dataChanged = newSignature != lastEntriesSignature;
                                boolean intervalElapsed = (now - lastTreeUIRefreshTime) >= TREE_REFRESH_INTERVAL_MS;
                                if (dataChanged && intervalElapsed) {
                                    lastEntriesSignature = newSignature;
                                    lastTreeUIRefreshTime = now;
                                    Platform.runLater(() -> {
                                        if (isDragging) return;
                                        TreeItem<String> oldRoot = networkTablesTree.getRoot();
                                        Set<String> expandedPaths = saveExpandedState(oldRoot);
                                        Map<TreeItem<String>, String> oldItemToKeyMap = new HashMap<>(itemToKeyMap);
                                        itemToKeyMap.clear();
                                        TreeItem<String> newRoot = createTreeRoot();
                                        populateTree(newRoot);
                                        restoreExpandedState(newRoot, expandedPaths);
                                        itemToKeyMap.putAll(oldItemToKeyMap);
                                        networkTablesTree.setRoot(newRoot);
                                    });
                                }
                            }
                        }
                    } else {
                        if (!isDragging) {
                            long now = System.currentTimeMillis();
                            if ((now - lastTreeUIRefreshTime) >= TREE_REFRESH_INTERVAL_MS) {
                                lastTreeUIRefreshTime = now;
                                lastEntriesSignature = 0;
                                Platform.runLater(() -> {
                                    if (!isDragging) {
                                        networkTablesTree.setRoot(createTreeRoot());
                                        populateTree(networkTablesTree.getRoot());
                                    }
                                });
                            }
                        }
                    }
                } catch (InterruptedException e) {
                    break;
                }
            }
        });
        updateThread.setDaemon(true);
        updateThread.start();
    }
    
    public static void shutdown() {
        synchronized (SidebarNetworkTablesChooser.class) {
            if (updateThread != null && updateThread.isAlive()) {
                updateThread.interrupt();
            }
            entries.clear();
            itemToKeyMap.clear();
            sendableChooserPaths.clear();
            fieldPaths.clear();
        }
    }

    private static void setupDragAndDrop() {
        networkTablesTree.setOnDragDetected(event -> {
            TreeItem<String> selectedItem = networkTablesTree.getSelectionModel().getSelectedItem();
            if (selectedItem != null && !selectedItem.getValue().equals("NetworkTables")
                    && !selectedItem.getValue().equals("Not Connected")) {

                String key = null;
                String displayValue = selectedItem.getValue();

                synchronized (SidebarNetworkTablesChooser.class) {
                    String storedKey = itemToKeyMap.get(selectedItem);
                    if (storedKey != null) {
                        key = storedKey;
                    } else {
                        key = getFullKeyPath(selectedItem);
                    }
                    
                    boolean isValidKey = false;
                    if (key != null) {
                        String fullPath = buildFullPath(selectedItem);
                        isValidKey = entries.containsKey(key) || 
                                    sendableChooserPaths.contains(key) || 
                                    fieldPaths.contains(key) ||
                                    sendableChooserPaths.contains(fullPath) ||
                                    fieldPaths.contains(fullPath) ||
                                    (key.endsWith("/active") && sendableChooserPaths.contains(key.substring(0, key.length() - 7)));
                    }
                    
                    if (isValidKey) {
                        isDragging = true;
                        
                        Dragboard dragboard = networkTablesTree.startDragAndDrop(TransferMode.COPY);
                        ClipboardContent content = new ClipboardContent();
                        content.putString(key);
                        dragboard.setContent(content);

                        Text dragText = new Text(displayValue);
                        dragText.setFont(Font.font(12));
                        dragText.setFill(Color.WHITE);

                        javafx.scene.Group dragGroup = new javafx.scene.Group();
                        javafx.scene.layout.StackPane dragPane = new javafx.scene.layout.StackPane(dragText);
                        dragPane.setStyle(
                                "-fx-background-color: #FF8C00; -fx-padding: 6px 12px; -fx-background-radius: 6px;");
                        dragGroup.getChildren().add(dragPane);

                        javafx.scene.SnapshotParameters params = new javafx.scene.SnapshotParameters();
                        params.setFill(Color.TRANSPARENT);
                        javafx.scene.image.WritableImage snapshot = dragGroup.snapshot(params, null);
                        dragboard.setDragView(snapshot, -10, -10);

                        System.out.println("Dragging key: " + key);
                        event.consume();
                    } else {
                        System.out.println("Cannot drag - key not found: " + key);
                    }
                }
            }
        });
        
        networkTablesTree.setOnDragDone(event -> {
            synchronized (SidebarNetworkTablesChooser.class) {
                isDragging = false;
            }
        });
    }

    private static String getFullKeyPath(TreeItem<String> item) {
        if (item == null || item.getValue().equals("NetworkTables")) {
            return null;
        }

        String storedKey = itemToKeyMap.get(item);
        if (storedKey != null) {
            return storedKey;
        }

        StringBuilder path = new StringBuilder();
        TreeItem<String> current = item;

        while (current != null && !current.getValue().equals("NetworkTables")) {
            String value = current.getValue();
            int equalsIndex = value.indexOf(" = ");
            String partName = equalsIndex >= 0 ? value.substring(0, equalsIndex) : value;

            if (path.length() > 0) {
                path.insert(0, "/");
            }
            path.insert(0, partName);
            current = current.getParent();
        }

        String fullPath = path.toString();
        synchronized (SidebarNetworkTablesChooser.class) {
            if (entries.containsKey(fullPath)) {
                return fullPath;
            } else if (sendableChooserPaths.contains(fullPath)) {
                return fullPath + "/active";
            } else if (fieldPaths.contains(fullPath)) {
                return fullPath;
            }
            return null;
        }
    }

    private static Set<String> saveExpandedState(TreeItem<String> root) {
        Set<String> expandedPaths = new HashSet<>();
        if (root != null) {
            saveExpandedStateRecursive(root, "", expandedPaths);
        }
        return expandedPaths;
    }

    private static void saveExpandedStateRecursive(TreeItem<String> item, String path, Set<String> expandedPaths) {
        String currentPath = path.isEmpty() ? item.getValue() : path + "/" + item.getValue();
        if (item.isExpanded()) {
            expandedPaths.add(currentPath);
        }
        for (TreeItem<String> child : item.getChildren()) {
            saveExpandedStateRecursive(child, currentPath, expandedPaths);
        }
    }

    private static void restoreExpandedState(TreeItem<String> root, Set<String> expandedPaths) {
        if (root != null) {
            restoreExpandedStateRecursive(root, "", expandedPaths);
        }
    }

    private static void restoreExpandedStateRecursive(TreeItem<String> item, String path, Set<String> expandedPaths) {
        String currentPath = path.isEmpty() ? item.getValue() : path + "/" + item.getValue();
        String value = item.getValue();
        int equalsIndex = value.indexOf(" = ");
        String nodeName = equalsIndex >= 0 ? value.substring(0, equalsIndex) : value;
        String nodePath = path.isEmpty() ? nodeName : path + "/" + nodeName;

        if (expandedPaths.contains(currentPath) || expandedPaths.contains(nodePath)) {
            item.setExpanded(true);
        }
        for (TreeItem<String> child : item.getChildren()) {
            restoreExpandedStateRecursive(child, currentPath, expandedPaths);
        }
    }

    private static void fetchEntriesRecursive(NetworkTable table, String prefix,
            Map<String, NetworkTableEntry> entries) {
        try {
            if (table == null) {
                return;
            }

            boolean hasActive = false;
            boolean hasOptions = false;
            boolean hasRobot = false;
            boolean hasType = false;
            boolean hasPoseField = false;
            try {
                var keys = table.getKeys();
                for (String key : keys) {
                    if (key.equals("active")) {
                        hasActive = true;
                    } else if (key.equals("options")) {
                        hasOptions = true;
                    } else if (key.equals("robot") || key.contains("Robot")) {
                        hasRobot = true;
                    } else if (key.equals(".type")) {
                        hasType = true;
                    } else if (key.contains("Pose") || key.contains("Field")) {
                        hasPoseField = true;
                    }
                }

                if (hasActive && hasOptions) {
                    sendableChooserPaths.add(prefix);
                }

                if (hasRobot || (hasType && hasPoseField)) {
                    fieldPaths.add(prefix);
                }

                for (String key : keys) {
                    String fullKey = prefix + "/" + key;
                    NetworkTableEntry entry = table.getEntry(key);
                    if (entry != null) {
                        entries.put(fullKey, entry);
                    }
                }
            } catch (Exception e) {
            }

            try {
                var subTables = table.getSubTables();
                for (String subTableName : subTables) {
                    String subPrefix = prefix + "/" + subTableName;
                    NetworkTable subTable = table.getSubTable(subTableName);
                    fetchEntriesRecursive(subTable, subPrefix, entries);
                }
            } catch (Exception e) {
            }
        } catch (Exception e) {
            System.err.println("Error fetching entries recursively at prefix " + prefix + ": " + e.getMessage());
        }
    }

    private static String buildFullPath(TreeItem<String> item) {
        if (item == null || item.getValue().equals("NetworkTables")) {
            return "";
        }

        StringBuilder path = new StringBuilder();
        TreeItem<String> current = item;

        while (current != null && !current.getValue().equals("NetworkTables")) {
            String value = current.getValue();
            int equalsIndex = value.indexOf(" = ");
            String partName = equalsIndex >= 0 ? value.substring(0, equalsIndex) : value;

            if (path.length() > 0) {
                path.insert(0, "/");
            }
            path.insert(0, partName);
            current = current.getParent();
        }

        return path.toString();
    }
}
