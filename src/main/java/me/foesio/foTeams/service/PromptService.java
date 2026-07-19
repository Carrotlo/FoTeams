package me.foesio.foTeams.service;

import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public final class PromptService {
    private final Map<UUID, Prompt> prompts = new ConcurrentHashMap<>();

    public void setPrompt(Player player, String message, Consumer<String> consumer) {
        prompts.put(player.getUniqueId(), new Prompt(message, consumer));
        player.sendMessage(message);
    }

    public Prompt removePrompt(UUID uniqueId) {
        return prompts.remove(uniqueId);
    }

    public void clear() {
        prompts.clear();
    }

    public record Prompt(String message, Consumer<String> consumer) {
    }
}
