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

    private CardIDMapper cardIDMapper;

    @Override
    public void start(Stage primaryStage) {
        primaryStage.setTitle("YGO Recipe Editor");

        BorderPane root = new BorderPane();
        root.setStyle("-fx-background-color: #1a1a2e;");

        // Top section - File selection
        VBox topSection = createTopSection(primaryStage);
        root.setTop(topSection);

        // Center section - Recipe list
        VBox centerSection = createCenterSection();
        root.setCenter(centerSection);

        // Right section - Deck details
        VBox rightSection = createRightSection();
        root.setRight(rightSection);

        Scene scene = new Scene(root, 900, 650);

        // Apply styles inline for reliability (external CSS can be added later)
        try {
            var cssUrl = getClass().getResource("/styles.css");
            if (cssUrl != null) {
                scene.getStylesheets().add(cssUrl.toExternalForm());
            }
        } catch (Exception e) {
            System.out.println("CSS not loaded, using inline styles");
        }

        primaryStage.setScene(scene);
        primaryStage.show();

        try {
            cardIDMapper = new CardIDMapper();
        } catch (SQLException | ClassNotFoundException e) {
            throw new RuntimeException(e);
        }

        // Try to load last used file
        tryLoadLastFile();
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

        Button browseButton = new Button("Browse...");
        browseButton.setStyle("-fx-background-color: #e94560; -fx-text-fill: white; -fx-font-weight: bold; " +
                "-fx-padding: 8 16; -fx-background-radius: 4; -fx-cursor: hand;");
        browseButton.setOnAction(_ -> browseForFile(stage));

        loadButton = new Button("Reload");
        loadButton.setStyle("-fx-background-color: #e94560; -fx-text-fill: white; -fx-font-weight: bold; " +
                "-fx-padding: 8 16; -fx-background-radius: 4; -fx-cursor: hand;");
        loadButton.setDisable(true);
        loadButton.setOnAction(_ -> loadCurrentFile());

        Button clearPrefButton = new Button("Clear Saved Path");
        clearPrefButton.setStyle("-fx-background-color: #0f4c75; -fx-text-fill: #a0d0e0; -fx-font-size: 11; " +
                "-fx-padding: 6 12; -fx-background-radius: 4; -fx-cursor: hand;");
        clearPrefButton.setOnAction(_ -> {
            prefs.remove(PREF_LAST_FILE);
            showAlert(Alert.AlertType.INFORMATION, "Preference Cleared", "Saved file path has been cleared.");
        });

        fileBox.getChildren().addAll(filePathLabel, browseButton, loadButton, clearPrefButton);

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
                (_, _, newVal) -> {
                    try {
                        showDeckDetails(newVal);
                    } catch (SQLException e) {
                        throw new RuntimeException(e);
                    }
                }
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

        rightBox.getChildren().addAll(detailsLabel, deckDetailsArea);
        return rightBox;
    }

    private void browseForFile(Stage stage) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Open Save Game File");
        fileChooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("All Files", "*.*"),
                new FileChooser.ExtensionFilter("Save Files", "*.sav", "*.dat"),
                new FileChooser.ExtensionFilter("YGO Recipe Files", "*.ygr", "*.YGR")
        );

        // Start in last directory if available
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
            SaveGameParser parser = new SaveGameParser(fileData);

            filePathLabel.setText(currentFile.getAbsolutePath());
            filePathLabel.setTextFill(Color.web("#4ecca3"));
            loadButton.setDisable(false);

            // Parse and display recipes
            List<DeckRecipe> recipes = parser.parseRecipes();
            recipeListView.getItems().clear();
            recipeListView.getItems().addAll(recipes);

            if (recipes.isEmpty()) {
                deckDetailsArea.setText("No recipes found in file.\n\n" +
                        "File: " + currentFile.getName() + "\n" +
                        "Size: " + fileData.length + " bytes\n\n" +
                        "Hex dump (first 256 bytes):\n" +
                        parser.getHexDump(0, 256));
            } else {
                deckDetailsArea.setText("Loaded " + recipes.size() + " recipe(s)\n\n" +
                        "File: " + currentFile.getName() + "\n" +
                        "Size: " + fileData.length + " bytes\n\n" +
                        "Select a recipe to view details.");
            }

        } catch (IOException e) {
            showAlert(Alert.AlertType.ERROR, "Read Error", "Failed to read file: " + e.getMessage());
        } catch (Exception e) {
            showAlert(Alert.AlertType.ERROR, "Parse Error", "Failed to parse file: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void showDeckDetails(DeckRecipe recipe) throws SQLException {
        if (recipe == null) {
            deckDetailsArea.setText("");
            return;
        }

        StringBuilder sb = new StringBuilder();
        sb.append("═══════════════════════════════\n");
        sb.append("  ").append(recipe.getName()).append("\n");
        sb.append("═══════════════════════════════\n\n");

        sb.append("Slot: ").append(recipe.getSlotIndex()).append("\n");
        if (recipe.getLastModified() != null) {
            sb.append("Modified: ").append(recipe.getLastModified()).append("\n");
        }
        sb.append("Data Offset: 0x").append(String.format("%04X", recipe.getDataOffset())).append("\n");
        sb.append("Card Data: 0x").append(String.format("%04X", recipe.getCardDataOffset())).append("\n\n");

        sb.append("┌─ Deck Composition ─────────┐\n");
        sb.append("│  Main Deck:  ").append(String.format("%3d", recipe.getMainDeckCount())).append(" cards     │\n");
        sb.append("│  Extra Deck: ").append(String.format("%3d", recipe.getExtraDeckCount())).append(" cards     │\n");
        sb.append("│  Side Deck:  ").append(String.format("%3d", recipe.getSideDeckCount())).append(" cards     │\n");
        sb.append("│  ─────────────────────     │\n");
        sb.append("│  Total:      ").append(String.format("%3d", recipe.getTotalCardCount())).append(" cards     │\n");
        sb.append("└────────────────────────────┘\n\n");

        if (!recipe.getMainDeckIds().isEmpty()) {
            sb.append("Main Deck Cards:\n");
            sb.append(formatCardIds(recipe.getMainDeckIds()));
            sb.append("\n");
        }

        if (!recipe.getExtraDeckIds().isEmpty()) {
            sb.append("Extra Deck Card IDs:\n");
            sb.append(formatCardIds(recipe.getExtraDeckIds()));
            sb.append("\n");
        }

        if (!recipe.getSideDeckIds().isEmpty()) {
            sb.append("Side Deck Card IDs:\n");
            sb.append(formatCardIds(recipe.getSideDeckIds()));
        }

        deckDetailsArea.setText(sb.toString());
    }

    private String formatCardIds(List<Integer> ids) throws SQLException {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < ids.size(); i++) {
            String cardName = cardIDMapper.cardName(ids.get(i));
            sb.append(cardName);
            if ((i + 1) % 8 == 0) {
                sb.append("\n");
            } else if (i < ids.size() - 1) {
                sb.append(", ");
            }
        }
        if (ids.size() % 8 != 0) {
            sb.append("\n");
        }
        return sb.toString();
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

    // Custom cell for recipe list
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