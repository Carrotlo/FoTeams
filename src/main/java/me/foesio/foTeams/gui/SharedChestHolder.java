package me.foesio.foTeams.gui;

import org.bukkit.Bukkit;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public final class SharedChestHolder implements InventoryHolder {
    private final int teamId;
    private final Inventory inventory;
    private int unlockedRows;

    public SharedChestHolder(int teamId, int rows, int unlockedRows, String title) {
        this.teamId = teamId;
        int normalizedRows = Math.max(1, Math.min(6, rows));
        this.unlockedRows = Math.max(1, Math.min(normalizedRows, unlockedRows));
        this.inventory = Bukkit.createInventory(this, normalizedRows * 9, title);
    }

    public int getTeamId() {
        return teamId;
    }

    public int unlockedRows() {
        return unlockedRows;
    }

    public void setUnlockedRows(int rows) {
        this.unlockedRows = Math.max(1, Math.min(inventory.getSize() / 9, rows));
    }

    public boolean isLocked(int slot) {
        return slot >= unlockedRows * 9 && slot < inventory.getSize();
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
