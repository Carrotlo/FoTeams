package me.foesio.foTeams.api.event;

import me.foesio.foTeams.api.TeamSnapshot;
import org.bukkit.event.HandlerList;

/** Fired after a newly created team is stored. */
public final class TeamCreatedEvent extends TeamEvent {
    private static final HandlerList HANDLERS = new HandlerList();
    private final TeamSnapshot team;
    public TeamCreatedEvent(TeamSnapshot team) { this.team = team; }
    public TeamSnapshot getTeam() { return team; }
    @Override public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
