package me.foesio.foTeams.config;

import org.bukkit.plugin.java.JavaPlugin;

public final class PluginConfigs {
    private final FileConfig permissions;
    private final FileConfig swearWords;

    public PluginConfigs(JavaPlugin plugin) {
        this.permissions = new FileConfig(plugin, "permissions.yml");
        this.swearWords = new FileConfig(plugin, "swear-words.yml");
    }

    public void loadAll() {
        permissions.load();
        swearWords.load();
    }

    public void reloadAll() {
        permissions.reload();
        swearWords.reload();
    }

    public FileConfig permissions() {
        return permissions;
    }

    public FileConfig swearWords() {
        return swearWords;
    }
}
