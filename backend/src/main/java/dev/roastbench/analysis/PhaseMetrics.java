package dev.roastbench.analysis;

/**
 * One roast phase.
 *
 * @param phase           DRYING (charge to dry end), MAILLARD (dry end to first crack) or DEVELOPMENT (first crack to drop)
 * @param startSeconds    start, seconds since charge
 * @param endSeconds      end, seconds since charge
 * @param durationSeconds length of the phase
 * @param percentOfTotal  share of the total roast time, 0-100
 * @param startBeanTemp   bean temperature at the start, degrees Celsius
 * @param endBeanTemp     bean temperature at the end, degrees Celsius
 * @param averageRor      mean rate of rise over the phase in degrees Celsius per minute; for the drying phase it is
 *                        measured from the turning point, because the probe is still hot from the drum at charge
 * @param estimated       {@code true} when a boundary was estimated instead of marked (e.g. dry end at 150 C)
 */
public record PhaseMetrics(Phase phase, double startSeconds, double endSeconds, double durationSeconds,
                           double percentOfTotal, double startBeanTemp, double endBeanTemp, double averageRor,
                           boolean estimated) {

    public enum Phase {
        DRYING, MAILLARD, DEVELOPMENT
    }
}
