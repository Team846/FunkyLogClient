module com.funkylogclient {
    requires javafx.controls;
    requires javafx.fxml;
    requires javafx.graphics;
    requires javafx.base;
    requires ntcore.java;
    requires wpiutil.java;

    opens com.funkylogclient to javafx.fxml;

    exports com.funkylogclient;
}
