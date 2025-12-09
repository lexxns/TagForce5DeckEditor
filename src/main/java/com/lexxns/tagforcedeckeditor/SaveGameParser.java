package com.lexxns.tagforcedeckeditor;

import java.util.ArrayList;
import java.util.List;

/**
 * Parser for Yu-Gi-Oh save game files (deck recipe save data).
 *
 * File structure:
 * - Header (0x00-0x1F): File metadata
 * - Recipe slots start at 0x20, each 0x1B4 (436) bytes
 *
 * Recipe entry structure (offsets relative to recipe start):
 *   +0x00 (4 bytes):  Unknown marker (always 01 00 00 00?)
 *   +0x04 (64 bytes): Deck name (UTF-16LE, max 32 chars, null-terminated)
 *   +0x44 (52 bytes): Reserved/padding
 *   +0x78 (8 bytes):  DateTime: year(2), month(2), day(2), hour(2) as uint16 LE
 *   +0x80 (2 bytes):  Main deck card count
 *   +0x82 (2 bytes):  Total card count (main + extra)
 *   +0x84 (16 bytes): Metadata
 *   +0x94 (var):      Card IDs (2 bytes each, little endian)
 */
public class SaveGameParser {

    private final byte[] data;

    // File structure constants
    private static final int FILE_HEADER_SIZE = 0x20;
    private static final int RECIPE_BLOCK_SIZE = 0x1B4;  // 436 bytes per recipe

    // Recipe entry offsets (relative to recipe start)
    private static final int MARKER_OFFSET = 0x00;
    private static final int NAME_OFFSET = 0x04;
    private static final int NAME_MAX_CHARS = 32;
    private static final int DECK_HEADER_OFFSET = 0x84;
    private static final int MAIN_COUNT_OFFSET = 0x94;
    private static final int TOTAL_COUNT_OFFSET = 0x82;
    private static final int CARD_IDS_OFFSET = 0xA0;

    public SaveGameParser(byte[] data) {
        this.data = data;
    }

    /**
     * Get the raw file data.
     */
    public byte[] getData() {
        return data;
    }

    /**
     * Parse all recipe entries from the save file.
     */
    public List<DeckRecipe> parseRecipes() {
        List<DeckRecipe> recipes = new ArrayList<>();

        if (data.length < FILE_HEADER_SIZE + RECIPE_BLOCK_SIZE) {
            return recipes;
        }

        int recipeOffset = FILE_HEADER_SIZE;
        int maxRecipes = 20;
        int recipeIndex = 0;

        while (recipeOffset + CARD_IDS_OFFSET < data.length && recipes.size() < maxRecipes) {
            // Check if we've hit empty padding (stop reading)
            if (isEmptyBlock(recipeOffset)) {
                break;
            }

            DeckRecipe recipe = parseRecipeAt(recipeOffset, recipeIndex);

            if (recipe != null) {
                recipes.add(recipe);
            }

            recipeOffset += RECIPE_BLOCK_SIZE;
            recipeIndex++;
        }

        return recipes;
    }

    /**
     * Check if a block is empty (all zeros in name area).
     */
    private boolean isEmptyBlock(int offset) {
        // Check the first few bytes of name area
        for (int i = 0; i < 8 && offset + NAME_OFFSET + i < data.length; i++) {
            if (data[offset + NAME_OFFSET + i] != 0) {
                return false;
            }
        }
        return true;
    }

    /**
     * Parse a single recipe entry at the given file offset.
     */
    private DeckRecipe parseRecipeAt(int offset, int index) {
        if (offset + CARD_IDS_OFFSET >= data.length) {
            return null;
        }

        // Read name
        String name = readUTF16LEString(offset + NAME_OFFSET, NAME_MAX_CHARS);
        if (name == null || name.isEmpty()) {
            return null;
        }

        // Read datetime
        int year = readUInt16(offset + DECK_HEADER_OFFSET);
        int month = readUInt16(offset + DECK_HEADER_OFFSET + 2);
        int day = readUInt16(offset + DECK_HEADER_OFFSET + 4);
        int hour = readUInt16(offset + DECK_HEADER_OFFSET + 6);

        // Read deck counts
        int mainCount = readUInt16(offset + MAIN_COUNT_OFFSET);
        int totalCount = readUInt16(offset + TOTAL_COUNT_OFFSET);

        // Validate counts
        if (mainCount > 60 || mainCount < 0 || totalCount > 90) {
            return null;
        }

        // Calculate extra deck count
        int extraCount = Math.max(0, totalCount - mainCount);

        // Read card IDs
        int cardOffset = offset + CARD_IDS_OFFSET;
        List<Integer> mainDeckIds = readCardIds(cardOffset, mainCount);
        cardOffset += mainCount * 2;

        List<Integer> extraDeckIds = readCardIds(cardOffset, extraCount);

        // Use the index as our display slot (0-based)
        DeckRecipe recipe = new DeckRecipe(
                index,
                name,
                mainCount,
                extraDeckIds.size(),
                0,
                mainDeckIds,
                extraDeckIds,
                new ArrayList<>()
        );

        recipe.setDataOffset(offset);
        recipe.setCardDataOffset(offset + CARD_IDS_OFFSET);

        if (year > 2000 && year < 2100) {
            recipe.setLastModified(String.format("%d-%02d-%02d %02d:00", year, month, day, hour));
        }

        return recipe;
    }

    /**
     * Read a list of card IDs from the given offset.
     */
    private List<Integer> readCardIds(int offset, int count) {
        List<Integer> ids = new ArrayList<>();

        for (int i = 0; i < count && offset + 1 < data.length; i++) {
            int cardId = readUInt16(offset);
            if (cardId > 0) {
                ids.add(cardId);
            }
            offset += 2;
        }

        return ids;
    }

    /**
     * Read a UTF-16LE encoded string.
     */
    private String readUTF16LEString(int offset, int maxChars) {
        if (offset + maxChars * 2 > data.length) {
            return null;
        }

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < maxChars; i++) {
            int charOffset = offset + i * 2;
            int low = data[charOffset] & 0xFF;
            int high = data[charOffset + 1] & 0xFF;
            int codePoint = low | (high << 8);

            if (codePoint == 0) break;
            sb.append((char) codePoint);
        }

        return sb.toString();
    }

    /**
     * Read a 16-bit unsigned integer (little endian).
     */
    private int readUInt16(int offset) {
        if (offset + 1 >= data.length) return 0;
        return (data[offset] & 0xFF) | ((data[offset + 1] & 0xFF) << 8);
    }

    /**
     * Read a 32-bit signed integer (little endian).
     */
    private int readInt32(int offset) {
        if (offset + 3 >= data.length) return -1;
        return (data[offset] & 0xFF) |
                ((data[offset + 1] & 0xFF) << 8) |
                ((data[offset + 2] & 0xFF) << 16) |
                ((data[offset + 3] & 0xFF) << 24);
    }

    /**
     * Get a hex dump of a section of the file for debugging.
     */
    public String getHexDump(int start, int length) {
        StringBuilder sb = new StringBuilder();
        StringBuilder ascii = new StringBuilder();

        int actualStart = Math.max(0, start);

        for (int i = actualStart; i < actualStart + length && i < data.length; i++) {
            if ((i - actualStart) % 16 == 0 && i > actualStart) {
                sb.append(" | ").append(ascii).append("\n");
                ascii = new StringBuilder();
            }

            if ((i - actualStart) % 16 == 0) {
                sb.append(String.format("%04X: ", i));
            }

            sb.append(String.format("%02X ", data[i] & 0xFF));

            char c = (char) (data[i] & 0xFF);
            ascii.append(c >= 32 && c < 127 ? c : '.');
        }

        // Pad final line
        if (ascii.length() > 0) {
            int remaining = 16 - ascii.length();
            for (int i = 0; i < remaining; i++) {
                sb.append("   ");
            }
            sb.append(" | ").append(ascii).append("\n");
        }

        return sb.toString();
    }

    /**
     * Get the block size for recipe entries.
     */
    public static int getRecipeBlockSize() {
        return RECIPE_BLOCK_SIZE;
    }
}