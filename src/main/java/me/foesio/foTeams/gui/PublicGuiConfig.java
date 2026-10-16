package me.foesio.foTeams.gui;

import me.foesio.core.config.ResourceFiles;
import me.foesio.core.gui.GuiButtonConfig;
import me.foesio.core.gui.GuiItemConfig;
import me.foesio.core.gui.GuiResourceLoader;
import me.foesio.core.migration.FoMigrationStore;
import me.foesio.foTeams.util.Text;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

/** Presentation-only resource for the player-facing FoTeams screens. */
public final class PublicGuiConfig {
    private static final String RESOURCE = "guis/public-text.yml";

    private final JavaPlugin plugin;
    private FileConfiguration config;
    private boolean migrationInstalled;

    public PublicGuiConfig(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void initialize(FoMigrationStore migrations) {
        if (!TeamPublicGuiMigration.install(plugin, migrations)) {
            config = bundledDefaults();
            migrationInstalled = false;
            return;
        }
        migrationInstalled = true;
        reload();
    }

    public boolean reload() {
        File file = ResourceFiles.dataFile(plugin, RESOURCE);
        YamlConfiguration loaded = new YamlConfiguration();
        try {
            loaded.load(file);
        } catch (Exception exception) {
            plugin.getLogger().warning("Could not load " + RESOURCE + "; previous public text remains active: "
                    + exception.getMessage());
            return false;
        }
        FileConfiguration defaults = bundledDefaults();
        for (String path : defaults.getKeys(true)) {
            if (defaults.isConfigurationSection(path) || !loaded.contains(path, false)) {
                continue;
            }
            Object value = loaded.get(path);
            Object expected = defaults.get(path);
            if (expected != null && value != null && !expected.getClass().isInstance(value)) {
                if (config != null) {
                    plugin.getLogger().warning(RESOURCE + ":" + path
                            + " has an invalid type; previous public text remains active.");
                    return false;
                }
                plugin.getLogger().warning(RESOURCE + ":" + path + " has an invalid type; using bundled default "
                        + expected + " in memory for startup only. Installed file was not changed.");
                loaded.set(path, expected);
            }
        }
        try {
            if (!validNumberFormat(loaded)) {
                if (config != null) {
                    plugin.getLogger().warning(RESOURCE + ": invalid number format; previous public text remains active.");
                    return false;
                }
                repairNumberFormat(loaded, defaults, message -> plugin.getLogger().warning(
                        RESOURCE + ":" + message + " in memory for startup only. Installed file was not changed."));
            }
            if (!validChatKeywords(loaded)) {
                if (config != null) {
                    plugin.getLogger().warning(RESOURCE + ": invalid chat keywords; previous public text remains active.");
                    return false;
                }
                repairChatKeywords(loaded, defaults, message -> plugin.getLogger().warning(
                        RESOURCE + ":" + message + " in memory for startup only. Installed file was not changed."));
            }
            validateFields(loaded, defaults);
        } catch (RuntimeException invalid) {
            plugin.getLogger().warning(RESOURCE + " cannot be repaired; previous public text remains active: "
                    + invalid.getMessage());
            return false;
        }
        config = loaded;
        Text.configureMoney(loaded.getString("numbers.decimal-separator"),
                loaded.getStringList("numbers.compact-suffixes"));
        return true;
    }

    public boolean validateInstalled() {
        File file = ResourceFiles.dataFile(plugin, RESOURCE);
        YamlConfiguration candidate = new YamlConfiguration();
        try {
            candidate.load(file);
            validateFields(candidate, bundledDefaults());
            return true;
        } catch (Exception failure) {
            plugin.getLogger().warning("Public text validation failed; current text remains active: " + failure.getMessage());
            return false;
        }
    }

    static void validateFields(FileConfiguration candidate, FileConfiguration defaults) {
        for (String path : defaults.getKeys(true)) {
            if (defaults.isConfigurationSection(path) || !candidate.contains(path, false)) {
                continue;
            }
            Object value = candidate.get(path);
            Object expected = defaults.get(path);
            if (expected != null && value != null && !expected.getClass().isInstance(value)) {
                throw new IllegalArgumentException(path + " has an invalid type");
            }
        }
        if (!validNumberFormat(candidate)) {
            throw new IllegalArgumentException("number format is invalid");
        }
        if (!validChatKeywords(candidate)) {
            throw new IllegalArgumentException("chat keywords are invalid");
        }
    }

    static boolean validNumberFormat(FileConfiguration candidate) {
        if (!candidate.isString("numbers.decimal-separator") || !candidate.isList("numbers.compact-suffixes")) {
            return false;
        }
        List<?> suffixes = candidate.getList("numbers.compact-suffixes");
        return suffixes != null && suffixes.size() == 14
                && suffixes.stream().allMatch(value -> value instanceof String);
    }

    static void repairNumberFormat(YamlConfiguration target, FileConfiguration defaults, Consumer<String> warning) {
        String separator = "numbers.decimal-separator";
        if (!target.isString(separator)) {
            Object invalid = target.get(separator);
            target.set(separator, defaults.get(separator));
            warning.accept(separator + "=" + invalid + " invalid; using bundled default " + defaults.get(separator));
        }
        String suffixes = "numbers.compact-suffixes";
        List<?> current = target.getList(suffixes);
        if (current == null || current.size() != 14 || current.stream().anyMatch(value -> !(value instanceof String))) {
            Object invalid = target.get(suffixes);
            target.set(suffixes, defaults.get(suffixes));
            warning.accept(suffixes + "=" + invalid + " invalid; using bundled default " + defaults.get(suffixes));
        }
        if (!validNumberFormat(target)) {
            throw new IllegalArgumentException("Bundled number format did not repair the installed fields");
        }
    }

    static boolean validChatKeywords(FileConfiguration candidate) {
        String cancel = candidate.getString("labels.chat.cancel-keyword", "");
        String deposit = candidate.getString("labels.bank-actions.deposit-keyword", "");
        String withdraw = candidate.getString("labels.bank-actions.withdraw-keyword", "");
        return validKeyword(cancel) && validKeyword(deposit) && validKeyword(withdraw)
                && !cancel.equalsIgnoreCase(deposit) && !cancel.equalsIgnoreCase(withdraw)
                && !deposit.equalsIgnoreCase(withdraw);
    }

    static void repairChatKeywords(YamlConfiguration target, FileConfiguration defaults, Consumer<String> warning) {
        List<String> paths = List.of("labels.chat.cancel-keyword", "labels.bank-actions.deposit-keyword",
                "labels.bank-actions.withdraw-keyword");
        for (String path : paths) {
            if (!target.isString(path) || !validKeyword(target.getString(path, ""))) {
                replaceKeyword(target, defaults, path, warning);
            }
        }
        for (int first = 0; first < paths.size(); first++) {
            for (int second = first + 1; second < paths.size(); second++) {
                String left = paths.get(first);
                String right = paths.get(second);
                if (target.getString(left, "").equalsIgnoreCase(target.getString(right, ""))) {
                    // A collision has no uniquely invalid side. Repair only the
                    // conflicting pair and retain any unrelated third keyword.
                    replaceKeyword(target, defaults, left, warning);
                    replaceKeyword(target, defaults, right, warning);
                }
            }
        }
        if (!validChatKeywords(target)) {
            throw new IllegalArgumentException("Bundled chat keywords did not repair the conflicting fields");
        }
    }

    private static void replaceKeyword(YamlConfiguration target, FileConfiguration defaults,
                                       String path, Consumer<String> warning) {
        Object invalid = target.get(path);
        Object replacement = defaults.get(path);
        target.set(path, replacement);
        warning.accept(path + "=" + invalid + " invalid or conflicting; using bundled default " + replacement);
    }

    private static boolean validKeyword(String value) {
        return value != null && !value.isBlank() && value.matches("[^\\s]{1,32}");
    }

    public boolean available() {
        return config != null && migrationInstalled;
    }

    public String title(String screen, String fallback) {
        return config.getString("titles." + screen, fallback);
    }

    public String label(String path, String fallback) {
        return label(path, fallback, Map.of());
    }

    public String label(String path, String fallback, Map<String, String> placeholders) {
        // An omitted current-schema label stays absent. The fallback argument is
        // only for callers before the public resource has initialized.
        String value = config == null ? fallback : config.getString("labels." + path, "");
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            value = value.replace("{" + entry.getKey() + "}", Objects.toString(entry.getValue(), ""))
                    .replace("%" + entry.getKey() + "%", Objects.toString(entry.getValue(), ""));
        }
        return value;
    }

    public ItemStack button(Player viewer, String screen, String key, Material fallbackMaterial,
            String fallbackName, List<String> fallbackLore, Map<String, String> placeholders) {
        return button(viewer, screen, key, fallbackMaterial, fallbackName, fallbackLore, placeholders, Map.of());
    }

    public ItemStack button(Player viewer, String screen, String key, Material fallbackMaterial,
            String fallbackName, List<String> fallbackLore, Map<String, String> placeholders,
            Map<String, List<String>> linePlaceholders) {
        GuiItemConfig fallback = GuiItemConfig.of(fallbackMaterial, fallbackName, fallbackLore);
        GuiItemConfig item = GuiItemConfig.from(config.getConfigurationSection("buttons." + screen + "." + key), fallback);
        if (linePlaceholders != null && !linePlaceholders.isEmpty()) {
            item = item.withLore(expandLines(item.lore(), linePlaceholders));
        }
        return item
                .create(viewer, placeholders);
    }

    public ItemStack button(Player viewer, String screen, String key, Material fallbackMaterial,
            String fallbackName, List<String> fallbackLore) {
        return button(viewer, screen, key, fallbackMaterial, fallbackName, fallbackLore, Map.of());
    }

    public ItemStack filler(Player viewer) {
        GuiItemConfig fallback = GuiItemConfig.of(Material.GRAY_STAINED_GLASS_PANE, " ", List.of());
        return GuiItemConfig.from(config.getConfigurationSection("filler"), fallback).create(viewer);
    }

    public GuiButtonConfig navigationButtons() {
        return GuiButtonConfig.from(config.getConfigurationSection("navigation"));
    }

    static List<String> expandLines(List<String> configured, Map<String, List<String>> linePlaceholders) {
        List<String> expanded = new ArrayList<>();
        for (String line : configured) {
            String trimmed = Objects.toString(line, "").trim();
            boolean replaced = false;
            for (Map.Entry<String, List<String>> entry : linePlaceholders.entrySet()) {
                String key = entry.getKey();
                if (trimmed.equals("{" + key + "}") || trimmed.equals("%" + key + "%")) {
                    expanded.addAll(entry.getValue());
                    replaced = true;
                    break;
                }
            }
            if (!replaced) {
                expanded.add(line);
            }
        }
        return List.copyOf(expanded);
    }

    private boolean migrateLegacyConfig() {
        File targetFile = ResourceFiles.dataFile(plugin, RESOURCE);
        byte[] original = null;
        try {
            original = Files.readAllBytes(targetFile.toPath());
            YamlConfiguration target = new YamlConfiguration();
            target.load(targetFile);
            YamlConfiguration defaults = bundledDefaults();
            Map<String, String> titles = Map.of(
                    "dashboard", "dashboard",
                    "settings", "settings",
                    "members", "members",
                    "warps", "warps",
                    "info", "info",
                    "relations", "relations",
                    "confirm", "confirm",
                    "upgrades", "upgrades"
            );
            for (Map.Entry<String, String> entry : titles.entrySet()) {
                copyIfUntouched("gui.titles." + entry.getKey(), "titles." + entry.getValue(), target, defaults);
            }
            copyIfUntouched("gui.filler-material", "filler.material", target, defaults);
            copyIfUntouched("gui.confirm-material", "buttons.confirm.confirm.material", target, defaults);
            copyIfUntouched("gui.cancel-material", "buttons.confirm.cancel.material", target, defaults);
            retainBackup(targetFile, ".pre-v4-backup");
            target.save(targetFile);
            return true;
        } catch (Exception exception) {
            restoreOriginal(targetFile, original, exception);
            plugin.getLogger().warning("Could not save migrated FoTeams GUI resource: " + exception.getMessage());
            return false;
        }
    }

    private boolean migrateGuiSchemaV5() {
        File targetFile = ResourceFiles.dataFile(plugin, RESOURCE);
        byte[] original = null;
        try {
            original = Files.readAllBytes(targetFile.toPath());
            YamlConfiguration target = new YamlConfiguration();
            target.load(targetFile);
            YamlConfiguration defaults = bundledDefaults();
            retainBackup(targetFile, ".pre-v5-backup");
            applySchemaV5(target, defaults);
            target.save(targetFile);
            return true;
        } catch (Exception exception) {
            restoreOriginal(targetFile, original, exception);
            plugin.getLogger().warning("Could not migrate FoTeams GUI schema: " + exception.getMessage());
            return false;
        }
    }

    private void retainBackup(File targetFile, String suffix) throws java.io.IOException {
        File backup = new File(targetFile.getParentFile(), targetFile.getName() + suffix);
        if (!backup.exists()) {
            Files.copy(targetFile.toPath(), backup.toPath(), StandardCopyOption.COPY_ATTRIBUTES);
        }
    }

    private void restoreOriginal(File targetFile, byte[] original, Exception migrationFailure) {
        if (original == null) {
            return;
        }
        try {
            Files.write(targetFile.toPath(), original);
        } catch (Exception restoreFailure) {
            migrationFailure.addSuppressed(restoreFailure);
        }
    }

    static boolean applySchemaV5(YamlConfiguration target, YamlConfiguration defaults) {
        boolean changed = false;
        for (String root : List.of(
                "navigation",
                "labels",
                "buttons.dashboard.invites-empty",
                "buttons.members.entry-owner",
                "buttons.members.entry-admin",
                "buttons.members.entry-member",
                "buttons.transfer-ownership.owner",
                "buttons.transfer-ownership.target",
                "buttons.settings.pvp-forced",
                "buttons.warps.home-missing",
                "buttons.warps.warp-protected",
                "buttons.info.team-with-level",
                "buttons.info.member",
                "buttons.relations.entry-ally",
                "buttons.relations.entry-enemy",
                "buttons.relations.entry-request",
                "buttons.upgrades.size-maxed",
                "buttons.upgrades.echest-maxed",
                "buttons.confirm.transfer-confirm",
                "buttons.confirm.transfer-target",
                "buttons.confirm.transfer-cancel")) {
            changed |= copyMissingRoot(target, defaults, root);
        }

        Map<String, Object> oldDefaults = new LinkedHashMap<>();
        oldDefaults.put("buttons.dashboard.invites.lore", List.of("#ffffffNo pending team invites."));
        oldDefaults.put("buttons.dashboard.info.lore", List.of("#ffffffView your team information."));
        oldDefaults.put("buttons.dashboard.members.lore", List.of("#ffffffView and manage team members."));
        oldDefaults.put("buttons.dashboard.warps.lore", List.of("#ffffffManage team home and warps."));
        oldDefaults.put("buttons.dashboard.score-top.lore", List.of("#ffffffView the team score leaderboard."));
        oldDefaults.put("buttons.dashboard.balance.lore", List.of("#ffffffDeposit or withdraw team funds."));
        oldDefaults.put("buttons.members.header.lore", List.of("#ffffffManage your team members."));
        oldDefaults.put("buttons.relations.empty.lore", List.of("#ffffffThere are no other teams to manage."));
        oldDefaults.put("buttons.info.allies.lore", List.of("#ffffffAllies: #a7b8b0None"));
        oldDefaults.put("buttons.confirm.confirm.lore", List.of("#ffffffClick to continue."));
        oldDefaults.put("buttons.confirm.cancel.lore", List.of("#ffffffReturn without changing anything."));
        for (Map.Entry<String, Object> entry : oldDefaults.entrySet()) {
            String path = entry.getKey();
            if (Objects.deepEquals(target.get(path), entry.getValue())) {
                target.set(path, defaults.get(path));
                changed = true;
            }
        }
        return changed;
    }

    private static boolean copyMissingRoot(YamlConfiguration target, YamlConfiguration defaults, String root) {
        var section = defaults.getConfigurationSection(root);
        if (section == null) {
            return false;
        }
        boolean changed = false;
        for (String key : section.getKeys(true)) {
            String sourcePath = root + "." + key;
            if (section.isConfigurationSection(key) || target.isSet(sourcePath)) {
                continue;
            }
            target.set(sourcePath, defaults.get(sourcePath));
            changed = true;
        }
        return changed;
    }

    private YamlConfiguration bundledDefaults() {
        YamlConfiguration defaults = new YamlConfiguration();
        try (InputStream input = plugin.getResource(RESOURCE)) {
            if (input == null) {
                throw new IllegalStateException("Missing bundled " + RESOURCE);
            }
            defaults.load(new InputStreamReader(input, StandardCharsets.UTF_8));
            return defaults;
        } catch (Exception exception) {
            throw new IllegalStateException("Could not read bundled " + RESOURCE, exception);
        }
    }

    private void copyIfUntouched(String oldPath, String newPath, YamlConfiguration target, YamlConfiguration defaults) {
        Object oldValue = plugin.getConfig().get(oldPath);
        if (!plugin.getConfig().isSet(oldPath)
                || !Objects.deepEquals(target.get(newPath), defaults.get(newPath))) {
            return;
        }
        target.set(newPath, oldValue);
    }
}
