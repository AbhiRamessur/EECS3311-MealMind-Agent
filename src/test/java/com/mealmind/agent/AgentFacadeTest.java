package com.mealmind.agent;

import static com.mealmind.agent.RecipeReplies.recipe;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mealmind.llm.LlmErrorKind;
import com.mealmind.llm.LlmException;
import com.mealmind.llm.LlmService;
import com.mealmind.model.ChangeEvent;
import com.mealmind.model.RecipeBook;
import com.mealmind.profile.DietType;
import com.mealmind.profile.UserProfile;
import com.mealmind.tools.ToolManager;
import com.mealmind.validation.ConstraintValidator;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AgentFacadeTest {

    private UserProfile profile;
    private ScriptedClient client;
    private LlmService llm;
    private AgentFacade facade;
    private List<ChangeEvent> events;

    @BeforeEach
    void setUp() {
        profile = new UserProfile();
        client = new ScriptedClient();
        llm = client.service();
        facade = new AgentFacade(llm, new ToolManager(), new AgentMemory(), new ConstraintValidator(profile), profile,
                new RecipeBook(), AgentLimits.defaults());
        events = new ArrayList<>();
        facade.addObserver(events::add);
    }

    @Test
    void pendingLifecycle() {
        assertEquals(Optional.empty(), facade.pendingRecipe());
        assertFalse(facade.discardPendingRecipe());

        client.reply(recipe("First Bowl", "Rice", "Beans"), recipe("Second Bowl", "Rice", "Beans"));
        facade.generateRecipe("first", null);
        assertEquals("First Bowl", facade.pendingRecipe().orElseThrow().recipe().getName());

        facade.generateRecipe("second", null);
        assertEquals("Second Bowl", facade.pendingRecipe().orElseThrow().recipe().getName());

        assertTrue(facade.discardPendingRecipe());
        assertEquals(Optional.empty(), facade.pendingRecipe());
        assertFalse(facade.discardPendingRecipe());
    }

    @Test
    void observersNotifiedOncePerGenerateAndEffectiveDiscardOnly() {
        client.reply(recipe("Bowl", "Rice", "Beans")).fail(LlmErrorKind.TIMEOUT, "slow");

        facade.generateRecipe("one", null);
        assertThrows(LlmException.class, () -> facade.generateRecipe("two", null));
        facade.discardPendingRecipe();
        facade.discardPendingRecipe();

        assertEquals(List.of("Pending recipe AI001", "Discarded pending recipe AI001"),
                events.stream().map(ChangeEvent::description).toList());
    }

    @Test
    void onlyOneRequestRunsAtATime() throws Exception {
        CountDownLatch release = new CountDownLatch(1);
        client.blockUntil(release).reply(recipe("Slow Bowl", "Rice", "Beans"), recipe("Next Bowl", "Rice", "Beans"));
        AtomicReference<GeneratedRecipe> background = new AtomicReference<>();
        Thread worker = new Thread(() -> background.set(facade.generateRecipe("slow", null)));
        worker.start();
        assertTrue(client.entered.await(5, TimeUnit.SECONDS));

        IllegalStateException busy = assertThrows(IllegalStateException.class, () -> facade.generateRecipe("again", null));
        assertEquals("The agent is already working on a request", busy.getMessage());

        release.countDown();
        worker.join(5000);
        assertEquals("Slow Bowl", background.get().recipe().getName());
        assertEquals("Next Bowl", facade.generateRecipe("now free", null).recipe().getName());
    }

    @Test
    void availabilityFollowsTheLlmService() {
        assertTrue(facade.isAiAvailable());
        assertEquals(Optional.empty(), facade.unavailableReason());

        llm.disable("No OpenAI API key configured");

        assertFalse(facade.isAiAvailable());
        assertEquals(Optional.of("No OpenAI API key configured"), facade.unavailableReason());
    }

    @Test
    void unsafeResultIsStoredAsFlaggedPendingWithoutThrowing() {
        profile.setDiet(DietType.VEGETARIAN);
        for (int i = 0; i < 4; i++) {
            client.reply(recipe("Chicken Bowl", "Chicken breast", "Rice"));
        }

        GeneratedRecipe result = facade.generateRecipe("chicken", null);

        assertFalse(result.safe());
        assertFalse(facade.pendingRecipe().orElseThrow().safe());
        assertEquals("Pending recipe AI001 (flagged unsafe)", events.get(0).description());
    }
}
