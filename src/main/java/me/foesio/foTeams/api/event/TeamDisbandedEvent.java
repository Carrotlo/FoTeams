package me.foesio.foTeams.api.event;

import me.foesio.foTeams.api.TeamSnapshot;
import org.bukkit.event.HandlerList;

/** Fired after team deletion and its relation/invite cleanup complete. */
public final class TeamDisbandedEvent extends TeamEvent {
    private static final HandlerList HANDLERS = new HandlerList();
    private final TeamSnapshot formerTeam;
    public TeamDisbandedEvent(TeamSnapshot formerTeam) { this.formerTeam = formerTeam; }
    public TeamSnapshot getFormerTeam() { return formerTeam; }
    @Override public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
