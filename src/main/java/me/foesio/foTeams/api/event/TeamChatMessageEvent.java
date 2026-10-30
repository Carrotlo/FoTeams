package me.foesio.foTeams.api.event;

import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;

import java.util.UUID;

/** Fired before a team or ally message is delivered, on the sender's scheduler context. */
public final class TeamChatMessageEvent extends TeamEvent implements Cancellable {
    public enum Channel { TEAM, ALLY }
    private static final HandlerList HANDLERS = new HandlerList();
    private final UUID senderId;
    private final int teamId;
    private final Channel channel;
    private final String message;
    private boolean cancelled;
    public TeamChatMessageEvent(UUID senderId, int teamId, Channel channel, String message) {
        this.senderId = senderId;
        this.teamId = teamId;
        this.channel = channel;
        this.message = message;
    }
    public UUID getSenderId() { return senderId; }
    public int getTeamId() { return teamId; }
    public Channel getChannel() { return channel; }
    public String getMessage() { return message; }
    @Override public boolean isCancelled() { return cancelled; }
    @Override public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }
    @Override public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
