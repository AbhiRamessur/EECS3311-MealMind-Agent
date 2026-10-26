package com.mealmind.persistence;

/**
 * Unchecked exception thrown when stored data cannot be read or written. Messages name the file or
 * resource and, where possible, the offending record and field, so the user can fix the data.
 */
public class RepositoryException extends RuntimeException {

    /**
     * Creates an exception with a message.
     *
     * @param message what went wrong and where
     */
    public RepositoryException(String message) {
        super(message);
    }

    /**
     * Creates an exception with a message and the underlying cause.
     *
     * @param message what went wrong and where
     * @param cause   the original exception
     */
    public RepositoryException(String message, Throwable cause) {
        super(message, cause);
    }
}
