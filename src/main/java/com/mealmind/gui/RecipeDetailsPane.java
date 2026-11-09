package com.mealmind.gui;

import com.mealmind.app.ApplicationContext;
import com.mealmind.command.RemoveRecipeCommand;
import com.mealmind.command.ScaleRecipeCommand;
import com.mealmind.gui.logic.BackgroundRunner;
import com.mealmind.gui.logic.BusyFlag;
import com.mealmind.gui.logic.FormResult;
import com.mealmind.gui.logic.RecipeDetail;
import com.mealmind.gui.logic.RecipeDetails;
import com.mealmind.gui.logic.ScaleForm;
import com.mealmind.gui.logic.UiThread;
import com.mealmind.model.Recipe;
import com.mealmind.preferences.RecipePreferences;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiConsumer;
import javafx.collections.FXCollections;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/**
 * The Recipes tab's Details section: the selected recipe as {@code recipe show} prints it, plus rating,
 * favourite, scale and remove controls. Ratings and favourites change {@link RecipePreferences} directly
 * (not undoable, as in the CLI); scaling and removing run commands through the shared history. Below them, the
 * {@link SubstitutePane} ("Substitute an ingredient", F07) suggests and applies ingredient substitutes.
 */
final class RecipeDetailsPane extends VBox {

    private static final String CLEAR = "Clear";

    private final ApplicationContext ctx;
    private final BiConsumer<String, Boolean> status;
    private final Label title = new Label("Select a recipe");
    private final Label summary = new Label();
    private final TextArea body = new TextArea();
    private final ChoiceBox<String> rating = new ChoiceBox<>(FXCollections.observableArrayList("1", "2", "3", "4", "5", CLEAR));
    private final Button rate = new Button("Rate");
    private final ToggleButton favorite = new ToggleButton("Favorite");
    private final TextField servings = new TextField();
    private final Button scale = new Button("Scale");
    private final Button remove = new Button("Remove saved recipe");
    private final SubstitutePane substitute;
    private String recipeId;

    RecipeDetailsPane(ApplicationContext ctx, BiConsumer<String, Boolean> status, BackgroundRunner runner, UiThread ui,
                      BusyFlag aiBusy) {
        super(8);
        this.ctx = Objects.requireNonNull(ctx, "ctx");
        this.status = Objects.requireNonNull(status, "status");
        this.substitute = new SubstitutePane(ctx, runner, ui, status, aiBusy);
        title.getStyleClass().add("detail-title");
        body.setEditable(false);
        body.setWrapText(true);
        body.setPrefRowCount(14);
        rating.setValue("5");
        servings.setPromptText("servings");
        servings.setPrefColumnCount(4);
        rate.setTooltip(new Tooltip("Rate the selected recipe (1-5, or Clear)"));
        favorite.setTooltip(new Tooltip("Mark or unmark the selected recipe as a favorite"));
        scale.setTooltip(new Tooltip("Scale the selected recipe to this many servings (undoable)"));
        rate.setOnAction(e -> rate());
        favorite.setOnAction(e -> toggleFavorite());
        scale.setOnAction(e -> scale());
        remove.setOnAction(e -> remove());
        Label ratingLabel = new Label("Rating");
        ratingLabel.setLabelFor(rating);
        Label servingsLabel = new Label("Servings");
        servingsLabel.setLabelFor(servings);
        getChildren().addAll(title, summary, body, new HBox(8, ratingLabel, rating, rate, favorite),
                new HBox(8, servingsLabel, servings, scale, remove), substitute);
        VBox.setVgrow(body, Priority.ALWAYS);
        getStyleClass().add("tab-content");
        show(null);
    }

    /** Shows a recipe, or an empty pane when {@code id} is null or unknown. */
    void show(String id) {
        Optional<RecipeDetail> detail = RecipeDetails.of(id, ctx.recipeBook(), ctx.preferences(), ctx.validator());
        recipeId = detail.map(RecipeDetail::id).orElse(null);
        boolean none = recipeId == null;
        title.setText(detail.map(RecipeDetail::title).orElse("Select a recipe"));
        summary.setText(detail.map(RecipeDetail::summaryText).orElse(""));
        body.setText(detail.map(RecipeDetailsPane::bodyText).orElse(""));
        rate.setDisable(none);
        favorite.setDisable(none);
        favorite.setSelected(!none && ctx.preferences().isFavorite(recipeId));
        scale.setDisable(none);
        servings.setDisable(none);
        if (!none) {
            servings.setText(String.valueOf(ctx.recipeBook().get(recipeId).orElseThrow().getServings()));
        }
        boolean bundled = !none && ctx.isBundledRecipe(recipeId);
        remove.setDisable(none || bundled);
        remove.setTooltip(new Tooltip(bundled ? "Bundled sample recipes cannot be removed" : "Remove this saved recipe (undoable)"));
        substitute.showRecipe(recipeId);
    }

    private static String bodyText(RecipeDetail detail) {
        StringBuilder text = new StringBuilder(detail.perServingText()).append("\n\nIngredients:\n");
        detail.ingredientLines().forEach(line -> text.append("  - ").append(line).append('\n'));
        text.append("\nSteps:\n");
        detail.instructionLines().forEach(line -> text.append("  ").append(line).append('\n'));
        text.append('\n').append(detail.ratingLine()).append('\n').append(detail.safetyLine());
        if (detail.aiEstimate()) {
            text.append('\n').append(RecipeDetail.AI_ESTIMATE_LINE);
        }
        return text.toString();
    }

    private Recipe selected() {
        return ctx.recipeBook().get(recipeId).orElseThrow();
    }

    private void rate() {
        Recipe recipe = selected();
        RecipePreferences preferences = ctx.preferences();
        if (CLEAR.equals(rating.getValue())) {
            status.accept(preferences.clearRating(recipe.getId())
                    ? "Rating removed for " + recipe.getName() + "." : recipe.getName() + " was not rated.", false);
            return;
        }
        int stars = Integer.parseInt(rating.getValue());
        preferences.rate(recipe.getId(), stars);
        status.accept("Rated " + recipe.getName() + ": " + stars + "/5.", false);
    }

    private void toggleFavorite() {
        Recipe recipe = selected();
        boolean wanted = favorite.isSelected();
        boolean changed = ctx.preferences().setFavorite(recipe.getId(), wanted);
        if (wanted) {
            status.accept(changed ? "Added " + recipe.getName() + " to favorites." : "Already a favorite.", false);
        } else {
            status.accept(changed ? "Removed " + recipe.getName() + " from favorites." : "Not a favorite.", false);
        }
    }

    private void scale() {
        FormResult<Integer> parsed = ScaleForm.parseServings(servings.getText());
        if (!parsed.isValid()) {
            status.accept("Not scaled: " + parsed.errors().get(0).message(), true);
            return;
        }
        String id = recipeId;
        try {
            ctx.history().pushAndExecute(new ScaleRecipeCommand(ctx.recipeBook(), id, parsed.value().orElseThrow()));
            Recipe scaled = ctx.recipeBook().get(id).orElseThrow();
            status.accept("Scaled " + scaled.getName() + " to " + scaled.getServings()
                    + " servings (per-serving values unchanged).", false);
        } catch (RuntimeException e) {
            status.accept("Not scaled: " + e.getMessage(), true);
        }
    }

    private void remove() {
        Recipe recipe = selected();
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION, "Remove " + recipe.getName() + "? Undo can restore it.",
                ButtonType.OK, ButtonType.CANCEL);
        confirm.setHeaderText(null);
        if (confirm.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) {
            return;
        }
        try {
            ctx.history().pushAndExecute(new RemoveRecipeCommand(ctx.recipeBook(), recipe.getId()));
            status.accept("Removed " + recipe.getName() + ". Its rating and favorite flag were dropped.", false);
        } catch (RuntimeException e) {
            status.accept("Not removed: " + e.getMessage(), true);
        }
    }

    String recipeId() {
        return recipeId;
    }

    SubstitutePane substitute() {
        return substitute;
    }

    /** Closes the substitution section's bindings. */
    void close() {
        substitute.close();
    }

    ChoiceBox<String> ratingChoice() {
        return rating;
    }

    Button rateButton() {
        return rate;
    }

    ToggleButton favoriteButton() {
        return favorite;
    }

    TextField servingsField() {
        return servings;
    }

    Button scaleButton() {
        return scale;
    }

    Button removeButton() {
        return remove;
    }

    Label titleLabel() {
        return title;
    }

    TextArea bodyArea() {
        return body;
    }
}
