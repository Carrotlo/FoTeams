package me.foesio.foTeams.api;

import java.util.List;

/** Level outcome. requestedXp can exceed XP retained under a max-level cap; persistence is delayed. */
public record TeamXpResult(long requestedXp, int oldLevel, int newLevel, long currentXp,
                           long requiredXp, List<Integer> gainedLevels) {
    public TeamXpResult {
        gainedLevels = List.copyOf(gainedLevels);
    }
}
