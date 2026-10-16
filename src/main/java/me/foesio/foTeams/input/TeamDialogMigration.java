package me.foesio.foTeams.input;

import me.foesio.core.migration.FoMigrationStore;
import me.foesio.foTeams.FoTeams;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** One-time conversion of released scalar-button dialogs to full public definitions. */
public final class TeamDialogMigration {
    private static final int VERSION = 7;
    private static final List<String> OLD_DIALOGS = List.of("team-name", "team-tag", "team-description",
            "tag-color", "warp-create", "team-search");

    private TeamDialogMigration() {
    }

    public static boolean install(FoTeams plugin, FoMigrationStore versions) {
        if (versions.version() >= VERSION) {
            return true;
        }
        if (versions.version() < 6) {
            plugin.getLogger().warning("Public dialog conversion is waiting for the public GUI schema migration.");
            return false;
        }
        if (plugin.freshDataFolder()) {
            return installFresh(plugin, versions);
        }
        Path root = plugin.getDataFolder().toPath();
        Map<Path, byte[]> originals = new LinkedHashMap<>();
        Map<Path, byte[]> proposals = new LinkedHashMap<>();
        int priorVersion = plugin.migrationStartVersion();
        try {
            for (String id : OLD_DIALOGS) {
                Path target = root.resolve("dialogs/" + id + ".yml");
                if (!Files.exists(target)) {
                    continue; // An owner-removed old dialog keeps its chat fallback.
                }
                requireRegular(target);
                byte[] original = Files.readAllBytes(target);
                YamlConfiguration owner = readFile(target);
                YamlConfiguration oldDefault = readResource(plugin, "legacy/dialogs-v5/" + id + ".yml");
                YamlConfiguration newest = readResource(plugin, "dialogs/" + id + ".yml");
                overlay(owner, oldDefault, newest);
                repair(newest, readResource(plugin, "dialogs/" + id + ".yml"));
                TeamTextDialogs.parse(newest);
                originals.put(target, original);
                proposals.put(target, newest.saveToString().getBytes(StandardCharsets.UTF_8));
            }
            Path balance = root.resolve("dialogs/balance.yml");
            if (!Files.exists(balance)) {
                byte[] bundled = resourceBytes(plugin, "dialogs/balance.yml");
                BalanceDialogConfig.parse(readResource(plugin, "dialogs/balance.yml"));
                originals.put(balance, null);
                proposals.put(balance, bundled);
            } else {
                requireRegular(balance);
                byte[] original = Files.readAllBytes(balance);
                YamlConfiguration owner = readFile(balance);
                String before = owner.saveToString();
                repairBalance(owner, readResource(plugin, "dialogs/balance.yml"));
                String after = owner.saveToString();
                if (!before.equals(after)) {
                    originals.put(balance, original);
                    proposals.put(balance, after.getBytes(StandardCharsets.UTF_8));
                }
            }
            boolean[] committed = {false};
            boolean advanced = versions.runToVersion(VERSION, () -> {
                try {
                    commit(proposals, originals, priorVersion);
                    committed[0] = true;
                    return true;
                } catch (Exception failure) {
                    plugin.getLogger().severe("Public dialog migration failed: " + failure.getMessage());
                    try {
                        rollback(originals);
                    } catch (IOException rollbackFailure) {
                        plugin.getLogger().severe("Public dialog rollback failed: " + rollbackFailure.getMessage());
                    }
                    return false;
                }
            });
            if (!advanced && committed[0]) {
                rollback(originals);
            }
            return advanced;
        } catch (Exception failure) {
            plugin.getLogger().severe("Public dialog migration remains pending: " + failure.getMessage());
            return false;
        }
    }

    private static boolean installFresh(FoTeams plugin, FoMigrationStore versions) {
        var created = new java.util.ArrayList<Path>();
        try {
            for (String id : OLD_DIALOGS) {
                String resource = "dialogs/" + id + ".yml";
                Path target = plugin.getDataFolder().toPath().resolve(resource);
                if (Files.exists(target)) {
                    throw new IOException("Fresh dialog target already exists: " + target);
                }
                TeamTextDialogs.parse(readResource(plugin, resource));
                plugin.saveResource(resource, false);
                created.add(target);
            }
            Path balance = plugin.getDataFolder().toPath().resolve("dialogs/balance.yml");
            if (Files.exists(balance)) {
                throw new IOException("Fresh balance dialog target already exists");
            }
            BalanceDialogConfig.parse(readResource(plugin, "dialogs/balance.yml"));
            plugin.saveResource("dialogs/balance.yml", false);
            created.add(balance);
            if (!versions.runToVersion(VERSION, () -> true)) {
                throw new IOException("Could not record fresh dialog schema version");
            }
            return true;
        } catch (Exception failure) {
            for (Path path : created) {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException rollbackFailure) {
                    failure.addSuppressed(rollbackFailure);
                }
            }
            plugin.getLogger().severe("Fresh public dialog installation failed: " + failure.getMessage());
            return false;
        }
    }

    static void overlay(YamlConfiguration owner, YamlConfiguration oldDefault, YamlConfiguration newest) {
        for (String path : oldDefault.getKeys(true)) {
            if (oldDefault.isConfigurationSection(path)) {
                continue;
            }
            String target = (path.equals("confirm-button") || path.equals("cancel-button"))
                    ? path + ".label" : path;
            if ((path.equals("confirm-button") || path.equals("cancel-button"))
                    && owner.isConfigurationSection(path)) {
                continue;
            }
            if (!owner.contains(path, false)) {
                if (optional(target)) {
                    newest.set(target, null);
                }
                continue;
            }
            Object current = owner.get(path);
            if (!Objects.deepEquals(current, oldDefault.get(path))) {
                newest.set(target, current);
            }
        }
        for (String button : List.of("confirm-button", "cancel-button")) {
            if (owner.isConfigurationSection(button)) {
                for (String field : List.of("label", "tooltip", "icon", "width")) {
                    String path = button + "." + field;
                    if (owner.contains(path, false)) {
                        Object current = owner.get(path);
                        if (!field.equals("label") || !Objects.deepEquals(current, oldDefault.get(button))) {
                            newest.set(path, current);
                        }
                    } else if (field.equals("tooltip") || field.equals("icon")) {
                        newest.set(path, null);
                    }
                }
            }
        }
        for (String path : owner.getKeys(true)) {
            if (owner.isConfigurationSection(path) || oldDefault.contains(path, false)
                    || path.startsWith("confirm-button.") || path.startsWith("cancel-button.")) {
                continue;
            }
            if (newest.contains(path, false)) {
                newest.set(path, owner.get(path));
            }
        }
    }

    private static boolean optional(String path) {
        return path.equals("body") || path.equals("initial-value") || path.equals("placeholder")
                || path.endsWith(".tooltip") || path.endsWith(".icon");
    }

    private static void repair(YamlConfiguration candidate, YamlConfiguration bundled) {
        for (int attempt = 0; attempt < 30; attempt++) {
            try {
                TeamTextDialogs.parse(candidate);
                return;
            } catch (IllegalArgumentException invalid) {
                String path = invalid.getMessage().split(" ")[0];
                if (bundled.contains(path, false)) {
                    candidate.set(path, bundled.get(path));
                } else if (optional(path)) {
                    candidate.set(path, null);
                } else {
                    throw invalid;
                }
            }
        }
        throw new IllegalArgumentException("Too many invalid dialog fields");
    }

    private static void repairBalance(YamlConfiguration candidate, YamlConfiguration bundled) {
        for (int attempt = 0; attempt < 30; attempt++) {
            try {
                BalanceDialogConfig.parse(candidate);
                return;
            } catch (IllegalArgumentException invalid) {
                String path = invalid.getMessage().split(" ")[0];
                if (bundled.contains(path, false)) {
                    candidate.set(path, bundled.get(path));
                } else if (optional(path)) {
                    candidate.set(path, null);
                } else {
                    throw invalid;
                }
            }
        }
        throw new IllegalArgumentException("Too many invalid balance dialog fields");
    }

    static void commit(Map<Path, byte[]> proposals, Map<Path, byte[]> originals, int version) throws IOException {
        Map<Path, Path> staged = new LinkedHashMap<>();
        try {
            for (Map.Entry<Path, byte[]> proposal : proposals.entrySet()) {
                Path target = proposal.getKey();
                Files.createDirectories(target.getParent());
                Path stage = Files.createTempFile(target.getParent(), ".dialog-stage-", ".yml");
                Files.write(stage, proposal.getValue());
                staged.put(target, stage);
            }
            for (Map.Entry<Path, byte[]> original : originals.entrySet()) {
                if (original.getValue() == null) {
                    continue;
                }
                Path backup = original.getKey().resolveSibling(original.getKey().getFileName()
                        + ".dialog-v" + version + "-backup");
                if (Files.exists(backup)) {
                    if (!Arrays.equals(original.getValue(), Files.readAllBytes(backup))) {
                        throw new IOException("Existing dialog backup differs: " + backup);
                    }
                } else {
                    Files.write(backup, original.getValue());
                }
            }
            for (Map.Entry<Path, Path> entry : staged.entrySet()) {
                replace(entry.getValue(), entry.getKey());
            }
        } finally {
            for (Path path : staged.values()) {
                Files.deleteIfExists(path);
            }
        }
    }

    static void rollback(Map<Path, byte[]> originals) throws IOException {
        IOException failure = null;
        for (Map.Entry<Path, byte[]> entry : originals.entrySet()) {
            try {
                if (entry.getValue() == null) {
                    Files.deleteIfExists(entry.getKey());
                } else {
                    Path stage = Files.createTempFile(entry.getKey().getParent(), ".dialog-rollback-", ".yml");
                    try {
                        Files.write(stage, entry.getValue());
                        replace(stage, entry.getKey());
                    } finally {
                        Files.deleteIfExists(stage);
                    }
                }
            } catch (IOException error) {
                if (failure == null) {
                    failure = error;
                } else {
                    failure.addSuppressed(error);
                }
            }
        }
        if (failure != null) {
            throw failure;
        }
    }

    private static void replace(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (java.nio.file.AtomicMoveNotSupportedException unavailable) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static void requireRegular(Path file) throws IOException {
        if (Files.isSymbolicLink(file) || !Files.isRegularFile(file)) {
            throw new IOException("Dialog path is not a regular file: " + file);
        }
    }

    private static YamlConfiguration readFile(Path file) throws Exception {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.load(file.toFile());
        return yaml;
    }

    private static YamlConfiguration readResource(FoTeams plugin, String resource) throws Exception {
        try (InputStream stream = plugin.getResource(resource)) {
            if (stream == null) {
                throw new IOException("Missing bundled " + resource);
            }
            YamlConfiguration yaml = new YamlConfiguration();
            yaml.load(new InputStreamReader(stream, StandardCharsets.UTF_8));
            return yaml;
        }
    }

    private static byte[] resourceBytes(FoTeams plugin, String resource) throws IOException {
        try (InputStream stream = plugin.getResource(resource)) {
            if (stream == null) {
                throw new IOException("Missing bundled " + resource);
            }
            return stream.readAllBytes();
        }
    }
}
