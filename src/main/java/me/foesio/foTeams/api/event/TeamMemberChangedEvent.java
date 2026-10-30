package me.foesio.foTeams.api.event;

import me.foesio.foTeams.api.TeamRole;
import org.bukkit.event.HandlerList;

import java.util.UUID;

/** Fired after a membership or role change is stored. Null role means no membership. */
public final class TeamMemberChangedEvent extends TeamEvent {
    public enum Cause { JOIN, LEAVE, KICK, PROMOTE, DEMOTE, TRANSFER }
    private static final HandlerList HANDLERS = new HandlerList();
    private final int teamId;
    private final UUID playerId;
    private final TeamRole oldRole;
    private final TeamRole newRole;
    private final Cause cause;
    public TeamMemberChangedEvent(int teamId, UUID playerId, TeamRole oldRole, TeamRole newRole, Cause cause) {
        this.teamId = teamId;
        this.playerId = playerId;
        this.oldRole = oldRole;
        this.newRole = newRole;
        this.cause = cause;
    }
    public int getTeamId() { return teamId; }
    public UUID getPlayerId() { return playerId; }
    public TeamRole getOldRole() { return oldRole; }
    public TeamRole getNewRole() { return newRole; }
    public Cause getCause() { return cause; }
    @Override public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
