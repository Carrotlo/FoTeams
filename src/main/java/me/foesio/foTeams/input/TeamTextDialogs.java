package me.foesio.foTeams.input;

import me.foesio.core.dialog.DialogButton;
import me.foesio.core.dialog.TextDialogRequest;
import me.foesio.foTeams.FoTeams;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

/** Installed public dialog definitions. Owner omissions remain absent. */
final class TeamTextDialogs {
    private static final Set<String> IDS = Set.of("team-name", "team-tag", "team-description",
            "tag-color", "warp-create", "team-search");
    private final File dataFolder;
    private final Logger logger;
    private Map<String, TextDialogRequest> active = Map.of();

    TeamTextDialogs(FoTeams plugin) {
        this(plugin.getDataFolder(), plugin.getLogger());
    }

    TeamTextDialogs(File dataFolder, Logger logger) {
        this.dataFolder = dataFolder;
        this.logger = logger;
        reload(true);
    }

    boolean reload() {
        return reload(false);
    }

    TextDialogRequest request(String id) {
        return active.get(id);
    }

    static boolean validateInstalled(FoTeams plugin) {
        try {
            for (String id : IDS) {
                File file = new File(plugin.getDataFolder(), "dialogs/" + id + ".yml");
                if (!file.exists()) {
                    continue;
                }
                YamlConfiguration yaml = new YamlConfiguration();
                yaml.load(file);
                parse(yaml);
            }
            return true;
        } catch (Exception failure) {
            plugin.getLogger().warning("Public dialog validation failed; current dialogs remain active: "
                    + failure.getMessage());
            return false;
        }
    }

    private boolean reload(boolean startup) {
        Map<String, TextDialogRequest> next = new LinkedHashMap<>();
        try {
            for (String id : IDS) {
                String resource = "dialogs/" + id + ".yml";
                File file = new File(dataFolder, resource);
                if (!file.isFile()) {
                    continue;
                }
                YamlConfiguration bundled = bundled(resource);
                YamlConfiguration owner = new YamlConfiguration();
                try {
                    owner.load(file);
                } catch (Exception invalidYaml) {
                    if (!startup) {
                        throw invalidYaml;
                    }
                    logger.warning(resource + " is unparseable; this native dialog is unavailable "
                            + "until the owner file is fixed, so chat input remains available: "
                            + invalidYaml.getMessage());
                    continue;
                }
                try {
                    next.put(id, parse(owner));
                } catch (RuntimeException invalid) {
                    if (!startup) {
                        throw invalid;
                    }
                    try {
                        next.put(id, repairStartup(owner, bundled, resource));
                    } catch (RuntimeException unrecoverable) {
                        logger.warning(resource + " has an unrepairable field; only this native "
                                + "dialog is unavailable and chat input remains available: "
                                + unrecoverable.getMessage());
                    }
                }
            }
            active = Map.copyOf(next);
            return true;
        } catch (Exception failure) {
            logger.warning("Public dialog reload failed; previous dialogs remain active: " + failure.getMessage());
            return false;
        }
    }

    private TextDialogRequest repairStartup(YamlConfiguration owner, YamlConfiguration bundled, String resource) {
        for (int attempt = 0; attempt < 30; attempt++) {
            try {
                return parse(owner);
            } catch (IllegalArgumentException invalid) {
                String path = invalid.getMessage().split(" ")[0];
                if (!bundled.contains(path, false)) {
                    if (path.endsWith(".tooltip") || path.endsWith(".icon")
                            || path.equals("body") || path.equals("initial-value") || path.equals("placeholder")) {
                        logger.warning(resource + ":" + path + "=" + owner.get(path)
                                + " (" + invalid.getMessage() + ") -> absent bundled default in memory; "
                                + "installed file was not changed.");
                        owner.set(path, null);
                        continue;
                    }
                    throw invalid;
                }
                logger.warning(resource + ":" + path + "=" + owner.get(path)
                        + " (" + invalid.getMessage() + ") -> bundled default " + bundled.get(path)
                        + " in memory; installed file was not changed.");
                owner.set(path, bundled.get(path));
            }
        }
        throw new IllegalArgumentException("Too many invalid public dialog fields");
    }

    private static YamlConfiguration bundled(String path) throws Exception {
        try (InputStream stream = TeamTextDialogs.class.getClassLoader().getResourceAsStream(path)) {
            if (stream == null) {
                throw new IllegalStateException("Missing bundled " + path);
            }
            YamlConfiguration yaml = new YamlConfiguration();
            yaml.load(new InputStreamReader(stream, StandardCharsets.UTF_8));
            return yaml;
        }
    }

    static TextDialogRequest parse(YamlConfiguration file) {
        String title = requiredString(file, "title");
        String label = requiredString(file, "field-label");
        List<String> body = optionalLines(file, "body");
        int bodyWidth = positive(file, "body-width");
        int inputWidth = positive(file, "input-width");
        int maxLength = positive(file, "max-length");
        return new TextDialogRequest(title, body, label,
                optionalString(file, "initial-value"), optionalString(file, "placeholder"),
                button(file, "confirm-button"), button(file, "cancel-button"),
                bodyWidth, inputWidth, maxLength,
                optionalBoolean(file, "label-visible", true),
                optionalBoolean(file, "can-close-with-escape", false),
                optionalBoolean(file, "pause", false));
    }

    private static DialogButton button(YamlConfiguration file, String path) {
        String label = file.isString(path) ? requiredString(file, path)
                : requiredString(file, path + ".label");
        int width = file.contains(path + ".width", false) ? positive(file, path + ".width")
                : positive(file, "button-width");
        return new DialogButton(label, optionalString(file, path + ".tooltip"), width,
                optionalString(file, path + ".icon"));
    }

    private static String requiredString(YamlConfiguration file, String path) {
        if (!file.isString(path) || file.getString(path, "").isBlank()) {
            throw new IllegalArgumentException(path + " must be nonempty text");
        }
        return file.getString(path);
    }

    private static String optionalString(YamlConfiguration file, String path) {
        if (!file.contains(path, false)) {
            return "";
        }
        if (!file.isString(path)) {
            throw new IllegalArgumentException(path + " must be text");
        }
        return file.getString(path, "");
    }

    private static List<String> optionalLines(YamlConfiguration file, String path) {
        if (!file.contains(path, false)) {
            return List.of();
        }
        if (!file.isList(path) || file.getList(path).stream().anyMatch(value -> !(value instanceof String))) {
            throw new IllegalArgumentException(path + " must be a text list");
        }
        return file.getStringList(path);
    }

    private static int positive(YamlConfiguration file, String path) {
        if (!file.isInt(path) || file.getInt(path) < 1) {
            throw new IllegalArgumentException(path + " must be a positive integer");
        }
        return file.getInt(path);
    }

    private static boolean optionalBoolean(YamlConfiguration file, String path, boolean fallback) {
        if (!file.contains(path, false)) {
            return fallback;
        }
        if (!file.isBoolean(path)) {
            throw new IllegalArgumentException(path + " must be true or false");
        }
        return file.getBoolean(path);
    }
}
