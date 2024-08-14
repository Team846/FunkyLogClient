package com.funkylogclient;

import java.util.ArrayList;

import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.geometry.Insets;
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
        topSpacing.setMinHeight(20);
        filters.add(topSpacing);

        Text filterByText = new Text("Filter by:");
        filterByText.setStyle(Styles.TEXT_STYLE);
        filters.add(filterByText);

        Region midLinedSpacing = new Region();
        midLinedSpacing.setMinHeight(3);
        midLinedSpacing.setStyle("-fx-border-width: 1px; -fx-border-color: transparent transparent #aaa transparent");
        filters.add(midLinedSpacing);

        filters.add(makeFiltersBox());

        Region bottomLinedSpacing = new Region();
        bottomLinedSpacing.setMinHeight(10);
        bottomLinedSpacing.setStyle("-fx-border-width: 1px; -fx-border-color: transparent transparent #aaa transparent");
        filters.add(bottomLinedSpacing);

        return filters;
    }

    private static VBox makeFiltersBox() {
        VBox filtersBox = new VBox(10);
        filtersBox.setPadding(new Insets(15.0, 5.0, 5.0, 5.0));

        HBox errorBox = new HBox(15);

        CheckBox errorsCheckBox = new CheckBox();
        errorsCheckBox.setSelected(true);
        errorsCheckBox.selectedProperty().addListener(new ChangeListener<Boolean>() {
            @Override
            public void changed(ObservableValue<? extends Boolean> observable, Boolean oldValue, Boolean newValue) {
                FunkyLogSorter.setErrorsAllowed(newValue);
            }
        });
        errorBox.getChildren().add(errorsCheckBox);

        Text errorBoxLabel = new Text("Errors");
        errorBoxLabel.setStyle(Styles.TEXT_MED);
        errorBox.getChildren().add(errorBoxLabel);

        HBox warningBox = new HBox(15);

        CheckBox warningsCheckBox = new CheckBox();
        warningsCheckBox.setSelected(true);
        warningsCheckBox.selectedProperty().addListener(new ChangeListener<Boolean>() {
            @Override
            public void changed(ObservableValue<? extends Boolean> observable, Boolean oldValue, Boolean newValue) {
                FunkyLogSorter.setWarningsAllowed(newValue);
            }
        });
        warningBox.getChildren().add(warningsCheckBox);

        Text warningBoxLabel = new Text("Warnings");
        warningBoxLabel.setStyle(Styles.TEXT_MED);
        warningBox.getChildren().add(warningBoxLabel);

        HBox logsBox = new HBox(15);

        CheckBox logsCheckBox = new CheckBox();
        logsCheckBox.selectedProperty().addListener(new ChangeListener<Boolean>() {
            @Override
            public void changed(ObservableValue<? extends Boolean> observable, Boolean oldValue, Boolean newValue) {
                FunkyLogSorter.setLogsAllowed(newValue);
            }
        });
        logsCheckBox.setSelected(true);
        logsBox.getChildren().add(logsCheckBox);

        Text logsBoxLabel = new Text("Logs");
        logsBoxLabel.setStyle(Styles.TEXT_MED);
        logsBox.getChildren().add(logsBoxLabel);

        filtersBox.getChildren().add(errorBox);
        filtersBox.getChildren().add(warningBox);
        filtersBox.getChildren().add(logsBox);

        return filtersBox;
    }
}
