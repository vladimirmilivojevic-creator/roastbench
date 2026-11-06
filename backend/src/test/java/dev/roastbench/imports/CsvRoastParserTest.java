package dev.roastbench.imports;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import dev.roastbench.analysis.EventType;
import java.time.Instant;
import java.util.Locale;
import org.junit.jupiter.api.Test;

class CsvRoastParserTest {

    private final CsvRoastParser parser = new CsvRoastParser();

    @Test
    void parsesPlainCommaSeparatedSeconds() {
        ParsedRoast roast = parser.parse(Fixtures.simpleCsv(",", 600, true));

        assertThat(roast.curve().size()).isEqualTo(301);
        assertThat(roast.curve().firstTime()).isEqualTo(0.0);
        assertThat(roast.curve().beanTemp()[0]).isCloseTo(100.0, within(1e-9));
        assertThat(roast.curve().beanTemp()[300]).isCloseTo(160.0, within(1e-9));
        assertThat(roast.curve().envTemp()[10]).isEqualTo(250.0);
        assertThat(roast.events()).isEmpty();
        assertThat(roast.format()).isEqualTo(SourceFormat.CSV);
        assertThat(roast.warnings()).anyMatch(w -> w.contains("CHARGE is not marked"));
    }

    @Test
    void worksWithoutEnvironmentTemperature() {
        ParsedRoast roast = parser.parse(Fixtures.simpleCsv(",", 100, false));

        assertThat(roast.curve().hasEnvTemp()).isFalse();
    }

    @Test
    void parsesSemicolonDelimitedWithDecimalCommaAndClockTimes() {
        StringBuilder csv = new StringBuilder("Time;BT;ET\n");
        for (int t = 0; t <= 120; t += 2) {
            csv.append(Fixtures.mmss(t)).append(';')
                    .append(String.format(Locale.ROOT, "%.1f", 100.5 + 0.1 * t).replace('.', ','))
                    .append(';').append("250,0").append('\n');
        }

        ParsedRoast roast = parser.parse(csv.toString());

        assertThat(roast.curve().size()).isEqualTo(61);
        assertThat(roast.curve().timeSeconds()[1]).isEqualTo(2.0);
        assertThat(roast.curve().beanTemp()[0]).isCloseTo(100.5, within(1e-9));
        assertThat(roast.curve().beanTemp()[60]).isCloseTo(112.5, within(1e-9));
    }

    @Test
    void parsesArtisanStyleTabSeparatedExportWithMetaRow() {
        StringBuilder csv = new StringBuilder();
        csv.append("Date:25.04.2020\tUnit:C\tCHARGE:00:10\tTP:01:10\tDRY:04:00\tFCs:08:00\tFCe:\tSCs:\tSCe:\t"
                + "DROP:10:00\tCOOL:12:00\tTime:10:00\n");
        csv.append("Time1\tTime2\tET\tBT\tEvent\n");
        for (int t = 0; t <= 720; t += 2) {
            csv.append(Fixtures.mmss(t)).append('\t').append(t >= 10 ? Fixtures.mmss(t - 10) : "")
                    .append("\t250.0\t").append(String.format(Locale.ROOT, "%.1f", 100 + 0.1 * t)).append("\t\n");
        }

        ParsedRoast roast = parser.parse(csv.toString());

        assertThat(roast.curve().firstTime()).isEqualTo(0.0);
        assertThat(roast.curve().size()).isEqualTo(361 - 5);
        assertThat(roast.warnings()).anyMatch(w -> w.contains("5 samples before charge"));
        assertThat(roast.events())
                .containsEntry(EventType.TURNING_POINT, 60.0)
                .containsEntry(EventType.DRY_END, 230.0)
                .containsEntry(EventType.FIRST_CRACK_START, 470.0)
                .containsEntry(EventType.DROP, 590.0)
                .containsEntry(EventType.COOL, 710.0)
                .doesNotContainKeys(EventType.FIRST_CRACK_END, EventType.SECOND_CRACK_START);
        assertThat(roast.roastedAt()).isEqualTo(Instant.parse("2020-04-25T12:00:00Z"));
    }

    @Test
    void readsEventsFromAnEventColumn() {
        StringBuilder csv = new StringBuilder("Time,BT,ET,Event\n");
        for (int t = 0; t <= 600; t += 2) {
            String event = switch (t) {
                case 0 -> "Charge";
                case 240 -> "Dry End";
                case 480 -> "FC Start";
                case 600 -> "Drop";
                default -> "";
            };
            csv.append(t).append(',').append(100 + 0.1 * t).append(",250,").append(event).append('\n');
        }

        ParsedRoast roast = parser.parse(csv.toString());

        assertThat(roast.events())
                .containsEntry(EventType.DRY_END, 240.0)
                .containsEntry(EventType.FIRST_CRACK_START, 480.0)
                .containsEntry(EventType.DROP, 600.0)
                .doesNotContainKey(EventType.CHARGE);
        assertThat(roast.warnings()).noneMatch(w -> w.contains("CHARGE is not marked"));
    }

    @Test
    void aChargeEventShiftsTimeAndDropsEarlierSamples() {
        StringBuilder csv = new StringBuilder("Time,BT,Event\n");
        for (int t = 0; t <= 200; t += 2) {
            csv.append(t).append(',').append(100 + 0.1 * t).append(',').append(t == 20 ? "Charge" : t == 120 ? "Drop" : "")
                    .append('\n');
        }

        ParsedRoast roast = parser.parse(csv.toString());

        assertThat(roast.curve().firstTime()).isEqualTo(0.0);
        assertThat(roast.curve().size()).isEqualTo(101 - 10);
        assertThat(roast.events()).containsEntry(EventType.DROP, 100.0);
    }

    @Test
    void readsMetadataFromHashComments() {
        String csv = "# title: Brazil Cerrado\n# beans: Brazil Cerrado natural\n# machine: Giesen W6\n"
                + "# date: 2026-03-14\n# green_weight_g: 250\n# roasted_weight_g: 212.5\n"
                + Fixtures.simpleCsv(",", 100, true);

        ParsedRoast roast = parser.parse(csv);

        assertThat(roast.title()).isEqualTo("Brazil Cerrado");
        assertThat(roast.beans()).isEqualTo("Brazil Cerrado natural");
        assertThat(roast.machine()).isEqualTo("Giesen W6");
        assertThat(roast.roastedAt()).isEqualTo(Instant.parse("2026-03-14T12:00:00Z"));
        assertThat(roast.greenWeightG()).isEqualTo(250.0);
        assertThat(roast.roastedWeightG()).isEqualTo(212.5);
    }

    @Test
    void readsWeightsInKilograms() {
        String csv = "# green_weight_kg: 5\n# roasted_weight_kg: 4,2\n" + Fixtures.simpleCsv(",", 100, false);

        ParsedRoast roast = parser.parse(csv);

        assertThat(roast.greenWeightG()).isEqualTo(5000.0);
        assertThat(roast.roastedWeightG()).isCloseTo(4200.0, within(1e-9));
    }

    @Test
    void convertsFahrenheitFromHeaderHint() {
        StringBuilder csv = new StringBuilder("Time (s),BT (°F),ET (°F)\n");
        for (int t = 0; t < 30; t++) {
            csv.append(t).append(",212,392\n");
        }

        ParsedRoast roast = parser.parse(csv.toString());

        assertThat(roast.curve().beanTemp()[0]).isCloseTo(100.0, within(1e-9));
        assertThat(roast.curve().envTemp()[0]).isCloseTo(200.0, within(1e-9));
        assertThat(roast.warnings()).anyMatch(w -> w.contains("Fahrenheit"));
    }

    @Test
    void convertsFahrenheitFromMetadata() {
        StringBuilder csv = new StringBuilder("# unit: F\nTime,BT\n");
        for (int t = 0; t < 30; t++) {
            csv.append(t).append(",212\n");
        }

        assertThat(parser.parse(csv.toString()).curve().beanTemp()[0]).isCloseTo(100.0, within(1e-9));
    }

    @Test
    void readsTimeInMinutesWhenHeaderSaysSo() {
        StringBuilder csv = new StringBuilder("Time (min),BT\n");
        for (int i = 0; i < 30; i++) {
            csv.append(i * 0.5).append(',').append(100 + i).append('\n');
        }

        ParsedRoast roast = parser.parse(csv.toString());

        assertThat(roast.curve().timeSeconds()[2]).isEqualTo(60.0);
    }

    @Test
    void stripsByteOrderMark() {
        ParsedRoast roast = parser.parse("﻿" + Fixtures.simpleCsv(",", 100, false));

        assertThat(roast.curve().size()).isEqualTo(51);
    }

    @Test
    void skipsRowsWithoutBeanTemperatureAndSaysSo() {
        StringBuilder csv = new StringBuilder("Time,BT\n");
        for (int t = 0; t < 30; t++) {
            csv.append(t).append(',').append(t == 10 || t == 11 ? "" : String.valueOf(100 + t)).append('\n');
        }

        ParsedRoast roast = parser.parse(csv.toString());

        assertThat(roast.curve().size()).isEqualTo(28);
        assertThat(roast.warnings()).anyMatch(w -> w.contains("2 rows without a bean temperature"));
    }

    @Test
    void skipsRowsWhoseTimeDoesNotIncrease() {
        StringBuilder csv = new StringBuilder("Time,BT\n");
        for (int t = 0; t < 30; t++) {
            csv.append(t).append(',').append(100 + t).append('\n');
        }
        csv.append("29,130\n");

        ParsedRoast roast = parser.parse(csv.toString());

        assertThat(roast.curve().size()).isEqualTo(30);
        assertThat(roast.warnings()).anyMatch(w -> w.contains("non-increasing"));
    }

    @Test
    void ignoresUnknownEventLabelsWithAWarning() {
        StringBuilder csv = new StringBuilder("Time,BT,Event\n");
        for (int t = 0; t < 30; t++) {
            csv.append(t).append(',').append(100 + t).append(',').append(t == 5 ? "Sneezed" : "").append('\n');
        }

        ParsedRoast roast = parser.parse(csv.toString());

        assertThat(roast.warnings()).anyMatch(w -> w.contains("Unknown event 'Sneezed'"));
    }

    @Test
    void honoursQuotedCells() {
        StringBuilder csv = new StringBuilder("\"Time\",\"BT\"\n");
        for (int t = 0; t < 30; t++) {
            csv.append('"').append(t).append("\",\"").append(100 + t).append("\"\n");
        }

        assertThat(parser.parse(csv.toString()).curve().size()).isEqualTo(30);
    }

    @Test
    void rejectsFilesWithoutHeaderRow() {
        assertThatThrownBy(() -> parser.parse("# title: only comments\n"))
                .isInstanceOf(ImportException.class)
                .hasMessageContaining("no header row");
    }

    @Test
    void rejectsHeaderWithoutBeanTemperatureColumn() {
        assertThatThrownBy(() -> parser.parse("Time,Temperature\n0,100\n"))
                .isInstanceOf(ImportException.class)
                .hasMessageContaining("time column and a bean temperature");
    }

    @Test
    void reportsInvalidNumbersWithLineNumbers() {
        String csv = "Time,BT\n0,100\n1,101\n2,abc\n3,103\n";

        assertThatThrownBy(() -> parser.parse(csv))
                .isInstanceOfSatisfying(ImportException.class, ex -> assertThat(ex.details())
                        .containsExactly("Line 4: invalid bean temperature 'abc'"));
    }

    @Test
    void reportsInvalidTimes() {
        String csv = "Time,BT\n0,100\nxx,101\n";

        assertThatThrownBy(() -> parser.parse(csv))
                .isInstanceOfSatisfying(ImportException.class, ex -> assertThat(ex.details())
                        .containsExactly("Line 3: invalid time 'xx'"));
    }

    @Test
    void rejectsImplausibleTemperaturesAndHintsAtFahrenheit() {
        StringBuilder csv = new StringBuilder("Time,BT\n");
        for (int t = 0; t < 30; t++) {
            csv.append(t).append(',').append(400 + t).append('\n');
        }

        assertThatThrownBy(() -> parser.parse(csv.toString()))
                .isInstanceOfSatisfying(ImportException.class, ex -> assertThat(ex.details().get(0))
                        .contains("plausible range").contains("Fahrenheit"));
    }

    @Test
    void capsTheNumberOfReportedErrors() {
        StringBuilder csv = new StringBuilder("Time,BT\n");
        for (int t = 0; t < 40; t++) {
            csv.append(t).append(",bad\n");
        }

        assertThatThrownBy(() -> parser.parse(csv.toString()))
                .isInstanceOfSatisfying(ImportException.class, ex -> {
                    assertThat(ex.details()).hasSize(21);
                    assertThat(ex.details().get(20)).contains("more invalid rows");
                });
    }

    @Test
    void rejectsFilesWithTooFewSamples() {
        assertThatThrownBy(() -> parser.parse("Time,BT\n0,100\n1,101\n2,102\n"))
                .isInstanceOf(ImportException.class)
                .hasMessageContaining("only 3 usable samples");
    }

    @Test
    void ignoresContradictoryWeightsWithAWarning() {
        String csv = "# green_weight_g: 200\n# roasted_weight_g: 250\n" + Fixtures.simpleCsv(",", 100, false);

        ParsedRoast roast = parser.parse(csv);

        assertThat(roast.greenWeightG()).isNull();
        assertThat(roast.warnings()).anyMatch(w -> w.contains("weights were ignored"));
    }

    @Test
    void recognisesCommonEventLabels() {
        assertThat(CsvRoastParser.eventFromLabel("First Crack")).isEqualTo(EventType.FIRST_CRACK_START);
        assertThat(CsvRoastParser.eventFromLabel("FC end")).isEqualTo(EventType.FIRST_CRACK_END);
        assertThat(CsvRoastParser.eventFromLabel("Colour change")).isEqualTo(EventType.DRY_END);
        assertThat(CsvRoastParser.eventFromLabel("SC start")).isEqualTo(EventType.SECOND_CRACK_START);
        assertThat(CsvRoastParser.eventFromLabel("Turning point")).isEqualTo(EventType.TURNING_POINT);
        assertThat(CsvRoastParser.eventFromLabel("Discharge")).isEqualTo(EventType.DROP);
        assertThat(CsvRoastParser.eventFromLabel("lunch")).isNull();
    }

    @Test
    void parsesClockTimesInSeveralShapes() {
        assertThat(CsvRoastParser.parseTime("1:05", false)).isEqualTo(65.0);
        assertThat(CsvRoastParser.parseTime("01:05.5", false)).isEqualTo(65.5);
        assertThat(CsvRoastParser.parseTime("1:01:05", false)).isEqualTo(3665.0);
        assertThat(CsvRoastParser.parseTime("12,5", false)).isEqualTo(12.5);
        assertThat(CsvRoastParser.parseTime("2", true)).isEqualTo(120.0);
        assertThatThrownBy(() -> CsvRoastParser.parseTime("1:2:3:4", false)).isInstanceOf(NumberFormatException.class);
    }
}
