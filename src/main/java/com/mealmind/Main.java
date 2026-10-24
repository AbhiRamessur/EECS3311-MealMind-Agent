package com.mealmind;

/**
 * Entry point for MealMind. For now it only prints the version; configuration loading and the
 * CLI/GUI are wired in by later commits.
 */
public final class Main {

    /** Application version; keep in sync with the version in {@code pom.xml}. */
    public static final String VERSION = "0.1.0-SNAPSHOT";

    private Main() {
    }

    /**
     * Prints the application name and version.
     *
     * @param args command-line arguments (unused)
     */
    public static void main(String[] args) {
        System.out.println("MealMind " + VERSION);
    }
}
