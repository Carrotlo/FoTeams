package me.foesio.foTeams.input;

import me.foesio.foTeams.FoTeams;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

public record BalanceDialogConfig(
        String title,
        List<String> body,
        String fieldLabel,
        String initialValue,
        Button depositButton,
        Button withdrawButton,
        Button backButton,
        int bodyWidth,
        int inputWidth,
        int maxLength,
        boolean labelVisible,
        boolean canCloseWithEscape,
        boolean pause,
        int columns
) {
    private static final String RESOURCE = "dialogs/balance.yml";
    private static volatile BalanceDialogConfig lastGood;

    public static BalanceDialogConfig load(FoTeams plugin) {
        File file = new File(plugin.getDataFolder(), RESOURCE);
        if (!file.isFile()) {
            throw new IllegalStateException("Missing installed " + RESOURCE);
        }
        try {
            YamlConfiguration owner = new YamlConfiguration();
            owner.load(file);
            BalanceDialogConfig candidate = parse(owner);
            lastGood = candidate;
            return candidate;
        } catch (Exception invalid) {
            if (lastGood != null) {
                plugin.getLogger().warning(RESOURCE + " is invalid; previous dialog remains active: " + invalid.getMessage());
                return lastGood;
            }
            try {
                YamlConfiguration owner = new YamlConfiguration();
                owner.load(file);
                BalanceDialogConfig fallback = repairStartup(plugin, owner, bundled(plugin));
                lastGood = fallback;
                return fallback;
            } catch (Exception failure) {
                throw new IllegalStateException("Cannot load " + RESOURCE
                        + "; native balance dialog is unavailable and chat input remains available", failure);
            }
        }
    }

    public static boolean validateInstalled(FoTeams plugin) {
        File file = new File(plugin.getDataFolder(), RESOURCE);
        if (!file.exists()) {
            return true; // An owner-removed native dialog keeps its chat fallback.
        }
        try {
            YamlConfiguration owner = new YamlConfiguration();
            owner.load(file);
            parse(owner);
            return true;
        } catch (Exception invalid) {
            plugin.getLogger().warning(RESOURCE + " is invalid; previous native dialog remains active: "
                    + invalid.getMessage());
            return false;
        }
    }

    private static YamlConfiguration bundled(FoTeams plugin) throws Exception {
        try (InputStream input = plugin.getResource(RESOURCE)) {
            if (input == null) {
                throw new IllegalStateException("Missing bundled " + RESOURCE);
            }
            YamlConfiguration yaml = new YamlConfiguration();
            yaml.load(new InputStreamReader(input, StandardCharsets.UTF_8));
            return yaml;
        }
    }

    private static BalanceDialogConfig repairStartup(FoTeams plugin, YamlConfiguration owner,
                                                    YamlConfiguration bundled) {
        for (int attempt = 0; attempt < 30; attempt++) {
            try {
                return parse(owner);
            } catch (IllegalArgumentException invalid) {
                String path = invalid.getMessage().split(" ")[0];
                if (bundled.contains(path, false)) {
                    plugin.getLogger().warning(RESOURCE + ":" + path + "=" + owner.get(path)
                            + " (" + invalid.getMessage() + ") -> bundled default " + bundled.get(path)
                            + " in memory; installed file was not changed.");
                    owner.set(path, bundled.get(path));
                } else if (path.endsWith(".tooltip") || path.endsWith(".icon")
                        || path.equals("body") || path.equals("placeholder") || path.equals("initial-value")) {
                    plugin.getLogger().warning(RESOURCE + ":" + path + "=" + owner.get(path)
                            + " (" + invalid.getMessage() + ") -> absent bundled default in memory; "
                            + "installed file was not changed.");
                    owner.set(path, null);
                } else {
                    throw invalid;
                }
            }
        }
        throw new IllegalArgumentException("Too many invalid balance dialog fields");
    }

    static BalanceDialogConfig parse(YamlConfiguration yaml) {
        return new BalanceDialogConfig(
                requiredString(yaml, "title"),
                optionalLines(yaml, "body"),
                requiredString(yaml, "field-label"),
                optionalString(yaml, "initial-value"),
                button(yaml, "deposit-button"),
                button(yaml, "withdraw-button"),
                button(yaml, "back-button"),
                positive(yaml, "body-width"),
                positive(yaml, "input-width"),
                positive(yaml, "max-length"),
                optionalBoolean(yaml, "label-visible", true),
                optionalBoolean(yaml, "can-close-with-escape", true),
                optionalBoolean(yaml, "pause", false),
                positive(yaml, "columns")
        );
    }

    private static Button button(YamlConfiguration yaml, String path) {
        int width = yaml.contains(path + ".width", false) ? positive(yaml, path + ".width")
                : positive(yaml, "button-width");
        return new Button(requiredString(yaml, path + ".label"), optionalString(yaml, path + ".tooltip"),
                optionalString(yaml, path + ".icon"), width);
    }

    private static String requiredString(YamlConfiguration yaml, String path) {
        if (!yaml.isString(path) || yaml.getString(path, "").isBlank()) {
            throw new IllegalArgumentException(path + " must be nonempty text");
        }
        return yaml.getString(path);
    }

    private static String optionalString(YamlConfiguration yaml, String path) {
        if (!yaml.contains(path, false)) {
            return "";
        }
        if (!yaml.isString(path)) {
            throw new IllegalArgumentException(path + " must be text");
        }
        return yaml.getString(path, "");
    }

    private static List<String> optionalLines(YamlConfiguration yaml, String path) {
        if (!yaml.contains(path, false)) {
            return List.of();
        }
        if (!yaml.isList(path) || yaml.getList(path).stream().anyMatch(value -> !(value instanceof String))) {
            throw new IllegalArgumentException(path + " must be a text list");
        }
        return yaml.getStringList(path);
    }

    private static int positive(YamlConfiguration yaml, String path) {
        if (!yaml.isInt(path) || yaml.getInt(path) < 1) {
            throw new IllegalArgumentException(path + " must be a positive integer");
        }
        return yaml.getInt(path);
    }

    private static boolean optionalBoolean(YamlConfiguration yaml, String path, boolean fallback) {
        if (!yaml.contains(path, false)) {
            return fallback;
        }
        if (!yaml.isBoolean(path)) {
            throw new IllegalArgumentException(path + " must be true or false");
        }
        return yaml.getBoolean(path);
    }

    public record Button(String label, String tooltip, String icon, int width) {
    }
}
