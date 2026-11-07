package com.mealmind.app;

import com.mealmind.config.AppConfig;
import com.mealmind.config.ConfigurationException;
import com.mealmind.llm.LlmClientFactory;
import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Startup steps shared by the command line and the GUI, so both front ends configure a session the same way.
 */
public final class Startup {

    private Startup() {
    }

    /**
     * Loads {@link AppConfig} from {@code envFile} and {@code env} and gives the session's AI service its
     * client, or disables AI with the reason. No network call is made.
     *
     * @param ctx     the session
     * @param env     environment variables
     * @param envFile the {@code .env} file (it may not exist)
     * @return the line to show when AI is disabled, e.g. "AI features are disabled: ... Deterministic
     *         features still work.", or empty when AI is configured
     * @throws NullPointerException if an argument is null
     */
    public static Optional<String> configureAi(ApplicationContext ctx, Map<String, String> env, Path envFile) {
        Objects.requireNonNull(ctx, "ctx");
        Objects.requireNonNull(env, "env");
        Objects.requireNonNull(envFile, "envFile");
        try {
            ctx.llm().useClient(LlmClientFactory.create(AppConfig.load(envFile, env)));
            return Optional.empty();
        } catch (ConfigurationException | IllegalArgumentException e) {
            String reason = e.getMessage().endsWith(".") ? e.getMessage().substring(0, e.getMessage().length() - 1)
                    : e.getMessage();
            ctx.llm().disable(reason);
            return Optional.of("AI features are disabled: " + reason + ". Deterministic features still work.");
        }
    }
}
