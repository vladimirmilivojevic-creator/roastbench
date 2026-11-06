package dev.roastbench.imports;

import java.util.Locale;
import java.util.StringJoiner;
import java.util.function.IntToDoubleFunction;

/** Builders for roast log text used across the import tests. */
final class Fixtures {

    private Fixtures() {
    }

    static double[] series(int n, IntToDoubleFunction f) {
        double[] out = new double[n];
        for (int i = 0; i < n; i++) {
            out[i] = f.applyAsDouble(i);
        }
        return out;
    }

    static String pyList(double[] values) {
        StringJoiner joiner = new StringJoiner(", ", "[", "]");
        for (double v : values) {
            joiner.add(Double.toString(v));
        }
        return joiner.toString();
    }

    static String pyList(int... values) {
        StringJoiner joiner = new StringJoiner(", ", "[", "]");
        for (int v : values) {
            joiner.add(Integer.toString(v));
        }
        return joiner.toString();
    }

    /** An Artisan profile in Python-literal form (single quotes, True/False/None) like Artisan writes it. */
    static String alog(String mode, double[] timex, double[] temp1, double[] temp2, int[] timeindex, String extra) {
        return "{'version': '3.1.0', 'viewerMode': False, 'mode': '" + mode + "', "
                + "'timex': " + pyList(timex) + ", 'temp1': " + pyList(temp1) + ", 'temp2': " + pyList(temp2)
                + ", 'timeindex': " + pyList(timeindex) + (extra.isEmpty() ? "" : ", " + extra) + ", 'plus_store': None}";
    }

    /**
     * 601 samples, 2 s apart (0..1200 s). Charge is sample 4 (t = 8 s). Before charge the probe reads 200 C; after
     * charge bean temperature is 100 + 0.1 * (seconds since charge), environment temperature is constant 250.
     * DRY = sample 100, FCs = 300, FCe = 330, DROP = 480, COOL = 600.
     */
    static String standardAlog(String extra) {
        double[] timex = series(601, i -> 2.0 * i);
        double[] bt = series(601, i -> i < 4 ? 200.0 : 100.0 + 0.1 * (2.0 * i - 8.0));
        double[] et = series(601, i -> 250.0);
        return alog("C", timex, et, bt, new int[] {4, 100, 300, 330, 0, 0, 480, 600}, extra);
    }

    /** CSV with {@code seconds} 0..{@code lastSecond} in 2 s steps; BT = 100 + 0.1 t, ET = 250. */
    static String simpleCsv(String delimiter, int lastSecond, boolean withEnv) {
        StringBuilder sb = new StringBuilder();
        sb.append("Time").append(delimiter).append("BT");
        if (withEnv) {
            sb.append(delimiter).append("ET");
        }
        sb.append('\n');
        for (int t = 0; t <= lastSecond; t += 2) {
            sb.append(t).append(delimiter).append(String.format(Locale.ROOT, "%.1f", 100 + 0.1 * t));
            if (withEnv) {
                sb.append(delimiter).append("250.0");
            }
            sb.append('\n');
        }
        return sb.toString();
    }

    static String mmss(int seconds) {
        return String.format(Locale.ROOT, "%02d:%02d", seconds / 60, seconds % 60);
    }
}
