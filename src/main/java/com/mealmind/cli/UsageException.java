package com.mealmind.cli;

/**
 * Signals that a command's arguments were missing or malformed. Its message is the usage text,
 * which {@link CLIHandler} prints as {@code "Usage: <message>"}.
 */
final class UsageException extends RuntimeException {

    UsageException(String usage) {
        super(usage);
    }
}
