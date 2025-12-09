package com.lexxns.tagforcedeckeditor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Represents a deck recipe from a save file.
 */
public class DeckRecipe {

    private final int slotIndex;
    private String name;
    private int mainDeckCount;
    private int extraDeckCount;
    private int sideDeckCount;
    private List<Integer> mainDeckIds;
    private List<Integer> extraDeckIds;
    private List<Integer> sideDeckIds;

    // File offsets for editing
    private int dataOffset;      // Start of this recipe's data block
    private int cardDataOffset;  // Start of card ID data
    private String lastModified; // Last modified timestamp

    public DeckRecipe(int slotIndex, String name, int mainDeckCount, int extraDeckCount, int sideDeckCount,
                      List<Integer> mainDeckIds, List<Integer> extraDeckIds, List<Integer> sideDeckIds) {
        this.slotIndex = slotIndex;
        this.name = name;
        this.mainDeckCount = mainDeckCount;
        this.extraDeckCount = extraDeckCount;
        this.sideDeckCount = sideDeckCount;
        this.mainDeckIds = new ArrayList<>(mainDeckIds);
        this.extraDeckIds = new ArrayList<>(extraDeckIds);
        this.sideDeckIds = new ArrayList<>(sideDeckIds);
    }

    public int getSlotIndex() {
        return slotIndex;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getMainDeckCount() {
        return mainDeckCount;
    }

    public int getExtraDeckCount() {
        return extraDeckCount;
    }

    public int getSideDeckCount() {
        return sideDeckCount;
    }

    public int getTotalCardCount() {
        return mainDeckCount + extraDeckCount + sideDeckCount;
    }

    public List<Integer> getMainDeckIds() {
        return Collections.unmodifiableList(mainDeckIds);
    }

    public List<Integer> getExtraDeckIds() {
        return Collections.unmodifiableList(extraDeckIds);
    }

    public List<Integer> getSideDeckIds() {
        return Collections.unmodifiableList(sideDeckIds);
    }

    public void setMainDeck(List<Integer> cardIds) {
        this.mainDeckIds = new ArrayList<>(cardIds);
        this.mainDeckCount = cardIds.size();
    }

    public void setExtraDeck(List<Integer> cardIds) {
        this.extraDeckIds = new ArrayList<>(cardIds);
        this.extraDeckCount = cardIds.size();
    }

    public void setSideDeck(List<Integer> cardIds) {
        this.sideDeckIds = new ArrayList<>(cardIds);
        this.sideDeckCount = cardIds.size();
    }

    public boolean isEmpty() {
        return mainDeckCount == 0 && extraDeckCount == 0 && sideDeckCount == 0;
    }

    public int getDataOffset() {
        return dataOffset;
    }

    public void setDataOffset(int dataOffset) {
        this.dataOffset = dataOffset;
    }

    public int getCardDataOffset() {
        return cardDataOffset;
    }

    public void setCardDataOffset(int cardDataOffset) {
        this.cardDataOffset = cardDataOffset;
    }

    public String getLastModified() {
        return lastModified;
    }

    public void setLastModified(String lastModified) {
        this.lastModified = lastModified;
    }

    @Override
    public String toString() {
        return String.format("%s (Main: %d, Extra: %d, Side: %d)",
                name, mainDeckCount, extraDeckCount, sideDeckCount);
    }
}