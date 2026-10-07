package com.lexxns.tagforcedeckeditor;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression tests for the recipe card layout.
 * <p>
 * The game stores the deck as three fixed-size blocks in the order
 * Main (60 slots) → Side (15 slots) → Extra (15 slots), with the matching
 * counts at 0x94 (main), 0x98 (side) and 0x9C (extra). An earlier version of
 * this editor wrote the Side and Extra <em>counts</em> the wrong way round, so
 * a Side Deck with fewer than 15 cards made the game read extra slots as Side
 * Deck cards, which showed up blank and unusable.
 */
class SaveGameParserTest {

    private static final int FILE_HEADER = SaveGameParser.getFileHeaderSize();
    private static final int BLOCK = SaveGameParser.getRecipeBlockSize();
    private static final int CARDS_OFFSET = 0xA0;
    private static final int MAIN_BYTES = 120;
    private static final int SIDE_BYTES = 30;

    private static final int MAIN_COUNT_OFFSET = 0x94;
    private static final int SIDE_COUNT_OFFSET = 0x98;
    private static final int EXTRA_COUNT_OFFSET = 0x9C;
    private static final int MAIN_EXTRA_COUNT_OFFSET = 0x8C;

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
    void sideDeckIsWrittenInTheBlockAfterTheMainDeck() {
        byte[] data = emptySave();
        SaveGameParser parser = new SaveGameParser(data);

        List<Integer> main = ids(1000, 40);
        List<Integer> side = ids(7000, 3);
        List<Integer> extra = ids(5000, 12);
        parser.writeDeck(FILE_HEADER, "layout", main, side, extra);

        int cardStart = FILE_HEADER + CARDS_OFFSET;

        // Side deck lives at +120 bytes (slot index 60).
        for (int i = 0; i < side.size(); i++) {
            assertEquals(side.get(i), readUInt16(data, cardStart + MAIN_BYTES + i * 2),
                    "side deck card " + i + " must follow the main deck block");
        }

        // Extra deck lives at +150 bytes (slot index 75), after the side block.
        for (int i = 0; i < extra.size(); i++) {
            assertEquals(extra.get(i), readUInt16(data, cardStart + MAIN_BYTES + SIDE_BYTES + i * 2),
                    "extra deck card " + i + " must follow the side deck block");
        }
    }

    @Test
    void countsAreWrittenInTheSideThenExtraOrderTheGameReads() {
        byte[] data = emptySave();
        SaveGameParser parser = new SaveGameParser(data);

        parser.writeDeck(FILE_HEADER, "counts", ids(1000, 42), ids(7000, 4), ids(5000, 12));

        assertEquals(42, readUInt16(data, FILE_HEADER + MAIN_COUNT_OFFSET));
        assertEquals(4, readUInt16(data, FILE_HEADER + SIDE_COUNT_OFFSET), "0x98 is the Side Deck count");
        assertEquals(12, readUInt16(data, FILE_HEADER + EXTRA_COUNT_OFFSET), "0x9C is the Extra Deck count");
        assertEquals(42 + 12, readUInt16(data, FILE_HEADER + MAIN_EXTRA_COUNT_OFFSET));
    }

    /**
     * Mirrors the in-game failure this fix addresses: a full 15-card Extra Deck
     * plus a short 2-card Side Deck. Writing the Side count into the Extra
     * count field used to make the game load Extra Deck monsters as Side Deck
     * cards.
     */
    @Test
    void shortSideDeckWithFullExtraDeckKeepsBothDecksSeparate() {
        byte[] data = emptySave();
        SaveGameParser parser = new SaveGameParser(data);

        List<Integer> main = ids(1000, 40);
        List<Integer> side = ids(7000, 2);   // e.g. Cyber Dragon x2
        List<Integer> extra = ids(5000, 15); // e.g. the Synchro/Fusion toolbox
        parser.writeDeck(FILE_HEADER, "Roid (1)", main, side, extra);

        assertEquals(2, readUInt16(data, FILE_HEADER + SIDE_COUNT_OFFSET));
        assertEquals(15, readUInt16(data, FILE_HEADER + EXTRA_COUNT_OFFSET));

        DeckRecipe recipe = parser.parseRecipes().get(0);
        assertEquals(side, recipe.getSideDeckIds());
        assertEquals(extra, recipe.getExtraDeckIds());
        assertEquals(2, recipe.getSideDeckCount());
        assertEquals(15, recipe.getExtraDeckCount());
    }

    @Test
    void parseReadsBackTheThreeDecksInTheRightOrder() {
        byte[] data = emptySave();
        SaveGameParser parser = new SaveGameParser(data);

        List<Integer> main = ids(1000, 40);
        List<Integer> side = ids(7000, 3);
        List<Integer> extra = ids(5000, 15);
        parser.writeDeck(FILE_HEADER, "round trip", main, side, extra);

        List<DeckRecipe> recipes = parser.parseRecipes();
        assertEquals(1, recipes.size());

        DeckRecipe recipe = recipes.get(0);
        assertEquals("round trip", recipe.getName());
        assertEquals(main, recipe.getMainDeckIds());
        assertEquals(side, recipe.getSideDeckIds());
        assertEquals(extra, recipe.getExtraDeckIds());
        assertEquals(40, recipe.getMainDeckCount());
        assertEquals(3, recipe.getSideDeckCount());
        assertEquals(15, recipe.getExtraDeckCount());
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
