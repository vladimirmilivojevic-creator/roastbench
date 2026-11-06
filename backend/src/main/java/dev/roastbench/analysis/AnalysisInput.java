package dev.roastbench.analysis;

import java.util.EnumMap;
import java.util.Map;

/**
 * Everything the analyzer needs about one roast.
 *
 * @param curve          temperature samples, time relative to CHARGE
 * @param events         event times in seconds since CHARGE (CHARGE itself is implicit at 0)
 * @param greenWeightG   green coffee weight in grams, may be {@code null}
 * @param roastedWeightG roasted coffee weight in grams, may be {@code null}
 */
public record AnalysisInput(RoastCurve curve, Map<EventType, Double> events, Double greenWeightG,
                            Double roastedWeightG) {

    public AnalysisInput {
        if (curve == null) {
            throw new AnalysisException("Curve is required");
        }
        EnumMap<EventType, Double> copy = new EnumMap<>(EventType.class);
        if (events != null) {
            events.forEach((type, time) -> {
                if (type != EventType.CHARGE && time != null) {
                    copy.put(type, time);
                }
            });
        }
        events = Map.copyOf(copy);
    }
}
