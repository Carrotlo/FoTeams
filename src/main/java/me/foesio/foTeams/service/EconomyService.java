package me.foesio.foTeams.service;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Method;
import java.util.logging.Level;

public final class EconomyService {
    private final JavaPlugin plugin;
    private Object economy;
    private Method getBalance;
    private Method withdrawPlayer;
    private Method depositPlayer;
    private Method transactionSuccess;

    public EconomyService(JavaPlugin plugin) {
        this.plugin = plugin;
        hook();
    }

    public void hook() {
        economy = null;
        getBalance = null;
        withdrawPlayer = null;
        depositPlayer = null;
        transactionSuccess = null;

        Plugin vault = Bukkit.getPluginManager().getPlugin("Vault");
        if (vault == null || !vault.isEnabled()) {
            return;
        }

        try {
            ClassLoader vaultLoader = vault.getClass().getClassLoader();
            Class<?> economyClass = Class.forName("net.milkbowl.vault.economy.Economy", false, vaultLoader);
            Class<?> responseClass = Class.forName("net.milkbowl.vault.economy.EconomyResponse", false, vaultLoader);
            RegisteredServiceProvider<?> registration = plugin.getServer().getServicesManager().getRegistration(economyClass);
            if (registration == null) {
                return;
            }

            Object provider = registration.getProvider();
            getBalance = economyClass.getMethod("getBalance", OfflinePlayer.class);
            withdrawPlayer = economyClass.getMethod("withdrawPlayer", OfflinePlayer.class, double.class);
            depositPlayer = economyClass.getMethod("depositPlayer", OfflinePlayer.class, double.class);
            transactionSuccess = responseClass.getMethod("transactionSuccess");
            economy = provider;
        } catch (ReflectiveOperationException | LinkageError exception) {
            plugin.getLogger().log(Level.WARNING, "Vault economy hook is unavailable.", exception);
        }
    }

    public boolean isEnabled() {
        return economy != null;
    }

    public double balance(OfflinePlayer player) {
        if (economy == null || getBalance == null) {
            return 0;
        }
        try {
            Object result = getBalance.invoke(economy, player);
            return result instanceof Number number ? number.doubleValue() : 0;
        } catch (ReflectiveOperationException | LinkageError exception) {
            plugin.getLogger().log(Level.WARNING, "Vault balance lookup failed.", exception);
            return 0;
        }
    }

    public boolean withdraw(OfflinePlayer player, double amount) {
        return transaction(withdrawPlayer, player, amount);
    }

    public boolean deposit(OfflinePlayer player, double amount) {
        return transaction(depositPlayer, player, amount);
    }

    private boolean transaction(Method method, OfflinePlayer player, double amount) {
        if (economy == null || method == null || transactionSuccess == null) {
            return false;
        }
        try {
            Object response = method.invoke(economy, player, amount);
            Object result = transactionSuccess.invoke(response);
            return result instanceof Boolean success && success;
        } catch (ReflectiveOperationException | LinkageError exception) {
            plugin.getLogger().log(Level.WARNING, "Vault economy transaction failed.", exception);
            return false;
        }
    }
}
