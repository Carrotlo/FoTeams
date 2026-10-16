package me.foesio.foTeams.gui;

import me.foesio.core.gui.publicgui.GuiDefinitionException;
import me.foesio.core.gui.publicgui.PublicGuiMigration;
import me.foesio.core.gui.publicgui.PublicGuiSchema;
import me.foesio.core.migration.FoMigrationStore;
import me.foesio.foTeams.FoTeams;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.LinkedHashSet;

/** One-time v6 conversion from the former shared GUI resource to Core screen files. */
final class TeamPublicGuiMigration {
    private static final int VERSION = 6;
    private static final String OLD_RESOURCE = "legacy/team-v5.yml";
    private static final String RELEASED_V3_RESOURCE = "legacy/team-v3.yml";
    private static final String OLD_FILE = "guis/team.yml";
    private static final String TEXT_FILE = "guis/public-text.yml";
    private static final List<String> FIELDS = List.of("material", "name", "lore", "amount",
            "custom-model-data", "glow", "item-flags", "skull-skin-url");
    // Values replaced by the released v5 schema migration. A v4 owner file
    // still carrying these exact defaults must receive the newest template.
    private static final Map<String, List<String>> V4_CHANGED_DEFAULTS = Map.ofEntries(
            Map.entry("buttons.dashboard.invites.lore", List.of("#ffffffNo pending team invites.")),
            Map.entry("buttons.dashboard.info.lore", List.of("#ffffffView your team information.")),
            Map.entry("buttons.dashboard.members.lore", List.of("#ffffffView and manage team members.")),
            Map.entry("buttons.dashboard.warps.lore", List.of("#ffffffManage team home and warps.")),
            Map.entry("buttons.dashboard.score-top.lore", List.of("#ffffffView the team score leaderboard.")),
            Map.entry("buttons.dashboard.balance.lore", List.of("#ffffffDeposit or withdraw team funds.")),
            Map.entry("buttons.members.header.lore", List.of("#ffffffManage your team members.")),
            Map.entry("buttons.relations.empty.lore", List.of("#ffffffThere are no other teams to manage.")),
            Map.entry("buttons.info.allies.lore", List.of("#ffffffAllies: #a7b8b0None")),
            Map.entry("buttons.confirm.confirm.lore", List.of("#ffffffClick to continue.")),
            Map.entry("buttons.confirm.cancel.lore", List.of("#ffffffReturn without changing anything."))
    );

    private TeamPublicGuiMigration() {
    }

    static boolean install(JavaPlugin plugin, FoMigrationStore versions) {
        if (versions.version() >= VERSION) {
            return true;
        }
        Path root = plugin.getDataFolder().toPath();
        Path oldPath = root.resolve(OLD_FILE);
        int originalVersion = plugin instanceof FoTeams teams
                ? teams.migrationStartVersion() : versions.version();
        try {
            if (plugin instanceof FoTeams teams && teams.freshDataFolder()) {
                return installFresh(plugin, versions, root);
            }
            YamlConfiguration old = Files.isRegularFile(oldPath) ? readFile(oldPath) : null;
            YamlConfiguration oldDefault = readResource(plugin,
                    originalVersion < 4 ? RELEASED_V3_RESOURCE : OLD_RESOURCE);
            // Existing generated screen files without v6 provenance could contain owner work.
            // Preserve them and let the owner resolve that ambiguity rather than overwriting.
            for (String menu : CorePublicMenus.MENUS) {
                Path target = root.resolve("guis/" + menu + ".yml");
                if (Files.exists(target)) {
                    throw new IOException("Unversioned screen file already exists: " + target);
                }
            }
            if (Files.exists(root.resolve(TEXT_FILE))) {
                throw new IOException("Unversioned public-text file already exists");
            }
            // Released FoTeams stored public GUI values only in config.yml. The
            // shared guis/team.yml resource first appeared in the later v4/v5 schema.
            boolean oldConfig = old == null && originalVersion < 4;
            if (old == null && !oldConfig) {
                throw new IOException("Existing FoTeams data has no predecessor guis/team.yml; preserving a possible owner deletion");
            }
            if (old != null) {
                retainBackup(oldPath, originalVersion);
            } else if (oldConfig) {
                retainBackup(root.resolve("config.yml"), originalVersion);
            }
            Map<String, PublicGuiMigration.Change> changes = new LinkedHashMap<>();
            for (String menu : CorePublicMenus.MENUS) {
                String path = "guis/" + menu + ".yml";
                YamlConfiguration bundled = readResource(plugin, path);
                YamlConfiguration target = readResource(plugin, path);
                if (old != null) {
                    if (originalVersion < 4) {
                        overlayOldConfig(plugin.getConfig(), menu, target);
                    }
                    overlayScreen(menu, old, oldDefault, target, originalVersion);
                } else if (oldConfig) {
                    overlayOldConfig(plugin.getConfig(), menu, target);
                }
                repairInvalid(plugin, menu, target, bundled);
                byte[] bytes = target.saveToString().getBytes(StandardCharsets.UTF_8);
                changes.put(path, PublicGuiMigration.Change.freshInstall(original -> {
                    if (original != null) {
                        throw new IOException("Refusing to overwrite unversioned owner file " + path);
                    }
                    return bytes;
                }));
            }
            YamlConfiguration text = readResource(plugin, TEXT_FILE);
            if (old != null) {
                YamlConfiguration textDefault = readResource(plugin, TEXT_FILE);
                for (String rootPath : List.of("filler", "navigation", "labels")) {
                    overlayLeaves(old, oldDefault, text, rootPath, rootPath,
                            originalVersion == 3 || originalVersion >= 5);
                }
                repairAuxiliary(plugin, text, textDefault);
            }
            byte[] textBytes = text.saveToString().getBytes(StandardCharsets.UTF_8);
            changes.put(TEXT_FILE, PublicGuiMigration.Change.freshInstall(original -> {
                if (original != null) {
                    throw new IOException("Refusing to overwrite unversioned owner file " + TEXT_FILE);
                }
                return textBytes;
            }));
            return PublicGuiMigration.runToVersion(plugin, versions, VERSION, changes, staged -> {
                for (String menu : CorePublicMenus.MENUS) {
                    String path = "guis/" + menu + ".yml";
                    YamlConfiguration candidate = readFile(staged.get(path));
                    try {
                        PublicGuiSchema.parse("teams." + menu, path, candidate, CorePublicMenus.contract(menu));
                    } catch (GuiDefinitionException exception) {
                        throw new IOException(path + ": " + exception.getMessage(), exception);
                    }
                }
                PublicGuiConfig.validateFields(readFile(staged.get(TEXT_FILE)),
                        readResource(plugin, TEXT_FILE));
            });
        } catch (Exception exception) {
            plugin.getLogger().severe("FoTeams public GUI migration remains pending: " + exception.getMessage());
            return false;
        }
    }

    private static boolean installFresh(JavaPlugin plugin, FoMigrationStore versions, Path root) throws IOException {
        var installed = new java.util.ArrayList<Path>();
        try {
            for (String menu : CorePublicMenus.MENUS) {
                String resource = "guis/" + menu + ".yml";
                Path target = root.resolve(resource);
                if (Files.exists(target)) {
                    throw new IOException("Fresh GUI target already exists: " + target);
                }
                YamlConfiguration bundled = readResource(plugin, resource);
                PublicGuiSchema.parse("teams." + menu, resource, bundled, CorePublicMenus.contract(menu));
                plugin.saveResource(resource, false);
                installed.add(target);
            }
            if (Files.exists(root.resolve(TEXT_FILE))) {
                throw new IOException("Fresh public text target already exists");
            }
            YamlConfiguration bundledText = readResource(plugin, TEXT_FILE);
            PublicGuiConfig.validateFields(bundledText, bundledText);
            plugin.saveResource(TEXT_FILE, false);
            installed.add(root.resolve(TEXT_FILE));
            if (!versions.runToVersion(VERSION, () -> true)) {
                throw new IOException("Could not record fresh GUI schema version");
            }
            return true;
        } catch (Exception failure) {
            for (Path path : installed) {
                Files.deleteIfExists(path);
            }
            if (failure instanceof IOException io) {
                throw io;
            }
            throw new IOException("Fresh public GUI installation failed", failure);
        }
    }

    static void overlayScreen(String menu, YamlConfiguration old,
                              YamlConfiguration oldDefault, YamlConfiguration target, int originalVersion) {
        YamlConfiguration comparisonDefault = originalVersion == 4 ? v4ComparisonDefaults(oldDefault) : oldDefault;
        String oldTitle = switch (menu) {
            case "dashboard-empty", "dashboard-team" -> "dashboard";
            case "transfer-confirm", "disband-confirm" -> "confirm";
            default -> menu;
        };
        copyCustomized(old, comparisonDefault, target, "titles." + oldTitle, "title", false);
        if (!menu.equals("chest")) {
            overlayLeaves(old, comparisonDefault, target, "filler", "filler",
                    originalVersion == 3 || originalVersion >= 5);
        }
        ConfigurationSection states = target.getConfigurationSection("states");
        if (states == null) {
            return;
        }
        for (String state : states.getKeys(false)) {
            String source = legacyItemPath(menu, state, originalVersion);
            if (source != null) {
                Object pendingInvitesLore = originalVersion < 5 && menu.equals("dashboard-empty")
                        && state.equals("invites") ? target.get("states.invites.lore") : null;
                overlayLeaves(old, comparisonDefault, target, source, "states." + state,
                        originalVersion == 3 || originalVersion >= 5);
                // v3's one invite item contained only the no-invites lore. Keep the
                // new pending state list placeholder and map old lore to invites-empty.
                if (pendingInvitesLore != null) target.set("states.invites.lore", pendingInvitesLore);
            }
        }
    }

    private static YamlConfiguration v4ComparisonDefaults(YamlConfiguration v5) {
        YamlConfiguration v4 = new YamlConfiguration();
        try {
            v4.loadFromString(v5.saveToString());
        } catch (org.bukkit.configuration.InvalidConfigurationException impossible) {
            throw new IllegalStateException("Bundled v5 GUI default cannot be copied", impossible);
        }
        V4_CHANGED_DEFAULTS.forEach(v4::set);
        return v4;
    }

    private static String legacyItemPath(String menu, String state, int originalVersion) {
        if (menu.equals("dashboard-empty") && state.equals("invites-empty") && originalVersion < 5) {
            // v3/v4 used one item for both invite states; v5 has a separate empty state.
            return "buttons.dashboard.invites";
        }
        if (List.of("back", "previous-page", "next-page", "search", "clear-search").contains(state)) {
            return "navigation." + state;
        }
        if (menu.equals("chest") || state.equals("balance-unavailable")) {
            return null;
        }
        if (menu.equals("info") && state.equals("empty")) {
            return "buttons.members.empty";
        }
        if (menu.equals("transfer-confirm")) {
            return "buttons.confirm.transfer-" + state;
        }
        String screen = switch (menu) {
            case "dashboard-empty", "dashboard-team" -> "dashboard";
            case "disband-confirm" -> "confirm";
            default -> menu;
        };
        return "buttons." + screen + "." + state;
    }

    static void overlayOldConfig(FileConfiguration config, String menu, YamlConfiguration target) {
        String screen = switch (menu) {
            case "dashboard-empty", "dashboard-team" -> "dashboard";
            case "transfer-confirm", "disband-confirm" -> "confirm";
            default -> menu;
        };
        String oldDefault = switch (screen) {
            case "dashboard" -> "FoTeams";
            case "settings" -> "Team Settings";
            case "members" -> "Members";
            case "warps" -> "Warps";
            case "info" -> "Team Info";
            case "relations" -> "Relations";
            case "confirm" -> "Confirm";
            case "upgrades" -> "Team Upgrades";
            default -> null;
        };
        Object title = config.get("gui.titles." + screen);
        if (oldDefault != null && title != null && !Objects.equals(title, oldDefault)) {
            target.set("title", title);
        }
        Object material = config.get("gui.filler-material");
        if (target.contains("filler.material", false) && material != null
                && !Objects.equals(material, "GRAY_STAINED_GLASS_PANE")) {
            target.set("filler.material", material);
        }
        if (menu.equals("disband-confirm")) {
            copyOldMaterial(config, target, "gui.confirm-material", "states.confirm.material", "LIME_DYE");
            copyOldMaterial(config, target, "gui.cancel-material", "states.cancel.material", "RED_DYE");
        }
    }

    private static void copyOldMaterial(FileConfiguration config, YamlConfiguration target, String oldPath,
                                        String newPath, String oldDefault) {
        Object value = config.get(oldPath);
        if (value != null && !Objects.equals(value, oldDefault)) {
            target.set(newPath, value);
        }
    }

    private static void overlayLeaves(YamlConfiguration owner, YamlConfiguration oldDefault,
                                      YamlConfiguration target, String from, String to, boolean preserveOmissions) {
        ConfigurationSection sourceDefault = oldDefault.getConfigurationSection(from);
        ConfigurationSection sourceOwner = owner.getConfigurationSection(from);
        if (sourceDefault == null && sourceOwner == null) {
            return;
        }
        var leaves = new LinkedHashSet<String>();
        if (sourceDefault != null) {
            leaves.addAll(sourceDefault.getKeys(true));
        }
        if (sourceOwner != null) {
            leaves.addAll(sourceOwner.getKeys(true));
        }
        for (String leaf : leaves) {
            if ((sourceDefault != null && sourceDefault.isConfigurationSection(leaf))
                    || (sourceOwner != null && sourceOwner.isConfigurationSection(leaf))) {
                continue;
            }
            String oldPath = from + "." + leaf;
            String newPath = to + "." + leaf;
            String field = leaf.substring(leaf.lastIndexOf('.') + 1);
            if (!target.contains(newPath, false) && !FIELDS.contains(field)) {
                continue;
            }
            if (oldDefault.contains(oldPath, false)) {
                copyCustomized(owner, oldDefault, target, oldPath, newPath, preserveOmissions);
            } else if (owner.contains(oldPath, false)) {
                target.set(newPath, owner.get(oldPath));
            }
        }
    }

    private static void copyCustomized(YamlConfiguration owner, YamlConfiguration oldDefault,
                                       YamlConfiguration target, String oldPath, String newPath,
                                       boolean preserveOmissions) {
        if (!oldDefault.contains(oldPath, false)) {
            return;
        }
        if (!owner.contains(oldPath, false)) {
            if (preserveOmissions) {
                target.set(newPath, null);
            }
            return;
        }
        Object value = owner.get(oldPath);
        if (!Objects.deepEquals(value, oldDefault.get(oldPath))) {
            target.set(newPath, value);
        }
    }

    static void repairInvalid(JavaPlugin plugin, String menu, YamlConfiguration target,
                                      YamlConfiguration bundled) throws GuiDefinitionException {
        String file = "guis/" + menu + ".yml";
        for (int attempt = 0; attempt < 100; attempt++) {
            try {
                PublicGuiSchema.parse("teams." + menu, file, target, CorePublicMenus.contract(menu));
                return;
            } catch (GuiDefinitionException failure) {
                String path = failure.key().replaceAll("\\[\\d+].*$", "");
                if (bundled.contains(path, false)) {
                    Object invalid = target.get(path);
                    target.set(path, bundled.get(path));
                    if (plugin != null) {
                        plugin.getLogger().warning("Migrated " + file + ":" + path + " invalid " + invalid
                                + " (" + failure.getMessage() + "); using newest bundled default " + bundled.get(path));
                    }
                } else if (target.contains(path, false) && optional(path)) {
                    Object invalid = target.get(path);
                    target.set(path, null);
                    if (plugin != null) {
                        plugin.getLogger().warning("Migrated " + file + ":" + path + " invalid " + invalid
                                + " (" + failure.getMessage() + "); newest bundled default is absent");
                    }
                } else {
                    throw failure;
                }
            }
        }
        throw new IllegalStateException(file + ": too many invalid fields to isolate");
    }

    private static boolean optional(String path) {
        return path.endsWith(".custom-model-data") || path.endsWith(".amount")
                || path.endsWith(".glow") || path.endsWith(".item-flags")
                || path.endsWith(".name") || path.endsWith(".lore") || path.endsWith(".skull-skin-url");
    }

    private static void repairAuxiliary(JavaPlugin plugin, YamlConfiguration target, YamlConfiguration bundled) {
        for (String path : bundled.getKeys(true)) {
            if (bundled.isConfigurationSection(path) || !target.contains(path, false)) {
                continue;
            }
            Object value = target.get(path);
            Object expected = bundled.get(path);
            if (expected != null && value != null && !expected.getClass().isInstance(value)) {
                target.set(path, expected);
                plugin.getLogger().warning("Migrated " + TEXT_FILE + ":" + path + " invalid " + value
                        + "; using newest bundled default " + expected);
            }
        }
        if (!PublicGuiConfig.validNumberFormat(target)) {
            PublicGuiConfig.repairNumberFormat(target, bundled, message ->
                    plugin.getLogger().warning("Migrated " + TEXT_FILE + ":" + message));
        }
        if (!PublicGuiConfig.validChatKeywords(target)) {
            PublicGuiConfig.repairChatKeywords(target, bundled, message ->
                    plugin.getLogger().warning("Migrated " + TEXT_FILE + ":" + message));
        }
    }

    private static void retainBackup(Path original, int version) throws IOException {
        byte[] bytes = Files.readAllBytes(original);
        Path backup = original.resolveSibling(original.getFileName() + ".gui-v" + version + "-backup");
        if (Files.exists(backup)) {
            if (!java.util.Arrays.equals(bytes, Files.readAllBytes(backup))) {
                throw new IOException("Existing original-version backup differs: " + backup);
            }
            return;
        }
        Files.copy(original, backup, StandardCopyOption.COPY_ATTRIBUTES);
    }

    private static YamlConfiguration readResource(JavaPlugin plugin, String path) throws IOException {
        try (InputStream stream = plugin.getResource(path)) {
            if (stream == null) {
                throw new IOException("Missing bundled " + path);
            }
            YamlConfiguration yaml = new YamlConfiguration();
            yaml.load(new InputStreamReader(stream, StandardCharsets.UTF_8));
            return yaml;
        } catch (org.bukkit.configuration.InvalidConfigurationException exception) {
            throw new IOException("Invalid bundled " + path, exception);
        }
    }

    private static YamlConfiguration readFile(Path path) throws IOException {
        if (path == null) {
            throw new IOException("Missing staged GUI path");
        }
        YamlConfiguration yaml = new YamlConfiguration();
        try {
            yaml.load(path.toFile());
            return yaml;
        } catch (org.bukkit.configuration.InvalidConfigurationException exception) {
            throw new IOException("Unparseable YAML " + path, exception);
        }
    }
}
