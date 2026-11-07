package dev.roastbench.imports;

import java.util.List;

/** The uploaded file cannot be read. It carries a list of details (for example line numbers) for the user. */
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
