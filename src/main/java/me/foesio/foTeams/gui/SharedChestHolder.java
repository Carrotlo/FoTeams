package me.foesio.foTeams.gui;

import org.bukkit.Bukkit;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public final class SharedChestHolder implements InventoryHolder {
    private final int teamId;
    private final Inventory inventory;

    public SharedChestHolder(int teamId, int rows, String title) {
        this.teamId = teamId;
        int normalizedRows = Math.max(1, Math.min(6, rows));
        this.inventory = Bukkit.createInventory(this, normalizedRows * 9, title);
    }

    public int getTeamId() {
        return teamId;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
