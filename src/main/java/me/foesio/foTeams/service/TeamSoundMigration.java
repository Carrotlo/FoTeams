package me.foesio.foTeams.service;

import me.foesio.core.migration.FoMigrationStore;
import me.foesio.core.sound.FoSoundService;
import me.foesio.foTeams.FoTeams;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Moves released config.yml sound settings into the owner-owned sounds.yml once. */
public final class TeamSoundMigration {
    private static final int VERSION = 10;
    private static final Map<String, String> LEGACY = Map.of(
            "sounds.gui-open", "gui.open",
            "sounds.success", "team.success",
            "sounds.failure", "team.failure",
            "sounds.teleport", "team.teleport"
    );

    private TeamSoundMigration() {
    }

    public static boolean install(FoTeams plugin, FoMigrationStore versions) {
        if (versions.version() >= VERSION) {
            return true;
        }
        if (versions.version() < 9) {
            return false;
        }
        Path soundsFile = plugin.getDataFolder().toPath().resolve("sounds.yml");
        if (Files.exists(soundsFile)) {
            if (!Files.isRegularFile(soundsFile) || Files.isSymbolicLink(soundsFile)
                    || !FoSoundService.loadPreservingRemovals(plugin).validateInstalled()) {
                plugin.getLogger().warning("Installed sounds.yml is invalid; sound migration remains pending.");
                return false;
            }
            return migrateInstalled(plugin, versions, soundsFile);
        }
        if (!plugin.freshDataFolder() && plugin.migrationStartVersion() >= 4) {
            // A later sound schema could have been deliberately removed by its owner.
            return versions.runToVersion(VERSION, () -> true);
        }
        if (plugin.freshDataFolder()) {
            return installFresh(plugin, versions, soundsFile);
        }
        return migrateLegacy(plugin, versions, soundsFile);
    }

    private static boolean migrateInstalled(FoTeams plugin, FoMigrationStore versions, Path soundsFile) {
        Path configFile = plugin.getDataFolder().toPath().resolve("config.yml");
        byte[] soundsBefore;
        byte[] configBefore;
        byte[] soundsAfter;
        boolean changeSounds;
        boolean changeConfig = LEGACY.keySet().stream().anyMatch(path -> plugin.getConfig().contains(path, false));
        try {
            soundsBefore = Files.readAllBytes(soundsFile);
            configBefore = Files.readAllBytes(configFile);
            YamlConfiguration owner = new YamlConfiguration();
            owner.load(soundsFile.toFile());
            changeSounds = owner.contains("team.gui-open", false);
            if (changeSounds) {
                if (!owner.contains("gui.open", false)) {
                    var section = owner.getConfigurationSection("team.gui-open");
                    if (section == null) {
                        owner.set("gui.open", owner.get("team.gui-open"));
                    } else {
                        for (Map.Entry<String, Object> field : section.getValues(true).entrySet()) {
                            if (!(field.getValue() instanceof org.bukkit.configuration.ConfigurationSection)) {
                                owner.set("gui.open." + field.getKey(), field.getValue());
                            }
                        }
                    }
                }
                owner.set("team.gui-open", null);
            }
            soundsAfter = owner.saveToString().getBytes(StandardCharsets.UTF_8);
            if (!changeSounds && !changeConfig) {
                return versions.runToVersion(VERSION, () -> true);
            }
            if (changeSounds) {
                retainBackup(plugin, soundsFile, soundsBefore);
            }
            if (changeConfig) {
                retainBackup(plugin, configFile, configBefore);
            }
        } catch (Exception failure) {
            plugin.getLogger().warning("Installed sound migration remains pending: " + failure.getMessage());
            return false;
        }
        boolean[] attempted = {false};
        boolean advanced = versions.runToVersion(VERSION, () -> {
            try {
                attempted[0] = true;
                if (changeSounds) {
                    atomicWrite(soundsFile, soundsAfter);
                    if (!FoSoundService.loadPreservingRemovals(plugin).validateInstalled()) {
                        throw new IOException("Migrated sounds.yml failed strict validation");
                    }
                }
                if (changeConfig) {
                    for (String source : LEGACY.keySet()) {
                        plugin.getConfig().set(source, null);
                    }
                    atomicWrite(configFile, plugin.getConfig().saveToString().getBytes(StandardCharsets.UTF_8));
                }
                return true;
            } catch (Exception failure) {
                plugin.getLogger().warning("Installed sound migration failed: " + failure.getMessage());
                return false;
            }
        });
        if (!advanced && attempted[0]) {
            try {
                if (changeSounds) {
                    atomicWrite(soundsFile, soundsBefore);
                }
                if (changeConfig) {
                    atomicWrite(configFile, configBefore);
                    plugin.reloadConfig();
                }
            } catch (Exception rollbackFailure) {
                plugin.getLogger().severe("Installed sound rollback failed: " + rollbackFailure.getMessage());
            }
        }
        return advanced;
    }

    private static boolean installFresh(FoTeams plugin, FoMigrationStore versions, Path target) {
        try {
            plugin.saveResource("sounds.yml", false);
            if (!FoSoundService.loadPreservingRemovals(plugin).validateInstalled()
                    || !versions.runToVersion(VERSION, () -> true)) {
                Files.deleteIfExists(target);
                return false;
            }
            return true;
        } catch (Exception failure) {
            try {
                Files.deleteIfExists(target);
            } catch (IOException rollbackFailure) {
                failure.addSuppressed(rollbackFailure);
            }
            plugin.getLogger().warning("Fresh sounds.yml installation failed: " + failure.getMessage());
            return false;
        }
    }

    private static boolean migrateLegacy(FoTeams plugin, FoMigrationStore versions, Path target) {
        Path configFile = plugin.getDataFolder().toPath().resolve("config.yml");
        byte[] configBefore;
        byte[] proposed;
        try {
            configBefore = Files.readAllBytes(configFile);
            YamlConfiguration oldDefault = bundled(plugin, "legacy/config-v3.yml");
            YamlConfiguration newest = bundled(plugin, "sounds.yml");
            overlay(plugin, plugin.getConfig(), oldDefault, newest);
            proposed = newest.saveToString().getBytes(StandardCharsets.UTF_8);
            Path backup = configFile.resolveSibling("config.yml.gui-v" + plugin.migrationStartVersion() + "-backup");
            if (!Files.exists(backup)) {
                Files.copy(configFile, backup, StandardCopyOption.COPY_ATTRIBUTES);
            }
        } catch (Exception failure) {
            plugin.getLogger().warning("Legacy sound migration remains pending: " + failure.getMessage());
            return false;
        }
        boolean[] committed = {false};
        boolean advanced = versions.runToVersion(VERSION, () -> {
            try {
                atomicWrite(target, proposed);
                committed[0] = true;
                if (!FoSoundService.loadPreservingRemovals(plugin).validateInstalled()) {
                    throw new IOException("Staged sounds.yml did not pass strict Core validation");
                }
                for (String source : LEGACY.keySet()) {
                    plugin.getConfig().set(source, null);
                }
                atomicWrite(configFile, plugin.getConfig().saveToString().getBytes(StandardCharsets.UTF_8));
                return true;
            } catch (Exception failure) {
                plugin.getLogger().warning("Legacy sound migration failed: " + failure.getMessage());
                return false;
            }
        });
        if (!advanced) {
            try {
                if (committed[0]) {
                    Files.deleteIfExists(target);
                }
                atomicWrite(configFile, configBefore);
                plugin.reloadConfig();
            } catch (Exception rollbackFailure) {
                plugin.getLogger().severe("Legacy sound rollback failed: " + rollbackFailure.getMessage());
            }
        }
        return advanced;
    }

    static void overlay(FoTeams plugin, org.bukkit.configuration.file.FileConfiguration owner,
                        YamlConfiguration oldDefault, YamlConfiguration newest) {
        for (Map.Entry<String, String> mapping : LEGACY.entrySet()) {
            String source = mapping.getKey();
            String target = mapping.getValue();
            if (!owner.contains(source, false)) {
                newest.set(target + ".enabled", false);
                continue;
            }
            Object value = owner.get(source);
            if (Objects.equals(value, oldDefault.get(source))) {
                continue;
            }
            if (value instanceof String text && validLegacySound(text)) {
                newest.set(target + ".sound", text);
            } else if (value instanceof String text && text.isBlank()) {
                newest.set(target + ".enabled", false);
            } else if (plugin != null) {
                plugin.getLogger().warning("Invalid legacy " + source + "=" + value
                        + "; using newest bundled sounds.yml default for " + target);
            }
        }
    }

    static boolean validLegacySound(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        if (value.matches("[a-z0-9_.-]+:[a-z0-9_./-]+")) {
            return true;
        }
        try {
            org.bukkit.Sound.class.getField(value);
            return true;
        } catch (NoSuchFieldException invalid) {
            return false;
        }
    }

    private static YamlConfiguration bundled(FoTeams plugin, String resource) throws Exception {
        try (InputStream stream = plugin.getResource(resource)) {
            if (stream == null) {
                throw new IOException("Missing bundled " + resource);
            }
            YamlConfiguration yaml = new YamlConfiguration();
            yaml.load(new InputStreamReader(stream, StandardCharsets.UTF_8));
            return yaml;
        }
    }

    private static void atomicWrite(Path target, byte[] bytes) throws IOException {
        Files.createDirectories(target.getParent());
        Path staged = Files.createTempFile(target.getParent(), ".teams-sound-", ".tmp");
        try {
            Files.write(staged, bytes);
            try {
                Files.move(staged, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (java.nio.file.AtomicMoveNotSupportedException unavailable) {
                Files.move(staged, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(staged);
        }
    }

    private static void retainBackup(FoTeams plugin, Path original, byte[] bytes) throws IOException {
        Path backup = original.resolveSibling(original.getFileName() + ".gui-v"
                + plugin.migrationStartVersion() + "-backup");
        if (!Files.exists(backup)) {
            Files.write(backup, bytes);
        }
    }
}
