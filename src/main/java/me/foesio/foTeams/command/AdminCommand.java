package me.foesio.foTeams.command;

import me.foesio.foTeams.FoTeams;
import me.foesio.foTeams.model.Team;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;
import java.util.logging.Level;

public final class AdminCommand implements CommandExecutor, TabCompleter {
    private final FoTeams plugin;
    private static final List<String> SUBCOMMANDS = List.of("help", "reload", "version", "editor", "chatspy");

    public AdminCommand(FoTeams plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("foteams.admin")) {
            plugin.getMessages().send(sender, "no-permission");
            plugin.getAdminSounds().updateError(sender);
            return true;
        }
        if (args.length == 0) {
            plugin.getMessages().sendList(sender, "help-admin");
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        try {
            return switch (sub) {
                case "help" -> help(sender);
                case "reload" -> reload(sender);
                case "version" -> version(sender);
                case "editor" -> editor(sender, args);
                case "chatspy" -> chatspy(sender);
                default -> help(sender);
            };
        } catch (Exception exception) {
            plugin.getLogger().log(Level.WARNING, "Admin command failed: " + sub, exception);
            plugin.getMessages().send(sender, "invalid-input");
            plugin.getAdminSounds().updateError(sender);
            return true;
        }
    }

    private boolean help(CommandSender sender) {
        plugin.getMessages().sendList(sender, "help-admin");
        return true;
    }

    private boolean reload(CommandSender sender) {
        try {
            plugin.reloadPlugin();
            plugin.getMessages().send(sender, "reload-success");
            if (sender instanceof Player player) {
                plugin.getAdminSounds().reload(player);
            }
        } catch (Exception exception) {
            plugin.getLogger().log(Level.WARNING, "Reload command failed.", exception);
            if (sender instanceof Player player) {
                plugin.getAdminSounds().reloadError(player);
            }
            sender.sendMessage("Reload failed: " + exception.getMessage());
        }
        return true;
    }

    private boolean version(CommandSender sender) {
        plugin.getUpdates().checkAndSendVersion(sender);
        return true;
    }

    private boolean editor(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Players only.");
            return true;
        }
        if (args.length < 2) {
            plugin.getGuiService().openEditorHome(player);
            plugin.getMessages().send(player, "editor-opened");
            return true;
        }
        String teamName = String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length));
        Team team = plugin.getTeamService().byName(teamName).orElse(null);
        if (team == null) {
            plugin.getMessages().send(player, "team-not-found");
            plugin.getAdminSounds().updateError(player);
            return true;
        }
        plugin.getGuiService().openAdminEditor(player, team);
        return true;
    }

    private boolean chatspy(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Players only.");
            return true;
        }
        boolean enabled = plugin.getTeamChatService().toggleSpy(player.getUniqueId());
        plugin.getMessages().send(player, enabled ? "chatspy-enabled" : "chatspy-disabled");
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return SUBCOMMANDS.stream().filter(value -> value.startsWith(args[0].toLowerCase(Locale.ROOT))).toList();
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("editor")) {
            return plugin.getTeamService().teamNames().stream().filter(name -> name.toLowerCase(Locale.ROOT).startsWith(args[1].toLowerCase(Locale.ROOT))).toList();
        }
        return List.of();
    }
}
