package me.foesio.foTeams.input;

import org.bukkit.entity.Player;

import java.util.function.Consumer;

public interface InputGuiService {
    void openInput(Player player, InputPrompt prompt, Consumer<String> onSubmit, Runnable onCancel);

    void reload();

    void close();

    boolean usesNativeDialogs();
}
