package com.mealmind.gui.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.mealmind.model.MealType;
import com.mealmind.planning.PlanRequest;
import com.mealmind.planning.PlanningStrategies;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** PlanGenerateForm, PlanAssignForm and PlanEditForm. */
class PlanFormsTest {

    private static final Set<MealType> LUNCH_DINNER = EnumSet.of(MealType.DINNER, MealType.LUNCH);

    private static List<String> fields(FormResult<?> result) {
        return result.errors().stream().map(FieldError::field).toList();
    }

    @Test
    void generateFormValidRequestKeepsSlotsInEnumOrder() {
        PlanRequest request = PlanGenerateForm.parse(" 3 ", "budget", EnumSet.of(MealType.SNACK, MealType.BREAKFAST), " 50 ")
                .value().orElseThrow();

        assertEquals(3, request.days());
        assertEquals("budget", request.strategy().id());
        assertEquals(List.of(MealType.BREAKFAST, MealType.SNACK), request.slots());
        assertEquals(50.0, request.budget());
        assertNull(PlanGenerateForm.parse("7", "pantry-first", LUNCH_DINNER, "").value().orElseThrow().budget());
    }

    @Test
    void generateFormBoundsAndMessagesFromTheDomain() {
        String high = assertThrows(IllegalArgumentException.class,
                () -> new PlanRequest(8, PlanningStrategies.byId("budget"), List.of(MealType.LUNCH), null)).getMessage();
        String unknown = assertThrows(IllegalArgumentException.class, () -> PlanningStrategies.byId("keto")).getMessage();
        String budget = assertThrows(IllegalArgumentException.class,
                () -> new PlanRequest(1, PlanningStrategies.byId("budget"), List.of(MealType.LUNCH), 0.0)).getMessage();

        assertEquals(List.of(new FieldError("days", high)), PlanGenerateForm.parse("8", "budget", LUNCH_DINNER, "").errors());
        assertEquals("days", fields(PlanGenerateForm.parse("0", "budget", LUNCH_DINNER, "")).get(0));
        assertEquals(List.of(new FieldError("days", "must be a number")), PlanGenerateForm.parse("week", "budget", LUNCH_DINNER, "").errors());
        assertEquals(List.of(new FieldError("strategy", unknown)), PlanGenerateForm.parse("3", "keto", LUNCH_DINNER, "").errors());
        assertEquals(List.of(new FieldError("slots", "choose between 1 and 4 meal slots")),
                PlanGenerateForm.parse("3", "budget", Set.of(), "").errors());
        assertEquals(List.of(new FieldError("budget", budget)), PlanGenerateForm.parse("3", "budget", LUNCH_DINNER, "0").errors());
        assertEquals(List.of(new FieldError("budget", "must be a number")), PlanGenerateForm.parse("3", "budget", LUNCH_DINNER, "$5").errors());
        assertEquals(List.of("days", "strategy", "slots", "budget"), fields(PlanGenerateForm.parse("9", "", null, "-1")));
        assertEquals(4, PlanGenerateForm.parse("1", "budget", EnumSet.allOf(MealType.class), "").value().orElseThrow().slots().size());
    }

    @Test
    void strategyOptionsAndParseDays() {
        assertEquals(List.of(new StrategyOption("pantry-first", "Pantry-First"), new StrategyOption("budget", "Budget-Conscious"),
                new StrategyOption("high-protein", "High-Protein")), PlanGenerateForm.strategyOptions());
        assertEquals(5, PlanGenerateForm.parseDays("5").value().orElseThrow());
        assertEquals("days", fields(PlanGenerateForm.parseDays("10")).get(0));
    }

    @Test
    void assignFormFieldsAndBounds() {
        assertEquals(new PlanAssignment(2, MealType.DINNER, "R103", null), PlanAssignForm.parse(2, MealType.DINNER, " R103 ", "", 3).value().orElseThrow());
        assertEquals(new PlanAssignment(3, MealType.LUNCH, "R101", 12), PlanAssignForm.parse(3, MealType.LUNCH, "R101", "12", 3).value().orElseThrow());
        assertEquals(List.of(new FieldError("day", "Day must be between 1 and 3, got 4")), PlanAssignForm.parse(4, MealType.LUNCH, "R101", "", 3).errors());
        assertEquals(List.of("slot"), fields(PlanAssignForm.parse(1, null, "R101", "", 3)));
        assertEquals(List.of("recipe"), fields(PlanAssignForm.parse(1, MealType.LUNCH, "  ", "", 3)));
        for (String servings : new String[] {"0", "13", "two"}) {
            assertEquals(List.of("servings"), fields(PlanAssignForm.parse(1, MealType.LUNCH, "R101", servings, 3)), servings);
        }
        assertEquals(List.of("day", "slot", "recipe", "servings"), fields(PlanAssignForm.parse(null, null, null, "0", 3)));
    }

    @Test
    void editFormMessagesEqualTheFacade() {
        assertEquals("swap tuesday dinner", PlanEditForm.parseInstruction("  swap tuesday dinner ").value().orElseThrow());
        assertEquals(List.of(new FieldError("instruction", "Describe the change you want")), PlanEditForm.parseInstruction(" ").errors());
        assertEquals(List.of(new FieldError("instruction", "The request is too long (at most 300 characters)")),
                PlanEditForm.parseInstruction("x".repeat(301)).errors());
        assertEquals(300, PlanEditForm.parseInstruction("x".repeat(300)).value().orElseThrow().length());
    }
}
