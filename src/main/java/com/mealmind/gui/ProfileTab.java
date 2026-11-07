package com.mealmind.gui;

import com.mealmind.gui.logic.FieldError;
import com.mealmind.gui.logic.FormResult;
import com.mealmind.gui.logic.ProfileForm;
import com.mealmind.gui.logic.ProfileFormValues;
import com.mealmind.profile.DietType;
import com.mealmind.profile.ProfileData;
import com.mealmind.profile.UserProfile;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiConsumer;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.control.Button;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Control;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.Tab;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;

/**
 * The Profile tab: a form over {@link UserProfile}. Edits stay in the form until Apply, which parses them
 * with {@link ProfileForm} and applies a valid result with {@link UserProfile#applyData}. The form reloads
 * when the profile changes elsewhere, unless the user has unapplied edits.
 */
final class ProfileTab extends Tab {

    private final UserProfile profile;
    private final BiConsumer<String, Boolean> status;
    private final ChoiceBox<DietType> diet = new ChoiceBox<>(FXCollections.observableArrayList(DietType.values()));
    private final ObservableList<String> allergens = FXCollections.observableArrayList();
    private final ListView<String> allergenList = new ListView<>(allergens);
    private final TextField allergenField = new TextField();
    private final Button addAllergen = new Button("Add");
    private final Button removeAllergen = new Button("Remove");
    private final TextField calories = numberField("not set");
    private final TextField protein = numberField("not set");
    private final TextField carbs = numberField("not set");
    private final TextField fat = numberField("not set");
    private final TextField budget = numberField("not set");
    private final TextField servings = numberField("1");
    private final Button apply = new Button("Apply");
    private final Button reset = new Button("Reset");
    private final Map<String, Label> errorLabels = new HashMap<>();
    private boolean dirty;
    private boolean loading;

    ProfileTab(UserProfile profile, BiConsumer<String, Boolean> status) {
        super("Profile");
        this.profile = Objects.requireNonNull(profile, "profile");
        this.status = Objects.requireNonNull(status, "status");
        setClosable(false);
        diet.setConverter(new StringConverter<>() {
            @Override
            public String toString(DietType type) {
                return type == null ? "" : type.getDisplayName();
            }

            @Override
            public DietType fromString(String text) {
                return DietType.parse(text);
            }
        });

        GridPane form = new GridPane();
        form.getStyleClass().add("form");
        form.setHgap(8);
        form.setVgap(6);
        int row = 0;
        row = addRow(form, row, "Diet", diet, "diet");
        row = addRow(form, row, "Calories (kcal per day)", calories, "calories");
        row = addRow(form, row, "Protein (g per day)", protein, "protein");
        row = addRow(form, row, "Carbs (g per day)", carbs, "carbs");
        row = addRow(form, row, "Fat (g per day)", fat, "fat");
        row = addRow(form, row, "Weekly budget ($)", budget, "budget");
        addRow(form, row, "Household servings", servings, "servings");

        allergenField.setPromptText("e.g. peanut");
        Label allergenLabel = new Label("Allergens");
        allergenLabel.setLabelFor(allergenField);
        allergenList.setPrefHeight(120);
        addAllergen.setTooltip(new Tooltip("Add the typed allergen to the form"));
        removeAllergen.setTooltip(new Tooltip("Remove the selected allergen from the form"));
        addAllergen.setOnAction(e -> addAllergen());
        removeAllergen.setOnAction(e -> removeAllergen());
        removeAllergen.disableProperty().bind(allergenList.getSelectionModel().selectedItemProperty().isNull());
        VBox allergenBox = new VBox(6, allergenLabel, allergenList, new HBox(6, allergenField, addAllergen, removeAllergen));

        apply.setTooltip(new Tooltip("Save these settings to your profile"));
        reset.setTooltip(new Tooltip("Discard unapplied edits"));
        apply.setDefaultButton(true);
        apply.setOnAction(e -> apply());
        reset.setOnAction(e -> reload());
        Label hint = new Label("Free-text profile input is not available yet.");
        hint.getStyleClass().add("hint");

        VBox content = new VBox(12, new HBox(24, form, allergenBox), new HBox(8, apply, reset), hint);
        content.getStyleClass().add("tab-content");
        setContent(content);

        for (TextField field : new TextField[] {calories, protein, carbs, fat, budget, servings}) {
            field.textProperty().addListener((obs, old, now) -> markDirty());
        }
        diet.valueProperty().addListener((obs, old, now) -> markDirty());
        reload();
    }

    private static TextField numberField(String prompt) {
        TextField field = new TextField();
        field.setPromptText(prompt);
        field.setPrefColumnCount(8);
        return field;
    }

    private int addRow(GridPane form, int row, String text, Control input, String field) {
        Label label = new Label(text);
        label.setLabelFor(input);
        Label error = new Label();
        error.getStyleClass().add("field-error");
        errorLabels.put(field, error);
        form.addRow(row, label, input, error);
        return row + 1;
    }

    /** Called on every profile notification: reloads unless the user has unapplied edits. */
    void onProfileChanged() {
        if (!dirty) {
            reload();
        }
    }

    /** Shows the profile's current values and clears errors and the dirty flag. */
    void reload() {
        loading = true;
        ProfileFormValues values = ProfileForm.toForm(profile.toData());
        diet.setValue(DietType.parse(values.diet()));
        allergens.setAll(values.allergens());
        calories.setText(values.calories());
        protein.setText(values.protein());
        carbs.setText(values.carbs());
        fat.setText(values.fat());
        budget.setText(values.budget());
        servings.setText(values.servings());
        errorLabels.values().forEach(label -> label.setText(""));
        loading = false;
        dirty = false;
    }

    private void markDirty() {
        if (!loading) {
            dirty = true;
        }
    }

    private void addAllergen() {
        ProfileForm.allergenName(allergenField.getText()).ifPresent(name -> {
            if (!allergens.contains(name)) {
                allergens.add(name);
                markDirty();
            }
        });
        allergenField.clear();
    }

    private void removeAllergen() {
        String selected = allergenList.getSelectionModel().getSelectedItem();
        if (selected != null) {
            allergens.remove(selected);
            markDirty();
        }
    }

    private void apply() {
        errorLabels.values().forEach(label -> label.setText(""));
        FormResult<ProfileData> result = ProfileForm.parse(diet.getValue() == null ? "" : diet.getValue().name(),
                new LinkedHashSet<>(allergens), calories.getText(), protein.getText(), carbs.getText(), fat.getText(),
                budget.getText(), servings.getText());
        if (!result.isValid()) {
            for (FieldError error : result.errors()) {
                Label label = errorLabels.get(error.field());
                if (label != null) {
                    label.setText(error.message());
                }
            }
            FieldError first = result.errors().get(0);
            status.accept("Profile not saved: " + first.field() + " " + first.message(), true);
            return;
        }
        dirty = false;
        profile.applyData(result.value().orElseThrow());
        reload();
        status.accept("Profile saved.", false);
    }

    ChoiceBox<DietType> dietChoice() {
        return diet;
    }

    TextField allergenField() {
        return allergenField;
    }

    Button addAllergenButton() {
        return addAllergen;
    }

    Button applyButton() {
        return apply;
    }

    TextField caloriesField() {
        return calories;
    }

    Label errorLabel(String field) {
        return errorLabels.get(field);
    }
}
