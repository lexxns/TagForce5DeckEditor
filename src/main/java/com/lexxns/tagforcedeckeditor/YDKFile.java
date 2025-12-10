package com.lexxns.tagforcedeckeditor;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Parses and represents a YGOPro deck file (.ydk format).
 * YDK format:
 * - Lines starting with '#' or '!' are section headers or comments
 * - #main marks the start of main deck cards
 * - #extra marks the start of extra deck cards
 * - !side marks the start of side deck cards
 * - Card IDs are integers, one per line
 */
public class YDKFile {

    private static final String SECTION_MAIN = "#main";
    private static final String SECTION_EXTRA = "#extra";
    private static final String SECTION_SIDE = "!side";

    private final String deckName;
    private final List<Integer> mainDeck;
    private final List<Integer> extraDeck;
    private final List<Integer> sideDeck;

    private YDKFile(String deckName, List<Integer> mainDeck, List<Integer> extraDeck, List<Integer> sideDeck) {
        this.deckName = deckName;
        this.mainDeck = List.copyOf(mainDeck);
        this.extraDeck = List.copyOf(extraDeck);
        this.sideDeck = List.copyOf(sideDeck);
    }

    /**
     * Parses a YDK file from the given file path.
     *
     * @param file the .ydk file to parse
     * @return a YDKFile instance containing the parsed deck data
     * @throws IOException if the file cannot be read
     * @throws YDKParseException if the file format is invalid
     */
    public static YDKFile parse(File file) throws IOException, YDKParseException {
        return parse(file.toPath());
    }

    /**
     * Parses a YDK file from the given path.
     *
     * @param path the path to the .ydk file
     * @return a YDKFile instance containing the parsed deck data
     * @throws IOException if the file cannot be read
     * @throws YDKParseException if the file format is invalid
     */
    public static YDKFile parse(Path path) throws IOException, YDKParseException {
        String fileName = path.getFileName().toString();
        String deckName = extractDeckName(fileName);

        List<Integer> mainDeck = new ArrayList<>();
        List<Integer> extraDeck = new ArrayList<>();
        List<Integer> sideDeck = new ArrayList<>();

        Section currentSection = Section.NONE;

        try (BufferedReader reader = Files.newBufferedReader(path)) {
            String line;
            int lineNumber = 0;

            while ((line = reader.readLine()) != null) {
                lineNumber++;
                line = line.trim();

                // Skip empty lines
                if (line.isEmpty()) {
                    continue;
                }

                // Check for section headers
                if (line.equalsIgnoreCase(SECTION_MAIN)) {
                    currentSection = Section.MAIN;
                    continue;
                } else if (line.equalsIgnoreCase(SECTION_EXTRA)) {
                    currentSection = Section.EXTRA;
                    continue;
                } else if (line.equalsIgnoreCase(SECTION_SIDE)) {
                    currentSection = Section.SIDE;
                    continue;
                }

                // Skip comment lines (lines starting with # that aren't section headers)
                if (line.startsWith("#") || line.startsWith("!")) {
                    continue;
                }

                // Parse card ID
                try {
                    int cardId = Integer.parseInt(line);

                    switch (currentSection) {
                        case MAIN:
                            mainDeck.add(cardId);
                            break;
                        case EXTRA:
                            extraDeck.add(cardId);
                            break;
                        case SIDE:
                            sideDeck.add(cardId);
                            break;
                        case NONE:
                            // Card ID found before any section header - skip or warn
                            break;
                    }
                } catch (NumberFormatException e) {
                    throw new YDKParseException("Invalid card ID at line " + lineNumber + ": " + line);
                }
            }
        }

        // Validate deck sizes
        if (mainDeck.size() < 40 || mainDeck.size() > 60) {
            throw new YDKParseException("Main deck must contain 40-60 cards, found: " + mainDeck.size());
        }
        if (extraDeck.size() > 15) {
            throw new YDKParseException("Extra deck cannot exceed 15 cards, found: " + extraDeck.size());
        }
        if (sideDeck.size() > 15) {
            throw new YDKParseException("Side deck cannot exceed 15 cards, found: " + sideDeck.size());
        }

        return new YDKFile(deckName, mainDeck, extraDeck, sideDeck);
    }

    /**
     * Extracts the deck name from the filename by removing the .ydk extension.
     */
    private static String extractDeckName(String fileName) {
        if (fileName.toLowerCase().endsWith(".ydk")) {
            return fileName.substring(0, fileName.length() - 4);
        }
        return fileName;
    }

    public String getDeckName() {
        return deckName;
    }

    public List<Integer> getMainDeck() {
        return mainDeck;
    }

    public List<Integer> getExtraDeck() {
        return extraDeck;
    }

    public List<Integer> getSideDeck() {
        return sideDeck;
    }

    public int getMainDeckCount() {
        return mainDeck.size();
    }

    public int getExtraDeckCount() {
        return extraDeck.size();
    }

    public int getSideDeckCount() {
        return sideDeck.size();
    }

    public int getTotalCardCount() {
        return mainDeck.size() + extraDeck.size() + sideDeck.size();
    }

    @Override
    public String toString() {
        return String.format("YDKFile{name='%s', main=%d, extra=%d, side=%d}",
                deckName, mainDeck.size(), extraDeck.size(), sideDeck.size());
    }

    private enum Section {
        NONE, MAIN, EXTRA, SIDE
    }

    /**
     * Exception thrown when a YDK file cannot be parsed.
     */
    public static class YDKParseException extends Exception {
        public YDKParseException(String message) {
            super(message);
        }
    }
}