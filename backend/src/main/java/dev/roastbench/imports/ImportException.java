package dev.roastbench.imports;

import java.util.List;

/** The uploaded roast log could not be read. Carries line-level details for the API error response. */
public class ImportException extends RuntimeException {

    private final List<String> details;

    public ImportException(String message) {
        this(message, List.of());
    }

    public ImportException(String message, List<String> details) {
        super(message);
        this.details = List.copyOf(details);
    }

    public List<String> details() {
        return details;
    }
}
