package me.foesio.foTeams.util;

import java.text.DecimalFormat;

public final class Text {
    private static final DecimalFormat MONEY = new DecimalFormat("#,##0.00");

    private Text() {
    }

    public static String money(double value) {
        return MONEY.format(value);
    }
}
