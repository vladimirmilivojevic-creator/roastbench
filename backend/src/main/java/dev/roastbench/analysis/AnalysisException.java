package dev.roastbench.analysis;

/** Raised when roast data is structurally valid but physically or logically inconsistent. */
public class AnalysisException extends RuntimeException {

    public AnalysisException(String message) {
        super(message);
    }
}
