package me.foesio.foTeams.api.event;

import org.bukkit.event.HandlerList;

/** Fired after a changed team name is stored. */
public final class TeamRenamedEvent extends TeamEvent {
    private static final HandlerList HANDLERS = new HandlerList();
    private final int teamId;
    private final String oldName;
    private final String newName;
    public TeamRenamedEvent(int teamId, String oldName, String newName) {
        this.teamId = teamId;
        this.oldName = oldName;
        this.newName = newName;
    }
    public int getTeamId() { return teamId; }
    public String getOldName() { return oldName; }
    public String getNewName() { return newName; }
    @Override public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
