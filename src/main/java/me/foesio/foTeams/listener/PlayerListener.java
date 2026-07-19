package me.foesio.foTeams.listener;

import me.foesio.foTeams.FoTeams;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.logging.Level;

public final class PlayerListener implements Listener {
    private final FoTeams plugin;

    public PlayerListener(FoTeams plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        plugin.getTeamChatService().clear(event.getPlayer());
        plugin.getPromptService().removePrompt(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        Player killer = victim.getKiller();
        if (killer == null) {
            return;
        }
        try {
            plugin.getTeamService().addKill(
                    killer,
                    victim,
                    plugin.getConfig().getBoolean("score.count-friendly-kills", false),
                    plugin.getConfig().getBoolean("score.count-ally-kills", false),
                    plugin.getConfig().getInt("score.prevent-repeat-farming-window-seconds", 120)
            );
        } catch (Exception exception) {
            plugin.getLogger().log(Level.WARNING, "Failed to track team score.", exception);
        }
    }
}
