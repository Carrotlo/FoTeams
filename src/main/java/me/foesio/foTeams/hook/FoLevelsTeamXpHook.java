package me.foesio.foTeams.hook;

import me.foesio.foTeams.FoTeams;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.EventExecutor;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.util.logging.Level;

public final class FoLevelsTeamXpHook implements Listener {
    private static final String EVENT_CLASS_NAME = "me.foesio.foLevels.api.event.FoLevelsXpGainEvent";

    private final FoTeams plugin;
    private Method getPlayer;
    private Method getAmount;
    private Method getReason;
    private Method getSourceKey;

    public FoLevelsTeamXpHook(FoTeams plugin) {
        this.plugin = plugin;
    }

    @SuppressWarnings("unchecked")
    public boolean register() {
        Plugin foLevels = plugin.getServer().getPluginManager().getPlugin("FoLevels");
        if (foLevels == null || !foLevels.isEnabled()) {
            return false;
        }
        try {
            Class<?> rawEventClass = Class.forName(EVENT_CLASS_NAME, true, foLevels.getClass().getClassLoader());
            if (!Event.class.isAssignableFrom(rawEventClass)) {
                return false;
            }
            getPlayer = rawEventClass.getMethod("getPlayer");
            getAmount = rawEventClass.getMethod("getAmount");
            getReason = rawEventClass.getMethod("getReason");
            getSourceKey = rawEventClass.getMethod("getSourceKey");

            Class<? extends Event> eventClass = (Class<? extends Event>) rawEventClass;
            EventExecutor executor = (listener, event) -> handleXpGain(event);
            plugin.getServer().getPluginManager().registerEvent(eventClass, this, EventPriority.MONITOR, executor, plugin, true);
            plugin.getLogger().info("Hooked into FoLevels XP events for team levels.");
            return true;
        } catch (ClassNotFoundException ignored) {
            return false;
        } catch (ReflectiveOperationException exception) {
            plugin.getLogger().log(Level.WARNING, "FoLevels team level integration is unavailable.", exception);
            return false;
        }
    }

    private void handleXpGain(Event event) {
        if (!plugin.getTeamLevelService().isEnabled()) {
            return;
        }
        if (event instanceof Cancellable cancellable && cancellable.isCancelled()) {
            return;
        }
        try {
            Player player = (Player) getPlayer.invoke(event);
            Object amountValue = getAmount.invoke(event);
            if (player == null || !player.isOnline() || !(amountValue instanceof Number number)) {
                return;
            }
            String reason = String.valueOf(getReason.invoke(event));
            String sourceKey = String.valueOf(getSourceKey.invoke(event));
            plugin.getTeamLevelService().addXpFromFoLevels(player, number.longValue(), reason, sourceKey);
        } catch (ReflectiveOperationException | ClassCastException exception) {
            plugin.getLogger().log(Level.WARNING, "Failed to apply FoLevels XP to team levels.", exception);
        } catch (Exception exception) {
            plugin.getLogger().log(Level.WARNING, "Failed to save team level XP.", exception);
        }
    }
}
