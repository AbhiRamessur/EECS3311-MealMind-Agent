package com.mealmind.gui.logic;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mealmind.profile.DietType;
import com.mealmind.profile.ProfileData;
import com.mealmind.profile.UserProfile;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

class ProfileFormTest {

    private static FormResult<ProfileData> parse(String calories, String protein, String carbs, String fat, String budget,
                                                 String servings) {
        return ProfileForm.parse("vegetarian", Set.of("Peanut"), calories, protein, carbs, fat, budget, servings);
    }

    /** The message UserProfile itself produces for a setter call. */
    private static String profileMessage(Executable setter) {
        return assertThrows(IllegalArgumentException.class, setter).getMessage();
    }

    private static List<FieldError> errors(FormResult<ProfileData> result) {
        assertFalse(result.isValid());
        return result.errors();
    }

    @Test
    void validFormYieldsTheProfileData() {
        FormResult<ProfileData> result = parse(" 2000 ", "120", "250.5", "70", "100", "3");

        assertTrue(result.isValid());
        assertEquals(new ProfileData(DietType.VEGETARIAN, Set.of("peanut"), 2000, 120.0, 250.5, 70.0, 100.0, 3),
                result.value().orElseThrow());
    }

    @Test
    void blankAndNoneClearTargets() {
        ProfileData data = parse("", "none", "NONE", "  ", null, "1").value().orElseThrow();

        assertEquals(new ProfileData(DietType.VEGETARIAN, Set.of("peanut"), null, null, null, null, null, 1), data);
    }

    @Test
    void boundsUseTheSameMessagesAsUserProfile() {
        UserProfile p = new UserProfile();
        assertEquals(List.of(new FieldError("calories", profileMessage(() -> p.setCalorieTarget(799)))),
                errors(parse("799", "", "", "", "", "1")));
        assertEquals(List.of(new FieldError("calories", profileMessage(() -> p.setCalorieTarget(6001)))),
                errors(parse("6001", "", "", "", "", "1")));
        assertEquals(List.of(new FieldError("protein", profileMessage(() -> p.setProteinTargetG(0.0)))),
                errors(parse("", "0", "", "", "", "1")));
        assertEquals(List.of(new FieldError("protein", profileMessage(() -> p.setProteinTargetG(1000.5)))),
                errors(parse("", "1000.5", "", "", "", "1")));
        assertEquals(List.of(new FieldError("carbs", profileMessage(() -> p.setCarbsTargetG(-1.0)))),
                errors(parse("", "", "-1", "", "", "1")));
        assertEquals(List.of(new FieldError("carbs", profileMessage(() -> p.setCarbsTargetG(1001.0)))),
                errors(parse("", "", "1001", "", "", "1")));
        assertEquals(List.of(new FieldError("fat", profileMessage(() -> p.setFatTargetG(0.0)))),
                errors(parse("", "", "", "0", "", "1")));
        assertEquals(List.of(new FieldError("fat", profileMessage(() -> p.setFatTargetG(1001.0)))),
                errors(parse("", "", "", "1001", "", "1")));
        assertEquals(List.of(new FieldError("budget", profileMessage(() -> p.setWeeklyBudget(0.0)))),
                errors(parse("", "", "", "", "0", "1")));
        assertEquals(List.of(new FieldError("budget", profileMessage(() -> p.setWeeklyBudget(10000.5)))),
                errors(parse("", "", "", "", "10000.5", "1")));
        assertEquals(List.of(new FieldError("servings", profileMessage(() -> p.setHouseholdServings(0)))),
                errors(parse("", "", "", "", "", "0")));
        assertEquals(List.of(new FieldError("servings", profileMessage(() -> p.setHouseholdServings(13)))),
                errors(parse("", "", "", "", "", "13")));
    }

    @Test
    void nonNumericTextIsRejectedForEachField() {
        assertEquals(List.of(new FieldError("calories", "must be a number"), new FieldError("protein", "must be a number"),
                new FieldError("carbs", "must be a number"), new FieldError("fat", "must be a number"),
                new FieldError("budget", "must be a number"), new FieldError("servings", "must be a number")),
                errors(parse("2000.5", "lots", "1,5", "NaN", "$10", "two")));
    }

    @Test
    void servingsAreRequired() {
        assertEquals(List.of(new FieldError("servings", "is required")), errors(parse("", "", "", "", "", " ")));
        assertEquals(List.of(new FieldError("servings", "is required")), errors(parse("", "", "", "", "", "none")));
    }

    @Test
    void severalErrorsAreCollectedForTheRightFields() {
        List<FieldError> found = errors(ProfileForm.parse("keto", Set.of("peanut", " "), "5", "abc", "", "", "", "0"));

        assertEquals(List.of("diet", "calories", "protein", "servings", "form"), found.stream().map(FieldError::field).toList());
        assertEquals("Unknown diet 'keto'. Use one of: omnivore, vegetarian, vegan, pescatarian", found.get(0).message());
        assertEquals("Allergen name must not be blank", found.get(4).message());
    }

    @Test
    void nothingIsApplied() {
        UserProfile profile = new UserProfile();
        ProfileData before = profile.toData();

        parse("2000", "120", "", "", "", "2");
        parse("1", "", "", "", "", "0");

        assertEquals(before, profile.toData());
    }

    @Test
    void toFormRoundTripsAndAllergensFollowTheProfileRules() {
        ProfileData data = new ProfileData(DietType.PESCATARIAN, Set.of("tree nuts", "sesame"), 1800, 95.5, null, 60.0,
                1000.0, 4);
        ProfileFormValues values = ProfileForm.toForm(data);

        assertEquals(new ProfileFormValues("Pescatarian", List.of("sesame", "tree nuts"), "1800", "95.5", "", "60", "1000", "4"), values);
        assertEquals(data, ProfileForm.parse(values.diet(), Set.copyOf(values.allergens()), values.calories(), values.protein(),
                values.carbs(), values.fat(), values.budget(), values.servings()).value().orElseThrow());
        assertEquals(Optional.of("tree nuts"), ProfileForm.allergenName("  Tree   NUTS "));
        assertEquals(Optional.empty(), ProfileForm.allergenName("  "));
        assertEquals(Set.of("tree nuts"), ProfileForm.parse("omnivore", Set.of(" Tree   NUTS "), "", "", "", "", "", "1")
                .value().orElseThrow().allergens());
    }

    @Test
    void fieldNamesAndFormResultRules() {
        assertArrayEquals(new String[] {"diet", "calories", "protein", "carbs", "fat", "budget", "servings"}, ProfileForm.fieldNames());
        assertTrue(FormResult.ok("x").isValid());
        FormResult<String> failed = FormResult.failed(List.of(new FieldError("name", "bad")));
        assertFalse(failed.isValid());
        assertThrows(UnsupportedOperationException.class, () -> failed.errors().add(new FieldError("a", "b")));
        assertThrows(IllegalArgumentException.class, () -> FormResult.failed(List.of()));
        assertThrows(IllegalArgumentException.class, () -> new FormResult<>(Optional.of("x"), List.of(new FieldError("a", "b"))));
    }
}
