package me.foesio.foTeams.util;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TextTest {
    @Test
    void configuredDecimalAndSuffixReachFinalMoneyPlaceholder() {
        List<String> original = List.of("K", "M", "B", "T", "Qa", "Qi", "Sx", "Sp", "Oc", "No", "Dc", "Ud", "Dd", "Td");
        try {
            Text.configureMoney(",", List.of("k", "m", "b", "t", "qa", "qi", "sx", "sp", "oc", "no", "dc", "ud", "dd", "td"));
            assertEquals("1,50k", Text.money(1500));
            assertEquals("12,5", Text.money(12.5));
        } finally {
            Text.configureMoney(".", original);
        }
    }
}
