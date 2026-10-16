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
        if (!sender.hasPermission("foteams.use")) {
            plugin.sendMessage(player, "no-permission");
            playCommandError(player);
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
                default -> {
                    playCommandError(player);
                    yield help(player);
                }
            };
        } catch (SQLException exception) {
            plugin.getLogger().log(Level.WARNING, "Team command failed.", exception);
            plugin.sendMessage(player, "command-failed");
            playCommandError(player);
            return true;
        }
    }

    private boolean help(Player player) {
        plugin.sendMessageList(player, "help-player");
        return true;
    }

    private boolean create(Player player, String[] args) throws SQLException {
        if (plugin.getTeamService().teamOf(player.getUniqueId()).isPresent()) {
            plugin.sendMessage(player, "already-in-team");
            playCommandError(player);
            return true;
        }
        if (args.length < 2) {
            plugin.sendMessage(player, "usage-create");
            playCommandError(player);
            return true;
        }
        String name = String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length));
        if (!validateTeamName(player, name)) {
            return true;
        }
        if (plugin.getTeamService().isNameTaken(name)) {
            plugin.sendMessage(player, "team-name-taken");
            playCommandError(player);
            return true;
        }
        Team team = plugin.getTeamService().createTeam(player, name);
        plugin.getSounds().play(player, "team.created");
        plugin.sendMessage(player, "team-created", Map.of("{team}", team.getName()));
        return true;
    }

    private boolean validateTeamName(Player player, String name) {
        if (!plugin.getTeamService().isValidTeamNameCharacters(name)) {
            plugin.sendMessage(player, "name-invalid");
            playCommandError(player);
            return false;
        }
        if (!plugin.getTeamService().isValidTeamNameLength(name)) {
            plugin.sendMessage(player, "name-too-long", Map.of("{max}", String.valueOf(plugin.getTeamService().maxNameLength())));
            playCommandError(player);
            return false;
        }
        if (plugin.getSwearFilterService().containsBlockedWord(name)) {
            plugin.sendMessage(player, "blocked-word");
            playCommandError(player);
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
            plugin.sendMessage(player, "no-permission-role");
            playCommandError(player);
            return true;
        }
        if (args.length < 2) {
            plugin.sendMessage(player, "usage-invite");
            playCommandError(player);
            return true;
        }
        Player target = resolveOnlinePlayer(args[1]);
        if (target == null) {
            plugin.sendMessage(player, "player-not-found");
            playCommandError(player);
            return true;
        }
        if (plugin.getTeamService().teamOf(target.getUniqueId()).isPresent()) {
            plugin.sendMessage(player, "player-already-in-team");
            playCommandError(player);
            return true;
        }
        plugin.getTeamService().invite(team, player.getUniqueId(), target.getUniqueId());
        plugin.getSounds().play(player, "team.invite");
        plugin.sendMessage(player, "invite-sent", Map.of("{player}", target.getName(), "{team}", team.getName()));
        plugin.sendMessage(target, "invite-received", Map.of("{team}", team.getName()));
        plugin.getSounds().play(target, "team.invited");
        return true;
    }

    private boolean join(Player player, String[] args) throws SQLException {
        if (plugin.getTeamService().teamOf(player.getUniqueId()).isPresent()) {
            plugin.sendMessage(player, "already-in-team");
            playCommandError(player);
            return true;
        }
        if (args.length < 2) {
            plugin.sendMessage(player, "usage-join");
            playCommandError(player);
            return true;
        }
        String teamName = String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length));
        Team team = plugin.getTeamService().byName(teamName).orElse(null);
        if (team == null) {
            plugin.sendMessage(player, "team-not-found");
            playCommandError(player);
            return true;
        }
        if (!plugin.getTeamService().hasInvite(player.getUniqueId(), team.getId())) {
            plugin.sendMessage(player, "no-invite");
            playCommandError(player);
            return true;
        }
        if (plugin.getTeamService().isFull(team)) {
            plugin.sendMessage(player, "team-full");
            playCommandError(player);
            return true;
        }
        plugin.getTeamService().join(player, team);
        plugin.getSounds().play(player, "team.joined");
        for (java.util.UUID memberId : plugin.getTeamService().allMembers(team)) {
            Player target = Bukkit.getPlayer(memberId);
            if (target != null) {
                plugin.sendMessage(target, "member-joined", Map.of("{player}", player.getName(), "{team}", team.getName()));
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
            plugin.sendMessage(player, "owner-use-disband");
            playCommandError(player);
            return true;
        }
        try {
            plugin.getTeamService().leave(player);
            plugin.getSounds().play(player, "team.left");
            plugin.sendMessage(player, "leave-success", Map.of("{team}", team.getName()));
        } catch (IllegalStateException exception) {
            plugin.sendMessage(player, "leave-failed");
            playCommandError(player);
        }
        return true;
    }

    private boolean invites(Player player) {
        Team team = requireTeam(player);
        if (team == null) {
            return true;
        }
        if (!canManageInvites(team, player)) {
            plugin.sendMessage(player, "no-permission-role");
            playCommandError(player);
            return true;
        }
        List<TeamInvite> invites = plugin.getTeamService().teamInvites(team);
        if (invites.isEmpty()) {
            plugin.sendMessage(player, "no-pending-invites");
            return true;
        }
        plugin.sendMessage(player, "invites-header", Map.of("{team}", team.getName()));
        for (TeamInvite invite : invites) {
            OfflinePlayer target = Bukkit.getOfflinePlayer(invite.playerId());
            plugin.sendMessage(player, "invites-entry", Map.of("{player}", offlineName(target)));
        }
        return true;
    }

    private boolean revoke(Player player, String[] args) throws SQLException {
        Team team = requireTeam(player);
        if (team == null) {
            return true;
        }
        if (!canManageInvites(team, player)) {
            plugin.sendMessage(player, "no-permission-role");
            playCommandError(player);
            return true;
        }
        if (args.length < 2) {
            plugin.sendMessage(player, "usage-revoke");
            playCommandError(player);
            return true;
        }
        MemberTarget target = resolveTeamInvite(team, args[1]).orElse(null);
        if (target == null || !plugin.getTeamService().revokeInvite(team, target.id())) {
            plugin.sendMessage(player, "no-invite");
            playCommandError(player);
            return true;
        }
        plugin.sendMessage(player, "invite-revoked", Map.of("{player}", target.name(), "{team}", team.getName()));
        plugin.getSounds().play(player, "team.invite-revoked");
        Player online = Bukkit.getPlayer(target.id());
        if (online != null) {
            plugin.sendMessage(online, "invite-revoked-target", Map.of("{team}", team.getName()));
        }
        return true;
    }

    private boolean disband(Player player) {
        Team team = requireTeam(player);
        if (team == null) {
            return true;
        }
        if (!plugin.getTeamService().can(team, player.getUniqueId(), TeamAction.DISBAND)) {
            plugin.sendMessage(player, "no-permission-role");
            playCommandError(player);
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
            plugin.sendMessage(player, "no-permission-role");
            playCommandError(player);
            return true;
        }
        if (plugin.getConfig().getBoolean("team-pvp-force-disable-all", false)) {
            plugin.sendMessage(player, "pvp-protection-locked");
            playCommandError(player);
            return true;
        }
        boolean enabled;
        if (args.length > 1) {
            Boolean parsed = parseToggleState(args[1]);
            if (parsed == null) {
                plugin.sendMessage(player, "usage-pvp");
                playCommandError(player);
                return true;
            }
            enabled = parsed;
        } else {
            enabled = !team.isTeamPvpProtectionEnabled();
        }
        if (!plugin.getTeamService().setTeamPvpProtection(team, enabled)) {
            plugin.sendMessage(player, enabled ? "pvp-protection-already-enabled" : "pvp-protection-already-disabled");
            playCommandError(player);
            return true;
        }
        broadcastTeamMessage(team, enabled ? "pvp-protection-enabled" : "pvp-protection-disabled");
        plugin.getSounds().play(player, "team.settings-saved");
        return true;
    }

    private boolean kick(Player player, String[] args) throws SQLException {
        Team team = requireTeam(player);
        if (team == null) {
            return true;
        }
        if (!plugin.getTeamService().can(team, player.getUniqueId(), TeamAction.KICK)) {
            plugin.sendMessage(player, "no-permission-role");
            playCommandError(player);
            return true;
        }
        if (args.length < 2) {
            plugin.sendMessage(player, "usage-kick");
            playCommandError(player);
            return true;
        }
        MemberTarget target = resolveTeamMember(team, args[1]).orElse(null);
        if (target == null) {
            plugin.sendMessage(player, "player-not-found");
            playCommandError(player);
            return true;
        }
        TeamRole role = target.role();
        if (role == TeamRole.OWNER) {
            plugin.sendMessage(player, "member-owner-protected");
            playCommandError(player);
            return true;
        }
        if (role == null) {
            plugin.sendMessage(player, "kick-target-invalid");
            playCommandError(player);
            return true;
        }
        plugin.getTeamService().kick(team, target.id());
        plugin.getSounds().play(player, "team.member-updated");
        plugin.sendMessage(player, "kick-success", Map.of("{player}", target.name()));
        Player targetOnline = Bukkit.getPlayer(target.id());
        if (targetOnline != null) {
            plugin.sendMessage(targetOnline, "kick-target", Map.of("{team}", team.getName()));
        }
        return true;
    }

    private boolean promote(Player player, String[] args) throws SQLException {
        Team team = requireTeam(player);
        if (team == null) {
            return true;
        }
        if (!plugin.getTeamService().can(team, player.getUniqueId(), TeamAction.PROMOTE)) {
            plugin.sendMessage(player, "no-permission-role");
            playCommandError(player);
            return true;
        }
        if (args.length < 2) {
            plugin.sendMessage(player, "usage-promote");
            playCommandError(player);
            return true;
        }
        MemberTarget target = resolveTeamMember(team, args[1]).orElse(null);
        if (target == null) {
            plugin.sendMessage(player, "player-not-found");
            playCommandError(player);
            return true;
        }
        TeamRole role = target.role();
        if (role == TeamRole.OWNER) {
            plugin.sendMessage(player, "member-owner-protected");
            playCommandError(player);
            return true;
        }
        if (role != TeamRole.MEMBER) {
            plugin.sendMessage(player, "promote-target-invalid");
            playCommandError(player);
            return true;
        }
        if (!plugin.getTeamService().promote(team, target.id())) {
            plugin.sendMessage(player, "admin-limit", Map.of("{max}", String.valueOf(plugin.getTeamService().maxAdmins())));
            playCommandError(player);
            return true;
        }
        plugin.sendMessage(player, "promote-success", Map.of("{player}", target.name(), "{role}",
                plugin.publicGui().label("roles.admin", "")));
        plugin.getSounds().play(player, "team.member-updated");
        return true;
    }

    private boolean demote(Player player, String[] args) throws SQLException {
        Team team = requireTeam(player);
        if (team == null) {
            return true;
        }
        if (!plugin.getTeamService().can(team, player.getUniqueId(), TeamAction.DEMOTE)) {
            plugin.sendMessage(player, "no-permission-role");
            playCommandError(player);
            return true;
        }
        if (args.length < 2) {
            plugin.sendMessage(player, "usage-demote");
            playCommandError(player);
            return true;
        }
        MemberTarget target = resolveTeamMember(team, args[1]).orElse(null);
        if (target == null) {
            plugin.sendMessage(player, "player-not-found");
            playCommandError(player);
            return true;
        }
        TeamRole role = target.role();
        if (role == TeamRole.OWNER) {
            plugin.sendMessage(player, "member-owner-protected");
            playCommandError(player);
            return true;
        }
        if (role != TeamRole.ADMIN) {
            plugin.sendMessage(player, "demote-target-invalid");
            playCommandError(player);
            return true;
        }
        plugin.getTeamService().demote(team, target.id());
        plugin.getSounds().play(player, "team.member-updated");
        plugin.sendMessage(player, "demote-success", Map.of("{player}", target.name(), "{role}",
                plugin.publicGui().label("roles.member", "")));
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
            plugin.sendMessage(player, "no-permission-role");
            playCommandError(player);
            return true;
        }
        if (args.length < 2) {
            plugin.sendMessage(player, "usage-ally");
            playCommandError(player);
            return true;
        }
        String teamName = String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length));
        Team other = plugin.getTeamService().byName(teamName).orElse(null);
        if (other == null || other.getId() == team.getId()) {
            plugin.sendMessage(player, "team-not-found");
            playCommandError(player);
            return true;
        }
        boolean accepted = plugin.getTeamService().requestOrAcceptAlly(team, other);
        plugin.getSounds().play(player, "team.relation-updated");
        plugin.sendMessage(player, accepted ? "ally-request-accepted" : "ally-request-sent", Map.of("{other}", other.getName()));
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
            plugin.sendMessage(player, "no-permission-role");
            playCommandError(player);
            return true;
        }
        if (args.length < 2) {
            plugin.sendMessage(player, "usage-unally");
            playCommandError(player);
            return true;
        }
        String teamName = String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length));
        Team other = plugin.getTeamService().byName(teamName).orElse(null);
        if (other == null || other.getId() == team.getId()) {
            plugin.sendMessage(player, "team-not-found");
            playCommandError(player);
            return true;
        }
        if (plugin.getTeamService().relation(team, other) != RelationType.ALLY) {
            plugin.sendMessage(player, "not-allied", Map.of("{other}", other.getName()));
            playCommandError(player);
            return true;
        }
        plugin.getTeamService().clearRelation(team, other);
        plugin.getSounds().play(player, "team.relation-updated");
        plugin.sendMessage(player, "unally-success", Map.of("{other}", other.getName()));
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
            plugin.sendMessage(player, mode == ChatMode.TEAM ? "chat-disabled" : "allychat-disabled");
            return;
        }
        plugin.getTeamChatService().setMode(player.getUniqueId(), mode);
        plugin.sendMessage(player, mode == ChatMode.TEAM ? "chat-enabled" : "allychat-enabled");
    }

    private boolean home(Player player) {
        Team team = requireTeam(player);
        if (team == null) {
            return true;
        }
        if (!plugin.getTeamService().can(team, player.getUniqueId(), TeamAction.USE_HOME)) {
            plugin.sendMessage(player, "no-permission-role");
            playCommandError(player);
            return true;
        }
        if (team.getHome() == null) {
            plugin.sendMessage(player, "home-missing");
            playCommandError(player);
            return true;
        }
        plugin.getTeleportDelayService().start(player, team.getHome(), "home", () -> plugin.getSounds().play(player, "team.teleport"));
        return true;
    }

    private boolean setHome(Player player) throws SQLException {
        Team team = requireTeam(player);
        if (team == null) {
            return true;
        }
        if (!plugin.getTeamService().can(team, player.getUniqueId(), TeamAction.SET_HOME)) {
            plugin.sendMessage(player, "no-permission-role");
            playCommandError(player);
            return true;
        }
        plugin.getTeamService().setHome(team, player.getLocation());
        plugin.getSounds().play(player, "team.home-set");
        plugin.sendMessage(player, "home-set");
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
            plugin.sendMessage(player, "no-permission-role");
            playCommandError(player);
            return true;
        }
        String warpName = args[1].toLowerCase(Locale.ROOT);
        var location = team.getWarps().get(warpName);
        if (location == null) {
            plugin.sendMessage(player, "warp-missing");
            playCommandError(player);
            return true;
        }
        if (plugin.getTeamService().warpRequiresPassword(team, warpName)) {
            if (args.length < 3) {
                plugin.sendMessage(player, "warp-password-required");
                playCommandError(player);
                return true;
            }
            if (!plugin.getTeamService().matchesWarpPassword(team, warpName, args[2])) {
                plugin.sendMessage(player, "warp-password-wrong");
                playCommandError(player);
                return true;
            }
        }
        plugin.getTeleportDelayService().start(player, location, warpName, () -> plugin.getSounds().play(player, "team.teleport"));
        return true;
    }

    private boolean setWarp(Player player, String[] args) throws SQLException {
        Team team = requireTeam(player);
        if (team == null) {
            return true;
        }
        if (!plugin.getTeamService().can(team, player.getUniqueId(), TeamAction.SET_WARP)) {
            plugin.sendMessage(player, "no-permission-role");
            playCommandError(player);
            return true;
        }
        if (args.length < 2) {
            plugin.sendMessage(player, "usage-setwarp");
            playCommandError(player);
            return true;
        }
        String warpName = args[1].toLowerCase(Locale.ROOT);
        if (warpName.isBlank()) {
            plugin.sendMessage(player, "usage-setwarp");
            playCommandError(player);
            return true;
        }
        String password = args.length >= 3 ? args[2] : null;
        if (!plugin.getTeamService().canAddWarp(team, warpName)) {
            plugin.sendMessage(player, "warp-limit", Map.of("{max}", String.valueOf(plugin.getTeamService().maxWarps())));
            playCommandError(player);
            return true;
        }
        plugin.getTeamService().setWarp(team, warpName, player.getLocation(), password);
        plugin.getSounds().play(player, "team.warp-set");
        plugin.sendMessage(player, "warp-set", Map.of("{warp}", warpName));
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
                plugin.sendMessage(player, plugin.getTeamService().hasKnownPlayerName(query) ? "target-not-in-team" : "team-not-found");
                playCommandError(player);
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
            plugin.sendMessage(player, "no-permission-role");
            playCommandError(player);
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
        plugin.sendMessage(player, "score-top-header");
        int rank = 1;
        for (Team team : plugin.getTeamService().sortedByScore().stream().limit(10).toList()) {
            plugin.sendMessage(player, "score-top-entry", Map.of("{rank}", String.valueOf(rank),
                    "{team}", team.getName(), "{score}", String.valueOf(team.getScore())));
            rank++;
        }
        return true;
    }

    private boolean level(Player player, String[] args) {
        if (!plugin.getTeamLevelService().isEnabled()) {
            plugin.getTeamLevelService().sendDisabledMessage(player);
            playCommandError(player);
            return true;
        }
        if (args.length >= 2 && args[1].equalsIgnoreCase("top")) {
            return levelTop(player);
        }
        Team team = requireTeam(player);
        if (team == null) {
            return true;
        }
        plugin.sendMessage(player, "team-level-status", Map.of(
                "{team}", team.getName(),
                "{level}", String.valueOf(team.getTeamLevel()),
                "{current}", String.valueOf(team.getTeamXp()),
                "{required}", String.valueOf(plugin.getTeamLevelService().requiredXp(team)),
                "{rank}", String.valueOf(plugin.getTeamService().levelRank(team))
        ));
        return true;
    }

    private boolean levelTop(Player player) {
        plugin.sendMessage(player, "team-level-top-header");
        int rank = 1;
        for (Team team : plugin.getTeamService().sortedByLevel().stream().limit(10).toList()) {
            plugin.sendMessage(player, "team-level-top-entry", Map.of(
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
        if (!plugin.getEconomyService().isAvailable()) {
            plugin.sendMessage(player, "economy-disabled");
            playCommandError(player);
            return true;
        }
        plugin.sendMessage(player, "bank-balance", Map.of("{balance}", Text.money(team.getBalance())));
        return true;
    }

    private boolean balanceTop(Player player) {
        if (!plugin.getEconomyService().isAvailable()) {
            plugin.sendMessage(player, "economy-disabled");
            playCommandError(player);
            return true;
        }
        plugin.sendMessage(player, "balance-top-header");
        int rank = 1;
        for (Team team : plugin.getTeamService().sortedByBalance().stream().limit(10).toList()) {
            plugin.sendMessage(player, "balance-top-entry", Map.of("{rank}", String.valueOf(rank),
                    "{team}", team.getName(), "{balance}", Text.money(team.getBalance())));
            rank++;
        }
        return true;
    }

    private boolean deposit(Player player, String[] args) throws SQLException {
        Team team = requireTeam(player);
        if (team == null) {
            return true;
        }
        if (!plugin.getEconomyService().isAvailable()) {
            plugin.sendMessage(player, "economy-disabled");
            playCommandError(player);
            return true;
        }
        if (!plugin.getTeamService().can(team, player.getUniqueId(), TeamAction.DEPOSIT)) {
            plugin.sendMessage(player, "no-permission-role");
            playCommandError(player);
            return true;
        }
        if (args.length < 2) {
            plugin.sendMessage(player, "usage-deposit");
            playCommandError(player);
            return true;
        }
        Double amount = parseAmount(player, args[1]);
        if (amount == null) {
            return true;
        }
        return depositAmount(player, team, amount);
    }

    public boolean depositAmount(Player player, Team team, double amount) {
        if (!plugin.getEconomyService().isAvailable()) {
            plugin.sendMessage(player, "economy-disabled");
            playCommandError(player);
            return true;
        }
        if (!plugin.getTeamService().can(team, player.getUniqueId(), TeamAction.DEPOSIT)) {
            plugin.sendMessage(player, "no-permission-role");
            playCommandError(player);
            return true;
        }
        if (!Double.isFinite(amount) || amount <= 0) {
            plugin.sendMessage(player, "amount-positive");
            playCommandError(player);
            return true;
        }
        if (plugin.getEconomyService().balance(player) < amount || !plugin.getEconomyService().withdraw(player, amount)) {
            plugin.sendMessage(player, "insufficient-funds");
            playCommandError(player);
            return true;
        }
        try {
            plugin.getTeamService().deposit(team, amount);
        } catch (SQLException exception) {
            if (plugin.getEconomyService().deposit(player, amount)) {
                plugin.sendMessage(player, "deposit-save-failed-refunded");
                playCommandError(player);
            } else {
                plugin.getLogger().log(Level.WARNING, "Failed to refund team deposit after database save failed for " + player.getName() + ".", exception);
                plugin.sendMessage(player, "deposit-save-failed-refund-failed");
                playCommandError(player);
            }
            return true;
        }
        plugin.sendMessage(player, "deposit-success", Map.of("{amount}", Text.money(amount)));
        return true;
    }

    private boolean withdraw(Player player, String[] args) throws SQLException {
        Team team = requireTeam(player);
        if (team == null) {
            return true;
        }
        if (!plugin.getEconomyService().isAvailable()) {
            plugin.sendMessage(player, "economy-disabled");
            playCommandError(player);
            return true;
        }
        if (!plugin.getTeamService().can(team, player.getUniqueId(), TeamAction.WITHDRAW)) {
            plugin.sendMessage(player, "no-permission-role");
            playCommandError(player);
            return true;
        }
        if (args.length < 2) {
            plugin.sendMessage(player, "usage-withdraw");
            playCommandError(player);
            return true;
        }
        Double amount = parseAmount(player, args[1]);
        if (amount == null) {
            return true;
        }
        return withdrawAmount(player, team, amount);
    }

    public boolean withdrawAmount(Player player, Team team, double amount) throws SQLException {
        if (!plugin.getEconomyService().isAvailable()) {
            plugin.sendMessage(player, "economy-disabled");
            playCommandError(player);
            return true;
        }
        if (!plugin.getTeamService().can(team, player.getUniqueId(), TeamAction.WITHDRAW)) {
            plugin.sendMessage(player, "no-permission-role");
            playCommandError(player);
            return true;
        }
        if (!Double.isFinite(amount) || amount <= 0) {
            plugin.sendMessage(player, "amount-positive");
            playCommandError(player);
            return true;
        }
        if (team.getBalance() < amount) {
            plugin.sendMessage(player, "insufficient-team-funds");
            playCommandError(player);
            return true;
        }
        plugin.getTeamService().withdraw(team, amount);
        if (!plugin.getEconomyService().deposit(player, amount)) {
            try {
                plugin.getTeamService().deposit(team, amount);
            } catch (SQLException exception) {
                plugin.getLogger().log(Level.WARNING, "Failed to restore team balance after economy payout failed for " + player.getName() + ".", exception);
            }
            plugin.sendMessage(player, "economy-transaction-failed");
            playCommandError(player);
            return true;
        }
        plugin.sendMessage(player, "withdraw-success", Map.of("{amount}", Text.money(amount)));
        return true;
    }

    private Double parseAmount(Player player, String input) {
        var parsed = LargeNumberParser.parseDouble(input);
        if (parsed.isEmpty()) {
            plugin.sendMessage(player, "money-invalid");
            playCommandError(player);
            return null;
        }
        return parsed.getAsDouble();
    }

    private Team requireTeam(Player player) {
        Optional<Team> optionalTeam = plugin.getTeamService().teamOf(player.getUniqueId());
        if (optionalTeam.isEmpty()) {
            plugin.sendMessage(player, "not-in-team");
            playCommandError(player);
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
                plugin.sendMessage(owner, messageKey, replacements);
            }
        }
        for (java.util.UUID adminId : team.getAdmins()) {
            Player admin = Bukkit.getPlayer(adminId);
            if (admin != null) {
                plugin.sendMessage(admin, messageKey, replacements);
            }
        }
    }

    private void broadcastTeamMessage(Team team, String messageKey) {
        for (UUID memberId : plugin.getTeamService().allMembers(team)) {
            Player target = Bukkit.getPlayer(memberId);
            if (target != null) {
                plugin.sendMessage(target, messageKey);
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

    private void playCommandError(Player player) {
        plugin.getAdminSounds().updateError(player);
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
