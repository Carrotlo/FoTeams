package me.foesio.foTeams.service;

import me.foesio.foTeams.FoTeams;
import me.foesio.foTeams.model.Team;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class TeamLevelService {
    private final FoTeams plugin;

    public TeamLevelService(FoTeams plugin) {
        this.plugin = plugin;
    }

    public boolean isConfiguredEnabled() {
        return plugin.getConfig().getBoolean("team-level.enabled", false);
    }

    public boolean isFoLevelsAvailable() {
        Plugin foLevels = plugin.getServer().getPluginManager().getPlugin("FoLevels");
        return foLevels != null && foLevels.isEnabled();
    }

    public boolean isEnabled() {
        return isConfiguredEnabled() && isFoLevelsAvailable();
    }

    public long requiredXp(Team team) {
        return plugin.getTeamService().requiredTeamLevelXp(team.getTeamLevel(), baseRequiredXp(), multiplier());
    }

    public double baseRequiredXp() {
        return Math.max(1.0D, plugin.getConfig().getDouble("team-level.formula.base-required-xp", 500.0D));
    }

    public double multiplier() {
        return Math.max(1.01D, plugin.getConfig().getDouble("team-level.formula.multiplier", 1.35D));
    }

    public int maxLevel() {
        return Math.max(0, plugin.getConfig().getInt("team-level.formula.max-level", 0));
    }

    public boolean addXpFromFoLevels(Player player, long amount, String reason, String sourceKey) throws SQLException {
        if (!isEnabled() || amount <= 0L) {
            return false;
        }
        Team team = plugin.getTeamService().teamOf(player.getUniqueId()).orElse(null);
        if (team == null) {
            return false;
        }

        TeamService.TeamLevelResult result = plugin.getTeamService().addTeamLevelXp(team, amount, baseRequiredXp(), multiplier(), maxLevel());
        if (result.gainedXp() <= 0L) {
            return false;
        }
        if (plugin.getConfig().getBoolean("team-level.show-xp-gain", false)) {
            plugin.getMessages().send(player, "team-level-xp-gained", replacements(team, player, result.newLevel(), result.gainedXp(), result.currentXp(), result.requiredXp()));
        }
        for (int level : result.gainedLevels()) {
            Map<String, String> replacements = replacements(team, player, level, result.gainedXp(), team.getTeamXp(), requiredXp(team), reason, sourceKey);
            broadcast(team, "team-level-up", replacements);
            applyRewards(team, player, level, replacements);
        }
        return true;
    }

    public void sendDisabledMessage(CommandSender sender) {
        if (!isConfiguredEnabled()) {
            plugin.getMessages().send(sender, "team-level-disabled");
            return;
        }
        plugin.getMessages().send(sender, "team-level-unavailable");
    }

    private void applyRewards(Team team, Player source, int level, Map<String, String> baseReplacements) {
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("team-level.rewards." + level);
        if (section == null) {
            return;
        }
        List<String> rewardMessages = section.getStringList("messages");
        if (!rewardMessages.isEmpty()) {
            for (UUID memberId : plugin.getTeamService().allMembers(team)) {
                Player member = Bukkit.getPlayer(memberId);
                if (member == null) {
                    continue;
                }
                for (String message : rewardMessages) {
                    member.sendMessage(plugin.getMessages().renderTemplateComponent(member, message, withPlayer(baseReplacements, member)));
                }
            }
        }
        for (String command : section.getStringList("console-commands")) {
            dispatchConsole(command, baseReplacements);
        }
        for (String command : section.getStringList("member-commands")) {
            for (UUID memberId : plugin.getTeamService().allMembers(team)) {
                Player member = Bukkit.getPlayer(memberId);
                if (member != null) {
                    dispatchConsole(command, withPlayer(baseReplacements, member));
                }
            }
        }
    }

    private void broadcast(Team team, String messageKey, Map<String, String> replacements) {
        for (UUID memberId : plugin.getTeamService().allMembers(team)) {
            Player member = Bukkit.getPlayer(memberId);
            if (member != null) {
                plugin.getMessages().send(member, messageKey, replacements);
            }
        }
    }

    private void dispatchConsole(String rawCommand, Map<String, String> replacements) {
        String command = replace(rawCommand, replacements).trim();
        if (command.startsWith("/")) {
            command = command.substring(1);
        }
        if (!command.isBlank()) {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
        }
    }

    private Map<String, String> replacements(Team team, Player source, int level, long gainedXp, long currentXp, long requiredXp) {
        return replacements(team, source, level, gainedXp, currentXp, requiredXp, "", "");
    }

    private Map<String, String> replacements(Team team, Player source, int level, long gainedXp, long currentXp, long requiredXp, String reason, String sourceKey) {
        Map<String, String> replacements = new HashMap<>();
        replacements.put("{team}", team.getName());
        replacements.put("{team_id}", String.valueOf(team.getId()));
        replacements.put("{level}", String.valueOf(level));
        replacements.put("{xp}", String.valueOf(gainedXp));
        replacements.put("{current}", String.valueOf(currentXp));
        replacements.put("{required}", String.valueOf(requiredXp));
        replacements.put("{source}", source.getName());
        replacements.put("{source_uuid}", source.getUniqueId().toString());
        replacements.put("{reason}", reason == null ? "" : reason);
        replacements.put("{source_key}", sourceKey == null ? "" : sourceKey);
        replacements.put("{owner}", ownerName(team));
        replacements.put("{player}", source.getName());
        replacements.put("{player_uuid}", source.getUniqueId().toString());
        return replacements;
    }

    private Map<String, String> withPlayer(Map<String, String> replacements, Player player) {
        Map<String, String> copy = new HashMap<>(replacements);
        copy.put("{player}", player.getName());
        copy.put("{player_uuid}", player.getUniqueId().toString());
        return copy;
    }

    private String replace(String raw, Map<String, String> replacements) {
        String result = raw == null ? "" : raw;
        for (Map.Entry<String, String> entry : replacements.entrySet()) {
            result = result.replace(entry.getKey(), entry.getValue());
        }
        return result;
    }

    private String ownerName(Team team) {
        if (!team.hasOwner()) {
            return "None";
        }
        OfflinePlayer owner = Bukkit.getOfflinePlayer(team.getOwnerId());
        return owner.getName() == null ? team.getOwnerId().toString() : owner.getName();
    }
}
