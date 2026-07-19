package me.foesio.foTeams.input;

import me.foesio.core.text.PromptNormalizer;
import me.foesio.foTeams.FoTeams;
import org.bukkit.entity.Player;

import java.util.function.Consumer;

public final class ChatPromptInputGuiService implements InputGuiService {
    private final FoTeams plugin;

    public ChatPromptInputGuiService(FoTeams plugin) {
        this.plugin = plugin;
    }

    @Override
    public void openInput(Player player, InputPrompt prompt, Consumer<String> onSubmit, Runnable onCancel) {
        player.closeInventory();
        plugin.getPromptService().setPrompt(player, prompt.chatPrompt(), input -> {
            if (PromptNormalizer.isCancel(input)) {
                plugin.getMessages().send(player, "prompt-cancelled");
                onCancel.run();
                return;
            }
            onSubmit.accept(input);
        });
    }

    @Override
    public void reload() {
    }

    @Override
    public void close() {
        plugin.getPromptService().clear();
    }

    @Override
    public boolean usesNativeDialogs() {
        return false;
    }
}
