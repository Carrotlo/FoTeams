package me.foesio.foTeams.service;

import me.foesio.foTeams.config.FileConfig;
import me.foesio.foTeams.model.TeamAction;
import me.foesio.foTeams.model.TeamRole;
import org.bukkit.configuration.ConfigurationSection;

import java.util.EnumMap;
import java.util.Map;

public final class RolePermissionService {
    private final FileConfig file;
    private final Map<TeamRole, Map<TeamAction, Boolean>> matrix = new EnumMap<>(TeamRole.class);

    public RolePermissionService(FileConfig file) {
        this.file = file;
        reload();
    }

    public void reload() {
        matrix.clear();
        ConfigurationSection roles = file.config().getConfigurationSection("roles");
        if (roles == null) {
            return;
        }
        for (TeamRole role : TeamRole.values()) {
            ConfigurationSection section = roles.getConfigurationSection(role.name());
            Map<TeamAction, Boolean> permissions = new EnumMap<>(TeamAction.class);
            for (TeamAction action : TeamAction.values()) {
                permissions.put(action, section != null && section.getBoolean(action.path(), false));
            }
            matrix.put(role, permissions);
        }
    }

    public boolean can(TeamRole role, TeamAction action) {
        if (role == null) {
            return false;
        }
        return matrix.getOrDefault(role, Map.of()).getOrDefault(action, false);
    }
}
