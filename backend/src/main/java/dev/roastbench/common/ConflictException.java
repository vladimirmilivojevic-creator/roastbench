package dev.roastbench.common;

/** The request is valid but conflicts with the current state (duplicate code, not enough stock, limit reached). */
public class ConflictException extends RuntimeException {

    public ConflictException(String message) {
        super(message);
    }
}
