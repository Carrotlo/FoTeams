package me.foesio.foTeams.api.event;

import me.foesio.foTeams.api.TeamRelation;
import org.bukkit.event.HandlerList;

/** Fired for each changed directional relation after relations are stored. */
public final class TeamRelationChangedEvent extends TeamEvent {
    private static final HandlerList HANDLERS = new HandlerList();
    private final int sourceTeamId;
    private final int targetTeamId;
    private final TeamRelation oldRelation;
    private final TeamRelation newRelation;
    public TeamRelationChangedEvent(int sourceTeamId, int targetTeamId, TeamRelation oldRelation, TeamRelation newRelation) {
        this.sourceTeamId = sourceTeamId;
        this.targetTeamId = targetTeamId;
        this.oldRelation = oldRelation;
        this.newRelation = newRelation;
    }
    public int getSourceTeamId() { return sourceTeamId; }
    public int getTargetTeamId() { return targetTeamId; }
    public TeamRelation getOldRelation() { return oldRelation; }
    public TeamRelation getNewRelation() { return newRelation; }
    @Override public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
