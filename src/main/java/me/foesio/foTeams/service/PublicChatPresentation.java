package me.foesio.foTeams.service;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;

/** Owner chat templates and PlaceholderAPI empty values from config.yml. */
public final class PublicChatPresentation {
    private static final Map<String, String> FORMATS = Map.of(
            "team", "chat.team-format",
            "ally", "chat.ally-format",
            "spy", "chat.spy-format"
    );
    private static final Map<String, String> PLACEHOLDERS = Map.of(
            "no-team", "placeholders.no-team",
            "no-rank", "placeholders.no-rank"
    );

    private volatile Snapshot active = new Snapshot(Map.of(), Map.of());

    public void initialize(FileConfiguration owner, FileConfiguration bundled, Consumer<String> warning) {
        active = snapshot(owner, bundled, true, warning);
    }

    public void reload(FileConfiguration owner) {
        active = snapshot(owner, null, false, ignored -> { });
    }

    public boolean validateInstalled(File file, Consumer<String> warning) {
        try {
            YamlConfiguration owner = new YamlConfiguration();
            owner.load(file);
            snapshot(owner, null, false, ignored -> { });
            return true;
        } catch (Exception failure) {
            warning.accept("Installed public chat config is invalid; previous chat text remains active: "
                    + failure.getMessage());
            return false;
        }
    }

    public String format(String id) {
        return active.formats().getOrDefault(id, "");
    }

    public String placeholder(String id) {
        return active.placeholders().getOrDefault(id, "");
    }

    static Snapshot snapshot(FileConfiguration owner, FileConfiguration bundled, boolean startup,
                             Consumer<String> warning) {
        Map<String, String> formats = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : FORMATS.entrySet()) {
            String path = entry.getValue();
            if (owner.contains(path, false) && owner.isString(path)
                    && !owner.getString(path, "").isBlank()) {
                formats.put(entry.getKey(), owner.getString(path));
            } else if (startup) {
                String fallback = bundled == null ? null : bundled.getString(path);
                if (fallback == null || fallback.isBlank()) {
                    throw new IllegalArgumentException(path + " has no bundled startup default");
                }
                warning.accept(path + "=" + owner.get(path) + " is invalid or missing; using bundled default "
                        + "in memory for startup only. Installed file was not changed.");
                formats.put(entry.getKey(), fallback);
            } else {
                throw new IllegalArgumentException(path + " must be nonempty text");
            }
        }
        Map<String, String> placeholders = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : PLACEHOLDERS.entrySet()) {
            String path = entry.getValue();
            if (!owner.contains(path, false)) {
                placeholders.put(entry.getKey(), "");
            } else if (owner.isString(path)) {
                placeholders.put(entry.getKey(), owner.getString(path, ""));
            } else if (startup) {
                String fallback = bundled == null ? null : bundled.getString(path);
                if (fallback == null) {
                    throw new IllegalArgumentException(path + " has no bundled startup default");
                }
                warning.accept(path + "=" + owner.get(path) + " has an invalid type; using bundled default "
                        + "in memory for startup only. Installed file was not changed.");
                placeholders.put(entry.getKey(), fallback);
            } else {
                throw new IllegalArgumentException(path + " must be text when present");
            }
        }
        return new Snapshot(Map.copyOf(formats), Map.copyOf(placeholders));
    }

    record Snapshot(Map<String, String> formats, Map<String, String> placeholders) {
    }
}
