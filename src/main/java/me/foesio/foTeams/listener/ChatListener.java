package me.foesio.foTeams.listener;

import me.foesio.foTeams.FoTeams;
import me.foesio.foTeams.model.ChatMode;
import me.foesio.foTeams.model.Team;
import me.foesio.foTeams.service.PromptService;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@SuppressWarnings("deprecation")
public final class ChatListener implements Listener {
    private static final long DUPLICATE_ROUTE_WINDOW_NANOS = TimeUnit.MILLISECONDS.toNanos(250);

    private final FoTeams plugin;
    private final Map<AsyncPlayerChatEvent, String> rawMessages = new ConcurrentHashMap<>();
    private final Map<UUID, RoutedChat> routedChats = new ConcurrentHashMap<>();

    public ChatListener(FoTeams plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void captureRawMessage(AsyncPlayerChatEvent event) {
        rawMessages.put(event, event.getMessage());
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        String rawMessage = rawMessages.remove(event);
        String message = rawMessage == null ? event.getMessage() : rawMessage;
        PromptService.Prompt prompt = plugin.getPromptService().removePrompt(player.getUniqueId());
        if (prompt != null) {
            event.setCancelled(true);
            plugin.getCore().scheduler().runForPlayer(player, () -> prompt.consumer().accept(message));
            return;
        }
        ChatMode mode = plugin.getTeamChatService().getMode(player.getUniqueId());
        if (mode != ChatMode.GLOBAL) {
            event.setCancelled(true);
            routeEventMessage(player, mode, message);
            return;
        }
        if (plugin.getConfig().getBoolean("chat.prefix-shortcuts-enabled", true) && message.length() > 1) {
            if (message.startsWith("!") && !message.substring(1).trim().isEmpty() && plugin.getTeamService().teamOf(player.getUniqueId()).isPresent()) {
                event.setCancelled(true);
                routeEventMessage(player, ChatMode.TEAM, message.substring(1).trim());
                return;
            }
            if (message.startsWith("?") && !message.substring(1).trim().isEmpty() && plugin.getTeamService().teamOf(player.getUniqueId()).isPresent()) {
                event.setCancelled(true);
                routeEventMessage(player, ChatMode.ALLY, message.substring(1).trim());
                return;
            }
        }
    }

    private void routeEventMessage(Player player, ChatMode mode, String message) {
        if (isDuplicateRoute(player.getUniqueId(), mode, message)) {
            return;
        }
        plugin.getCore().scheduler().runForPlayer(player, () -> routeMessage(player, mode, message));
    }

    private boolean isDuplicateRoute(UUID playerId, ChatMode mode, String message) {
        long now = System.nanoTime();
        RoutedChat routed = new RoutedChat(mode, message, now);
        RoutedChat previous = routedChats.put(playerId, routed);
        return previous != null
                && previous.mode() == mode
                && previous.message().equals(message)
                && now - previous.createdAtNanos() < DUPLICATE_ROUTE_WINDOW_NANOS;
    }

    private void routeMessage(Player sender, ChatMode mode, String message) {
        Team team = plugin.getTeamService().teamOf(sender.getUniqueId()).orElse(null);
        if (team == null) {
            plugin.getTeamChatService().setMode(sender.getUniqueId(), ChatMode.GLOBAL);
            return;
        }
        if (plugin.getSwearFilterService().containsBlockedWord(message)) {
            plugin.getMessages().send(sender, "blocked-word");
            return;
        }
        Map<String, String> tokens = new HashMap<>();
        tokens.put("{player}", sender.getName());
        tokens.put("{message}", message);
        tokens.put("{team}", team.getName());
        if (mode == ChatMode.TEAM) {
            String formatted = plugin.getMessages().renderTemplate(plugin.getConfig().getString("chat.team-format"), tokens);
            for (UUID memberId : plugin.getTeamService().allMembers(team)) {
                Player target = Bukkit.getPlayer(memberId);
                if (target != null) {
                    target.sendMessage(formatted);
                }
            }
            spy("TEAM", sender, message);
            return;
        }
        if (plugin.getTeamService().alliesOf(team).isEmpty()) {
            plugin.getMessages().send(sender, "chat-no-allies");
            plugin.getTeamChatService().setMode(sender.getUniqueId(), ChatMode.GLOBAL);
            return;
        }
        String formatted = plugin.getMessages().renderTemplate(plugin.getConfig().getString("chat.ally-format"), tokens);
        for (UUID memberId : plugin.getTeamService().allMembers(team)) {
            Player target = Bukkit.getPlayer(memberId);
            if (target != null) {
                target.sendMessage(formatted);
            }
        }
        for (Team ally : plugin.getTeamService().alliesOf(team)) {
            for (UUID memberId : plugin.getTeamService().allMembers(ally)) {
                Player target = Bukkit.getPlayer(memberId);
                if (target != null) {
                    target.sendMessage(formatted);
                }
            }
        }
        spy("ALLY", sender, message);
    }

    public void sendOneShot(Player sender, ChatMode mode, String message) {
        routeMessage(sender, mode, message);
    }

    private void spy(String channel, Player sender, String message) {
        Map<String, String> tokens = Map.of(
                "{channel}", channel,
                "{player}", sender.getName(),
                "{message}", message
        );
        String formatted = plugin.getMessages().renderTemplate(plugin.getConfig().getString("chat.spy-format"), tokens);
        for (UUID spy : plugin.getTeamChatService().spies()) {
            Player target = Bukkit.getPlayer(spy);
            if (target != null && !target.getUniqueId().equals(sender.getUniqueId())) {
                target.sendMessage(formatted);
            }
        }
    }

    private record RoutedChat(ChatMode mode, String message, long createdAtNanos) {
    }
}
