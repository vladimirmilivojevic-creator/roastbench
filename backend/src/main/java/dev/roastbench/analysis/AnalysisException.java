package dev.roastbench.analysis;

/** Thrown when the roast data does not make sense (for example first crack before dry end). */
public class AnalysisException extends RuntimeException {

    public AnalysisException(String message) {
        super(message);
    }
}
