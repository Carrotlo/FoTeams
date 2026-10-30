package me.foesio.foTeams.api.event;

import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;

import java.util.UUID;

/** Fired after validation and before a team is inserted. A null owner means admin-created. */
public final class TeamPreCreateEvent extends TeamEvent implements Cancellable {
    private static final HandlerList HANDLERS = new HandlerList();
    private final UUID ownerId;
    private final String name;
    private boolean cancelled;

    public TeamPreCreateEvent(UUID ownerId, String name) {
        this.ownerId = ownerId;
        this.name = name;
    }

    public UUID getOwnerId() { return ownerId; }
    public String getName() { return name; }
    @Override public boolean isCancelled() { return cancelled; }
    @Override public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }
    @Override public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
