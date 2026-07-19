package me.foesio.foTeams.input;

public record BalanceDialogRequest(
        String teamBalance,
        String playerBalance,
        boolean canDeposit,
        boolean canWithdraw
) {
}
