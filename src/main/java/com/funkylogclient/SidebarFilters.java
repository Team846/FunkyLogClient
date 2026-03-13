package com.funkylogclient;

import java.util.ArrayList;

import javafx.beans.value.ChangeListener;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.CheckBox;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;

public class SidebarFilters {
    public static ArrayList<Node> getSidebarFilters() {
        ArrayList<Node> filters = new ArrayList<Node>();

        Region topSpacing = new Region();
        topSpacing.setMinHeight(4);
        filters.add(topSpacing);

        Text filterByText = new Text("Filters");
        filterByText.setStyle(Styles.SECTION_HEADER_STYLE);
        filters.add(filterByText);

        Region divider = new Region();
        divider.setMinHeight(1);
        divider.setPrefHeight(1);
        divider.setStyle("-fx-background-color: " + Styles.BORDER_DARK + ";");
        filters.add(divider);

        filters.add(makeFiltersBox());

        Region bottomDivider = new Region();
        bottomDivider.setMinHeight(1);
        bottomDivider.setPrefHeight(1);
        bottomDivider.setStyle("-fx-background-color: " + Styles.BORDER_DARK + ";");
        filters.add(bottomDivider);

        return filters;
    }

    private static VBox makeFiltersBox() {
        VBox filtersBox = new VBox(8);
        filtersBox.setPadding(new Insets(10, 8, 10, 8));

        HBox errorBox = createFilterRow("Errors", true, (observable, oldValue, newValue) -> {
            FunkyLogSorter.setErrorsAllowed(newValue);
        });

        HBox warningBox = createFilterRow("Warnings", true, (observable, oldValue, newValue) -> {
            FunkyLogSorter.setWarningsAllowed(newValue);
        });

        HBox logsBox = createFilterRow("Logs", true, (observable, oldValue, newValue) -> {
            FunkyLogSorter.setLogsAllowed(newValue);
        });

        HBox teleopBox = createFilterRow("Teleop", true, (observable, oldValue, newValue) -> {
            FunkyLogSorter.setTeleopAllowed(newValue);
        });

        HBox autoBox = createFilterRow("Auto", true, (observable, oldValue, newValue) -> {
            FunkyLogSorter.setAutoAllowed(newValue);
        });

        HBox disabledBox = createFilterRow("Disabled", true, (observable, oldValue, newValue) -> {
            FunkyLogSorter.setDisabledAllowed(newValue);
        });

        filtersBox.getChildren().addAll(errorBox, warningBox, logsBox, teleopBox, autoBox, disabledBox);

        return filtersBox;
    }

    private static HBox createFilterRow(String label, boolean defaultSelected, ChangeListener<Boolean> listener) {
        HBox row = new HBox(12);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setPadding(new Insets(4, 8, 4, 8));
        row.setStyle("-fx-background-color: #2A2F35; -fx-background-radius: 6px;");

        row.setOnMouseEntered(e -> row.setStyle("-fx-background-color: #323840; -fx-background-radius: 6px;"));
        row.setOnMouseExited(e -> row.setStyle("-fx-background-color: #2A2F35; -fx-background-radius: 6px;"));

        CheckBox checkBox = new CheckBox();
        checkBox.setSelected(defaultSelected);
        checkBox.setStyle(Styles.CHECKBOX_STYLE);
        checkBox.selectedProperty().addListener(listener);

        Text labelText = new Text(label);
        labelText.setStyle("-fx-font-size: 13px; -fx-fill: " + Styles.TEXT_PRIMARY + "; -fx-font-family: " + Styles.FONT_FAMILY + ";");

        row.getChildren().addAll(checkBox, labelText);

        row.setOnMouseClicked(e -> {
            if (e.getTarget() != checkBox) {
                checkBox.setSelected(!checkBox.isSelected());
            }
        });

        return row;
    }
}
