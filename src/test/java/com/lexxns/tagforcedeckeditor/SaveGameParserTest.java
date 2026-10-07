package com.lexxns.tagforcedeckeditor;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression tests for the recipe card layout.
 * <p>
 * The game stores the deck as three fixed-size blocks in the order
 * Main (60 slots) → Extra (15 slots) → Side (15 slots). An earlier version of
 * this editor wrote Extra and Side the other way around, which made Extra Deck
 * monsters show up in the Side Deck (as blank, unusable cards) whenever the
 * Side Deck was not full.
 */
class SaveGameParserTest {

    private static final int FILE_HEADER = SaveGameParser.getFileHeaderSize();
    private static final int BLOCK = SaveGameParser.getRecipeBlockSize();
    private static final int CARDS_OFFSET = 0xA0;
    private static final int MAIN_BYTES = 120;
    private static final int EXTRA_BYTES = 30;

    private static byte[] emptySave() {
        return new byte[FILE_HEADER + BLOCK];
    }

    private static List<Integer> ids(int startInclusive, int count) {
        List<Integer> result = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            result.add(startInclusive + i);
        }
        return result;
    }

    private static int readUInt16(byte[] data, int offset) {
        return (data[offset] & 0xFF) | ((data[offset + 1] & 0xFF) << 8);
    }

    @Test
    void extraDeckIsWrittenInTheBlockAfterTheMainDeck() {
        byte[] data = emptySave();
        SaveGameParser parser = new SaveGameParser(data);

        List<Integer> main = ids(1000, 40);
        List<Integer> extra = ids(5000, 3);
        List<Integer> side = ids(7000, 2);
        parser.writeDeck(FILE_HEADER, "layout", main, side, extra);

        int cardStart = FILE_HEADER + CARDS_OFFSET;

        // Extra deck lives at +120 bytes (slot index 60), not after the side deck.
        for (int i = 0; i < extra.size(); i++) {
            assertEquals(extra.get(i), readUInt16(data, cardStart + MAIN_BYTES + i * 2),
                    "extra deck card " + i + " must follow the main deck block");
        }

        // Side deck lives at +150 bytes (slot index 75), after the extra deck block.
        for (int i = 0; i < side.size(); i++) {
            assertEquals(side.get(i), readUInt16(data, cardStart + MAIN_BYTES + EXTRA_BYTES + i * 2),
                    "side deck card " + i + " must follow the extra deck block");
        }

        // Unused slots stay zeroed and must never contain the extra deck cards.
        assertNotEquals(extra.get(0), readUInt16(data, cardStart + MAIN_BYTES + EXTRA_BYTES));
    }

    @Test
    void shortSideDeckDoesNotMoveTheExtraDeck() {
        byte[] data = emptySave();
        SaveGameParser parser = new SaveGameParser(data);

        List<Integer> main = ids(1000, 40);
        List<Integer> extra = ids(5000, 15);
        List<Integer> side = ids(7000, 3); // deliberately shorter than the 15-slot block
        parser.writeDeck(FILE_HEADER, "short side", main, side, extra);

        int cardStart = FILE_HEADER + CARDS_OFFSET;

        // The full 15-card extra deck must occupy slots 60..74 even though the
        // side deck only has 3 cards.
        for (int i = 0; i < 15; i++) {
            assertEquals(extra.get(i), readUInt16(data, cardStart + MAIN_BYTES + i * 2));
        }

        // The side deck must sit in slots 75..77.
        for (int i = 0; i < side.size(); i++) {
            assertEquals(side.get(i), readUInt16(data, cardStart + MAIN_BYTES + EXTRA_BYTES + i * 2));
        }
    }

    @Test
    void parseReadsBackTheThreeDecksInTheRightOrder() {
        byte[] data = emptySave();
        SaveGameParser parser = new SaveGameParser(data);

        List<Integer> main = ids(1000, 40);
        List<Integer> extra = ids(5000, 15);
        List<Integer> side = ids(7000, 3);
        parser.writeDeck(FILE_HEADER, "round trip", main, side, extra);

        List<DeckRecipe> recipes = parser.parseRecipes();
        assertEquals(1, recipes.size());

        DeckRecipe recipe = recipes.get(0);
        assertEquals("round trip", recipe.getName());
        assertEquals(main, recipe.getMainDeckIds());
        assertEquals(extra, recipe.getExtraDeckIds());
        assertEquals(side, recipe.getSideDeckIds());
        assertEquals(40, recipe.getMainDeckCount());
        assertEquals(15, recipe.getExtraDeckCount());
        assertEquals(3, recipe.getSideDeckCount());
    }

    @Test
    void writesCountFields() {
        byte[] data = emptySave();
        SaveGameParser parser = new SaveGameParser(data);

        parser.writeDeck(FILE_HEADER, "counts", ids(1000, 42), ids(7000, 4), ids(5000, 12));

        assertEquals(42, readUInt16(data, FILE_HEADER + 0x94));
        assertEquals(12, readUInt16(data, FILE_HEADER + 0x98));
        assertEquals(4, readUInt16(data, FILE_HEADER + 0x9C));
        assertEquals(42 + 12, readUInt16(data, FILE_HEADER + 0x8C));
    }

    @Test
    void acceptsAnEmptySideDeck() {
        byte[] data = emptySave();
        SaveGameParser parser = new SaveGameParser(data);

        parser.writeDeck(FILE_HEADER, "no side", ids(1000, 40), List.of(), ids(5000, 15));

        DeckRecipe recipe = parser.parseRecipes().get(0);
        assertEquals(15, recipe.getExtraDeckCount());
        assertTrue(recipe.getSideDeckIds().isEmpty());
    }
}
