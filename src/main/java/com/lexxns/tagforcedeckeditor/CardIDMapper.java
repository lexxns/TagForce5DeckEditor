package com.lexxns.tagforcedeckeditor;

import com.lexxns.tagforcedeckeditor.query.SQLiteWrapper;
import com.lexxns.tagforcedeckeditor.query.SelectQuery;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.sql.ResultSet;
import java.sql.SQLException;

public class CardIDMapper {
    private static final String database = getDatabasePath();
    private static final String table = "cards";
    private static final String cardName = "name";
    private static final String tagForceId = "tag_force_id";
    private static final String ydkId = "ydk_id";
    private final SQLiteWrapper wrapper;


    public CardIDMapper() throws SQLException, ClassNotFoundException {
        wrapper = new SQLiteWrapper(database);
        wrapper.open();
    }

    public String cardName(Integer cardId) throws SQLException {
        SelectQuery query = new SelectQuery(table);
        query.field(cardName)
                .where(String.format("%s = %s", tagForceId, cardId));
        try (ResultSet results = wrapper.select(query)) {
            return results.getString(cardName);
        }
    }

    private static String getDatabasePath() {
        try {
            // Get the resource as a stream
            InputStream inputStream = CardIDMapper.class.getResourceAsStream("tag_force_5.db");
            if (inputStream == null) {
                throw new RuntimeException("Database resource not found");
            }

            // Create a temp file that deletes on JVM exit
            File tempFile = File.createTempFile("tag_force_5", ".db");
            tempFile.deleteOnExit();

            // Copy the resource to the temp file
            Files.copy(inputStream, tempFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            inputStream.close();

            return tempFile.getAbsolutePath();
        } catch (IOException e) {
            throw new RuntimeException("Failed to extract database", e);
        }
    }
}
