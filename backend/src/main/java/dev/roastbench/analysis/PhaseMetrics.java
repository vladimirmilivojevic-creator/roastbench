package dev.roastbench.analysis;

/** One phase of the roast: DRYING, MAILLARD or DEVELOPMENT, with its time, temperatures and average RoR. */
public record PhaseMetrics(Phase phase, double startSeconds, double endSeconds, double durationSeconds,
                           double percentOfTotal, double startBeanTemp, double endBeanTemp, double averageRor,
                           boolean estimated) {

    public enum Phase {
        DRYING, MAILLARD, DEVELOPMENT
    }
}
