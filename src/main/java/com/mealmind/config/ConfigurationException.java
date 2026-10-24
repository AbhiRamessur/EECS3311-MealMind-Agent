package com.mealmind.config;

/**
 * Unchecked exception thrown when MealMind's configuration is missing or invalid.
 *
 * <p>Messages must never contain the API key value.
 */
public class ConfigurationException extends RuntimeException {

    /**
     * Creates an exception with the given message.
     *
     * @param message a description of the problem; must not contain secret values
     */
    public ConfigurationException(String message) {
        super(message);
    }
}
