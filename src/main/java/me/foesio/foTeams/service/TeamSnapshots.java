package me.foesio.foTeams.service;

import me.foesio.foTeams.api.TeamRole;
import me.foesio.foTeams.api.TeamSnapshot;
import me.foesio.foTeams.model.Team;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

final class TeamSnapshots {
    private TeamSnapshots() {}

    static TeamSnapshot of(Team team) {
        Map<UUID, TeamRole> roles = new HashMap<>();
        team.getMembers().forEach(id -> roles.put(id, TeamRole.MEMBER));
        team.getAdmins().forEach(id -> roles.put(id, TeamRole.ADMIN));
        if (team.hasOwner()) {
            roles.put(team.getOwnerId(), TeamRole.OWNER);
        }
        return new TeamSnapshot(team.getId(), team.getName(), team.getTag(), team.getDescription(),
                team.getColor(), team.hasOwner() ? team.getOwnerId() : null, roles, team.getMemberCap(),
                team.getScore(), team.getBalance(), team.getTeamLevel(), team.getTeamXp());
    }

    static TeamRole role(me.foesio.foTeams.model.TeamRole role) {
        return role == null ? null : TeamRole.valueOf(role.name());
    }
}
