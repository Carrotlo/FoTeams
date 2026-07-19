package me.foesio.foTeams.service;

import me.foesio.foTeams.model.ChatMode;
import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class TeamChatService {
    private final Map<UUID, ChatMode> modes = new ConcurrentHashMap<>();
    private final Set<UUID> spies = ConcurrentHashMap.newKeySet();

    public ChatMode getMode(UUID playerId) {
        return modes.getOrDefault(playerId, ChatMode.GLOBAL);
    }

    public void setMode(UUID playerId, ChatMode mode) {
        if (mode == ChatMode.GLOBAL) {
            modes.remove(playerId);
            return;
        }
        modes.put(playerId, mode);
    }

    public boolean toggleSpy(UUID playerId) {
        if (spies.remove(playerId)) {
            return false;
        }
        spies.add(playerId);
        return true;
    }

    public boolean isSpy(UUID playerId) {
        return spies.contains(playerId);
    }

    public Set<UUID> spies() {
        return spies;
    }

    public void clear(Player player) {
        modes.remove(player.getUniqueId());
        spies.remove(player.getUniqueId());
    }

    public void clearModes(Collection<UUID> players) {
        for (UUID playerId : players) {
            modes.remove(playerId);
        }
    }
}
