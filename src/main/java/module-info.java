module com.lexxns.tagforcedeckeditor {
    requires javafx.controls;
    requires javafx.fxml;

    requires org.controlsfx.controls;
    requires org.kordamp.bootstrapfx.core;
    requires java.prefs;
    requires java.sql;
    requires org.xerial.sqlitejdbc;

    opens com.lexxns.tagforcedeckeditor to javafx.fxml;
    exports com.lexxns.tagforcedeckeditor;
    exports com.lexxns.tagforcedeckeditor.query;
    opens com.lexxns.tagforcedeckeditor.query to javafx.fxml;
}