package me.foesio.foTeams.util;

import java.util.Locale;
import java.util.Map;

public final class TagColorUtil {
    private static final Map<String, String> NAMED_COLORS = Map.ofEntries(
            Map.entry("black", "&0"),
            Map.entry("dark_blue", "&1"),
            Map.entry("darkblue", "&1"),
            Map.entry("dark_green", "&2"),
            Map.entry("darkgreen", "&2"),
            Map.entry("dark_aqua", "&3"),
            Map.entry("darkaqua", "&3"),
            Map.entry("dark_red", "&4"),
            Map.entry("darkred", "&4"),
            Map.entry("dark_purple", "&5"),
            Map.entry("darkpurple", "&5"),
            Map.entry("gold", "&6"),
            Map.entry("gray", "&7"),
            Map.entry("grey", "&7"),
            Map.entry("dark_gray", "&8"),
            Map.entry("dark_grey", "&8"),
            Map.entry("darkgray", "&8"),
            Map.entry("darkgrey", "&8"),
            Map.entry("blue", "&9"),
            Map.entry("green", "&a"),
            Map.entry("aqua", "&b"),
            Map.entry("red", "&c"),
            Map.entry("light_purple", "&d"),
            Map.entry("lightpurple", "&d"),
            Map.entry("yellow", "&e"),
            Map.entry("white", "&f")
    );

    private TagColorUtil() {
    }

    public static String normalize(String input) {
        if (input == null) {
            return null;
        }
        String trimmed = input.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        if (trimmed.matches("(?i)#[0-9a-f]{6}")) {
            return trimmed;
        }
        if (trimmed.matches("(?i)[&§][0-9a-f]")) {
            return "&" + Character.toLowerCase(trimmed.charAt(1));
        }
        return NAMED_COLORS.get(trimmed.toLowerCase(Locale.ROOT));
    }
}
