package dev.roastbench.analysis;

import java.util.EnumMap;
import java.util.Map;

/** Everything the calculation needs: the curve, the events (seconds from charge) and the weights in grams. */
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
