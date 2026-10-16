package me.foesio.foTeams.input;

import me.foesio.foTeams.FoTeams;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.function.Consumer;

public final class ChatPromptInputGuiService implements InputGuiService {
    private final FoTeams plugin;

    public ChatPromptInputGuiService(FoTeams plugin) {
        this.plugin = plugin;
    }

    @Override
    public void openInput(Player player, InputPrompt prompt, Consumer<String> onSubmit, Runnable onCancel) {
        player.closeInventory();
        plugin.getPromptService().setPrompt(player,
                plugin.getMessages().renderTemplateComponent(player, prompt.chatPrompt(), Map.of(
                        "{cancel}", plugin.publicGui().label("chat.cancel-keyword", ""))), input -> {
            if (plugin.isCancelInput(input)) {
                plugin.sendMessage(player, "prompt-cancelled");
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
