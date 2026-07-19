package me.foesio.foTeams.gui;

import org.bukkit.Bukkit;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

public final class FoGui implements InventoryHolder {
    private final Inventory inventory;
    private final Map<Integer, Consumer<org.bukkit.event.inventory.InventoryClickEvent>> actions = new HashMap<>();

    public FoGui(int size, String title) {
        this.inventory = Bukkit.createInventory(this, size, title);
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public void setAction(int slot, Consumer<org.bukkit.event.inventory.InventoryClickEvent> action) {
        actions.put(slot, action);
    }

    public void click(org.bukkit.event.inventory.InventoryClickEvent event) {
        Consumer<org.bukkit.event.inventory.InventoryClickEvent> action = actions.get(event.getRawSlot());
        if (action != null) {
            action.accept(event);
        }
    }
}
