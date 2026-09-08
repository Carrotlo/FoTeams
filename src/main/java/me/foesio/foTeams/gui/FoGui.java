package me.foesio.foTeams.gui;

import me.foesio.core.editor.EditorItemFactory;
import me.foesio.core.gui.FoButtonStyle;
import me.foesio.core.message.FoStyle;
import me.foesio.core.text.FoText;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
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

    /** Applies the shared button presentation only to slots that actually have an action. */
    public void renderActionItems(Player viewer) {
        if (viewer == null) {
            return;
        }
        for (Integer slot : actions.keySet()) {
            renderItem(viewer, slot, "interact");
        }
    }

    /** Applies the shared presentation to non-clickable information cards as well. */
    public void renderInformationItems(Player viewer) {
        if (viewer == null) {
            return;
        }
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            if (actions.containsKey(slot)) {
                continue;
            }
            ItemStack current = inventory.getItem(slot);
            if (current == null || current.getType().isAir()) {
                continue;
            }
            ItemMeta meta = current.getItemMeta();
            String rawName = meta == null ? current.getType().name() : meta.getDisplayName();
            if (FoText.plain(rawName == null ? "" : rawName).trim().isBlank()) {
                continue;
            }
            renderInformationItem(viewer, slot);
        }
    }

    private void renderInformationItem(Player viewer, int slot) {
        ItemStack current = inventory.getItem(slot);
        if (current == null || current.getType().isAir()) {
            return;
        }

        ItemMeta currentMeta = current.getItemMeta();
        String name = currentMeta == null ? current.getType().name() : currentMeta.getDisplayName();
        String label = FoText.plain(name == null ? current.getType().name() : name).trim();
        if (label.isBlank()) {
            return;
        }
        String normalized = label.toLowerCase(Locale.ROOT);
        String color = normalized.contains("delete") || normalized.contains("disband")
                || normalized.contains("purge") ? FoStyle.BAD : FoStyle.THEME;
        List<String> information = currentMeta == null || currentMeta.getLore() == null
                ? new ArrayList<>() : new ArrayList<>(currentMeta.getLore());
        List<String> lore = new ArrayList<>();
        lore.add(FoButtonStyle.BLANK_LINE);
        lore.add(FoButtonStyle.INFO_HEADER);
        for (String line : information) {
            lore.add(FoButtonStyle.informationLine(line));
        }

        ItemStack replacement = EditorItemFactory.item(viewer, current.getType(),
                FoButtonStyle.buttonName(color, label), lore);
        if (currentMeta instanceof SkullMeta oldSkull && replacement.getItemMeta() instanceof SkullMeta newSkull) {
            newSkull.setOwningPlayer(oldSkull.getOwningPlayer());
            replacement.setItemMeta(newSkull);
        }
        inventory.setItem(slot, replacement);
    }

    private void renderItem(Player viewer, int slot, String clickAction) {
        ItemStack current = inventory.getItem(slot);
        if (current == null || current.getType().isAir()) {
            return;
        }

        ItemMeta currentMeta = current.getItemMeta();
        String name = currentMeta == null ? current.getType().name() : currentMeta.getDisplayName();
        String label = FoText.plain(name == null ? current.getType().name() : name).trim();
        if (label.isBlank()) {
            label = current.getType().name();
        }

        String normalized = label.toLowerCase(Locale.ROOT);
        boolean enabled = normalized.endsWith(": on") || normalized.endsWith(" on")
                || normalized.endsWith(": enabled") || normalized.endsWith(" enabled");
        boolean disabled = normalized.endsWith(": off") || normalized.endsWith(" off")
                || normalized.endsWith(": disabled") || normalized.endsWith(" disabled");
        if (enabled || disabled) {
            int separator = label.lastIndexOf(':');
            int suffixStart = separator >= 0 ? separator : label.lastIndexOf(' ');
            label = label.substring(0, Math.max(0, suffixStart)).trim();
        }

        List<String> information = currentMeta == null || currentMeta.getLore() == null
                ? new ArrayList<>() : new ArrayList<>(currentMeta.getLore());
        if ((enabled || disabled) && information.stream().noneMatch(line ->
                FoText.plain(line).trim().toLowerCase(Locale.ROOT).startsWith("state:"))) {
            information.add(0, "State: " + (enabled ? FoStyle.GOOD + "ON" : FoStyle.BAD + "OFF"));
        }

        String color = disabled || normalized.contains("delete") || normalized.contains("disband")
                || normalized.contains("purge") || normalized.contains("cancel")
                ? FoStyle.BAD : enabled || normalized.contains("confirm")
                ? FoStyle.GOOD : FoStyle.THEME;
        ItemStack replacement = EditorItemFactory.button(viewer, current.getType(), color, label,
                information, clickAction);
        if (currentMeta instanceof SkullMeta oldSkull && replacement.getItemMeta() instanceof SkullMeta newSkull) {
            newSkull.setOwningPlayer(oldSkull.getOwningPlayer());
            replacement.setItemMeta(newSkull);
        }
        inventory.setItem(slot, replacement);
    }
}
