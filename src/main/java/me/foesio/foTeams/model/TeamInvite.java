package me.foesio.foTeams.model;

import java.util.UUID;

public record TeamInvite(int teamId, UUID playerId, UUID inviterId, long createdAt, long expiresAt) {
    public boolean isExpired(long now) {
        return now > expiresAt;
    }
}
