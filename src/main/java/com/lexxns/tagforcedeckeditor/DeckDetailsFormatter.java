package com.lexxns.tagforcedeckeditor;

import java.sql.SQLException;
import java.util.List;

/**
 * Formats deck information for display in the UI.
 */
public class DeckDetailsFormatter {

    private final CardIDMapper cardIDMapper;

    public DeckDetailsFormatter(CardIDMapper cardIDMapper) {
        this.cardIDMapper = cardIDMapper;
    }

    /**
     * Formats a DeckRecipe for display.
     */
    public String format(DeckRecipe recipe) throws SQLException {
        StringBuilder sb = new StringBuilder();

        appendHeader(sb, recipe.getName());

        formatMetadata(recipe, sb);

        appendDeckComposition(sb, recipe.getMainDeckCount(), recipe.getExtraDeckCount(),
                recipe.getSideDeckCount(), recipe.getTotalCardCount());

        if (!recipe.getMainDeckIds().isEmpty()) {
            sb.append("Main Deck Cards:\n");
            appendCardNames(sb, recipe.getMainDeckIds(), CardIdType.TAG_FORCE, 8);
            sb.append("\n");
        }

        if (!recipe.getExtraDeckIds().isEmpty()) {
            sb.append("Extra Deck Cards:\n");
            appendCardNames(sb, recipe.getExtraDeckIds(), CardIdType.TAG_FORCE, 8);
            sb.append("\n");
        }

        if (!recipe.getSideDeckIds().isEmpty()) {
            sb.append("Side Deck Cards:\n");
            appendCardNames(sb, recipe.getSideDeckIds(), CardIdType.TAG_FORCE, 8);
        }

        return sb.toString();
    }

    static void formatMetadata(DeckRecipe recipe, StringBuilder sb) {
        sb.append("Slot: ").append(recipe.getSlotIndex()).append("\n");
        if (recipe.getLastModified() != null) {
            sb.append("Modified: ").append(recipe.getLastModified()).append("\n");
        }
        sb.append("Data Offset: 0x").append(String.format("%04X", recipe.getDataOffset())).append("\n");
        sb.append("Card Data: 0x").append(String.format("%04X", recipe.getCardDataOffset())).append("\n\n");
    }

    /**
     * Formats a loaded YDK file for display.
     */
    public String format(YDKFile ydk, int targetSlot) throws SQLException {
        StringBuilder sb = new StringBuilder();

        appendHeader(sb, "YDK LOADED");

        sb.append("Deck Name: ").append(ydk.getDeckName()).append("\n");
        sb.append("Target Slot: ").append(targetSlot).append("\n\n");

        appendDeckComposition(sb, ydk.getMainDeckCount(), ydk.getExtraDeckCount(),
                ydk.getSideDeckCount(), ydk.getTotalCardCount());

        sb.append("Main Deck Cards:\n");
        appendCardNames(sb, ydk.getMainDeck(), CardIdType.YDK, 6);
        sb.append("\n");

        if (!ydk.getExtraDeck().isEmpty()) {
            sb.append("Extra Deck Cards:\n");
            appendCardNames(sb, ydk.getExtraDeck(), CardIdType.YDK, 6);
            sb.append("\n");
        }

        if (!ydk.getSideDeck().isEmpty()) {
            sb.append("Side Deck Cards:\n");
            appendCardNames(sb, ydk.getSideDeck(), CardIdType.YDK, 6);
        }

        return sb.toString();
    }

    /**
     * Formats the initial file load message.
     */
    public String formatFileLoaded(String fileName, int fileSize, int recipeCount) {
        return "Loaded " + recipeCount + " recipe(s)\n\n" +
                "File: " + fileName + "\n" +
                "Size: " + fileSize + " bytes\n\n" +
                "Select a recipe to view details.";
    }

    /**
     * Formats the empty file message with hex dump.
     */
    public String formatEmptyFile(String fileName, int fileSize, String hexDump) {
        return "No recipes found in file.\n\n" +
                "File: " + fileName + "\n" +
                "Size: " + fileSize + " bytes\n\n" +
                "Hex dump (first 256 bytes):\n" +
                hexDump;
    }

    private void appendHeader(StringBuilder sb, String title) {
        sb.append("═══════════════════════════════\n");
        sb.append("  ").append(title).append("\n");
        sb.append("═══════════════════════════════\n\n");
    }

    private void appendDeckComposition(StringBuilder sb, int main, int extra, int side, int total) {
        sb.append("┌─ Deck Composition ─────────┐\n");
        sb.append("│  Main Deck:  ").append(String.format("%3d", main)).append(" cards     │\n");
        sb.append("│  Extra Deck: ").append(String.format("%3d", extra)).append(" cards     │\n");
        sb.append("│  Side Deck:  ").append(String.format("%3d", side)).append(" cards     │\n");
        sb.append("│  ─────────────────────     │\n");
        sb.append("│  Total:      ").append(String.format("%3d", total)).append(" cards     │\n");
        sb.append("└────────────────────────────┘\n\n");
    }

    private void appendCardNames(StringBuilder sb, List<Integer> ids, CardIdType idType, int cardsPerLine) throws SQLException {
        for (int i = 0; i < ids.size(); i++) {
            String cardName = resolveCardName(ids.get(i), idType);
            sb.append(cardName);

            if ((i + 1) % cardsPerLine == 0) {
                sb.append("\n");
            } else if (i < ids.size() - 1) {
                sb.append(", ");
            }
        }
        if (ids.size() % cardsPerLine != 0) {
            sb.append("\n");
        }
    }

    private String resolveCardName(int id, CardIdType idType) throws SQLException {
        return switch (idType) {
            case TAG_FORCE -> cardIDMapper.cardNameFromTagForceId(id);
            case YDK -> cardIDMapper.cardNameFromYdkId(id);
        };
    }

    private enum CardIdType {
        TAG_FORCE,
        YDK
    }
}