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
import org.bukkit.event.inventory.ClickType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

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
            int slot = event.getRawSlot();
            if (holder.isLocked(slot) || (event.getClick() == ClickType.DOUBLE_CLICK
                    && holder.unlockedRows() * 9 < topInventory.getSize())) {
                event.setCancelled(true);
                return;
            }
            if (slot >= topInventory.getSize() && event.isShiftClick()
                    && holder.unlockedRows() * 9 < topInventory.getSize()) {
                event.setCancelled(true);
                moveIntoUnlockedChest(event, topInventory, holder);
                return;
            }
            plugin.getGuiService().queueSharedChestSave(holder);
        }
    }

    private void moveIntoUnlockedChest(InventoryClickEvent event, Inventory chest, SharedChestHolder holder) {
        ItemStack current = event.getCurrentItem();
        if (current == null || current.getType().isAir()) {
            return;
        }
        ItemStack remaining = current.clone();
        int limit = Math.min(chest.getSize(), holder.unlockedRows() * 9);
        for (int slot = 0; slot < limit && remaining.getAmount() > 0; slot++) {
            ItemStack existing = chest.getItem(slot);
            if (existing == null || !existing.isSimilar(remaining)) {
                continue;
            }
            int capacity = Math.min(existing.getMaxStackSize(), chest.getMaxStackSize()) - existing.getAmount();
            if (capacity <= 0) {
                continue;
            }
            int moved = Math.min(capacity, remaining.getAmount());
            ItemStack merged = existing.clone();
            merged.setAmount(existing.getAmount() + moved);
            chest.setItem(slot, merged);
            remaining.setAmount(remaining.getAmount() - moved);
        }
        for (int slot = 0; slot < limit && remaining.getAmount() > 0; slot++) {
            ItemStack existing = chest.getItem(slot);
            if (existing != null && !existing.getType().isAir()) {
                continue;
            }
            int moved = Math.min(remaining.getAmount(), Math.min(remaining.getMaxStackSize(), chest.getMaxStackSize()));
            ItemStack placed = remaining.clone();
            placed.setAmount(moved);
            chest.setItem(slot, placed);
            remaining.setAmount(remaining.getAmount() - moved);
        }
        if (remaining.getAmount() != current.getAmount()) {
            event.setCurrentItem(remaining.getAmount() == 0 ? null : remaining);
            plugin.getGuiService().queueSharedChestSave(holder);
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        Inventory topInventory = event.getView().getTopInventory();
        if (topInventory.getHolder() instanceof EntryBrowserHolder || topInventory.getHolder() instanceof FoGui) {
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
                if (holder.isLocked(slot)) {
                    event.setCancelled(true);
                    return;
                }
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
        if (event.getPlayer() instanceof Player player) {
            plugin.getGuiService().clearScreenTrackingIfClosed(player);
        }
    }
}
