package me.foesio.foTeams.input;

import org.bukkit.entity.Player;

import java.util.function.Consumer;

public interface BalanceDialogService {
    boolean canOpenNative(Player player);

    boolean open(Player player, BalanceDialogRequest request, Consumer<BalanceDialogAction> onAction);
}
