package dev.roastbench.imports;

import dev.roastbench.analysis.AnalysisException;
import dev.roastbench.analysis.EventType;
import dev.roastbench.analysis.RoastCurve;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Reads Artisan .alog files. Fields we use: timex (time), temp2 (bean temperature), temp1 (environment temperature),
 * timeindex (where the events are), weight, mode (C or F), title, beans and roastertype.
 * Artisan writes -1 when there is no reading.
 */
public final class ArtisanAlogParser {

    private static final int MAX_SAMPLES = 20_000;
    private static final EventType[] TIME_INDEX_ORDER = {EventType.CHARGE, EventType.DRY_END,
            EventType.FIRST_CRACK_START, EventType.FIRST_CRACK_END, EventType.SECOND_CRACK_START,
            EventType.SECOND_CRACK_END, EventType.DROP, EventType.COOL};

    public ParsedRoast parse(String text) {
        Object root = PythonLiteralParser.parse(text);
        if (!(root instanceof Map<?, ?> profile)) {
            throw new ImportException("Not an Artisan profile: the top level is not a dictionary");
        }
        List<String> warnings = new ArrayList<>();

        double[] timex = requiredNumbers(profile, "timex");
        double[] bean = requiredNumbers(profile, "temp2");
        double[] env = optionalNumbers(profile, "temp1");
        int n = timex.length;
        if (n > MAX_SAMPLES) {
            throw new ImportException("Profile has " + n + " samples; the limit is " + MAX_SAMPLES);
        }
        if (bean.length != n) {
            throw new ImportException("Not an Artisan profile: 'timex' and 'temp2' have different lengths");
        }
        if (env != null && env.length != n) {
            env = null;
            warnings.add("Environment temperature (temp1) has a different length than time and was ignored");
        }

        boolean fahrenheit = "F".equalsIgnoreCase(text(profile, "mode"));
        if (fahrenheit) {
            warnings.add("Temperatures were converted from Fahrenheit to Celsius");
        }

        int[] timeIndex = timeIndex(profile.get("timeindex"));
        int charge = timeIndex[0];
        if (charge < 0 || charge >= n) {
            charge = 0;
            warnings.add("CHARGE is not marked in the profile; the first sample is treated as charge");
        }
        double t0 = timex[charge];

        List<double[]> kept = new ArrayList<>(); // {time, bean, env or NaN}
        int droppedInvalid = 0;
        int droppedOrder = 0;
        double lastTime = Double.NEGATIVE_INFINITY;
        for (int i = charge; i < n; i++) {
            double t = timex[i] - t0;
            double bt = convertTemp(bean[i], fahrenheit);
            if (Double.isNaN(bt) || Double.isNaN(t)) {
                droppedInvalid++;
                continue;
            }
            if (t <= lastTime) {
                droppedOrder++;
                continue;
            }
            lastTime = t;
            kept.add(new double[] {t, bt, env == null ? Double.NaN : convertTemp(env[i], fahrenheit)});
        }
        if (droppedInvalid > 0) {
            warnings.add(droppedInvalid + " samples without a bean temperature reading were skipped");
        }
        if (droppedOrder > 0) {
            warnings.add(droppedOrder + " samples with non-increasing time were skipped");
        }
        if (kept.size() < 2) {
            throw new ImportException("The profile has fewer than two valid bean temperature samples after charge");
        }

        double[] time = new double[kept.size()];
        double[] bt = new double[kept.size()];
        double[] et = new double[kept.size()];
        for (int i = 0; i < kept.size(); i++) {
            time[i] = kept.get(i)[0];
            bt[i] = kept.get(i)[1];
            et[i] = kept.get(i)[2];
        }

        Map<EventType, Double> events = new EnumMap<>(EventType.class);
        for (int k = 1; k < TIME_INDEX_ORDER.length; k++) {
            int idx = timeIndex[k];
            if (idx > charge && idx < n) {
                events.put(TIME_INDEX_ORDER[k], timex[idx] - t0);
            }
        }

        RoastCurve curve;
        try {
            curve = new RoastCurve(time, bt, EnvironmentGaps.fill(et));
        } catch (AnalysisException ex) {
            throw new ImportException("Invalid curve data: " + ex.getMessage());
        }

        double[] weights = weights(profile.get("weight"));
        Double green = weights[0] > 0 ? weights[0] : null;
        Double roasted = weights[1] > 0 ? weights[1] : null;
        if (green != null && roasted != null && roasted > green) {
            warnings.add("Roasted weight is larger than green weight in the profile; weights were ignored");
            green = null;
            roasted = null;
        }

        return new ParsedRoast(text(profile, "title"), text(profile, "beans"), text(profile, "roastertype"),
                roastedAt(profile), curve, events, green, roasted, SourceFormat.ARTISAN_ALOG, warnings);
    }

    private static double convertTemp(double value, boolean fahrenheit) {
        if (Double.isNaN(value) || value < 0 || value > 1000) { // Artisan uses -1 for "no reading"
            return Double.NaN;
        }
        return fahrenheit ? (value - 32.0) * 5.0 / 9.0 : value;
    }

    private static int[] timeIndex(Object raw) {
        int[] idx = new int[TIME_INDEX_ORDER.length];
        java.util.Arrays.fill(idx, -1);
        if (raw instanceof List<?> list) {
            for (int i = 0; i < Math.min(list.size(), idx.length); i++) {
                if (list.get(i) instanceof Number number) {
                    idx[i] = number.intValue();
                }
            }
        }
        return idx;
    }

    private static double[] weights(Object raw) {
        double[] out = {0, 0};
        if (!(raw instanceof List<?> list) || list.size() < 2) {
            return out;
        }
        double factor = 1.0;
        if (list.size() >= 3 && list.get(2) instanceof String unit) {
            factor = switch (unit.trim().toLowerCase(Locale.ROOT)) {
                case "kg" -> 1000.0;
                case "lb" -> 453.59237;
                case "oz" -> 28.349523125;
                default -> 1.0;
            };
        }
        for (int i = 0; i < 2; i++) {
            if (list.get(i) instanceof Number number && Double.isFinite(number.doubleValue())) {
                out[i] = number.doubleValue() * factor;
            }
        }
        return out;
    }

    private static Instant roastedAt(Map<?, ?> profile) {
        try {
            if (profile.get("roastepoch") instanceof Number epoch && epoch.longValue() > 0) {
                return Instant.ofEpochSecond(epoch.longValue());
            }
            String date = text(profile, "roastisodate");
            if (date != null) {
                String time = text(profile, "roasttime");
                LocalTime localTime = time == null ? LocalTime.NOON : LocalTime.parse(time);
                return LocalDate.parse(date).atTime(localTime).toInstant(ZoneOffset.UTC);
            }
        } catch (RuntimeException ignored) {
            // an unreadable date is not worth failing the import
        }
        return null;
    }

    private static String text(Map<?, ?> profile, String key) {
        Object value = profile.get(key);
        if (value instanceof String s && !s.isBlank()) {
            return s.trim();
        }
        return null;
    }

    private static double[] requiredNumbers(Map<?, ?> profile, String key) {
        double[] numbers = optionalNumbers(profile, key);
        if (numbers == null) {
            throw new ImportException("Not an Artisan profile: required field '" + key + "' is missing");
        }
        return numbers;
    }

    private static double[] optionalNumbers(Map<?, ?> profile, String key) {
        if (!(profile.get(key) instanceof List<?> list)) {
            return null;
        }
        double[] out = new double[list.size()];
        for (int i = 0; i < out.length; i++) {
            out[i] = list.get(i) instanceof Number number ? number.doubleValue() : Double.NaN;
        }
        return out;
    }
}
