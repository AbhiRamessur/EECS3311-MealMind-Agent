package com.mealmind.profile;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mealmind.model.ChangeEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class UserProfileTest {

    private UserProfile profile;
    private List<ChangeEvent> events;

    @BeforeEach
    void setUp() {
        profile = new UserProfile();
        events = new ArrayList<>();
        profile.addObserver(events::add);
    }

    @Test
    void hasDefaults() {
        assertEquals(DietType.OMNIVORE, profile.getDiet());
        assertEquals(Set.of(), profile.getAllergens());
        assertEquals(Optional.empty(), profile.getCalorieTarget());
        assertEquals(Optional.empty(), profile.getProteinTargetG());
        assertEquals(Optional.empty(), profile.getCarbsTargetG());
        assertEquals(Optional.empty(), profile.getFatTargetG());
        assertEquals(Optional.empty(), profile.getWeeklyBudget());
        assertEquals(1, profile.getHouseholdServings());
        assertEquals(ProfileData.defaults(), profile.toData());
    }

    @Test
    void allergensAreNormalizedAndDeduplicated() {
        assertTrue(profile.addAllergen("  Peanuts "));
        assertFalse(profile.addAllergen("peanuts"));
        assertTrue(profile.addAllergen("Sesame"));

        assertEquals(List.of("peanuts", "sesame"), List.copyOf(profile.getAllergens()));
        assertTrue(profile.removeAllergen("PEANUTS"));
        assertFalse(profile.removeAllergen("peanuts"));
        assertThrows(UnsupportedOperationException.class, () -> profile.getAllergens().add("x"));
        assertEquals(List.of("Allergen added: peanuts", "Allergen added: sesame", "Allergen removed: peanuts"),
                events.stream().map(ChangeEvent::description).toList());
    }

    @Test
    void blankAllergenIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> profile.addAllergen("  "));
        assertThrows(IllegalArgumentException.class, () -> profile.removeAllergen(null));
    }

    @Test
    void boundsAreEnforcedAtBothEnds() {
        assertThrows(IllegalArgumentException.class, () -> profile.setCalorieTarget(799));
        assertThrows(IllegalArgumentException.class, () -> profile.setCalorieTarget(6001));
        assertDoesNotThrow(() -> profile.setCalorieTarget(800));
        assertDoesNotThrow(() -> profile.setCalorieTarget(6000));

        assertThrows(IllegalArgumentException.class, () -> profile.setProteinTargetG(0.0));
        assertThrows(IllegalArgumentException.class, () -> profile.setCarbsTargetG(1001.0));
        assertThrows(IllegalArgumentException.class, () -> profile.setFatTargetG(Double.NaN));
        assertThrows(IllegalArgumentException.class, () -> profile.setFatTargetG(Double.POSITIVE_INFINITY));
        assertDoesNotThrow(() -> profile.setProteinTargetG(0.1));
        assertDoesNotThrow(() -> profile.setCarbsTargetG(1000.0));

        assertThrows(IllegalArgumentException.class, () -> profile.setWeeklyBudget(0.0));
        assertThrows(IllegalArgumentException.class, () -> profile.setWeeklyBudget(10001.0));
        assertDoesNotThrow(() -> profile.setWeeklyBudget(10000.0));

        assertThrows(IllegalArgumentException.class, () -> profile.setHouseholdServings(0));
        assertThrows(IllegalArgumentException.class, () -> profile.setHouseholdServings(13));
        assertDoesNotThrow(() -> profile.setHouseholdServings(12));
        assertDoesNotThrow(() -> profile.setHouseholdServings(1));
    }

    @Test
    void errorMessagesNameFieldAndRange() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> profile.setCalorieTarget(100));
        assertTrue(e.getMessage().contains("Calorie target") && e.getMessage().contains("800 and 6000"), e.getMessage());
    }

    @Test
    void nullClearsTargets() {
        profile.setCalorieTarget(2000);
        profile.setWeeklyBudget(100.0);

        profile.setCalorieTarget(null);
        profile.setWeeklyBudget(null);

        assertEquals(Optional.empty(), profile.getCalorieTarget());
        assertEquals(Optional.empty(), profile.getWeeklyBudget());
        assertEquals("Weekly budget cleared", events.get(events.size() - 1).description());
    }

    @Test
    void notifiesOncePerEffectiveChangeOnly() {
        profile.setDiet(DietType.VEGAN);
        profile.setDiet(DietType.VEGAN);
        profile.setCalorieTarget(2000);
        profile.setCalorieTarget(2000);
        profile.setHouseholdServings(1);
        profile.setProteinTargetG(null);

        assertEquals(List.of("Diet set to Vegan", "Calorie target set to 2000"),
                events.stream().map(ChangeEvent::description).toList());
    }

    @Test
    void applyDataIsAtomic() {
        profile.setCalorieTarget(2000);
        events.clear();
        ProfileData invalid = new ProfileData(DietType.VEGAN, Set.of("peanut"), 1800, null, null, null, -5.0, 2);

        assertThrows(IllegalArgumentException.class, () -> profile.applyData(invalid));

        assertEquals(DietType.OMNIVORE, profile.getDiet());
        assertEquals(Set.of(), profile.getAllergens());
        assertEquals(Optional.of(2000), profile.getCalorieTarget());
        assertEquals(0, events.size());
    }

    @Test
    void toDataApplyDataRoundTripNotifiesOnce() {
        ProfileData data = new ProfileData(DietType.PESCATARIAN, Set.of(" Tree Nuts ", "milk"), 2200, 120.0,
                250.0, 70.0, 150.0, 3);

        profile.applyData(data);
        profile.applyData(profile.toData());

        UserProfile copy = new UserProfile();
        copy.applyData(profile.toData());
        assertEquals(profile.toData(), copy.toData());
        assertEquals(Set.of("tree nuts", "milk"), profile.getAllergens());
        assertEquals(Optional.of(150.0), profile.getWeeklyBudget());
        assertEquals(1, events.size());
    }
}
