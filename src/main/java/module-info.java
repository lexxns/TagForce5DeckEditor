module com.lexxns.tagforcedeckeditor {
    requires javafx.controls;
    requires javafx.fxml;

    requires org.controlsfx.controls;
    requires org.kordamp.bootstrapfx.core;
    requires java.prefs;

    opens com.lexxns.tagforcedeckeditor to javafx.fxml;
    exports com.lexxns.tagforcedeckeditor;
}