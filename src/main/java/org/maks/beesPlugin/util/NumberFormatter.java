package org.maks.beesPlugin.util;

import java.util.Locale;

public class NumberFormatter {

    private NumberFormatter() {
        // utility class
    }

    public static String format(double value) {
        double abs = Math.abs(value);
        if (abs >= 1_000_000_000) {
            return String.format(Locale.US, "%.1f B", value / 1_000_000_000d);
        } else if (abs >= 1_000_000) {
            return String.format(Locale.US, "%.1f M", value / 1_000_000d);
        } else if (abs >= 1_000) {
            return String.format(Locale.US, "%.1f K", value / 1_000d);
        } else {
            return String.format(Locale.US, "%.0f", value);
        }
    }
}
