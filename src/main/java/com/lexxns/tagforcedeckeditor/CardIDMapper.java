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

    public String cardNameFromTagForceId(Integer cardId) throws SQLException {
        SelectQuery query = new SelectQuery(table);
        query.field(cardName)
                .where(String.format("%s = %s", tagForceId, cardId));
        try (ResultSet results = wrapper.select(query)) {
            if (results.next()) {
                return results.getString(cardName);
            }
            return "Unknown (TF:" + cardId + ")";
        }
    }

    public String cardNameFromYdkId(Integer cardId) throws SQLException {
        SelectQuery query = new SelectQuery(table);
        query.field(cardName)
                .where(String.format("%s = %s", ydkId, cardId));
        try (ResultSet results = wrapper.select(query)) {
            if (results.next()) {
                return results.getString(cardName);
            }
            return "Unknown (YDK:" + cardId + ")";
        }
    }

    /**
     * Converts a YDK card ID to a Tag Force card ID.
     *
     * @param cardId the YDK card ID
     * @return the Tag Force card ID
     * @throws CardNotFoundException if the card is not found in the database
     */
    public Integer tagForceIdFromYdkId(Integer cardId) throws SQLException, CardNotFoundException {
        SelectQuery query = new SelectQuery(table);
        query.field(tagForceId)
                .where(String.format("%s = %s", ydkId, cardId));
        try (ResultSet results = wrapper.select(query)) {
            if (results.next()) {
                int tfId = results.getInt(tagForceId);
                if (results.wasNull()) {
                    throw new CardNotFoundException("Card found but has no Tag Force ID: YDK " + cardId);
                }
                return tfId;
            }
            throw new CardNotFoundException("Card not found in database: YDK " + cardId);
        }
    }

    private static String getDatabasePath() {
        try {
            InputStream inputStream = CardIDMapper.class.getResourceAsStream("tag_force_5.db");
            if (inputStream == null) {
                throw new RuntimeException("Database resource not found");
            }

            File tempFile = File.createTempFile("tag_force_5", ".db");
            tempFile.deleteOnExit();

            Files.copy(inputStream, tempFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            inputStream.close();

            return tempFile.getAbsolutePath();
        } catch (IOException e) {
            throw new RuntimeException("Failed to extract database", e);
        }
    }

    public static class CardNotFoundException extends Exception {
        public CardNotFoundException(String message) {
            super(message);
        }
    }
}