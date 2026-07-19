package me.foesio.foTeams.config;

import me.foesio.core.config.ResourceFiles;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

public final class FileConfig {
    private final JavaPlugin plugin;
    private final String name;
    private File file;
    private FileConfiguration config;

    public FileConfig(JavaPlugin plugin, String name) {
        this.plugin = plugin;
        this.name = name;
    }

    public void load() {
        file = ResourceFiles.saveDefault(plugin, name);
        config = YamlConfiguration.loadConfiguration(file);
    }

    public void reload() {
        config = YamlConfiguration.loadConfiguration(file);
    }

    public void save() {
        try {
            config.save(file);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to save " + name, exception);
        }
    }

    public boolean copyMissingDefaultsFromResource() {
        if (config == null) {
            throw new IllegalStateException("Config not loaded: " + name);
        }
        try (InputStream stream = plugin.getResource(name)) {
            if (stream == null) {
                return false;
            }
            YamlConfiguration defaults = YamlConfiguration.loadConfiguration(new InputStreamReader(stream, StandardCharsets.UTF_8));
            boolean changed = false;
            for (String path : defaults.getKeys(true)) {
                if (path == null || path.isEmpty()) {
                    continue;
                }
                if (defaults.isConfigurationSection(path)) {
                    continue;
                }
                if (!config.contains(path)) {
                    config.set(path, defaults.get(path));
                    changed = true;
                }
            }
            if (changed) {
                save();
            }
            return changed;
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to merge defaults for " + name, exception);
        }
    }

    public FileConfiguration config() {
        return config;
    }
}
