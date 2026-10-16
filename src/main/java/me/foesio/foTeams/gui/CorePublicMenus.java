package me.foesio.foTeams.gui;

import me.foesio.core.gui.publicgui.GuiLoadReport;
import me.foesio.core.gui.publicgui.PublicGuiClick;
import me.foesio.core.gui.publicgui.PublicGuiContract;
import me.foesio.core.gui.publicgui.PublicGuiDefinition;
import me.foesio.core.gui.publicgui.PublicGuiFrame;
import me.foesio.core.gui.publicgui.PublicGuiInventoryHolder;
import me.foesio.core.gui.publicgui.PublicGuiRenderInput;
import me.foesio.core.gui.publicgui.PublicGuiRenderer;
import me.foesio.core.gui.publicgui.PublicGuiService;
import me.foesio.foTeams.FoTeams;
import me.foesio.foTeams.listener.GuiListener;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Core owns public inventory definitions, final items, loading, and click routing. */
public final class CorePublicMenus implements AutoCloseable {
    static final List<String> MENUS = List.of("dashboard-empty", "dashboard-team", "settings",
            "members", "transfer-ownership", "transfer-confirm", "warps", "relations", "info",
            "upgrades", "disband-confirm", "chest");
    private static final Set<String> VALUES = Set.of("admins", "ally_list", "balance", "balance_action",
            "color", "cost", "current", "description", "empty_message", "invite_list", "level", "max",
            "max_page", "max_warps", "members", "next", "owner", "page", "player", "rank",
            "required_xp", "role", "score", "status", "tag", "team", "warp", "warps", "xp");

    private final FoTeams plugin;
    private final GuiService gameplay;
    private final Map<String, PublicGuiService> services = new LinkedHashMap<>();
    private final Map<UUID, Session> sessions = new HashMap<>();

    public CorePublicMenus(FoTeams plugin, GuiService gameplay) {
        this.plugin = plugin;
        this.gameplay = gameplay;
        Path root = plugin.getDataFolder().toPath().resolve("guis");
        for (String menu : MENUS) {
            String id = id(menu);
            PublicGuiService service = new PublicGuiService(plugin);
            service.menu(id, root.resolve(menu + ".yml"), contract(menu))
                    .entryPoint("GuiService." + menu, id);
            service.action("click", this::handleClick);
            if (menu.equals("chest")) {
                service.requireState(id, "locked");
                service.pluginRoute(id, new GuiListener(plugin));
            } else if (Set.of("members", "transfer-ownership", "warps", "relations", "info").contains(menu)) {
                service.requireEntry(id, "content", 0);
            }
            service.start();
            service.reload();
            services.put(menu, service);
        }
    }

    private static String id(String menu) {
        return "teams." + menu;
    }

    static PublicGuiContract contract(String menu) {
        return menu.equals("chest")
                ? new PublicGuiContract(Set.of(), Map.of("storage-slots",
                    PublicGuiContract.GroupRule.exactly(PublicGuiContract.GroupRole.SHARED_STORAGE, 54)),
                    VALUES, PublicGuiContract.InteractionMode.PLUGIN_OWNED)
                : new PublicGuiContract(Set.of("click"), Map.of(), VALUES,
                    PublicGuiContract.InteractionMode.CORE);
    }

    public GuiLoadReport reload() {
        GuiLoadReport result = null;
        boolean successful = true;
        for (PublicGuiService service : services.values()) {
            GuiLoadReport next = service.reload();
            if (!next.successful()) {
                successful = false;
                result = next;
            } else if (result == null) {
                result = next;
            }
        }
        if (successful) {
            // Core rejects clicks from the old revision. Close stale read-only
            // views so players can reopen a freshly rendered screen.
            for (Player viewer : Bukkit.getOnlinePlayers()) {
                if (viewer.getOpenInventory().getTopInventory().getHolder()
                        instanceof PublicGuiInventoryHolder holder
                        && holder.frame().definition().menuId().startsWith("teams.")) {
                    viewer.closeInventory();
                }
            }
            sessions.clear();
        }
        return result;
    }

    public boolean validateInstalled() {
        boolean valid = true;
        for (PublicGuiService service : services.values()) {
            valid &= service.validateInstalled().successful();
        }
        return valid;
    }

    public boolean available() {
        return services.values().stream().anyMatch(service -> service.snapshot() != null);
    }

    public boolean available(String menu) {
        PublicGuiService service = services.get(menu);
        if (service == null || service.snapshot() == null) {
            return false;
        }
        if (Set.of("members", "transfer-ownership", "warps", "relations", "info").contains(menu)) {
            PublicGuiDefinition.Entry content = service.snapshot().require(id(menu)).entries().get("content");
            return content != null && !content.slots().isEmpty();
        }
        return true;
    }

    public void forget(UUID playerId) {
        sessions.remove(playerId);
    }

    public PublicGuiFrame planChest(Map<String, String> values) {
        return services.get("chest").plan(id("chest"), new PublicGuiRenderInput(
                titleValues(values), Map.of(), Map.of(), Map.of()));
    }

    public List<Integer> contentSlots(String menu) {
        PublicGuiService service = services.get(menu);
        if (service == null || service.snapshot() == null) {
            return List.of();
        }
        PublicGuiDefinition definition = service.snapshot().require(id(menu));
        PublicGuiDefinition.Entry region = definition.entries().get("content");
        return region == null ? List.of() : region.slots();
    }

    public boolean usesValue(String menu, String value) {
        PublicGuiService service = services.get(menu);
        if (service == null || service.snapshot() == null) {
            return false;
        }
        PublicGuiDefinition definition = service.snapshot().require(id(menu));
        String token = "{" + value + "}";
        return definition.title().contains(token)
                || definition.states().values().stream().anyMatch(state -> state.name().contains(token)
                        || state.lore().stream().anyMatch(line -> line.contains(token)));
    }

    public void open(Player viewer, FoGui collector) {
        String menu = collector.publicMenuId();
        if (!available(menu)) {
            plugin.getLogger().warning("Public GUI " + menu + " is unavailable: no valid Core snapshot.");
            plugin.sendMessage(viewer, "public-gui-unavailable");
            return;
        }
        PublicGuiService service = services.get(menu);
        Map<String, String> values = titleValues(collector.publicValues());
        PublicGuiFrame emptyFrame = service.plan(id(menu), new PublicGuiRenderInput(
                values, Map.of(), Map.of(), Map.of()));
        Map<String, Map<Integer, PublicGuiRenderInput.EntryValue>> entries = new LinkedHashMap<>();
        Map<String, Integer> nextIndex = new HashMap<>();
        for (int oldSlot = 0; oldSlot < collector.getInventory().getSize(); oldSlot++) {
            collectEntry(viewer, menu, emptyFrame, collector.getInventory().getItem(oldSlot), oldSlot,
                    values, entries, nextIndex);
        }
        for (FoGui.PublicEntry entry : collector.publicEntries()) {
            collectEntry(viewer, menu, emptyFrame, entry.item(), entry.callbackId(),
                    values, entries, nextIndex);
        }
        PublicGuiRenderInput input = new PublicGuiRenderInput(values, Map.of(), Map.of(), entries);
        sessions.put(viewer.getUniqueId(), new Session(menu, collector));
        try {
            service.open(viewer, id(menu), input);
        } catch (RuntimeException exception) {
            sessions.remove(viewer.getUniqueId());
            throw exception;
        }
    }

    static Map<String, String> titleValues(Map<String, String> context) {
        Map<String, String> values = new HashMap<>();
        VALUES.forEach(key -> values.put(key, ""));
        values.putAll(context);
        return Map.copyOf(values);
    }

    private void collectEntry(Player viewer, String menu, PublicGuiFrame frame, ItemStack markerItem,
                              int callbackId, Map<String, String> menuValues,
                              Map<String, Map<Integer, PublicGuiRenderInput.EntryValue>> entries,
                              Map<String, Integer> nextIndex) {
        GuiService.PublicItemMarker marker = gameplay.takePublicMarker(markerItem);
        if (marker == null) {
            return;
        }
        String regionId = region(menu, marker.screen(), marker.key());
        PublicGuiDefinition.Entry region = frame.definition().entries().get(regionId);
        if (region == null) {
            return;
        }
        String stateId = state(menu, marker.screen(), marker.key());
        if (!frame.definition().states().containsKey(stateId)) {
            return;
        }
        int index = nextIndex.merge(regionId, 1, Integer::sum) - 1;
        if (index >= region.slots().size()) {
            throw new IllegalStateException(menu + ": entry region " + regionId + " has too few configured slots");
        }
        int slot = region.slots().get(index);
        Map<String, String> itemValues = new HashMap<>(menuValues);
        itemValues.putAll(marker.values());
        ItemStack source = PublicGuiRenderer.state(viewer, frame, stateId, itemValues);
        if (source.getItemMeta() instanceof SkullMeta resultMeta
                && markerItem.getItemMeta() instanceof SkullMeta originalMeta
                && originalMeta.getOwningPlayer() != null) {
            resultMeta.setOwningPlayer(originalMeta.getOwningPlayer());
            source.setItemMeta(resultMeta);
        }
        entries.computeIfAbsent(regionId, ignored -> new LinkedHashMap<>())
                .put(slot, new PublicGuiRenderInput.EntryValue(String.valueOf(callbackId), source, Map.of()));
    }

    private void handleClick(PublicGuiClick click) {
        Session session = sessions.get(click.player().getUniqueId());
        if (session == null || !id(session.menu()).equals(click.menuId()) || click.entryId() == null) {
            return;
        }
        int oldSlot;
        try {
            oldSlot = Integer.parseInt(click.entryId());
        } catch (NumberFormatException exception) {
            return;
        }
        if (!session.collector().hasAction(oldSlot)) {
            return;
        }
        ClickType type = ClickType.valueOf(click.clickKind().name());
        session.collector().click(oldSlot, type, click.player());
    }

    private static String region(String menu, String screen, String key) {
        if (screen.equals("navigation")) {
            return key;
        }
        return switch (menu) {
            case "dashboard-empty" -> key.equals("invites-empty") ? "invites" : key;
            case "dashboard-team" -> key.equals("balance-unavailable") ? "balance" : key;
            case "settings" -> key.startsWith("pvp-") ? "pvp" : key;
            case "members", "transfer-ownership", "relations" ->
                    key.startsWith("entry") || key.equals("empty") || key.equals("owner")
                            || key.equals("target") ? "content" : key;
            case "warps" -> key.equals("home-missing") ? "home"
                    : key.equals("warp") || key.equals("warp-protected") ? "content" : key;
            case "info" -> key.equals("team-with-level") ? "team"
                    : key.equals("member") || key.equals("empty") ? "content" : key;
            case "upgrades" -> key.equals("size-maxed") ? "size"
                    : key.equals("echest-maxed") ? "echest" : key;
            case "transfer-confirm" -> key.equals("transfer-confirm") ? "confirm"
                    : key.equals("transfer-target") ? "target"
                    : key.equals("transfer-cancel") ? "cancel" : key;
            default -> key;
        };
    }

    private static String state(String menu, String screen, String key) {
        return menu.equals("transfer-confirm") ? region(menu, screen, key) : key;
    }

    @Override
    public void close() {
        sessions.clear();
        services.values().forEach(PublicGuiService::close);
    }

    private record Session(String menu, FoGui collector) {
    }
}
