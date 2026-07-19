package me.foesio.foTeams.input;

public record BalanceDialogAction(Type type, String amount) {
    public enum Type {
        DEPOSIT,
        WITHDRAW,
        BACK
    }

    public static BalanceDialogAction deposit(String amount) {
        return new BalanceDialogAction(Type.DEPOSIT, amount);
    }

    public static BalanceDialogAction withdraw(String amount) {
        return new BalanceDialogAction(Type.WITHDRAW, amount);
    }

    public static BalanceDialogAction back() {
        return new BalanceDialogAction(Type.BACK, "");
    }
}
