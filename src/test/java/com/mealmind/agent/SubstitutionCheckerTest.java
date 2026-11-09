package com.mealmind.agent;

import static com.mealmind.agent.SubstitutionReplies.RECIPE;
import static com.mealmind.agent.SubstitutionReplies.array;
import static com.mealmind.agent.SubstitutionReplies.option;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mealmind.model.Unit;
import com.mealmind.pantry.Pantry;
import com.mealmind.pantry.PantryItem;
import com.mealmind.profile.DietType;
import com.mealmind.profile.UserProfile;
import com.mealmind.substitution.SubstitutionOption;
import com.mealmind.validation.ConstraintValidator;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SubstitutionCheckerTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    private Pantry pantry;
    private UserProfile profile;
    private SubstitutionChecker checker;

    @BeforeEach
    void setUp() {
        pantry = new Pantry();
        profile = new UserProfile();
        checker = new SubstitutionChecker(RECIPE, 1, pantry, new ConstraintValidator(profile));
    }

    private Check<SubstitutionBatch> check(String json) {
        try {
            JsonNode node = JSON.readTree(json);
            return checker.check(node);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private String repair(String json) {
        return assertInstanceOf(Check.Repair.class, check(json)).feedback();
    }

    private SubstitutionBatch accepted(String json) {
        return (SubstitutionBatch) assertInstanceOf(Check.Accepted.class, check(json)).value();
    }

    @Test
    void validAnswerIsAcceptedWithPantryFlags() {
        pantry.addItem(new PantryItem("Sunflower seed butter", 150, Unit.GRAM));
        pantry.addItem(new PantryItem("Tahini", 50, Unit.GRAM));
        pantry.addItem(new PantryItem("Almond butter", 1, Unit.LITER));

        SubstitutionBatch batch = accepted(array(option("Sunflower seed butter", 1.5), option("Tahini", 1),
                option("Almond butter", 1)));

        List<SubstitutionOption> options = batch.options();
        assertEquals(List.of("Sunflower seed butter", "Tahini", "Almond butter"), options.stream().map(SubstitutionOption::name).toList());
        assertTrue(options.get(0).inPantry());
        assertFalse(options.get(1).inPantry(), "50 g is not enough for 100 g");
        assertFalse(options.get(2).inPantry(), "litres cannot be compared with grams");
        assertEquals(1.5, options.get(0).ratio());
        assertEquals("Works well", options.get(0).note());
        assertEquals(100, options.get(0).calories());
        assertEquals(1.5, options.get(0).cost());
        assertEquals(0, batch.removedForSafety());
        assertFalse(accepted(array(option("Oat butter", 1))).options().get(0).inPantry());
    }

    @Test
    void wrongShapesAreRepaired() {
        assertEquals("\"substitutions\" must be an array of 1 to 3 option objects.", repair("{\"name\": \"Tofu\"}"));
        assertEquals("\"substitutions\" must contain 1 to 3 options; it had 0.", repair("[]"));
        assertEquals("\"substitutions\" must contain 1 to 3 options; it had 4.",
                repair(array(option("A1", 1), option("B1", 1), option("C1", 1), option("D1", 1))));
        assertEquals("Option 1: must be an object.", repair("[\"Tofu\"]"));
    }

    @Test
    void nameRulesNameTheEntry() {
        assertEquals("Option 2: \"name\" must be a string of 1 to 60 characters.",
                repair(array(option("Tahini", 1), option(" ", 1))));
        assertTrue(repair(array(option("x".repeat(61), 1))).startsWith("Option 1: \"name\""));
        assertEquals("Option 1: peanut BUTTER is the ingredient being replaced; suggest a different one.",
                repair(array(option("peanut BUTTER", 1))));
        assertEquals("Option 2: tahini is listed more than once.", repair(array(option("Tahini", 1), option("tahini", 2))));
    }

    @Test
    void ratioAndNumberRules() {
        assertEquals("Option 1: \"ratio\" must be a number from 0.25 to 4.", repair(array(option("Tahini", 0.2))));
        assertEquals("Option 1: \"ratio\" must be a number from 0.25 to 4.", repair(array(option("Tahini", 4.5))));
        assertEquals("Option 1: \"ratio\" must be a number from 0.25 to 4.",
                repair("[{\"name\": \"Tahini\", \"ratio\": \"1\", \"calories\": 100, \"proteinG\": 5, \"carbsG\": 10, \"fatG\": 4, \"cost\": 1}]"));
        assertEquals("Option 1: \"calories\" must be a number >= 0.",
                repair("[{\"name\": \"Tahini\", \"ratio\": 1, \"calories\": \"100\", \"proteinG\": 5, \"carbsG\": 10, \"fatG\": 4, \"cost\": 1}]"));
        assertEquals("Option 1: \"fatG\" must be a number >= 0.",
                repair("[{\"name\": \"Tahini\", \"ratio\": 1, \"calories\": 100, \"proteinG\": 5, \"carbsG\": 10, \"fatG\": -4, \"cost\": 1}]"));
        assertEquals("Option 1: \"cost\" must be a number >= 0.",
                repair("[{\"name\": \"Tahini\", \"ratio\": 1, \"calories\": 100, \"proteinG\": 5, \"carbsG\": 10, \"fatG\": 4}]"));
        assertEquals("Option 1: \"note\" must be a string.",
                repair("[{\"name\": \"Tahini\", \"ratio\": 1, \"note\": 5, \"calories\": 100, \"proteinG\": 5, \"carbsG\": 10, \"fatG\": 4, \"cost\": 1}]"));
    }

    @Test
    void implausibleCaloriesAreRepairedOnlyWhenTheStructureIsValid() {
        String implausible = "{\"name\": \"Tahini\", \"ratio\": 1, \"calories\": 900, \"proteinG\": 5, \"carbsG\": 10, \"fatG\": 4, \"cost\": 1}";

        assertEquals("Option 1: 900 kcal does not match its macros (about 96 kcal).", repair(array(implausible)));
        String both = repair(array(implausible, option(" ", 1)));
        assertEquals("Option 2: \"name\" must be a string of 1 to 60 characters.", both);
    }

    @Test
    void unsafeOptionsAreDroppedWhileSafeOnesRemain() {
        profile.setDiet(DietType.VEGETARIAN);

        SubstitutionBatch batch = accepted(array(option("Chicken stock", 1), option("Tahini", 1)));

        assertEquals(List.of("Tahini"), batch.options().stream().map(SubstitutionOption::name).toList());
        assertEquals(1, batch.removedForSafety());
    }

    @Test
    void allUnsafeOptionsGiveUnsafeWithTheViolations() {
        profile.addAllergen("peanut");

        Check.Unsafe<?> unsafe = assertInstanceOf(Check.Unsafe.class, check(array(option("Peanut oil", 1), option("Roasted peanuts", 1))));

        assertTrue(unsafe.feedback().startsWith("None of your suggestions are safe for the user's profile: Peanut oil"),
                unsafe.feedback());
        assertTrue(unsafe.feedback().endsWith(". Suggest different substitutes."));
        SubstitutionBatch candidate = (SubstitutionBatch) unsafe.candidate();
        assertTrue(candidate.options().isEmpty());
        assertEquals(2, candidate.removedForSafety());
    }

    @Test
    void namesAndNotesAreSanitizedAndExtraFieldsIgnored() {
        String entry = "{\"name\": \"Cr\\u00e8me \\\"fra\\u00eeche\\\" {x}\", \"ratio\": 1, \"note\": \"Rich \\u2013 tangy\\nand thick "
                + "x".repeat(150) + "\", \"calories\": 100, \"proteinG\": 5, \"carbsG\": 10, \"fatG\": 4, \"cost\": 1, \"mood\": \"happy\"}";

        SubstitutionOption option = accepted("[" + entry + "]").options().get(0);

        assertEquals("Creme fraiche x", option.name());
        assertTrue(option.note().startsWith("Rich - tangy and thick"), option.note());
        assertTrue(option.note().length() <= 140);
    }
}
