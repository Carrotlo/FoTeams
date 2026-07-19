package me.foesio.foTeams.listener;

import me.foesio.foTeams.FoTeams;
import me.foesio.foTeams.model.Team;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class CombatListener implements Listener {
    private static final long ANCHOR_TRIGGER_WINDOW_MILLIS = 3000L;
    private static final double ANCHOR_ATTRIBUTION_RADIUS_SQUARED = 144.0D;

    private final FoTeams plugin;
    private final Map<AnchorKey, AnchorTrigger> recentAnchorTriggers = new ConcurrentHashMap<>();

    public CombatListener(FoTeams plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onRespawnAnchorInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        if (event.getClickedBlock() == null || event.getClickedBlock().getType() != Material.RESPAWN_ANCHOR) {
            return;
        }
        long now = System.currentTimeMillis();
        AnchorKey key = AnchorKey.from(event.getClickedBlock().getLocation());
        recentAnchorTriggers.put(key, new AnchorTrigger(event.getPlayer().getUniqueId(), now, key));
        cleanupAnchorTriggers(now);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onTeamDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) {
            return;
        }
        Player attacker = resolveAttacker(event);
        if (attacker == null || attacker.getUniqueId().equals(victim.getUniqueId())) {
            return;
        }
        if (shouldCancelFriendlyDamage(attacker, victim)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onExplosionDamage(EntityDamageEvent event) {
        if (event instanceof EntityDamageByEntityEvent) {
            return;
        }
        if (!(event.getEntity() instanceof Player victim)) {
            return;
        }
        if (event.getCause() != EntityDamageEvent.DamageCause.ENTITY_EXPLOSION
                && event.getCause() != EntityDamageEvent.DamageCause.BLOCK_EXPLOSION) {
            return;
        }
        Player attacker = null;
        Entity causing = event.getDamageSource().getCausingEntity();
        if (causing instanceof Player player) {
            attacker = player;
        }
        if (attacker == null) {
            attacker = resolveRecentAnchorAttacker(victim);
        }
        if (attacker == null || attacker.getUniqueId().equals(victim.getUniqueId())) {
            return;
        }
        if (shouldCancelFriendlyDamage(attacker, victim)) {
            event.setCancelled(true);
        }
    }

    private Player resolveAttacker(EntityDamageByEntityEvent event) {
        Entity damager = event.getDamager();
        if (damager instanceof Player player) {
            return player;
        }
        if (damager instanceof Projectile projectile && projectile.getShooter() instanceof Player player) {
            return player;
        }
        if (damager instanceof TNTPrimed tnt && tnt.getSource() instanceof Player player) {
            return player;
        }
        Entity causing = event.getDamageSource().getCausingEntity();
        if (causing instanceof Player player) {
            return player;
        }
        return null;
    }

    private boolean shouldCancelFriendlyDamage(Player attacker, Player victim) {
        Team victimTeam = plugin.getTeamService().teamOf(victim.getUniqueId()).orElse(null);
        Team attackerTeam = plugin.getTeamService().teamOf(attacker.getUniqueId()).orElse(null);
        if (victimTeam == null || attackerTeam == null || attackerTeam.getId() != victimTeam.getId()) {
            return false;
        }
        if (plugin.getConfig().getBoolean("team-pvp-force-disable-all", false)) {
            return false;
        }
        return victimTeam.isTeamPvpProtectionEnabled();
    }

    private Player resolveRecentAnchorAttacker(Player victim) {
        long now = System.currentTimeMillis();
        cleanupAnchorTriggers(now);
        Location victimLocation = victim.getLocation();
        UUID worldId = victimLocation.getWorld().getUID();
        AnchorTrigger best = null;
        double bestDistance = Double.MAX_VALUE;
        for (AnchorTrigger trigger : recentAnchorTriggers.values()) {
            if (!trigger.key().worldId().equals(worldId)) {
                continue;
            }
            double distance = trigger.key().distanceSquaredTo(victimLocation);
            if (distance > ANCHOR_ATTRIBUTION_RADIUS_SQUARED) {
                continue;
            }
            if (best == null || trigger.createdAtMillis() > best.createdAtMillis()
                    || (trigger.createdAtMillis() == best.createdAtMillis() && distance < bestDistance)) {
                best = trigger;
                bestDistance = distance;
            }
        }
        if (best == null) {
            return null;
        }
        return Bukkit.getPlayer(best.playerId());
    }

    private void cleanupAnchorTriggers(long now) {
        recentAnchorTriggers.entrySet().removeIf(entry -> now - entry.getValue().createdAtMillis() > ANCHOR_TRIGGER_WINDOW_MILLIS);
    }

    private record AnchorKey(UUID worldId, int x, int y, int z) {
        private static AnchorKey from(Location location) {
            return new AnchorKey(location.getWorld().getUID(), location.getBlockX(), location.getBlockY(), location.getBlockZ());
        }

        private double distanceSquaredTo(Location location) {
            double centerX = x + 0.5D;
            double centerY = y + 0.5D;
            double centerZ = z + 0.5D;
            return location.distanceSquared(new Location(location.getWorld(), centerX, centerY, centerZ));
        }
    }

    private record AnchorTrigger(UUID playerId, long createdAtMillis, AnchorKey key) {
    }
}
