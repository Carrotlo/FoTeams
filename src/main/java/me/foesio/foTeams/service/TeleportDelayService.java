package me.foesio.foTeams.service;

import me.foesio.foTeams.FoTeams;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class TeleportDelayService {
    private final FoTeams plugin;
    private final Map<UUID, PendingTeleport> pendingTeleports = new ConcurrentHashMap<>();

    public TeleportDelayService(FoTeams plugin) {
        this.plugin = plugin;
    }

    public void start(Player player, Location destination, String target) {
        start(player, destination, target, () -> {
        });
    }

    public void start(Player player, Location destination, String target, Runnable afterTeleport) {
        int delaySeconds = Math.max(0, plugin.getConfig().getInt("teleport-delay-seconds", 3));
        Location safeDestination = destination.clone();
        if (delaySeconds <= 0) {
            teleport(player, safeDestination, target, afterTeleport);
            return;
        }

        UUID token = UUID.randomUUID();
        pendingTeleports.put(player.getUniqueId(), new PendingTeleport(token, Position.of(player.getLocation())));
        sendCountdown(player, target, delaySeconds);
        scheduleTick(player, safeDestination, target, afterTeleport, delaySeconds, token);
    }

    public void cancelAll() {
        pendingTeleports.clear();
    }

    private void scheduleTick(Player player, Location destination, String target, Runnable afterTeleport, int secondsRemaining, UUID token) {
        plugin.getCore().scheduler().runLaterForPlayer(player, () -> tick(player, destination, target, afterTeleport, secondsRemaining, token), 20L);
    }

    private void tick(Player player, Location destination, String target, Runnable afterTeleport, int secondsRemaining, UUID token) {
        PendingTeleport pending = pendingTeleports.get(player.getUniqueId());
        if (pending == null || !pending.token().equals(token)) {
            return;
        }
        if (!player.isOnline()) {
            pendingTeleports.remove(player.getUniqueId(), pending);
            return;
        }
        if (pending.origin().hasChanged(player.getLocation())) {
            if (pendingTeleports.remove(player.getUniqueId(), pending)) {
                plugin.sendMessage(player, "teleport-canceled");
            }
            return;
        }

        int nextSecondsRemaining = secondsRemaining - 1;
        if (nextSecondsRemaining <= 0) {
            if (pendingTeleports.remove(player.getUniqueId(), pending)) {
                teleport(player, destination, target, afterTeleport);
            }
            return;
        }

        sendCountdown(player, target, nextSecondsRemaining);
        scheduleTick(player, destination, target, afterTeleport, nextSecondsRemaining, token);
    }

    private void teleport(Player player, Location destination, String target, Runnable afterTeleport) {
        if (!player.teleport(destination)) {
            plugin.sendMessage(player, "action-failed");
            return;
        }
        plugin.sendMessage(player, "teleport-success", Map.of("{target}", displayTarget(target)));
        afterTeleport.run();
    }

    private void sendCountdown(Player player, String target, int secondsRemaining) {
        String path = "teleport-countdown-actionbar";
        if (!plugin.getMessages().config().isString(path)
                || plugin.getMessages().config().getString(path, "").isEmpty()) {
            return;
        }
        player.sendActionBar(plugin.getMessages().renderComponent(player, path, "", Map.of(
                "{target}", displayTarget(target),
                "{seconds}", String.valueOf(secondsRemaining)
        )));
    }

    private String displayTarget(String target) {
        return target.equals("home") ? plugin.publicGui().label("teleport.home", "") : target;
    }

    private record PendingTeleport(UUID token, Position origin) {
    }

    private record Position(String world, double x, double y, double z) {
        private static Position of(Location location) {
            String worldName = location.getWorld() == null ? "" : location.getWorld().getName();
            return new Position(worldName, location.getX(), location.getY(), location.getZ());
        }

        private boolean hasChanged(Location location) {
            String currentWorld = location.getWorld() == null ? "" : location.getWorld().getName();
            return !world.equals(currentWorld)
                    || Double.compare(x, location.getX()) != 0
                    || Double.compare(y, location.getY()) != 0
                    || Double.compare(z, location.getZ()) != 0;
        }
    }
}
