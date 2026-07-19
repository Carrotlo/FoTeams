package me.foesio.foTeams.model;

public enum TeamAction {
    INVITE("invite"),
    KICK("kick"),
    PROMOTE("promote"),
    DEMOTE("demote"),
    TRANSFER("transfer"),
    DISBAND("disband"),
    SET_HOME("set-home"),
    DELETE_HOME("delete-home"),
    SET_WARP("set-warp"),
    DELETE_WARP("delete-warp"),
    USE_HOME("use-home"),
    USE_WARP("use-warp"),
    USE_ECHEST("use-echest"),
    CHANGE_NAME("change-name"),
    CHANGE_TAG("change-tag"),
    CHANGE_DESCRIPTION("change-description"),
    CHANGE_COLOR("change-color"),
    MANAGE_RELATIONS("manage-relations"),
    DEPOSIT("deposit"),
    WITHDRAW("withdraw");

    private final String path;

    TeamAction(String path) {
        this.path = path;
    }

    public String path() {
        return path;
    }
}
