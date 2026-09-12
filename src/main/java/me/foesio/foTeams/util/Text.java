package me.foesio.foTeams.util;

import me.foesio.core.number.NumberFormatters;

public final class Text {
    private Text() {
    }

    public static String money(double value) {
        return NumberFormatters.compact(value);
    }
}
