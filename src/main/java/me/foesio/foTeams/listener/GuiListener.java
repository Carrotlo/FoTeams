package me.foesio.foTeams.listener;

import me.foesio.foTeams.FoTeams;
import me.foesio.foTeams.gui.FoGui;
import me.foesio.foTeams.gui.SharedChestHolder;
import me.foesio.core.gui.EntryBrowserHolder;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

public final class GuiListener implements Listener {
    private final FoTeams plugin;

    public GuiListener(FoTeams plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        Inventory topInventory = event.getView().getTopInventory();
        if (topInventory.getHolder() instanceof EntryBrowserHolder holder) {
            event.setCancelled(true);
            if (event.getRawSlot() < 0 || event.getRawSlot() >= topInventory.getSize()) {
                return;
            }
            if (event.getWhoClicked() instanceof Player player) {
                plugin.getGuiService().handleEntryBrowserClick(player, event.getRawSlot(), event.getClick(), holder);
            }
            return;
        }
        if (topInventory.getHolder() instanceof FoGui gui) {
            event.setCancelled(true);
            if (event.getRawSlot() < 0 || event.getRawSlot() >= topInventory.getSize()) {
                return;
            }
            gui.click(event);
            return;
        }
        if (topInventory.getHolder() instanceof SharedChestHolder holder) {
            plugin.getGuiService().queueSharedChestSave(holder);
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        Inventory topInventory = event.getView().getTopInventory();
        if (topInventory.getHolder() instanceof EntryBrowserHolder) {
            for (int rawSlot : event.getRawSlots()) {
                if (rawSlot >= 0 && rawSlot < topInventory.getSize()) {
                    event.setCancelled(true);
                    return;
                }
            }
            return;
        }
        if (!(topInventory.getHolder() instanceof SharedChestHolder holder)) {
            return;
        }
        for (int slot : event.getRawSlots()) {
            if (slot >= 0 && slot < topInventory.getSize()) {
                plugin.getGuiService().queueSharedChestSave(holder);
                return;
            }
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (event.getPlayer() instanceof Player player && plugin.getGuiService().consumeSuppressedClose(player)) {
            return;
        }
        if (event.getInventory().getHolder() instanceof SharedChestHolder holder) {
            plugin.getGuiService().handleSharedChestClose(holder);
        }
    }
}
