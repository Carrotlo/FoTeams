package me.foesio.foTeams.gui;

import me.foesio.core.config.ResourceFiles;
import me.foesio.core.gui.GuiItemConfig;
import me.foesio.core.gui.GuiResourceLoader;
import me.foesio.core.migration.FoMigrationStore;
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
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Presentation-only resource for the player-facing FoTeams screens. */
public final class PublicGuiConfig {
    private static final String RESOURCE = "guis/team.yml";

    private final JavaPlugin plugin;
    private FileConfiguration config;

    public PublicGuiConfig(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void initialize(FoMigrationStore migrations) {
        GuiResourceLoader.loadAndBackfill(plugin, RESOURCE);
        migrations.runToVersion(3, this::migrateLegacyConfig);
        reload();
    }

    public void reload() {
        config = GuiResourceLoader.loadAndBackfill(plugin, RESOURCE);
    }

    public String title(String screen, String fallback) {
        return config.getString("titles." + screen, fallback);
    }

    public ItemStack button(Player viewer, String screen, String key, Material fallbackMaterial,
            String fallbackName, List<String> fallbackLore, Map<String, String> placeholders) {
        GuiItemConfig fallback = GuiItemConfig.of(fallbackMaterial, fallbackName, fallbackLore);
        return GuiItemConfig.from(config.getConfigurationSection("buttons." + screen + "." + key), fallback)
                .create(viewer, placeholders);
    }

    public ItemStack button(Player viewer, String screen, String key, Material fallbackMaterial,
            String fallbackName, List<String> fallbackLore) {
        return button(viewer, screen, key, fallbackMaterial, fallbackName, fallbackLore, Map.of());
    }

    private boolean migrateLegacyConfig() {
        File targetFile = ResourceFiles.dataFile(plugin, RESOURCE);
        YamlConfiguration target = YamlConfiguration.loadConfiguration(targetFile);
        YamlConfiguration defaults = new YamlConfiguration();
        try (InputStream input = plugin.getResource(RESOURCE)) {
            if (input == null) {
                return false;
            }
            defaults.load(new InputStreamReader(input, StandardCharsets.UTF_8));
        } catch (Exception exception) {
            plugin.getLogger().warning("Could not read bundled FoTeams GUI defaults: " + exception.getMessage());
            return false;
        }

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

        try {
            target.save(targetFile);
            return true;
        } catch (java.io.IOException exception) {
            plugin.getLogger().warning("Could not save migrated FoTeams GUI resource: " + exception.getMessage());
            return false;
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
