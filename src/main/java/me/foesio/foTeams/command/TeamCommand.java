package me.foesio.foTeams.command;

import me.foesio.core.number.LargeNumberParser;
import me.foesio.foTeams.FoTeams;
import me.foesio.foTeams.listener.ChatListener;
import me.foesio.foTeams.model.ChatMode;
import me.foesio.foTeams.model.RelationType;
import me.foesio.foTeams.model.Team;
import me.foesio.foTeams.model.TeamAction;
import me.foesio.foTeams.model.TeamInvite;
import me.foesio.foTeams.model.TeamRole;
import me.foesio.foTeams.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Level;

public final class TeamCommand implements CommandExecutor, TabCompleter {
    private final FoTeams plugin;
    private final ChatListener chatListener;
    private static final List<String> SUBCOMMANDS = List.of("help", "create", "invite", "invites", "revoke", "join", "leave", "disband", "kick", "promote", "demote", "pvp", "ally", "unally", "chat", "allychat", "home", "sethome", "warp", "setwarp", "warps", "info", "settings", "upgrades", "echest", "top", "level", "bal", "baltop", "deposit", "withdraw");

    public TeamCommand(FoTeams plugin, ChatListener chatListener) {
        this.plugin = plugin;
        this.chatListener = chatListener;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Players only.");
            return true;
        }
        if (args.length == 0) {
            plugin.getGuiService().openDashboard(player);
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        try {
            return switch (sub) {
                case "help" -> help(player);
                case "create" -> create(player, args);
                case "invite" -> invite(player, args);
                case "invites" -> invites(player);
                case "revoke" -> revoke(player, args);
                case "join" -> join(player, args);
                case "leave" -> leave(player);
                case "disband" -> disband(player);
                case "kick" -> kick(player, args);
                case "promote" -> promote(player, args);
                case "demote" -> demote(player, args);
                case "pvp" -> pvp(player, args);
                case "ally" -> ally(player, args);
                case "unally" -> unally(player, args);
                case "chat" -> teamChat(player, args);
                case "allychat" -> allyChat(player, args);
                case "home" -> home(player);
                case "sethome" -> setHome(player);
                case "warp" -> warp(player, args);
                case "setwarp" -> setWarp(player, args);
                case "warps" -> warps(player);
                case "info" -> info(player, args);
                case "settings" -> settings(player);
                case "upgrades" -> upgrades(player);
                case "echest" -> echest(player);
                case "top" -> top(player);
                case "level" -> level(player, args);
                case "bal" -> balance(player);
                case "baltop" -> balanceTop(player);
                case "deposit" -> deposit(player, args);
                case "withdraw" -> withdraw(player, args);
                default -> help(player);
            };
        } catch (SQLException exception) {
            plugin.getLogger().log(Level.WARNING, "Team command failed.", exception);
            plugin.getMessages().send(player, "command-failed");
            return true;
        }
    }

    private boolean help(Player player) {
        plugin.getMessages().sendList(player, "help-player");
        return true;
    }

    private boolean create(Player player, String[] args) throws SQLException {
        if (plugin.getTeamService().teamOf(player.getUniqueId()).isPresent()) {
            plugin.getMessages().send(player, "already-in-team");
            return true;
        }
        if (args.length < 2) {
            plugin.getMessages().send(player, "usage-create");
            return true;
        }
        String name = String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length));
        if (!validateTeamName(player, name)) {
            return true;
        }
        if (plugin.getTeamService().isNameTaken(name)) {
            plugin.getMessages().send(player, "team-name-taken");
            return true;
        }
        Team team = plugin.getTeamService().createTeam(player, name);
        plugin.getMessages().send(player, "team-created", Map.of("{team}", team.getName()));
        return true;
    }

    private boolean validateTeamName(Player player, String name) {
        if (!plugin.getTeamService().isValidTeamNameCharacters(name)) {
            plugin.getMessages().send(player, "name-invalid");
            return false;
        }
        if (!plugin.getTeamService().isValidTeamNameLength(name)) {
            plugin.getMessages().send(player, "name-too-long", Map.of("{max}", String.valueOf(plugin.getTeamService().maxNameLength())));
            return false;
        }
        if (plugin.getSwearFilterService().containsBlockedWord(name)) {
            plugin.getMessages().send(player, "blocked-word");
            return false;
        }
        return true;
    }

    private boolean invite(Player player, String[] args) throws SQLException {
        Team team = requireTeam(player);
        if (team == null) {
            return true;
        }
        if (!plugin.getTeamService().can(team, player.getUniqueId(), TeamAction.INVITE)) {
            plugin.getMessages().send(player, "no-permission-role");
            return true;
        }
        if (args.length < 2) {
            plugin.getMessages().send(player, "usage-invite");
            return true;
        }
        Player target = resolveOnlinePlayer(args[1]);
        if (target == null) {
            plugin.getMessages().send(player, "player-not-found");
            return true;
        }
        if (plugin.getTeamService().teamOf(target.getUniqueId()).isPresent()) {
            plugin.getMessages().send(player, "player-already-in-team");
            return true;
        }
        plugin.getTeamService().invite(team, player.getUniqueId(), target.getUniqueId());
        plugin.getMessages().send(player, "invite-sent", Map.of("{player}", target.getName(), "{team}", team.getName()));
        plugin.getMessages().send(target, "invite-received", Map.of("{team}", team.getName()));
        return true;
    }

    private boolean join(Player player, String[] args) throws SQLException {
        if (plugin.getTeamService().teamOf(player.getUniqueId()).isPresent()) {
            plugin.getMessages().send(player, "already-in-team");
            return true;
        }
        if (args.length < 2) {
            plugin.getMessages().send(player, "usage-join");
            return true;
        }
        String teamName = String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length));
        Team team = plugin.getTeamService().byName(teamName).orElse(null);
        if (team == null) {
            plugin.getMessages().send(player, "team-not-found");
            return true;
        }
        if (!plugin.getTeamService().hasInvite(player.getUniqueId(), team.getId())) {
            plugin.getMessages().send(player, "no-invite");
            return true;
        }
        if (plugin.getTeamService().isFull(team)) {
            plugin.getMessages().send(player, "team-full");
            return true;
        }
        plugin.getTeamService().join(player, team);
        for (java.util.UUID memberId : plugin.getTeamService().allMembers(team)) {
            Player target = Bukkit.getPlayer(memberId);
            if (target != null) {
                plugin.getMessages().send(target, "member-joined", Map.of("{player}", player.getName(), "{team}", team.getName()));
            }
        }
        return true;
    }

    private boolean leave(Player player) throws SQLException {
        Team team = requireTeam(player);
        if (team == null) {
            return true;
        }
        if (team.roleOf(player.getUniqueId()) == TeamRole.OWNER) {
            plugin.getMessages().send(player, "owner-use-disband");
            return true;
        }
        try {
            plugin.getTeamService().leave(player);
            plugin.getMessages().send(player, "leave-success", Map.of("{team}", team.getName()));
        } catch (IllegalStateException exception) {
            plugin.getMessages().send(player, "leave-failed");
        }
        return true;
    }

    private boolean invites(Player player) {
        Team team = requireTeam(player);
        if (team == null) {
            return true;
        }
        if (!canManageInvites(team, player)) {
            plugin.getMessages().send(player, "no-permission-role");
            return true;
        }
        List<TeamInvite> invites = plugin.getTeamService().teamInvites(team);
        if (invites.isEmpty()) {
            plugin.getMessages().send(player, "no-pending-invites");
            return true;
        }
        plugin.getMessages().send(player, "invites-header", Map.of("{team}", team.getName()));
        for (TeamInvite invite : invites) {
            OfflinePlayer target = Bukkit.getOfflinePlayer(invite.playerId());
            plugin.getMessages().send(player, "invites-entry", Map.of("{player}", offlineName(target)));
        }
        return true;
    }

    private boolean revoke(Player player, String[] args) throws SQLException {
        Team team = requireTeam(player);
        if (team == null) {
            return true;
        }
        if (!canManageInvites(team, player)) {
            plugin.getMessages().send(player, "no-permission-role");
            return true;
        }
        if (args.length < 2) {
            plugin.getMessages().send(player, "usage-revoke");
            return true;
        }
        MemberTarget target = resolveTeamInvite(team, args[1]).orElse(null);
        if (target == null || !plugin.getTeamService().revokeInvite(team, target.id())) {
            plugin.getMessages().send(player, "no-invite");
            return true;
        }
        plugin.getMessages().send(player, "invite-revoked", Map.of("{player}", target.name(), "{team}", team.getName()));
        Player online = Bukkit.getPlayer(target.id());
        if (online != null) {
            plugin.getMessages().send(online, "invite-revoked-target", Map.of("{team}", team.getName()));
        }
        return true;
    }

    private boolean disband(Player player) {
        Team team = requireTeam(player);
        if (team == null) {
            return true;
        }
        if (!plugin.getTeamService().can(team, player.getUniqueId(), TeamAction.DISBAND)) {
            plugin.getMessages().send(player, "no-permission-role");
            return true;
        }
        plugin.getGuiService().openConfirm(player, team, false);
        return true;
    }

    private boolean pvp(Player player, String[] args) throws SQLException {
        Team team = requireTeam(player);
        if (team == null) {
            return true;
        }
        if (!canManageTeamPvp(team, player)) {
            plugin.getMessages().send(player, "no-permission-role");
            return true;
        }
        if (plugin.getConfig().getBoolean("team-pvp-force-disable-all", false)) {
            player.sendMessage(plugin.getMessages().renderTemplate(
                    "{prefix}{bad}Team PvP setting is locked while {white}team-pvp-force-disable-all{bad} is true. Teammates can always damage each other.",
                    Map.of()
            ));
            return true;
        }
        boolean enabled;
        if (args.length > 1) {
            Boolean parsed = parseToggleState(args[1]);
            if (parsed == null) {
                plugin.getMessages().send(player, "usage-pvp");
                return true;
            }
            enabled = parsed;
        } else {
            enabled = !team.isTeamPvpProtectionEnabled();
        }
        if (!plugin.getTeamService().setTeamPvpProtection(team, enabled)) {
            plugin.getMessages().send(player, enabled ? "pvp-protection-already-enabled" : "pvp-protection-already-disabled");
            return true;
        }
        broadcastTeamMessage(team, enabled ? "pvp-protection-enabled" : "pvp-protection-disabled");
        return true;
    }

    private boolean kick(Player player, String[] args) throws SQLException {
        Team team = requireTeam(player);
        if (team == null) {
            return true;
        }
        if (!plugin.getTeamService().can(team, player.getUniqueId(), TeamAction.KICK)) {
            plugin.getMessages().send(player, "no-permission-role");
            return true;
        }
        if (args.length < 2) {
            plugin.getMessages().send(player, "usage-kick");
            return true;
        }
        MemberTarget target = resolveTeamMember(team, args[1]).orElse(null);
        if (target == null) {
            plugin.getMessages().send(player, "player-not-found");
            return true;
        }
        TeamRole role = target.role();
        if (role == TeamRole.OWNER) {
            plugin.getMessages().send(player, "member-owner-protected");
            return true;
        }
        if (role == null) {
            plugin.getMessages().send(player, "kick-target-invalid");
            return true;
        }
        plugin.getTeamService().kick(team, target.id());
        plugin.getMessages().send(player, "kick-success", Map.of("{player}", target.name()));
        Player targetOnline = Bukkit.getPlayer(target.id());
        if (targetOnline != null) {
            plugin.getMessages().send(targetOnline, "kick-target", Map.of("{team}", team.getName()));
        }
        return true;
    }

    private boolean promote(Player player, String[] args) throws SQLException {
        Team team = requireTeam(player);
        if (team == null) {
            return true;
        }
        if (!plugin.getTeamService().can(team, player.getUniqueId(), TeamAction.PROMOTE)) {
            plugin.getMessages().send(player, "no-permission-role");
            return true;
        }
        if (args.length < 2) {
            plugin.getMessages().send(player, "usage-promote");
            return true;
        }
        MemberTarget target = resolveTeamMember(team, args[1]).orElse(null);
        if (target == null) {
            plugin.getMessages().send(player, "player-not-found");
            return true;
        }
        TeamRole role = target.role();
        if (role == TeamRole.OWNER) {
            plugin.getMessages().send(player, "member-owner-protected");
            return true;
        }
        if (role != TeamRole.MEMBER) {
            plugin.getMessages().send(player, "promote-target-invalid");
            return true;
        }
        if (!plugin.getTeamService().promote(team, target.id())) {
            plugin.getMessages().send(player, "admin-limit", Map.of("{max}", String.valueOf(plugin.getTeamService().maxAdmins())));
            return true;
        }
        plugin.getMessages().send(player, "promote-success", Map.of("{player}", target.name(), "{role}", "admin"));
        return true;
    }

    private boolean demote(Player player, String[] args) throws SQLException {
        Team team = requireTeam(player);
        if (team == null) {
            return true;
        }
        if (!plugin.getTeamService().can(team, player.getUniqueId(), TeamAction.DEMOTE)) {
            plugin.getMessages().send(player, "no-permission-role");
            return true;
        }
        if (args.length < 2) {
            plugin.getMessages().send(player, "usage-demote");
            return true;
        }
        MemberTarget target = resolveTeamMember(team, args[1]).orElse(null);
        if (target == null) {
            plugin.getMessages().send(player, "player-not-found");
            return true;
        }
        TeamRole role = target.role();
        if (role == TeamRole.OWNER) {
            plugin.getMessages().send(player, "member-owner-protected");
            return true;
        }
        if (role != TeamRole.ADMIN) {
            plugin.getMessages().send(player, "demote-target-invalid");
            return true;
        }
        plugin.getTeamService().demote(team, target.id());
        plugin.getMessages().send(player, "demote-success", Map.of("{player}", target.name(), "{role}", "member"));
        return true;
    }

    private boolean teamChat(Player player, String[] args) {
        Team team = requireTeam(player);
        if (team == null) {
            return true;
        }
        if (args.length > 1) {
            chatListener.sendOneShot(player, ChatMode.TEAM, String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length)));
            return true;
        }
        toggleChat(player, ChatMode.TEAM);
        return true;
    }

    private boolean ally(Player player, String[] args) throws SQLException {
        Team team = requireTeam(player);
        if (team == null) {
            return true;
        }
        if (!plugin.getTeamService().can(team, player.getUniqueId(), TeamAction.MANAGE_RELATIONS)) {
            plugin.getMessages().send(player, "no-permission-role");
            return true;
        }
        if (args.length < 2) {
            plugin.getMessages().send(player, "usage-ally");
            return true;
        }
        String teamName = String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length));
        Team other = plugin.getTeamService().byName(teamName).orElse(null);
        if (other == null || other.getId() == team.getId()) {
            plugin.getMessages().send(player, "team-not-found");
            return true;
        }
        boolean accepted = plugin.getTeamService().requestOrAcceptAlly(team, other);
        plugin.getMessages().send(player, accepted ? "ally-request-accepted" : "ally-request-sent", Map.of("{other}", other.getName()));
        if (!accepted) {
            notifyTeamLeaders(other, "ally-request-received", Map.of("{other}", team.getName()));
        }
        return true;
    }

    private boolean unally(Player player, String[] args) throws SQLException {
        Team team = requireTeam(player);
        if (team == null) {
            return true;
        }
        if (!plugin.getTeamService().can(team, player.getUniqueId(), TeamAction.MANAGE_RELATIONS)) {
            plugin.getMessages().send(player, "no-permission-role");
            return true;
        }
        if (args.length < 2) {
            plugin.getMessages().send(player, "usage-unally");
            return true;
        }
        String teamName = String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length));
        Team other = plugin.getTeamService().byName(teamName).orElse(null);
        if (other == null || other.getId() == team.getId()) {
            plugin.getMessages().send(player, "team-not-found");
            return true;
        }
        if (plugin.getTeamService().relation(team, other) != RelationType.ALLY) {
            plugin.getMessages().send(player, "not-allied", Map.of("{other}", other.getName()));
            return true;
        }
        plugin.getTeamService().clearRelation(team, other);
        plugin.getMessages().send(player, "unally-success", Map.of("{other}", other.getName()));
        notifyTeamLeaders(other, "unally-received", Map.of("{other}", team.getName()));
        return true;
    }

    private boolean allyChat(Player player, String[] args) {
        Team team = requireTeam(player);
        if (team == null) {
            return true;
        }
        if (args.length > 1) {
            chatListener.sendOneShot(player, ChatMode.ALLY, String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length)));
            return true;
        }
        toggleChat(player, ChatMode.ALLY);
        return true;
    }

    public void toggleChat(Player player, ChatMode mode) {
        ChatMode current = plugin.getTeamChatService().getMode(player.getUniqueId());
        if (current == mode) {
            plugin.getTeamChatService().setMode(player.getUniqueId(), ChatMode.GLOBAL);
            plugin.getMessages().send(player, mode == ChatMode.TEAM ? "chat-disabled" : "allychat-disabled");
            return;
        }
        plugin.getTeamChatService().setMode(player.getUniqueId(), mode);
        plugin.getMessages().send(player, mode == ChatMode.TEAM ? "chat-enabled" : "allychat-enabled");
    }

    private boolean home(Player player) {
        Team team = requireTeam(player);
        if (team == null) {
            return true;
        }
        if (!plugin.getTeamService().can(team, player.getUniqueId(), TeamAction.USE_HOME)) {
            plugin.getMessages().send(player, "no-permission-role");
            return true;
        }
        if (team.getHome() == null) {
            plugin.getMessages().send(player, "home-missing");
            return true;
        }
        plugin.getTeleportDelayService().start(player, team.getHome(), "home");
        return true;
    }

    private boolean setHome(Player player) throws SQLException {
        Team team = requireTeam(player);
        if (team == null) {
            return true;
        }
        if (!plugin.getTeamService().can(team, player.getUniqueId(), TeamAction.SET_HOME)) {
            plugin.getMessages().send(player, "no-permission-role");
            return true;
        }
        plugin.getTeamService().setHome(team, player.getLocation());
        plugin.getMessages().send(player, "home-set");
        return true;
    }

    private boolean warp(Player player, String[] args) {
        Team team = requireTeam(player);
        if (team == null) {
            return true;
        }
        if (args.length < 2) {
            plugin.getGuiService().openWarps(player, team, false);
            return true;
        }
        if (!plugin.getTeamService().can(team, player.getUniqueId(), TeamAction.USE_WARP)) {
            plugin.getMessages().send(player, "no-permission-role");
            return true;
        }
        String warpName = args[1].toLowerCase(Locale.ROOT);
        var location = team.getWarps().get(warpName);
        if (location == null) {
            plugin.getMessages().send(player, "warp-missing");
            return true;
        }
        if (plugin.getTeamService().warpRequiresPassword(team, warpName)) {
            if (args.length < 3) {
                plugin.getMessages().send(player, "warp-password-required");
                return true;
            }
            if (!plugin.getTeamService().matchesWarpPassword(team, warpName, args[2])) {
                plugin.getMessages().send(player, "warp-password-wrong");
                return true;
            }
        }
        plugin.getTeleportDelayService().start(player, location, warpName);
        return true;
    }

    private boolean setWarp(Player player, String[] args) throws SQLException {
        Team team = requireTeam(player);
        if (team == null) {
            return true;
        }
        if (!plugin.getTeamService().can(team, player.getUniqueId(), TeamAction.SET_WARP)) {
            plugin.getMessages().send(player, "no-permission-role");
            return true;
        }
        if (args.length < 2) {
            plugin.getMessages().send(player, "usage-setwarp");
            return true;
        }
        String warpName = args[1].toLowerCase(Locale.ROOT);
        if (warpName.isBlank()) {
            plugin.getMessages().send(player, "usage-setwarp");
            return true;
        }
        String password = args.length >= 3 ? args[2] : null;
        if (!plugin.getTeamService().canAddWarp(team, warpName)) {
            plugin.getMessages().send(player, "warp-limit", Map.of("{max}", String.valueOf(plugin.getTeamService().maxWarps())));
            return true;
        }
        plugin.getTeamService().setWarp(team, warpName, player.getLocation(), password);
        plugin.getMessages().send(player, "warp-set", Map.of("{warp}", warpName));
        return true;
    }

    private boolean warps(Player player) {
        Team team = requireTeam(player);
        if (team == null) {
            return true;
        }
        plugin.getGuiService().openWarps(player, team, false);
        return true;
    }

    private boolean info(Player player, String[] args) {
        Team team;
        if (args.length < 2) {
            team = requireTeam(player);
        } else {
            String query = String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length));
            team = plugin.getTeamService().byName(query)
                    .or(() -> plugin.getTeamService().teamOfPlayerName(query))
                    .orElse(null);
            if (team == null) {
                plugin.getMessages().send(player, plugin.getTeamService().hasKnownPlayerName(query) ? "target-not-in-team" : "team-not-found");
                return true;
            }
        }
        if (team == null) {
            return true;
        }
        plugin.getGuiService().openInfo(player, team);
        return true;
    }

    private boolean settings(Player player) {
        Team team = requireTeam(player);
        if (team == null) {
            return true;
        }
        plugin.getGuiService().openSettings(player, team, false);
        return true;
    }

    private boolean upgrades(Player player) {
        Team team = requireTeam(player);
        if (team == null) {
            return true;
        }
        if (!canManageUpgrades(team, player)) {
            plugin.getMessages().send(player, "no-permission-role");
            return true;
        }
        plugin.getGuiService().openUpgrades(player, team, false);
        return true;
    }

    private boolean echest(Player player) {
        Team team = requireTeam(player);
        if (team == null) {
            return true;
        }
        plugin.getGuiService().openSharedChest(player, team, false);
        return true;
    }

    private boolean top(Player player) {
        player.sendMessage(plugin.getMessages().renderTemplate("{prefix}{theme}Top Kills", Map.of()));
        int rank = 1;
        for (Team team : plugin.getTeamService().sortedByScore().stream().limit(10).toList()) {
            player.sendMessage(plugin.getMessages().renderTemplate("{muted}#" + rank + " {white}" + team.getName() + " {muted}- {theme}" + team.getScore(), Map.of()));
            rank++;
        }
        return true;
    }

    private boolean level(Player player, String[] args) {
        if (!plugin.getTeamLevelService().isEnabled()) {
            plugin.getTeamLevelService().sendDisabledMessage(player);
            return true;
        }
        if (args.length >= 2 && args[1].equalsIgnoreCase("top")) {
            return levelTop(player);
        }
        Team team = requireTeam(player);
        if (team == null) {
            return true;
        }
        plugin.getMessages().send(player, "team-level-status", Map.of(
                "{team}", team.getName(),
                "{level}", String.valueOf(team.getTeamLevel()),
                "{current}", String.valueOf(team.getTeamXp()),
                "{required}", String.valueOf(plugin.getTeamLevelService().requiredXp(team)),
                "{rank}", String.valueOf(plugin.getTeamService().levelRank(team))
        ));
        return true;
    }

    private boolean levelTop(Player player) {
        plugin.getMessages().send(player, "team-level-top-header");
        int rank = 1;
        for (Team team : plugin.getTeamService().sortedByLevel().stream().limit(10).toList()) {
            plugin.getMessages().send(player, "team-level-top-entry", Map.of(
                    "{rank}", String.valueOf(rank),
                    "{team}", team.getName(),
                    "{level}", String.valueOf(team.getTeamLevel()),
                    "{current}", String.valueOf(team.getTeamXp()),
                    "{required}", String.valueOf(plugin.getTeamLevelService().requiredXp(team))
            ));
            rank++;
        }
        return true;
    }

    private boolean balance(Player player) {
        Team team = requireTeam(player);
        if (team == null) {
            return true;
        }
        if (!plugin.getEconomyService().isEnabled()) {
            plugin.getMessages().send(player, "economy-disabled");
            return true;
        }
        player.sendMessage(plugin.getMessages().renderTemplate("{prefix}{white}Team bank: {theme}" + Text.money(team.getBalance()), Map.of()));
        return true;
    }

    private boolean balanceTop(Player player) {
        if (!plugin.getEconomyService().isEnabled()) {
            plugin.getMessages().send(player, "economy-disabled");
            return true;
        }
        player.sendMessage(plugin.getMessages().renderTemplate("{prefix}{theme}Top Team Balances", Map.of()));
        int rank = 1;
        for (Team team : plugin.getTeamService().sortedByBalance().stream().limit(10).toList()) {
            player.sendMessage(plugin.getMessages().renderTemplate("{muted}#" + rank + " {white}" + team.getName() + " {muted}- {theme}" + Text.money(team.getBalance()), Map.of()));
            rank++;
        }
        return true;
    }

    private boolean deposit(Player player, String[] args) throws SQLException {
        Team team = requireTeam(player);
        if (team == null) {
            return true;
        }
        if (!plugin.getEconomyService().isEnabled()) {
            plugin.getMessages().send(player, "economy-disabled");
            return true;
        }
        if (!plugin.getTeamService().can(team, player.getUniqueId(), TeamAction.DEPOSIT)) {
            plugin.getMessages().send(player, "no-permission-role");
            return true;
        }
        if (args.length < 2) {
            plugin.getMessages().send(player, "usage-deposit");
            return true;
        }
        Double amount = parseAmount(player, args[1]);
        if (amount == null) {
            return true;
        }
        return depositAmount(player, team, amount);
    }

    public boolean depositAmount(Player player, Team team, double amount) {
        if (!plugin.getEconomyService().isEnabled()) {
            plugin.getMessages().send(player, "economy-disabled");
            return true;
        }
        if (!plugin.getTeamService().can(team, player.getUniqueId(), TeamAction.DEPOSIT)) {
            plugin.getMessages().send(player, "no-permission-role");
            return true;
        }
        if (!Double.isFinite(amount) || amount <= 0) {
            plugin.getMessages().send(player, "amount-positive");
            return true;
        }
        if (plugin.getEconomyService().balance(player) < amount || !plugin.getEconomyService().withdraw(player, amount)) {
            plugin.getMessages().send(player, "insufficient-funds");
            return true;
        }
        try {
            plugin.getTeamService().deposit(team, amount);
        } catch (SQLException exception) {
            if (plugin.getEconomyService().deposit(player, amount)) {
                plugin.getMessages().send(player, "deposit-save-failed-refunded");
            } else {
                plugin.getLogger().log(Level.WARNING, "Failed to refund team deposit after database save failed for " + player.getName() + ".", exception);
                plugin.getMessages().send(player, "deposit-save-failed-refund-failed");
            }
            return true;
        }
        plugin.getMessages().send(player, "deposit-success", Map.of("{amount}", Text.money(amount)));
        return true;
    }

    private boolean withdraw(Player player, String[] args) throws SQLException {
        Team team = requireTeam(player);
        if (team == null) {
            return true;
        }
        if (!plugin.getEconomyService().isEnabled()) {
            plugin.getMessages().send(player, "economy-disabled");
            return true;
        }
        if (!plugin.getTeamService().can(team, player.getUniqueId(), TeamAction.WITHDRAW)) {
            plugin.getMessages().send(player, "no-permission-role");
            return true;
        }
        if (args.length < 2) {
            plugin.getMessages().send(player, "usage-withdraw");
            return true;
        }
        Double amount = parseAmount(player, args[1]);
        if (amount == null) {
            return true;
        }
        return withdrawAmount(player, team, amount);
    }

    public boolean withdrawAmount(Player player, Team team, double amount) throws SQLException {
        if (!plugin.getEconomyService().isEnabled()) {
            plugin.getMessages().send(player, "economy-disabled");
            return true;
        }
        if (!plugin.getTeamService().can(team, player.getUniqueId(), TeamAction.WITHDRAW)) {
            plugin.getMessages().send(player, "no-permission-role");
            return true;
        }
        if (!Double.isFinite(amount) || amount <= 0) {
            plugin.getMessages().send(player, "amount-positive");
            return true;
        }
        if (team.getBalance() < amount) {
            plugin.getMessages().send(player, "insufficient-team-funds");
            return true;
        }
        plugin.getTeamService().withdraw(team, amount);
        if (!plugin.getEconomyService().deposit(player, amount)) {
            try {
                plugin.getTeamService().deposit(team, amount);
            } catch (SQLException exception) {
                plugin.getLogger().log(Level.WARNING, "Failed to restore team balance after economy payout failed for " + player.getName() + ".", exception);
            }
            plugin.getMessages().send(player, "economy-transaction-failed");
            return true;
        }
        plugin.getMessages().send(player, "withdraw-success", Map.of("{amount}", Text.money(amount)));
        return true;
    }

    private Double parseAmount(Player player, String input) {
        var parsed = LargeNumberParser.parseDouble(input);
        if (parsed.isEmpty()) {
            plugin.getMessages().send(player, "money-invalid");
            return null;
        }
        return parsed.getAsDouble();
    }

    private Team requireTeam(Player player) {
        Optional<Team> optionalTeam = plugin.getTeamService().teamOf(player.getUniqueId());
        if (optionalTeam.isEmpty()) {
            plugin.getMessages().send(player, "not-in-team");
            return null;
        }
        return optionalTeam.get();
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return SUBCOMMANDS.stream().filter(value -> value.startsWith(args[0].toLowerCase(Locale.ROOT))).toList();
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("join") || args[0].equalsIgnoreCase("info") || args[0].equalsIgnoreCase("ally") || args[0].equalsIgnoreCase("unally"))) {
            List<String> suggestions = new ArrayList<>(plugin.getTeamService().teamNames().stream()
                    .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(args[1].toLowerCase(Locale.ROOT)))
                    .toList());
            if (args[0].equalsIgnoreCase("info")) {
                for (Player online : Bukkit.getOnlinePlayers()) {
                    if (online.getName().toLowerCase(Locale.ROOT).startsWith(args[1].toLowerCase(Locale.ROOT)) && !suggestions.contains(online.getName())) {
                        suggestions.add(online.getName());
                    }
                }
            }
            return suggestions;
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("revoke") && sender instanceof Player player) {
            Team team = plugin.getTeamService().teamOf(player.getUniqueId()).orElse(null);
            if (team == null) {
                return List.of();
            }
            return plugin.getTeamService().teamInvites(team).stream()
                    .map(invite -> offlineName(Bukkit.getOfflinePlayer(invite.playerId())))
                    .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(args[1].toLowerCase(Locale.ROOT)))
                    .toList();
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("invite")) {
            List<String> names = new ArrayList<>();
            for (Player online : Bukkit.getOnlinePlayers()) {
                names.add(online.getName());
            }
            return names.stream().filter(name -> name.toLowerCase(Locale.ROOT).startsWith(args[1].toLowerCase(Locale.ROOT))).toList();
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("promote") || args[0].equalsIgnoreCase("demote") || args[0].equalsIgnoreCase("kick")) && sender instanceof Player player) {
            Team team = plugin.getTeamService().teamOf(player.getUniqueId()).orElse(null);
            if (team == null) {
                return List.of();
            }
            List<UUID> targets;
            if (args[0].equalsIgnoreCase("promote")) {
                targets = new ArrayList<>(team.getMembers());
            } else if (args[0].equalsIgnoreCase("demote")) {
                targets = new ArrayList<>(team.getAdmins());
            } else {
                targets = new ArrayList<>(team.getAdmins());
                targets.addAll(team.getMembers());
            }
            return targetSuggestions(targets, args[1]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("warp") && sender instanceof Player player) {
            Team team = plugin.getTeamService().teamOf(player.getUniqueId()).orElse(null);
            if (team == null) {
                return List.of();
            }
            return team.getWarps().keySet().stream().filter(name -> name.startsWith(args[1].toLowerCase(Locale.ROOT))).toList();
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("pvp")) {
            return List.of("on", "off").stream()
                    .filter(option -> option.startsWith(args[1].toLowerCase(Locale.ROOT)))
                    .toList();
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("level")) {
            return List.of("top").stream()
                    .filter(option -> option.startsWith(args[1].toLowerCase(Locale.ROOT)))
                    .toList();
        }
        return List.of();
    }

    private void notifyTeamLeaders(Team team, String messageKey, Map<String, String> replacements) {
        if (team.hasOwner()) {
            Player owner = Bukkit.getPlayer(team.getOwnerId());
            if (owner != null) {
                plugin.getMessages().send(owner, messageKey, replacements);
            }
        }
        for (java.util.UUID adminId : team.getAdmins()) {
            Player admin = Bukkit.getPlayer(adminId);
            if (admin != null) {
                plugin.getMessages().send(admin, messageKey, replacements);
            }
        }
    }

    private void broadcastTeamMessage(Team team, String messageKey) {
        for (UUID memberId : plugin.getTeamService().allMembers(team)) {
            Player target = Bukkit.getPlayer(memberId);
            if (target != null) {
                plugin.getMessages().send(target, messageKey);
            }
        }
    }

    private boolean canManageInvites(Team team, Player player) {
        TeamRole role = team.roleOf(player.getUniqueId());
        return role == TeamRole.OWNER || role == TeamRole.ADMIN;
    }

    private boolean canManageTeamPvp(Team team, Player player) {
        TeamRole role = team.roleOf(player.getUniqueId());
        return role == TeamRole.OWNER || role == TeamRole.ADMIN;
    }

    private boolean canManageUpgrades(Team team, Player player) {
        TeamRole role = team.roleOf(player.getUniqueId());
        return role == TeamRole.OWNER || role == TeamRole.ADMIN;
    }

    private Boolean parseToggleState(String input) {
        return switch (input.toLowerCase(Locale.ROOT)) {
            case "on", "enable", "enabled", "true" -> true;
            case "off", "disable", "disabled", "false" -> false;
            default -> null;
        };
    }

    private String offlineName(OfflinePlayer player) {
        return player.getName() == null ? player.getUniqueId().toString().substring(0, 8) : player.getName();
    }

    private Optional<MemberTarget> resolveTeamMember(Team team, String input) {
        String normalizedInput = normalizePlayerLookup(input);
        if (normalizedInput.isEmpty()) {
            return Optional.empty();
        }
        List<MemberTarget> targets = plugin.getTeamService().allMembers(team).stream()
                .map(id -> new MemberTarget(id, team.roleOf(id), playerName(id)))
                .toList();
        return targets.stream()
                .filter(target -> exactMemberNameMatches(target.name(), normalizedInput))
                .findFirst()
                .or(() -> targets.stream()
                        .filter(target -> bedrockAliasMatches(target.name(), normalizedInput))
                        .findFirst());
    }

    private Optional<MemberTarget> resolveTeamInvite(Team team, String input) {
        String normalizedInput = normalizePlayerLookup(input);
        if (normalizedInput.isEmpty()) {
            return Optional.empty();
        }
        List<MemberTarget> targets = plugin.getTeamService().teamInvites(team).stream()
                .map(invite -> new MemberTarget(invite.playerId(), null, playerName(invite.playerId())))
                .toList();
        return targets.stream()
                .filter(target -> exactMemberNameMatches(target.name(), normalizedInput))
                .findFirst()
                .or(() -> targets.stream()
                        .filter(target -> bedrockAliasMatches(target.name(), normalizedInput))
                        .findFirst());
    }

    private Player resolveOnlinePlayer(String input) {
        String normalizedInput = normalizePlayerLookup(input);
        if (normalizedInput.isEmpty()) {
            return null;
        }
        Player exact = Bukkit.getPlayerExact(input);
        if (exact != null) {
            return exact;
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (exactMemberNameMatches(player.getName(), normalizedInput)) {
                return player;
            }
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (bedrockAliasMatches(player.getName(), normalizedInput)) {
                return player;
            }
        }
        return null;
    }

    private List<String> targetSuggestions(Collection<UUID> targets, String input) {
        String normalizedInput = normalizePlayerLookup(input);
        return targets.stream()
                .map(this::playerName)
                .filter(name -> normalizedInput.isEmpty() || memberNameMatchesPrefix(name, normalizedInput))
                .toList();
    }

    private boolean exactMemberNameMatches(String name, String normalizedInput) {
        return normalizePlayerLookup(name).equals(normalizedInput);
    }

    private boolean bedrockAliasMatches(String name, String normalizedInput) {
        return withoutBedrockPrefix(normalizePlayerLookup(name)).equals(withoutBedrockPrefix(normalizedInput));
    }

    private boolean memberNameMatchesPrefix(String name, String normalizedInput) {
        String normalizedName = normalizePlayerLookup(name);
        return normalizedName.startsWith(normalizedInput) || withoutBedrockPrefix(normalizedName).startsWith(withoutBedrockPrefix(normalizedInput));
    }

    private String normalizePlayerLookup(String input) {
        return input == null ? "" : input.trim().toLowerCase(Locale.ROOT);
    }

    private String withoutBedrockPrefix(String input) {
        return input.startsWith(".") ? input.substring(1) : input;
    }

    private String playerName(UUID playerId) {
        Player online = Bukkit.getPlayer(playerId);
        if (online != null) {
            return online.getName();
        }
        OfflinePlayer offline = Bukkit.getOfflinePlayer(playerId);
        return offlineName(offline);
    }

    private record MemberTarget(UUID id, TeamRole role, String name) {
    }
}
