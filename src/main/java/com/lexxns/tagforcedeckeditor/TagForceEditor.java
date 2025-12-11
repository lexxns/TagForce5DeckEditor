package com.lexxns.tagforcedeckeditor;

import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.prefs.Preferences;

public class TagForceEditor extends Application {

    private static final String PREF_LAST_FILE = "lastSaveFile";
    private final Preferences prefs = Preferences.userNodeForPackage(TagForceEditor.class);

    private File currentFile;

    private Label filePathLabel;
    private ListView<DeckRecipe> recipeListView;
    private TextArea deckDetailsArea;
    private Button loadButton;
    private Button replaceSelectedButton;
    private Button addNewDeckButton;

    private CardIDMapper cardIDMapper;
    private SaveGameParser parser;
    private DeckDetailsFormatter formatter;

    @Override
    public void start(Stage primaryStage) {
        primaryStage.setTitle("YGO Recipe Editor");

        BorderPane root = new BorderPane();
        root.setStyle("-fx-background-color: #1a1a2e;");

        root.setTop(createTopSection(primaryStage));
        root.setCenter(createCenterSection());
        root.setRight(createRightSection());

        Scene scene = new Scene(root, 900, 650);
        loadStylesheet(scene);

        primaryStage.setScene(scene);
        primaryStage.show();

        initializeServices();
        tryLoadLastFile();
    }

    private void loadStylesheet(Scene scene) {
        try {
            var cssUrl = getClass().getResource("/styles.css");
            if (cssUrl != null) {
                scene.getStylesheets().add(cssUrl.toExternalForm());
            }
        } catch (Exception e) {
            System.out.println("CSS not loaded, using inline styles");
        }
    }

    private void initializeServices() {
        try {
            cardIDMapper = new CardIDMapper();
            formatter = new DeckDetailsFormatter(cardIDMapper);
        } catch (SQLException | ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    private VBox createTopSection(Stage stage) {
        VBox topBox = new VBox(10);
        topBox.setPadding(new Insets(20));
        topBox.setStyle("-fx-background-color: #16213e;");

        Label titleLabel = new Label("YGO Recipe Editor");
        titleLabel.setFont(Font.font("Segoe UI", FontWeight.BOLD, 24));
        titleLabel.setTextFill(Color.web("#e94560"));

        HBox fileBox = new HBox(10);
        fileBox.setAlignment(Pos.CENTER_LEFT);

        filePathLabel = new Label("No file selected");
        filePathLabel.setFont(Font.font("Consolas", 12));
        filePathLabel.setTextFill(Color.web("#a0a0a0"));
        filePathLabel.setMaxWidth(500);
        HBox.setHgrow(filePathLabel, Priority.ALWAYS);

        Button browseButton = createStyledButton("Browse...", "#e94560", "white");
        browseButton.setOnAction(_ -> browseForFile(stage));

        loadButton = createStyledButton("Reload", "#e94560", "white");
        loadButton.setDisable(true);
        loadButton.setOnAction(_ -> loadCurrentFile());

        Button clearPrefButton = new Button("Clear Saved Path");
        clearPrefButton.setStyle("-fx-background-color: #0f4c75; -fx-text-fill: #a0d0e0; -fx-font-size: 11; " +
                "-fx-padding: 6 12; -fx-background-radius: 4; -fx-cursor: hand;");
        clearPrefButton.setOnAction(_ -> {
            prefs.remove(PREF_LAST_FILE);
            showAlert(Alert.AlertType.INFORMATION, "Preference Cleared", "Saved file path has been cleared.");
        });

        addNewDeckButton = createStyledButton("Add New Deck", "#4ecca3", "#1a1a2e");
        addNewDeckButton.setDisable(true);
        addNewDeckButton.setOnAction(_ -> onAddNewDeck());

        fileBox.getChildren().addAll(filePathLabel, browseButton, loadButton, addNewDeckButton, clearPrefButton);
        topBox.getChildren().addAll(titleLabel, fileBox);
        return topBox;
    }

    private VBox createCenterSection() {
        VBox centerBox = new VBox(10);
        centerBox.setPadding(new Insets(20));

        Label recipeLabel = new Label("Deck Recipes");
        recipeLabel.setFont(Font.font("Segoe UI", FontWeight.SEMI_BOLD, 16));
        recipeLabel.setTextFill(Color.web("#0f4c75"));

        recipeListView = new ListView<>();
        recipeListView.setPlaceholder(new Label("Load a .YGR file to see recipes"));
        recipeListView.setStyle("-fx-background-color: #16213e; -fx-background-insets: 0;");
        recipeListView.setCellFactory(_ -> new RecipeListCell());
        recipeListView.getSelectionModel().selectedItemProperty().addListener(
                (_, _, newVal) -> onRecipeSelected(newVal)
        );
        VBox.setVgrow(recipeListView, Priority.ALWAYS);

        centerBox.getChildren().addAll(recipeLabel, recipeListView);
        return centerBox;
    }

    private VBox createRightSection() {
        VBox rightBox = new VBox(10);
        rightBox.setPadding(new Insets(20));
        rightBox.setPrefWidth(350);
        rightBox.setStyle("-fx-background-color: #0f3460;");

        Label detailsLabel = new Label("Deck Details");
        detailsLabel.setFont(Font.font("Segoe UI", FontWeight.SEMI_BOLD, 16));
        detailsLabel.setTextFill(Color.web("#e94560"));

        deckDetailsArea = new TextArea();
        deckDetailsArea.setEditable(false);
        deckDetailsArea.setWrapText(true);
        deckDetailsArea.setStyle("-fx-control-inner-background: #1a1a2e; -fx-text-fill: #e0e0e0; " +
                "-fx-font-family: 'Consolas', monospace; -fx-font-size: 12;");
        deckDetailsArea.setPromptText("Select a recipe to view details");
        VBox.setVgrow(deckDetailsArea, Priority.ALWAYS);

        replaceSelectedButton = new Button("Replace Selected...");
        replaceSelectedButton.setStyle("-fx-background-color: #4ecca3; -fx-text-fill: #1a1a2e; -fx-font-weight: bold; " +
                "-fx-padding: 10 20; -fx-background-radius: 4; -fx-cursor: hand;");
        replaceSelectedButton.setMaxWidth(Double.MAX_VALUE);
        replaceSelectedButton.setDisable(true);
        replaceSelectedButton.setOnAction(_ -> onReplaceSelected());

        rightBox.getChildren().addAll(detailsLabel, deckDetailsArea, replaceSelectedButton);
        return rightBox;
    }

    private Button createStyledButton(String text, String bgColor, String textColor) {
        Button button = new Button(text);
        button.setStyle(String.format(
                "-fx-background-color: %s; -fx-text-fill: %s; -fx-font-weight: bold; " +
                        "-fx-padding: 8 16; -fx-background-radius: 4; -fx-cursor: hand;",
                bgColor, textColor));
        return button;
    }

    private void onRecipeSelected(DeckRecipe recipe) {
        replaceSelectedButton.setDisable(recipe == null);

        if (recipe == null) {
            deckDetailsArea.setText("");
            return;
        }

        try {
            deckDetailsArea.setText(formatter.format(recipe));
        } catch (SQLException e) {
            showAlert(Alert.AlertType.ERROR, "Error", "Failed to load card names: " + e.getMessage());
        }
    }

    private void onReplaceSelected() {
        DeckRecipe selectedRecipe = recipeListView.getSelectionModel().getSelectedItem();
        if (selectedRecipe == null) {
            showAlert(Alert.AlertType.WARNING, "No Selection", "Please select a recipe slot to replace.");
            return;
        }

        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Open YDK Deck File");
        fileChooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("YDK Deck Files", "*.ydk", "*.YDK"),
                new FileChooser.ExtensionFilter("All Files", "*.*")
        );

        File file = fileChooser.showOpenDialog(replaceSelectedButton.getScene().getWindow());
        if (file == null) {
            return;
        }

        try {
            YDKFile loadedYDK = YDKFile.parse(file);

            // Convert YDK IDs to Tag Force IDs
            List<Integer> mainDeck = convertToTagForceIds(loadedYDK.getMainDeck(), "Main Deck");
            List<Integer> sideDeck = convertToTagForceIds(loadedYDK.getSideDeck(), "Side Deck");
            List<Integer> extraDeck = convertToTagForceIds(loadedYDK.getExtraDeck(), "Extra Deck");

            // Write to the parser's data buffer using the recipe's byte offset
            parser.writeDeck(
                    selectedRecipe.getDataOffset(),
                    loadedYDK.getDeckName(),
                    mainDeck,
                    sideDeck,
                    extraDeck
            );

            // Write the modified data back to the file
            saveCurrentFile();

            // Reload to refresh the UI
            loadCurrentFile();

            // Re-select the same slot
            recipeListView.getSelectionModel().select(selectedRecipe.getSlotIndex());

            showAlert(Alert.AlertType.INFORMATION, "Deck Replaced",
                    String.format("Successfully replaced slot %d with '%s' (%d cards)",
                            selectedRecipe.getSlotIndex(),
                            loadedYDK.getDeckName(),
                            loadedYDK.getTotalCardCount()));

        } catch (IOException e) {
            showAlert(Alert.AlertType.ERROR, "Read Error", "Failed to read YDK file: " + e.getMessage());
        } catch (YDKFile.YDKParseException e) {
            showAlert(Alert.AlertType.ERROR, "Parse Error", "Invalid YDK file: " + e.getMessage());
        } catch (CardIDMapper.CardNotFoundException e) {
            showAlert(Alert.AlertType.ERROR, "Card Not Found", e.getMessage());
        } catch (SQLException e) {
            showAlert(Alert.AlertType.ERROR, "Error", "Failed to convert card IDs: " + e.getMessage());
        } catch (IllegalArgumentException e) {
            showAlert(Alert.AlertType.ERROR, "Invalid Deck", e.getMessage());
        }
    }

    private void onAddNewDeck() {
        if (parser == null) {
            showAlert(Alert.AlertType.WARNING, "No File", "Please load a save file first.");
            return;
        }

        int currentDeckCount = recipeListView.getItems().size();
        if (currentDeckCount >= 20) {
            showAlert(Alert.AlertType.WARNING, "Limit Reached", "Maximum of 20 deck slots reached.");
            return;
        }

        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Open YDK Deck File to Add");
        fileChooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("YDK Deck Files", "*.ydk", "*.YDK"),
                new FileChooser.ExtensionFilter("All Files", "*.*")
        );

        File file = fileChooser.showOpenDialog(addNewDeckButton.getScene().getWindow());
        if (file == null) {
            return;
        }

        try {
            YDKFile loadedYDK = YDKFile.parse(file);

            // Convert YDK IDs to Tag Force IDs
            List<Integer> mainDeck = convertToTagForceIds(loadedYDK.getMainDeck(), "Main Deck");
            List<Integer> sideDeck = convertToTagForceIds(loadedYDK.getSideDeck(), "Side Deck");
            List<Integer> extraDeck = convertToTagForceIds(loadedYDK.getExtraDeck(), "Extra Deck");

            // Calculate offset for the new slot
            int newSlotIndex = currentDeckCount;
            int newSlotOffset = SaveGameParser.getFileHeaderSize() + (newSlotIndex * SaveGameParser.getRecipeBlockSize());

            // Initialize the new slot marker (01 00 00 00)
            parser.initializeNewSlot(newSlotOffset);

            // Write the deck data
            parser.writeDeck(
                    newSlotOffset,
                    loadedYDK.getDeckName(),
                    mainDeck,
                    sideDeck,
                    extraDeck
            );

            // Write the modified data back to the file
            saveCurrentFile();

            // Reload to refresh the UI
            loadCurrentFile();

            // Select the new slot
            recipeListView.getSelectionModel().select(newSlotIndex);

            showAlert(Alert.AlertType.INFORMATION, "Deck Added",
                    String.format("Successfully added '%s' to slot %d (%d cards)",
                            loadedYDK.getDeckName(),
                            newSlotIndex,
                            loadedYDK.getTotalCardCount()));

        } catch (IOException e) {
            showAlert(Alert.AlertType.ERROR, "Read Error", "Failed to read YDK file: " + e.getMessage());
        } catch (YDKFile.YDKParseException e) {
            showAlert(Alert.AlertType.ERROR, "Parse Error", "Invalid YDK file: " + e.getMessage());
        } catch (CardIDMapper.CardNotFoundException e) {
            showAlert(Alert.AlertType.ERROR, "Card Not Found", e.getMessage());
        } catch (SQLException e) {
            showAlert(Alert.AlertType.ERROR, "Error", "Failed to convert card IDs: " + e.getMessage());
        } catch (IllegalArgumentException e) {
            showAlert(Alert.AlertType.ERROR, "Invalid Deck", e.getMessage());
        }
    }

    private List<Integer> convertToTagForceIds(List<Integer> ydkIds, String deckType) throws SQLException, CardIDMapper.CardNotFoundException {
        List<String> notFoundCards = new ArrayList<>();
        List<Integer> result = new ArrayList<>();

        for (Integer ydkId : ydkIds) {
            try {
                result.add(cardIDMapper.tagForceIdFromYdkId(ydkId));
            } catch (CardIDMapper.CardNotFoundException e) {
                notFoundCards.add(ydkId.toString());
            }
        }

        if (!notFoundCards.isEmpty()) {
            throw new CardIDMapper.CardNotFoundException(
                    String.format("%d card(s) in %s not found in Tag Force 5 database: %s",
                            notFoundCards.size(), deckType, String.join(", ", notFoundCards)));
        }

        return result;
    }

    private void saveCurrentFile() throws IOException {
        if (currentFile == null) {
            throw new IOException("No file loaded");
        }
        Files.write(currentFile.toPath(), parser.getData());
    }

    private void browseForFile(Stage stage) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Open Save Game File");
        fileChooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("All Files", "*.*"),
                new FileChooser.ExtensionFilter("Save Files", "*.sav", "*.dat"),
                new FileChooser.ExtensionFilter("YGO Recipe Files", "*.ygr", "*.YGR")
        );

        String lastPath = prefs.get(PREF_LAST_FILE, null);
        if (lastPath != null) {
            File lastFile = new File(lastPath);
            if (lastFile.getParentFile() != null && lastFile.getParentFile().exists()) {
                fileChooser.setInitialDirectory(lastFile.getParentFile());
            }
        }

        File file = fileChooser.showOpenDialog(stage);
        if (file != null) {
            currentFile = file;
            prefs.put(PREF_LAST_FILE, file.getAbsolutePath());
            loadCurrentFile();
        }
    }

    private void tryLoadLastFile() {
        String lastPath = prefs.get(PREF_LAST_FILE, null);
        if (lastPath != null) {
            File file = new File(lastPath);
            if (file.exists()) {
                currentFile = file;
                loadCurrentFile();
            }
        }
    }

    private void loadCurrentFile() {
        if (currentFile == null || !currentFile.exists()) {
            showAlert(Alert.AlertType.ERROR, "Error", "File not found: " +
                    (currentFile != null ? currentFile.getAbsolutePath() : "null"));
            return;
        }

        try {
            byte[] fileData = Files.readAllBytes(currentFile.toPath());
            parser = new SaveGameParser(fileData);

            filePathLabel.setText(currentFile.getAbsolutePath());
            filePathLabel.setTextFill(Color.web("#4ecca3"));
            loadButton.setDisable(false);
            addNewDeckButton.setDisable(false);

            List<DeckRecipe> recipes = parser.parseRecipes();
            recipeListView.getItems().clear();
            recipeListView.getItems().addAll(recipes);

            if (recipes.isEmpty()) {
                deckDetailsArea.setText(formatter.formatEmptyFile(
                        currentFile.getName(), fileData.length, parser.getHexDump(0, 256)));
            } else {
                deckDetailsArea.setText(formatter.formatFileLoaded(
                        currentFile.getName(), fileData.length, recipes.size()));
            }

        } catch (IOException e) {
            showAlert(Alert.AlertType.ERROR, "Read Error", "Failed to read file: " + e.getMessage());
        } catch (Exception e) {
            showAlert(Alert.AlertType.ERROR, "Parse Error", "Failed to parse file: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void showAlert(Alert.AlertType type, String title, String content) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }

    public static void main(String[] args) {
        launch(args);
    }

    private static class RecipeListCell extends ListCell<DeckRecipe> {
        @Override
        protected void updateItem(DeckRecipe item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null) {
                setText(null);
                setGraphic(null);
            } else {
                VBox cellBox = new VBox(2);
                cellBox.setPadding(new Insets(8));

                Label nameLabel = new Label(item.getSlotIndex() + ". " + item.getName());
                nameLabel.setFont(Font.font("Segoe UI", FontWeight.BOLD, 14));
                nameLabel.setTextFill(Color.web("#eee"));

                Label countLabel = new Label(
                        String.format("Main: %d | Extra: %d | Side: %d",
                                item.getMainDeckCount(),
                                item.getExtraDeckCount(),
                                item.getSideDeckCount())
                );
                countLabel.setFont(Font.font("Consolas", 11));
                countLabel.setTextFill(Color.web("#888"));

                cellBox.getChildren().addAll(nameLabel, countLabel);
                setGraphic(cellBox);
            }
        }
    }
}