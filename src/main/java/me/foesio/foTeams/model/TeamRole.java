package me.foesio.foTeams.model;

public enum TeamRole {
    OWNER,
    ADMIN,
    MEMBER;

    public String displayName() {
        return name().toLowerCase();
    }
}
