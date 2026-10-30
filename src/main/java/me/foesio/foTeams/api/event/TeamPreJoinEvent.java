package me.foesio.foTeams.api.event;

import me.foesio.foTeams.api.TeamSnapshot;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;

import java.util.UUID;

/** Fired after join validation and before membership changes. */
public final class TeamPreJoinEvent extends TeamEvent implements Cancellable {
    private static final HandlerList HANDLERS = new HandlerList();
    private final UUID playerId;
    private final TeamSnapshot team;
    private boolean cancelled;

    public TeamPreJoinEvent(UUID playerId, TeamSnapshot team) {
        this.playerId = playerId;
        this.team = team;
    }

    public UUID getPlayerId() { return playerId; }
    public TeamSnapshot getTeam() { return team; }
    @Override public boolean isCancelled() { return cancelled; }
    @Override public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }
    @Override public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
