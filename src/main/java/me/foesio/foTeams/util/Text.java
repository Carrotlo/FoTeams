package me.foesio.foTeams.util;

import me.foesio.core.number.NumberFormatters;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.List;

public final class Text {
    private static volatile String decimalSeparator = ".";
    private static volatile List<String> suffixes = List.of("K", "M", "B", "T", "Qa", "Qi", "Sx", "Sp", "Oc", "No", "Dc", "Ud", "Dd", "Td");

    private Text() {
    }

    public static void configureMoney(String separator, List<String> compactSuffixes) {
        if (separator == null || compactSuffixes == null || compactSuffixes.size() != 14
                || compactSuffixes.stream().anyMatch(value -> value == null)) {
            throw new IllegalArgumentException("Invalid public number format");
        }
        decimalSeparator = separator;
        suffixes = List.copyOf(compactSuffixes);
    }

    public static String money(double value) {
        if (!Double.isFinite(value)) {
            return NumberFormatters.compact(value);
        }
        BigDecimal number = BigDecimal.valueOf(value);
        BigDecimal magnitude = number.abs();
        if (magnitude.compareTo(BigDecimal.valueOf(1000)) < 0) {
            return number.setScale(2, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()
                    .replace(".", decimalSeparator);
        }
        int index = -1;
        BigDecimal thousand = BigDecimal.valueOf(1000);
        while (magnitude.compareTo(thousand) >= 0 && index < suffixes.size() - 1) {
            magnitude = magnitude.divide(thousand, MathContext.DECIMAL128);
            index++;
        }
        if (index < suffixes.size() - 1 && magnitude.compareTo(new BigDecimal("999.995")) >= 0) {
            magnitude = magnitude.divide(thousand, MathContext.DECIMAL128);
            index++;
        }
        return (number.signum() < 0 ? "-" : "")
                + magnitude.setScale(2, RoundingMode.HALF_UP).toPlainString().replace(".", decimalSeparator)
                + suffixes.get(index);
    }
}
