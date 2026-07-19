package me.foesio.foTeams.input;

import me.foesio.foTeams.FoTeams;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.List;

public record BalanceDialogConfig(
        String title,
        List<String> body,
        String fieldLabel,
        String initialValue,
        String placeholder,
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

    public static BalanceDialogConfig load(FoTeams plugin) {
        File file = new File(plugin.getDataFolder(), RESOURCE);
        ensureFile(plugin, file);

        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        boolean changed = false;
        changed |= addDefault(config, "title", "{theme}Team Bank");
        changed |= addDefault(config, "body", List.of(
                "{muted}Team bank: {theme}{team_balance}",
                "{muted}Your balance: {white}{player_balance}"
        ));
        changed |= addDefault(config, "field-label", "{white}Amount");
        changed |= addDefault(config, "initial-value", "");
        changed |= addDefault(config, "placeholder", "100");
        changed |= addDefault(config, "button-width", 100);
        changed |= addDefault(config, "deposit-button.label", "{good}Deposit");
        changed |= addDefault(config, "deposit-button.tooltip", "Deposit this amount into the team bank.");
        changed |= addDefault(config, "deposit-button.icon", "emerald");
        changed |= addDefault(config, "withdraw-button.label", "{theme}Withdraw");
        changed |= addDefault(config, "withdraw-button.tooltip", "Withdraw this amount from the team bank.");
        changed |= addDefault(config, "withdraw-button.icon", "gold_ingot");
        changed |= addDefault(config, "back-button.label", "{bad}Back");
        changed |= addDefault(config, "back-button.tooltip", "Close without changing the team bank.");
        changed |= addDefault(config, "back-button.icon", "iron_door");
        changed |= addDefault(config, "body-width", 300);
        changed |= addDefault(config, "input-width", 300);
        changed |= addDefault(config, "max-length", 32);
        changed |= addDefault(config, "label-visible", true);
        changed |= addDefault(config, "can-close-with-escape", true);
        changed |= addDefault(config, "pause", false);
        changed |= addDefault(config, "columns", 3);
        if (changed) {
            save(plugin, config, file);
        }

        int buttonWidth = config.getInt("button-width", 100);
        return new BalanceDialogConfig(
                config.getString("title", "{theme}Team Bank"),
                body(config),
                config.getString("field-label", "{white}Amount"),
                config.getString("initial-value", ""),
                config.getString("placeholder", "100"),
                button(config, "deposit-button", "{good}Deposit", "Deposit this amount into the team bank.", "emerald", buttonWidth),
                button(config, "withdraw-button", "{theme}Withdraw", "Withdraw this amount from the team bank.", "gold_ingot", buttonWidth),
                button(config, "back-button", "{bad}Back", "Close without changing the team bank.", "iron_door", buttonWidth),
                config.getInt("body-width", 300),
                config.getInt("input-width", 300),
                config.getInt("max-length", 32),
                config.getBoolean("label-visible", true),
                config.getBoolean("can-close-with-escape", true),
                config.getBoolean("pause", false),
                Math.max(1, config.getInt("columns", 3))
        );
    }

    private static void ensureFile(FoTeams plugin, File file) {
        File parent = file.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            plugin.getLogger().warning("Could not create dialog config folder: " + parent.getPath());
        }
        if (!file.exists()) {
            try {
                plugin.saveResource(RESOURCE, false);
            } catch (IllegalArgumentException exception) {
                save(plugin, new YamlConfiguration(), file);
            }
        }
    }

    private static boolean addDefault(YamlConfiguration config, String path, Object value) {
        if (config.contains(path)) {
            return false;
        }
        config.set(path, value);
        return true;
    }

    private static List<String> body(YamlConfiguration config) {
        if (config.isList("body")) {
            return config.getStringList("body");
        }
        String text = config.getString("body", "");
        if (text.isBlank()) {
            return List.of();
        }
        return List.of(text.split("\\R", -1));
    }

    private static Button button(YamlConfiguration config, String path, String label, String tooltip, String icon, int width) {
        return new Button(
                config.getString(path + ".label", label),
                config.getString(path + ".tooltip", tooltip),
                config.getString(path + ".icon", icon),
                config.getInt(path + ".width", width)
        );
    }

    private static void save(FoTeams plugin, YamlConfiguration config, File file) {
        try {
            config.save(file);
        } catch (IOException exception) {
            plugin.getLogger().warning("Could not save " + RESOURCE + ": " + exception.getMessage());
        }
    }

    public record Button(String label, String tooltip, String icon, int width) {
    }
}
