package me.foesio.foTeams.gui;

import org.bukkit.Bukkit;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;

import java.util.HashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.bukkit.inventory.ItemStack;

public final class FoGui implements InventoryHolder {
    private final Inventory inventory;
    private final Map<Integer, Consumer<Click>> actions = new HashMap<>();
    private final List<PublicEntry> publicEntries = new ArrayList<>();
    private final Map<String, String> publicValues = new HashMap<>();
    private final String publicMenuId;

    public FoGui(int size, String title) {
        this(size, title, null);
    }

    public FoGui(int size, String title, String publicMenuId) {
        this.inventory = Bukkit.createInventory(this, size, title);
        this.publicMenuId = publicMenuId;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public String publicMenuId() {
        return publicMenuId;
    }

    public void setPublicValues(Map<String, String> values) {
        publicValues.clear();
        publicValues.putAll(values);
    }

    public void setPublicValue(String key, String value) {
        publicValues.put(key, value);
    }

    public Map<String, String> publicValues() {
        return Map.copyOf(publicValues);
    }

    public boolean hasAction(int slot) {
        return actions.containsKey(slot);
    }

    public void setAction(int slot, Consumer<Click> action) {
        actions.put(slot, action);
    }

    /** Keeps dynamic callbacks in source order, independent of owner slot moves. */
    public void addContentEntry(int displaySlot, ItemStack item, Consumer<Click> action) {
        if (publicMenuId == null) {
            inventory.setItem(displaySlot, item);
            if (action != null) {
                setAction(displaySlot, action);
            }
            return;
        }
        int callbackId = inventory.getSize() + publicEntries.size();
        publicEntries.add(new PublicEntry(callbackId, item));
        if (action != null) {
            setAction(callbackId, action);
        }
    }

    public List<PublicEntry> publicEntries() {
        return List.copyOf(publicEntries);
    }

    public void click(InventoryClickEvent event) {
        click(event.getRawSlot(), event.getClick(), (Player) event.getWhoClicked());
    }

    public void click(int slot, ClickType type, Player player) {
        Consumer<Click> action = actions.get(slot);
        if (action != null) {
            action.accept(new Click(type, player));
        }
    }

    public record Click(ClickType getClick, Player getWhoClicked) {
    }

    public record PublicEntry(int callbackId, ItemStack item) {
    }
}
