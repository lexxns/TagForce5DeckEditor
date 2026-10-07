package com.lexxns.tagforcedeckeditor;

import java.util.ArrayList;
import java.util.List;

/**
 * Parser for Yu-Gi-Oh save game files (deck recipe save data).
 * <p>
 * File structure:
 * - Header (0x00-0x1F): File metadata
 * - Recipe slots start at 0x20, each 0x1B4 (436) bytes
 * <p>
 * Recipe entry structure (offsets relative to recipe start):
 *   +0x00 (4 bytes):  Unknown marker (always 01 00 00 00)
 *   +0x04 (64 bytes): Deck name (UTF-16LE, max 32 chars, null-terminated)
 *   +0x44 (52 bytes): Reserved/padding
 *   +0x84 (8 bytes):  DateTime: year(2), month(2), day(2), hour(2) as uint16 LE
 *   +0x8C (2 bytes):  Main + Extra count
 *   +0x94 (2 bytes):  Main deck card count
 *   +0x98 (2 bytes):  Extra deck card count
 *   +0x9C (2 bytes):  Side deck card count
 * <p>
 * The card ID region is three fixed-size blocks (each ID is a little-endian
 * uint16) and is <em>not</em> packed by card count. The game always reserves
 * room for the maximum deck size, so a short Extra/Side deck still occupies its
 * full block:
 *   +0xA0 (120 bytes): Main deck card IDs  (60 slots)
 *   +0x118 (30 bytes): Extra deck card IDs (15 slots)
 *   +0x136 (30 bytes): Side deck card IDs  (15 slots)
 * <p>
 * Note the order in the file is Main, then Extra, then Side. Writing Side into
 * the Extra block (or vice versa) makes the game load the Extra Deck monsters
 * into the Side Deck, where they show up as blank/illegal cards.
 */
public class SaveGameParser {

    private final byte[] data;

    // File structure constants
    private static final int FILE_HEADER_SIZE = 0x20;
    private static final int RECIPE_BLOCK_SIZE = 0x1B4;  // 436 bytes per recipe

    // Recipe entry offsets (relative to recipe start)
    private static final int NAME_OFFSET = 0x04;
    private static final int NAME_MAX_CHARS = 32;
    private static final int DATETIME_OFFSET = 0x84;
    private static final int MAIN_EXTRA_COUNT_OFFSET = 0x8C;
    private static final int MAIN_COUNT_OFFSET = 0x94;
    private static final int EXTRA_COUNT_OFFSET = 0x98;
    private static final int SIDE_COUNT_OFFSET = 0x9C;
    private static final int CARD_IDS_OFFSET = 0xA0;

    // Maximum cards in the card data region
    private static final int MAX_MAIN_CARDS = 60;
    private static final int MAX_SIDE_CARDS = 15;
    private static final int MAX_EXTRA_CARDS = 15;
    private static final int MAX_TOTAL_CARDS = MAX_MAIN_CARDS + MAX_SIDE_CARDS + MAX_EXTRA_CARDS;

    // Fixed byte size of each card block (2 bytes per card, reserved for the max size)
    private static final int MAIN_DECK_BYTES = MAX_MAIN_CARDS * 2;   // 120
    private static final int EXTRA_DECK_BYTES = MAX_EXTRA_CARDS * 2; // 30

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
     * Write deck card data to a recipe slot.
     *
     * @param recipeOffset the byte offset of the recipe in the file
     * @param mainDeck     list of main deck card IDs (40-60 cards)
     * @param sideDeck     list of side deck card IDs (0-15 cards)
     * @param extraDeck    list of extra deck card IDs (0-15 cards)
     * @throws IllegalArgumentException if deck sizes are invalid
     */
    public void writeDeck(int recipeOffset, List<Integer> mainDeck, List<Integer> sideDeck, List<Integer> extraDeck) {
        validateDeckSizes(mainDeck, sideDeck, extraDeck);

        // Update counts
        writeUInt16(recipeOffset + MAIN_COUNT_OFFSET, mainDeck.size());
        writeUInt16(recipeOffset + EXTRA_COUNT_OFFSET, extraDeck.size());
        writeUInt16(recipeOffset + SIDE_COUNT_OFFSET, sideDeck.size());
        writeUInt16(recipeOffset + MAIN_EXTRA_COUNT_OFFSET, mainDeck.size() + extraDeck.size());

        // Clear entire card ID region
        int cardRegionStart = recipeOffset + CARD_IDS_OFFSET;
        zeroRegion(cardRegionStart, MAX_TOTAL_CARDS * 2);

        // Write into the game's fixed-size blocks: Main → Extra → Side.
        // Each block is reserved at its maximum size regardless of the actual
        // count, so a short Side Deck does not shift the Extra Deck.
        int offset = cardRegionStart;
        writeCardIds(offset, mainDeck);      // Main deck:  60 slots
        offset += MAIN_DECK_BYTES;
        writeCardIds(offset, extraDeck);     // Extra deck: 15 slots
        offset += EXTRA_DECK_BYTES;
        writeCardIds(offset, sideDeck);      // Side deck:  15 slots
    }

    /**
     * Write deck card data and update the deck name.
     *
     * @param recipeOffset the byte offset of the recipe in the file
     * @param deckName     the new deck name (max 32 characters)
     * @param mainDeck     list of main deck card IDs (40-60 cards)
     * @param sideDeck     list of side deck card IDs (0-15 cards)
     * @param extraDeck    list of extra deck card IDs (0-15 cards)
     * @throws IllegalArgumentException if deck sizes are invalid
     */
    public void writeDeck(int recipeOffset, String deckName, List<Integer> mainDeck, List<Integer> sideDeck, List<Integer> extraDeck) {
        writeDeckName(recipeOffset, deckName);
        writeDeck(recipeOffset, mainDeck, sideDeck, extraDeck);
    }

    /**
     * Write a deck name to a recipe slot.
     *
     * @param recipeOffset the byte offset of the recipe in the file
     * @param name         the deck name (max 32 characters, will be truncated if longer)
     */
    public void writeDeckName(int recipeOffset, String name) {
        int nameOffset = recipeOffset + NAME_OFFSET;
        int maxBytes = NAME_MAX_CHARS * 2;

        // Zero out the name region first
        zeroRegion(nameOffset, maxBytes);

        // Truncate name if necessary
        String truncatedName = name.length() > NAME_MAX_CHARS ? name.substring(0, NAME_MAX_CHARS) : name;

        // Write as UTF-16LE
        for (int i = 0; i < truncatedName.length(); i++) {
            char c = truncatedName.charAt(i);
            int charOffset = nameOffset + i * 2;
            data[charOffset] = (byte) (c & 0xFF);
            data[charOffset + 1] = (byte) ((c >> 8) & 0xFF);
        }
    }

    private void validateDeckSizes(List<Integer> mainDeck, List<Integer> sideDeck, List<Integer> extraDeck) {
        if (mainDeck.size() < 40 || mainDeck.size() > MAX_MAIN_CARDS) {
            throw new IllegalArgumentException("Main deck must contain 40-60 cards, got: " + mainDeck.size());
        }
        if (sideDeck.size() > MAX_SIDE_CARDS) {
            throw new IllegalArgumentException("Side deck cannot exceed 15 cards, got: " + sideDeck.size());
        }
        if (extraDeck.size() > MAX_EXTRA_CARDS) {
            throw new IllegalArgumentException("Extra deck cannot exceed 15 cards, got: " + extraDeck.size());
        }
    }

    private void zeroRegion(int offset, int length) {
        for (int i = 0; i < length && offset + i < data.length; i++) {
            data[offset + i] = 0;
        }
    }

    private void writeCardIds(int offset, List<Integer> cardIds) {
        for (int cardId : cardIds) {
            writeUInt16(offset, cardId);
            offset += 2;
        }
    }

    private void writeUInt16(int offset, int value) {
        if (offset + 1 < data.length) {
            data[offset] = (byte) (value & 0xFF);
            data[offset + 1] = (byte) ((value >> 8) & 0xFF);
        }
    }

    /**
     * Check if a block is empty (all zeros in name area).
     */
    private boolean isEmptyBlock(int offset) {
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

        String name = readUTF16LEString(offset + NAME_OFFSET, NAME_MAX_CHARS);
        if (name == null || name.isEmpty()) {
            return null;
        }

        int year = readUInt16(offset + DATETIME_OFFSET);
        int month = readUInt16(offset + DATETIME_OFFSET + 2);
        int day = readUInt16(offset + DATETIME_OFFSET + 4);
        int hour = readUInt16(offset + DATETIME_OFFSET + 6);

        int mainCount = readUInt16(offset + MAIN_COUNT_OFFSET);
        int extraCount = readUInt16(offset + EXTRA_COUNT_OFFSET);
        int sideCount = readUInt16(offset + SIDE_COUNT_OFFSET);

        if (mainCount > MAX_MAIN_CARDS || mainCount < 0) {
            return null;
        }
        if (extraCount > MAX_EXTRA_CARDS || extraCount < 0) {
            return null;
        }
        if (sideCount > MAX_SIDE_CARDS || sideCount < 0) {
            return null;
        }

        int cardOffset = offset + CARD_IDS_OFFSET;

        // Fixed-size blocks: Main (60) → Extra (15) → Side (15)
        List<Integer> mainDeckIds = readCardIds(cardOffset, mainCount);
        cardOffset += MAIN_DECK_BYTES;

        List<Integer> extraDeckIds = readCardIds(cardOffset, extraCount);
        cardOffset += EXTRA_DECK_BYTES;

        List<Integer> sideDeckIds = readCardIds(cardOffset, sideCount);

        DeckRecipe recipe = new DeckRecipe(
                index,
                name,
                mainCount,
                extraCount,
                sideCount,
                mainDeckIds,
                extraDeckIds,
                sideDeckIds
        );

        recipe.setDataOffset(offset);
        recipe.setCardDataOffset(offset + CARD_IDS_OFFSET);

        if (year > 2000 && year < 2100) {
            recipe.setLastModified(String.format("%d-%02d-%02d %02d:00", year, month, day, hour));
        }

        return recipe;
    }

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

    private int readUInt16(int offset) {
        if (offset + 1 >= data.length) return 0;
        return (data[offset] & 0xFF) | ((data[offset + 1] & 0xFF) << 8);
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

        if (!ascii.isEmpty()) {
            int remaining = 16 - ascii.length();
            sb.append("   ".repeat(Math.max(0, remaining)));
            sb.append(" | ").append(ascii).append("\n");
        }

        return sb.toString();
    }

    /**
     * Get the file header size.
     */
    public static int getFileHeaderSize() {
        return FILE_HEADER_SIZE;
    }

    /**
     * Get the block size for recipe entries.
     */
    public static int getRecipeBlockSize() {
        return RECIPE_BLOCK_SIZE;
    }

    /**
     * Initialize a new recipe slot with the required marker bytes.
     * Sets the marker to 01 00 00 00 and zeros the rest of the slot.
     *
     * @param recipeOffset the byte offset of the new recipe slot
     */
    public void initializeNewSlot(int recipeOffset) {
        // Zero out the entire slot first
        zeroRegion(recipeOffset, RECIPE_BLOCK_SIZE);

        // Write the marker (01 00 00 00)
        if (recipeOffset + 3 < data.length) {
            data[recipeOffset] = 0x01;
            data[recipeOffset + 1] = 0x00;
            data[recipeOffset + 2] = 0x00;
            data[recipeOffset + 3] = 0x00;
        }
    }
}