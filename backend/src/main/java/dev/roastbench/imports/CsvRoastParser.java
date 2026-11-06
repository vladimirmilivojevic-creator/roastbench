package dev.roastbench.imports;

import dev.roastbench.analysis.AnalysisException;
import dev.roastbench.analysis.EventType;
import dev.roastbench.analysis.RoastCurve;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads roast logs from CSV text. The format is deliberately tolerant; it is documented in
 * {@code docs/import-formats.md}.
 *
 * <ul>
 *   <li>delimiter: tab, semicolon or comma (detected on the header row); decimal comma allowed with tab/semicolon</li>
 *   <li>header row with a time column and a bean temperature column; optional environment temperature and event
 *       columns; temperature unit hints such as {@code BT (F)}</li>
 *   <li>time as seconds, {@code mm:ss} or {@code hh:mm:ss}</li>
 *   <li>optional metadata above the header as {@code # key: value} lines, or an Artisan-style first row such as
 *       {@code Date:25.04.2020<TAB>Unit:C<TAB>CHARGE:00:35<TAB>DROP:11:35}</li>
 * </ul>
 */
public final class CsvRoastParser {

    private static final int MIN_SAMPLES = 10;
    private static final int MAX_SAMPLES = 20_000;
    private static final int MAX_ERRORS = 20;
    private static final double MAX_PLAUSIBLE_TEMP_C = 350.0;

    private static final Pattern META_CELL = Pattern.compile("^\\s*([A-Za-z][A-Za-z0-9 _]*):(.*)$");
    private static final Pattern COMMENT_META = Pattern.compile("^\\s*#\\s*([A-Za-z][A-Za-z0-9 _]*)\\s*[:=]\\s*(.*)$");
    private static final Set<String> ARTISAN_META_KEYS =
            Set.of("date", "unit", "charge", "tp", "dry", "fcs", "fce", "scs", "sce", "drop", "cool", "time");

    private static final List<String> TIME_ALIASES =
            List.of("time", "time1", "seconds", "sec", "t", "elapsed", "elapsedtime", "timer", "time2");
    private static final Set<String> BEAN_ALIASES =
            Set.of("bt", "bean", "beantemp", "beantemperature", "tempbean", "btc");
    private static final Set<String> ENV_ALIASES = Set.of("et", "env", "envtemp", "environment", "environmenttemp",
            "drum", "drumtemp", "exhaust", "exhausttemp", "airtemp", "chamber");
    private static final Set<String> EVENT_ALIASES = Set.of("event", "events", "marker", "markers", "milestone");

    public ParsedRoast parse(String rawText) {
        String text = rawText.startsWith("﻿") ? rawText.substring(1) : rawText;
        String[] lines = text.split("\\r?\\n", -1);
        List<String> warnings = new ArrayList<>();
        Map<String, String> meta = new java.util.HashMap<>();
        Map<EventType, Double> metaEvents = new EnumMap<>(EventType.class);

        int headerLine = -1;
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            if (line.isBlank()) {
                continue;
            }
            Matcher comment = COMMENT_META.matcher(line);
            if (comment.matches()) {
                meta.put(normalizeKey(comment.group(1)), comment.group(2).trim());
                continue;
            }
            if (line.stripLeading().startsWith("#")) {
                continue;
            }
            if (readArtisanMetaRow(line, meta, metaEvents, i + 1)) {
                continue;
            }
            headerLine = i;
            break;
        }
        if (headerLine < 0) {
            throw new ImportException("The file has no header row with time and bean temperature columns");
        }

        char delimiter = detectDelimiter(lines[headerLine]);
        List<String> header = splitLine(lines[headerLine], delimiter);
        Columns columns = Columns.detect(header);
        if (columns.time < 0 || columns.bean < 0) {
            throw new ImportException("Could not find a time column and a bean temperature (BT) column in the header: "
                    + String.join(" | ", header));
        }
        boolean fahrenheit = columns.fahrenheit || "f".equalsIgnoreCase(meta.getOrDefault("unit", ""));
        if (fahrenheit) {
            warnings.add("Temperatures were converted from Fahrenheit to Celsius");
        }
        boolean decimalComma = delimiter != ',';

        List<String> errors = new ArrayList<>();
        List<double[]> rows = new ArrayList<>(); // {fileTime, bean, env or NaN}
        List<Double> eventTimes = new ArrayList<>();
        List<EventType> eventTypes = new ArrayList<>();
        Set<String> unknownEvents = new HashSet<>();
        int skippedEmpty = 0;

        for (int i = headerLine + 1; i < lines.length; i++) {
            String line = lines[i];
            if (line.isBlank()) {
                continue;
            }
            int lineNo = i + 1;
            List<String> cells = splitLine(line, delimiter);
            String timeCell = cell(cells, columns.time);
            double time;
            try {
                time = parseTime(timeCell, columns.timeInMinutes);
            } catch (NumberFormatException ex) {
                addError(errors, "Line " + lineNo + ": invalid time '" + timeCell + "'");
                continue;
            }
            String eventCell = columns.event >= 0 ? cell(cells, columns.event).trim() : "";
            if (!eventCell.isEmpty()) {
                EventType type = eventFromLabel(eventCell);
                if (type == null) {
                    if (unknownEvents.add(eventCell.toLowerCase(Locale.ROOT))) {
                        warnings.add("Unknown event '" + eventCell + "' (line " + lineNo + ") was ignored");
                    }
                } else {
                    eventTypes.add(type);
                    eventTimes.add(time);
                }
            }
            String beanCell = cell(cells, columns.bean).trim();
            if (beanCell.isEmpty()) {
                skippedEmpty++;
                continue;
            }
            double bean;
            try {
                bean = toCelsius(parseNumber(beanCell, decimalComma), fahrenheit);
            } catch (NumberFormatException ex) {
                addError(errors, "Line " + lineNo + ": invalid bean temperature '" + beanCell + "'");
                continue;
            }
            if (bean < 0 || bean > MAX_PLAUSIBLE_TEMP_C) {
                addError(errors, "Line " + lineNo + ": bean temperature " + round1(bean)
                        + " C is outside the plausible range 0-" + (int) MAX_PLAUSIBLE_TEMP_C + " C"
                        + (fahrenheit ? "" : " (is the file in Fahrenheit? add '# unit: F')"));
                continue;
            }
            double env = Double.NaN;
            if (columns.env >= 0) {
                String envCell = cell(cells, columns.env).trim();
                if (!envCell.isEmpty()) {
                    try {
                        env = toCelsius(parseNumber(envCell, decimalComma), fahrenheit);
                    } catch (NumberFormatException ex) {
                        addError(errors, "Line " + lineNo + ": invalid environment temperature '" + envCell + "'");
                        continue;
                    }
                }
            }
            rows.add(new double[] {time, bean, env});
            if (rows.size() > MAX_SAMPLES) {
                throw new ImportException("The file has more than " + MAX_SAMPLES + " samples");
            }
        }
        if (!errors.isEmpty()) {
            throw new ImportException("The CSV has invalid rows", errors);
        }
        if (skippedEmpty > 0) {
            warnings.add(skippedEmpty + " rows without a bean temperature were skipped");
        }
        if (rows.size() < MIN_SAMPLES) {
            throw new ImportException("The CSV has only " + rows.size() + " usable samples; at least " + MIN_SAMPLES
                    + " are needed");
        }

        // Charge on the file's time axis: event column, then Artisan meta row, then the first sample.
        double chargeTime = rows.get(0)[0];
        Double chargeFromEvents = firstEventTime(eventTypes, eventTimes, EventType.CHARGE);
        if (chargeFromEvents != null) {
            chargeTime = chargeFromEvents;
        } else if (metaEvents.containsKey(EventType.CHARGE)) {
            chargeTime = metaEvents.get(EventType.CHARGE);
        } else {
            warnings.add("CHARGE is not marked; the first sample is treated as charge");
        }

        List<double[]> kept = new ArrayList<>();
        int droppedBeforeCharge = 0;
        int droppedOrder = 0;
        double last = Double.NEGATIVE_INFINITY;
        for (double[] row : rows) {
            double t = row[0] - chargeTime;
            if (t < 0) {
                droppedBeforeCharge++;
                continue;
            }
            if (t <= last) {
                droppedOrder++;
                continue;
            }
            last = t;
            kept.add(new double[] {t, row[1], row[2]});
        }
        if (droppedBeforeCharge > 0) {
            warnings.add(droppedBeforeCharge + " samples before charge were skipped");
        }
        if (droppedOrder > 0) {
            warnings.add(droppedOrder + " samples with non-increasing time were skipped");
        }
        if (kept.size() < MIN_SAMPLES) {
            throw new ImportException("Only " + kept.size() + " samples remain after charge; at least " + MIN_SAMPLES
                    + " are needed");
        }

        Map<EventType, Double> events = new EnumMap<>(EventType.class);
        for (Map.Entry<EventType, Double> entry : metaEvents.entrySet()) {
            if (entry.getKey() != EventType.CHARGE) {
                events.put(entry.getKey(), entry.getValue() - chargeTime);
            }
        }
        for (int i = 0; i < eventTypes.size(); i++) {
            if (eventTypes.get(i) != EventType.CHARGE) {
                events.put(eventTypes.get(i), eventTimes.get(i) - chargeTime);
            }
        }
        events.values().removeIf(t -> t < 0);

        double[] time = new double[kept.size()];
        double[] bean = new double[kept.size()];
        double[] env = new double[kept.size()];
        for (int i = 0; i < kept.size(); i++) {
            time[i] = kept.get(i)[0];
            bean[i] = kept.get(i)[1];
            env[i] = kept.get(i)[2];
        }
        RoastCurve curve;
        try {
            curve = new RoastCurve(time, bean, EnvironmentGaps.fill(env));
        } catch (AnalysisException ex) {
            throw new ImportException("Invalid curve data: " + ex.getMessage());
        }

        Double green = weight(meta, "green");
        Double roasted = weight(meta, "roasted");
        if (green != null && roasted != null && roasted > green) {
            warnings.add("Roasted weight is larger than green weight in the file; weights were ignored");
            green = null;
            roasted = null;
        }
        return new ParsedRoast(blankToNull(meta.get("title")), blankToNull(firstNonNull(meta, "beans", "coffee")),
                blankToNull(firstNonNull(meta, "machine", "roaster")), parseDate(meta.get("date")), curve, events,
                green, roasted, SourceFormat.CSV, warnings);
    }

    // ---- metadata -------------------------------------------------------------------------------------------

    private boolean readArtisanMetaRow(String line, Map<String, String> meta, Map<EventType, Double> events,
                                       int lineNo) {
        char delimiter = line.indexOf('\t') >= 0 ? '\t' : (line.indexOf(';') >= 0 ? ';' : ',');
        List<String> cells = splitLine(line, delimiter);
        List<String[]> pairs = new ArrayList<>();
        boolean known = false;
        for (String cell : cells) {
            if (cell.isBlank()) {
                continue;
            }
            Matcher m = META_CELL.matcher(cell);
            if (!m.matches()) {
                return false;
            }
            String key = normalizeKey(m.group(1));
            known |= ARTISAN_META_KEYS.contains(key);
            pairs.add(new String[] {key, m.group(2).trim()});
        }
        if (pairs.size() < 2 || !known) {
            return false;
        }
        for (String[] pair : pairs) {
            EventType type = switch (pair[0]) {
                case "charge" -> EventType.CHARGE;
                case "tp" -> EventType.TURNING_POINT;
                case "dry" -> EventType.DRY_END;
                case "fcs" -> EventType.FIRST_CRACK_START;
                case "fce" -> EventType.FIRST_CRACK_END;
                case "scs" -> EventType.SECOND_CRACK_START;
                case "sce" -> EventType.SECOND_CRACK_END;
                case "drop" -> EventType.DROP;
                case "cool" -> EventType.COOL;
                default -> null;
            };
            if (type != null) {
                if (!pair[1].isEmpty()) {
                    try {
                        events.put(type, parseTime(pair[1], false));
                    } catch (NumberFormatException ex) {
                        throw new ImportException("Line " + lineNo + ": invalid time '" + pair[1] + "' for " + pair[0]);
                    }
                }
            } else {
                meta.put(pair[0], pair[1]);
            }
        }
        return true;
    }

    private static Double weight(Map<String, String> meta, String which) {
        String grams = meta.get(which + "weightg");
        String kilos = meta.get(which + "weightkg");
        try {
            if (grams != null && !grams.isBlank()) {
                return parseNumber(grams.trim(), true);
            }
            if (kilos != null && !kilos.isBlank()) {
                return parseNumber(kilos.trim(), true) * 1000.0;
            }
        } catch (NumberFormatException ex) {
            throw new ImportException("Invalid " + which + " weight in the file metadata");
        }
        return null;
    }

    private static Instant parseDate(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String v = value.trim();
        try {
            return OffsetDateTime.parse(v).toInstant();
        } catch (DateTimeParseException ignored) {
            // try the next format
        }
        try {
            return LocalDateTime.parse(v).toInstant(ZoneOffset.UTC);
        } catch (DateTimeParseException ignored) {
            // try the next format
        }
        for (String pattern : new String[] {"yyyy-MM-dd", "dd.MM.yyyy", "dd/MM/yyyy"}) {
            try {
                return LocalDate.parse(v, DateTimeFormatter.ofPattern(pattern)).atTime(12, 0).toInstant(ZoneOffset.UTC);
            } catch (DateTimeParseException ignored) {
                // try the next format
            }
        }
        return null;
    }

    // ---- columns --------------------------------------------------------------------------------------------

    private static final class Columns {
        int time = -1;
        int bean = -1;
        int env = -1;
        int event = -1;
        boolean timeInMinutes;
        boolean fahrenheit;

        static Columns detect(List<String> header) {
            Columns c = new Columns();
            int bestTimeRank = Integer.MAX_VALUE;
            for (int i = 0; i < header.size(); i++) {
                String raw = header.get(i);
                String lower = raw.toLowerCase(Locale.ROOT);
                String name = normalizeColumn(raw);
                if (name.isEmpty()) {
                    continue;
                }
                int rank = TIME_ALIASES.indexOf(name);
                if (rank >= 0 && rank < bestTimeRank) {
                    bestTimeRank = rank;
                    c.time = i;
                    c.timeInMinutes = lower.contains("(min") || lower.contains("[min");
                } else if (c.bean < 0 && BEAN_ALIASES.contains(name)) {
                    c.bean = i;
                    c.fahrenheit |= isFahrenheit(lower);
                } else if (c.env < 0 && ENV_ALIASES.contains(name)) {
                    c.env = i;
                    c.fahrenheit |= isFahrenheit(lower);
                } else if (c.event < 0 && EVENT_ALIASES.contains(name)) {
                    c.event = i;
                }
            }
            return c;
        }

        private static boolean isFahrenheit(String lowerHeader) {
            return lowerHeader.contains("°f") || lowerHeader.contains("(f)") || lowerHeader.contains("[f]")
                    || lowerHeader.contains("fahrenheit");
        }

        private static String normalizeColumn(String raw) {
            String withoutUnits = raw.replaceAll("\\(.*?\\)|\\[.*?]", "");
            return withoutUnits.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
        }
    }

    // ---- low-level parsing ----------------------------------------------------------------------------------

    static EventType eventFromLabel(String label) {
        String key = label.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
        return switch (key) {
            case "charge", "chargein", "start" -> EventType.CHARGE;
            case "tp", "turningpoint" -> EventType.TURNING_POINT;
            case "dry", "dryend", "dryingend", "yellowing", "colorchange", "colourchange" -> EventType.DRY_END;
            case "fc", "fcs", "fcstart", "firstcrack", "firstcrackstart" -> EventType.FIRST_CRACK_START;
            case "fce", "fcend", "firstcrackend" -> EventType.FIRST_CRACK_END;
            case "sc", "scs", "scstart", "secondcrack", "secondcrackstart" -> EventType.SECOND_CRACK_START;
            case "sce", "scend", "secondcrackend" -> EventType.SECOND_CRACK_END;
            case "drop", "end", "discharge", "dropout" -> EventType.DROP;
            case "cool", "coolend", "cooling" -> EventType.COOL;
            default -> null;
        };
    }

    static double parseTime(String raw, boolean minutes) {
        String s = raw.trim();
        if (s.isEmpty()) {
            throw new NumberFormatException("empty");
        }
        if (s.indexOf(':') >= 0) {
            String[] parts = s.split(":", -1);
            if (parts.length == 2) {
                return Long.parseLong(parts[0].trim()) * 60.0 + parseNumber(parts[1], true);
            }
            if (parts.length == 3) {
                return Long.parseLong(parts[0].trim()) * 3600.0 + Long.parseLong(parts[1].trim()) * 60.0
                        + parseNumber(parts[2], true);
            }
            throw new NumberFormatException(s);
        }
        double value = parseNumber(s, true);
        return minutes ? value * 60.0 : value;
    }

    static double parseNumber(String raw, boolean decimalComma) {
        String s = raw.trim();
        if (decimalComma) {
            s = s.replace(',', '.');
        }
        double value = Double.parseDouble(s);
        if (!Double.isFinite(value)) {
            throw new NumberFormatException(raw);
        }
        return value;
    }

    private static double toCelsius(double value, boolean fahrenheit) {
        return fahrenheit ? (value - 32.0) * 5.0 / 9.0 : value;
    }

    private static char detectDelimiter(String headerLine) {
        if (headerLine.indexOf('\t') >= 0) {
            return '\t';
        }
        long semicolons = headerLine.chars().filter(c -> c == ';').count();
        long commas = headerLine.chars().filter(c -> c == ',').count();
        return semicolons > commas ? ';' : ',';
    }

    /** Splits one line, honouring double-quoted cells. */
    static List<String> splitLine(String line, char delimiter) {
        List<String> cells = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (quoted) {
                if (c == '"') {
                    if (i + 1 < line.length() && line.charAt(i + 1) == '"') {
                        current.append('"');
                        i++;
                    } else {
                        quoted = false;
                    }
                } else {
                    current.append(c);
                }
            } else if (c == '"') {
                quoted = true;
            } else if (c == delimiter) {
                cells.add(current.toString());
                current.setLength(0);
            } else {
                current.append(c);
            }
        }
        cells.add(current.toString());
        return cells;
    }

    private static String cell(List<String> cells, int index) {
        return index >= 0 && index < cells.size() ? cells.get(index) : "";
    }

    private static Double firstEventTime(List<EventType> types, List<Double> times, EventType wanted) {
        for (int i = 0; i < types.size(); i++) {
            if (types.get(i) == wanted) {
                return times.get(i);
            }
        }
        return null;
    }

    private static String normalizeKey(String key) {
        return key.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    private static String firstNonNull(Map<String, String> meta, String... keys) {
        for (String key : keys) {
            if (meta.get(key) != null) {
                return meta.get(key);
            }
        }
        return null;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static void addError(List<String> errors, String message) {
        if (errors.size() < MAX_ERRORS) {
            errors.add(message);
        } else if (errors.size() == MAX_ERRORS) {
            errors.add("... more invalid rows not shown");
        }
    }

    private static double round1(double value) {
        return Math.round(value * 10.0) / 10.0;
    }
}
