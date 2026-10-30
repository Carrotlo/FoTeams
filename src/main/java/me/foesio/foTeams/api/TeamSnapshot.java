package me.foesio.foTeams.api;

import java.util.Map;
import java.util.UUID;

/** Immutable view of current in-memory team state at lookup time; team XP may await autosave. */
public record TeamSnapshot(int id, String name, String tag, String description, String color,
                           UUID ownerId, Map<UUID, TeamRole> roles, int memberCap,
                           int score, double balance, int level, long xp) {
    public TeamSnapshot {
        roles = Map.copyOf(roles);
    }
}
