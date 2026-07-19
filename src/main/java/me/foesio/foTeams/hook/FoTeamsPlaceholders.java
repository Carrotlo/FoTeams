package me.foesio.foTeams.hook;

import me.foesio.core.placeholder.FoPlaceholders;
import me.foesio.foTeams.FoTeams;
import me.foesio.foTeams.model.Team;
import me.foesio.foTeams.util.TagColorUtil;
import me.foesio.foTeams.util.Text;
import org.bukkit.OfflinePlayer;

import java.util.function.Function;

public final class FoTeamsPlaceholders {
    private FoTeamsPlaceholders() {
    }

    public static boolean register(FoTeams plugin) {
        return plugin.getCore().placeholders("foteams")
                .author("Carrotio")
                .offline("team_name", teamValue(plugin, Team::getName))
                .offline("team_tag", teamValue(plugin, Team::getTag))
                .offline("team_color", teamValue(plugin, FoTeamsPlaceholders::normalizedTeamColor))
                .offline("team_colour", teamValue(plugin, FoTeamsPlaceholders::normalizedTeamColor))
                .offline("team_description", teamValue(plugin, Team::getDescription))
                .offline("team_score", teamValue(plugin, team -> String.valueOf(team.getScore())))
                .offline("team_score_rank", teamValue(plugin, team -> String.valueOf(plugin.getTeamService().scoreRank(team)), noRankFallback(plugin)))
                .offline("team_balance", teamValue(plugin, team -> Text.money(team.getBalance())))
                .offline("team_balance_rank", teamValue(plugin, team -> String.valueOf(plugin.getTeamService().balanceRank(team)), noRankFallback(plugin)))
                .offline("team_level", teamValue(plugin, team -> String.valueOf(team.getTeamLevel())))
                .offline("team_level_xp", teamValue(plugin, team -> String.valueOf(team.getTeamXp())))
                .offline("team_level_required_xp", teamValue(plugin, team -> String.valueOf(plugin.getTeamLevelService().requiredXp(team))))
                .offline("team_level_remaining_xp", teamValue(plugin, team -> String.valueOf(Math.max(0L, plugin.getTeamLevelService().requiredXp(team) - team.getTeamXp()))))
                .offline("team_level_progress_percent", teamValue(plugin, team -> String.valueOf(levelProgressPercent(plugin, team))))
                .offline("team_level_rank", teamValue(plugin, team -> String.valueOf(plugin.getTeamService().levelRank(team)), noRankFallback(plugin)))
                .offline("team_role", player -> {
                    Team team = teamOf(plugin, player);
                    if (team == null) {
                        return noTeamFallback(plugin);
                    }
                    return team.roleOf(player.getUniqueId()) == null ? noTeamFallback(plugin) : team.roleOf(player.getUniqueId()).displayName();
                })
                .offline("team_member_count", teamValue(plugin, team -> String.valueOf(team.getMemberCount())))
                .offline("team_member_cap", teamValue(plugin, team -> String.valueOf(team.getMemberCap())))
                .offline("team_owner", teamValue(plugin, team -> team.hasOwner() ? ownerName(plugin, team) : noTeamFallback(plugin)))
                .registerIfAvailable();
    }

    private static FoPlaceholders.OfflinePlayerPlaceholder teamValue(FoTeams plugin, Function<Team, String> value) {
        return teamValue(plugin, value, noTeamFallback(plugin));
    }

    private static FoPlaceholders.OfflinePlayerPlaceholder teamValue(FoTeams plugin, Function<Team, String> value, String fallback) {
        return player -> {
            Team team = teamOf(plugin, player);
            return team == null ? fallback : value.apply(team);
        };
    }

    private static Team teamOf(FoTeams plugin, OfflinePlayer player) {
        if (player == null) {
            return null;
        }
        return plugin.getTeamService().teamOf(player.getUniqueId()).orElse(null);
    }

    private static String noTeamFallback(FoTeams plugin) {
        return plugin.getConfig().getString("placeholders.no-team", plugin.getMessages().render("placeholder-no-team"));
    }

    private static String noRankFallback(FoTeams plugin) {
        return plugin.getConfig().getString("placeholders.no-rank", plugin.getMessages().render("placeholder-no-rank"));
    }

    private static int levelProgressPercent(FoTeams plugin, Team team) {
        long requiredLevelXp = plugin.getTeamLevelService().requiredXp(team);
        if (requiredLevelXp <= 0L) {
            return 100;
        }
        double progress = Math.max(0.0D, Math.min(1.0D, team.getTeamXp() / (double) requiredLevelXp));
        return (int) Math.round(progress * 100.0D);
    }

    private static String normalizedTeamColor(Team team) {
        String normalized = TagColorUtil.normalize(team.getColor());
        if (normalized == null) {
            return team.getColor();
        }
        return normalized.startsWith("#") ? "&" + normalized.toUpperCase(java.util.Locale.ROOT) : normalized;
    }

    private static String ownerName(FoTeams plugin, Team team) {
        String name = plugin.getServer().getOfflinePlayer(team.getOwnerId()).getName();
        return name == null ? noTeamFallback(plugin) : name;
    }
}
