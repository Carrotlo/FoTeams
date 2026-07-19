package me.foesio.foTeams.gui;

import me.foesio.core.gui.GuiButtonConfig;
import me.foesio.core.gui.GuiTitles;
import me.foesio.core.item.FoItemStacks;
import me.foesio.core.number.LargeNumberParser;
import me.foesio.core.sound.SoundTypes;
import me.foesio.core.text.FoText;
import me.foesio.core.text.PromptNormalizer;
import me.foesio.core.editor.EditorItemFactory;
import me.foesio.foTeams.FoTeams;
import me.foesio.foTeams.input.BalanceDialogAction;
import me.foesio.foTeams.input.BalanceDialogRequest;
import me.foesio.foTeams.input.BalanceDialogService;
import me.foesio.foTeams.input.BalanceDialogServices;
import me.foesio.foTeams.input.InputPrompt;
import me.foesio.foTeams.model.RelationType;
import me.foesio.foTeams.model.Team;
import me.foesio.foTeams.model.TeamAction;
import me.foesio.foTeams.model.TeamInvite;
import me.foesio.foTeams.model.TeamRole;
import me.foesio.foTeams.util.TagColorUtil;
import me.foesio.foTeams.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;

public final class GuiService {
    private static final int[] EDITOR_CONTENT_SLOTS = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34, 37, 38, 39, 40, 41, 42, 43};
    private static final int[] EDITOR_HOME_SLOTS = {11, 12, 13, 14, 15, 16};

    private final FoTeams plugin;
    private final GuiButtonConfig buttons = GuiButtonConfig.defaults();
    private final Map<Integer, SharedChestHolder> sharedChests = new HashMap<>();
    private final Set<SharedChestHolder> pendingSharedChestSaves = Collections.newSetFromMap(new IdentityHashMap<>());
    private final List<ConfigCategory> configCategories;

    public GuiService(FoTeams plugin) {
        this.plugin = plugin;
        this.configCategories = createConfigCategories();
    }

    public boolean suppressNextInventoryClose(Player player) {
        Inventory topInventory = player.getOpenInventory().getTopInventory();
        if (!(topInventory.getHolder() instanceof FoGui || topInventory.getHolder() instanceof SharedChestHolder)) {
            return false;
        }
        plugin.getCore().inventoryCloseSuppressor().suppressNextClose(player);
        return true;
    }

    public boolean consumeSuppressedClose(Player player) {
        return plugin.getCore().inventoryCloseSuppressor().consumeSuppressedClose(player);
    }

    public void clearNativeDialogCloseSuppression(Player player) {
        plugin.getCore().inventoryCloseSuppressor().clear(player);
    }

    public void clearNativeDialogCloseSuppressionLater(Player player, long ticks) {
        plugin.getCore().inventoryCloseSuppressor().clearLater(plugin, player, ticks);
    }

    public void openDashboard(Player player) {
        Optional<Team> optionalTeam = plugin.getTeamService().teamOf(player.getUniqueId());
        FoGui gui = gui(optionalTeam.isEmpty() ? 27 : 45, plugin.getConfig().getString("gui.titles.dashboard", "FoTeams"));
        fill(gui);
        if (optionalTeam.isEmpty()) {
            gui.getInventory().setItem(11, item(Material.EMERALD, "#03fc88Create Team", List.of("#ffffffCreate your own team.", "#a7b8b0Click to type a team name.")));
            gui.setAction(11, event -> promptText(player, "prompt-name", input -> {
                try {
                    if (!validateTeamName(player, input)) {
                        return;
                    }
                    if (plugin.getTeamService().isNameTaken(input)) {
                        plugin.getMessages().send(player, "team-name-taken");
                        return;
                    }
                    Team created = plugin.getTeamService().createTeam(player, input);
                    plugin.getMessages().send(player, "team-created", Map.of("{team}", created.getName()));
                    openDashboard(player);
                } catch (Exception exception) {
                    plugin.getLogger().log(Level.WARNING, "Failed to create team from dashboard for " + player.getName() + ".", exception);
                    plugin.getMessages().send(player, "team-create-failed");
                }
            }));
            List<TeamInvite> invites = plugin.getTeamService().invites(player.getUniqueId());
            gui.getInventory().setItem(15, item(Material.WRITABLE_BOOK, "#03fc88Invites", invites.isEmpty()
                    ? List.of("#ffffffNo pending team invites.")
                    : invites.stream().limit(5).map(invite -> "#ffffff" + plugin.getTeamService().byId(invite.teamId()).map(Team::getName).orElse("Unknown")).toList()));
            gui.setAction(15, event -> {
                if (!invites.isEmpty()) {
                    Team team = plugin.getTeamService().byId(invites.getFirst().teamId()).orElse(null);
                    if (team != null) {
                        try {
                            plugin.getTeamService().join(player, team);
                            broadcastTeamMessage(team, "member-joined", Map.of("{player}", player.getName(), "{team}", team.getName()));
                            openDashboard(player);
                        } catch (IllegalStateException exception) {
                            plugin.getMessages().send(player, "team-full");
                        } catch (SQLException exception) {
                            plugin.getLogger().log(Level.WARNING, "Failed to join invited team " + team.getId() + " for " + player.getName() + ".", exception);
                            plugin.getMessages().send(player, "action-failed");
                        }
                    }
                }
            });
        } else {
            Team team = optionalTeam.get();
            gui.getInventory().setItem(10, item(Material.NAME_TAG, accent("Team Info"), List.of(
                    "#ffffffName: " + accent(team.getName()),
                    "#ffffffTag: " + tag(team),
                    "#ffffffDescription: #ffffff" + team.getDescription())));
            gui.setAction(10, event -> openInfo(player, team));
            gui.getInventory().setItem(12, item(Material.COMPARATOR, accent("Settings"), List.of("#ffffffManage your team.")));
            gui.setAction(12, event -> openSettings(player, team, false));
            gui.getInventory().setItem(14, item(Material.PLAYER_HEAD, accent("Members"), List.of(
                    "#ffffffOwner: " + playerName(team.getOwnerId()),
                    "#ffffffAdmins: " + team.getAdmins().size(),
                    "#ffffffMembers: " + team.getMembers().size())));
            gui.setAction(14, event -> openMembers(player, team, false));
            gui.getInventory().setItem(16, item(Material.ENDER_PEARL, accent("Warps"), List.of(
                    "#ffffffHome and warp travel.",
                    "#ffffffWarps: " + team.getWarps().size() + "/" + plugin.getTeamService().maxWarps())));
            gui.setAction(16, event -> openWarps(player, team, false));
            gui.getInventory().setItem(28, item(Material.EMERALD, accent("Score Top"), List.of(
                    "#ffffffScore: " + team.getScore(),
                    "#ffffffRank: " + plugin.getTeamService().scoreRank(team))));
            gui.setAction(28, event -> player.performCommand("team top"));
            gui.getInventory().setItem(30, item(Material.GOLD_INGOT, accent("Balance"), List.of(
                    "#ffffffBank: " + Text.money(team.getBalance()),
                    plugin.getEconomyService().isEnabled() ? "#ffffffClick to deposit or withdraw." : "#ff5d73Vault not installed.")));
            gui.setAction(30, event -> openBalanceDialog(player, team));
            gui.getInventory().setItem(32, item(Material.ENDER_CHEST, accent("Shared Chest"), List.of("#ffffffOpen your team ender chest.")));
            gui.setAction(32, event -> openSharedChest(player, team, false));
            gui.getInventory().setItem(34, item(Material.ANVIL, accent("Upgrades"), List.of("#ffffffUpgrade team size and shared chest size.")));
            gui.setAction(34, event -> openUpgrades(player, team, false));
            gui.getInventory().setItem(40, emptyInfoPane());
            gui.getInventory().setItem(42, emptyInfoPane());
        }
        player.openInventory(gui.getInventory());
        sound(player, "gui-open");
    }

    private void openBalanceDialog(Player player, Team team) {
        if (!plugin.getEconomyService().isEnabled()) {
            plugin.getMessages().send(player, "economy-disabled");
            return;
        }

        boolean canDeposit = plugin.getTeamService().can(team, player.getUniqueId(), TeamAction.DEPOSIT);
        boolean canWithdraw = plugin.getTeamService().can(team, player.getUniqueId(), TeamAction.WITHDRAW);
        if (!canDeposit && !canWithdraw) {
            plugin.getMessages().send(player, "no-permission-role");
            return;
        }

        BalanceDialogRequest request = new BalanceDialogRequest(
                Text.money(team.getBalance()),
                Text.money(plugin.getEconomyService().balance(player)),
                canDeposit,
                canWithdraw
        );
        BalanceDialogService service = BalanceDialogServices.create(plugin, plugin.getCore());
        boolean nativeAttempt = service.canOpenNative();
        boolean suppressedClose = false;
        if (nativeAttempt) {
            suppressedClose = suppressNextInventoryClose(player);
            player.closeInventory();
        }

        boolean openedNative = false;
        try {
            openedNative = service.open(player, request, action -> handleBalanceAction(player, team, action));
        } finally {
            if (suppressedClose) {
                if (openedNative) {
                    clearNativeDialogCloseSuppressionLater(player, 5L);
                } else {
                    clearNativeDialogCloseSuppression(player);
                }
            }
        }
        if (!openedNative) {
            openBalanceChatFallback(player, team, canDeposit, canWithdraw);
        }
    }

    private void openBalanceChatFallback(Player player, Team team, boolean canDeposit, boolean canWithdraw) {
        player.closeInventory();
        plugin.getPromptService().setPrompt(player, plugin.getMessages().render("prompt-bank-action", "prompt-bank-action", Map.of(
                "{actions}", bankActionsText(canDeposit, canWithdraw)
        )), input -> {
            if (PromptNormalizer.isCancel(input)) {
                plugin.getMessages().send(player, "prompt-cancelled");
                return;
            }
            String[] parts = input.trim().split("\\s+", 2);
            if (parts.length < 2) {
                plugin.getMessages().send(player, "bank-action-invalid", Map.of("{actions}", bankActionsText(canDeposit, canWithdraw)));
                return;
            }
            String action = parts[0].toLowerCase(Locale.ROOT);
            if ("deposit".equals(action)) {
                if (!canDeposit) {
                    plugin.getMessages().send(player, "no-permission-role");
                    return;
                }
                handleBalanceAction(player, team, BalanceDialogAction.deposit(parts[1]));
                return;
            }
            if ("withdraw".equals(action)) {
                if (!canWithdraw) {
                    plugin.getMessages().send(player, "no-permission-role");
                    return;
                }
                handleBalanceAction(player, team, BalanceDialogAction.withdraw(parts[1]));
                return;
            }
            plugin.getMessages().send(player, "bank-action-invalid", Map.of("{actions}", bankActionsText(canDeposit, canWithdraw)));
        });
    }

    private void handleBalanceAction(Player player, Team team, BalanceDialogAction action) {
        if (action == null) {
            return;
        }
        if (action.type() == BalanceDialogAction.Type.BACK) {
            openDashboard(player);
            return;
        }
        Team currentTeam = currentTeam(player, team);
        if (currentTeam == null) {
            plugin.getMessages().send(player, "not-in-team");
            return;
        }
        Double amount = parseBalanceAmount(player, action.amount());
        if (amount == null) {
            return;
        }
        try {
            if (action.type() == BalanceDialogAction.Type.DEPOSIT) {
                plugin.getCommandTeam().depositAmount(player, currentTeam, amount);
            } else if (action.type() == BalanceDialogAction.Type.WITHDRAW) {
                plugin.getCommandTeam().withdrawAmount(player, currentTeam, amount);
            }
        } catch (SQLException exception) {
            plugin.getLogger().log(Level.WARNING, "Failed to apply team bank action for team " + currentTeam.getId() + ".", exception);
            plugin.getMessages().send(player, "action-failed");
        }
    }

    private Team currentTeam(Player player, Team openedTeam) {
        Optional<Team> currentTeam = plugin.getTeamService().teamOf(player.getUniqueId());
        return currentTeam.filter(team -> team.getId() == openedTeam.getId()).orElse(null);
    }

    private Double parseBalanceAmount(Player player, String input) {
        var parsed = LargeNumberParser.parseDouble(input);
        if (parsed.isEmpty()) {
            plugin.getMessages().send(player, "money-invalid");
            return null;
        }
        return parsed.getAsDouble();
    }

    private String bankActionsText(boolean canDeposit, boolean canWithdraw) {
        if (canDeposit && canWithdraw) {
            return "{white}deposit <amount>{muted} or {white}withdraw <amount>";
        }
        if (canDeposit) {
            return "{white}deposit <amount>";
        }
        return "{white}withdraw <amount>";
    }

    public void openSettings(Player player, Team team, boolean adminView) {
        openSettings(player, team, adminView, null);
    }

    private void openSettings(Player player, Team team, boolean adminView, AdminEditorContext adminContext) {
        FoGui gui = gui(36, plugin.getConfig().getString("gui.titles.settings", "Settings"));
        fill(gui);
        gui.getInventory().setItem(10, item(Material.NAME_TAG, accent("Name"), List.of("#ffffffCurrent: " + accent(team.getName()), "#ffffffClick to rename.")));
        gui.setAction(10, event -> promptText(player, "prompt-name", team.getName(), input -> {
            if (!adminView && !plugin.getTeamService().can(team, player.getUniqueId(), TeamAction.CHANGE_NAME)) {
                plugin.getMessages().send(player, "no-permission-role");
                return;
            }
            if (!validateTeamName(player, input)) {
                return;
            }
            if (plugin.getTeamService().isNameTaken(input)) {
                plugin.getMessages().send(player, "team-name-taken");
                return;
            }
            try {
                plugin.getTeamService().updateName(team, input);
                plugin.getMessages().send(player, "name-updated", Map.of("{team}", input));
                openSettings(player, team, adminView, adminContext);
            } catch (SQLException exception) {
                plugin.getLogger().log(Level.WARNING, "Failed to update team name for team " + team.getId() + ".", exception);
                plugin.getMessages().send(player, "settings-save-failed");
            }
        }));
        gui.getInventory().setItem(11, item(Material.OAK_SIGN, accent("Tag"), List.of("#ffffffCurrent: " + tag(team), "#ffffffClick to edit the tag.")));
        gui.setAction(11, event -> promptText(player, "prompt-tag", team.getTag(), input -> mutate(player, team, adminView, TeamAction.CHANGE_TAG, () -> {
            if (!plugin.getTeamService().isValidTag(input)) {
                plugin.getMessages().send(player, "tag-invalid", Map.of("{max}", String.valueOf(plugin.getTeamService().maxTagLength())));
                return;
            }
            if (!validateAllowedTeamText(player, input)) {
                return;
            }
            plugin.getTeamService().updateTag(team, input.toUpperCase(Locale.ROOT));
            plugin.getMessages().send(player, "tag-updated", Map.of("{tag}", input.toUpperCase(Locale.ROOT)));
            openSettings(player, team, adminView, adminContext);
        })));
        gui.getInventory().setItem(12, item(Material.BOOK, accent("Description"), List.of("#ffffffCurrent: #ffffff" + team.getDescription(), "#ffffffClick to edit.")));
        gui.setAction(12, event -> promptText(player, "prompt-description", team.getDescription(), input -> mutate(player, team, adminView, TeamAction.CHANGE_DESCRIPTION, () -> {
            if (!validateAllowedTeamText(player, input)) {
                return;
            }
            plugin.getTeamService().updateDescription(team, input);
            plugin.getMessages().send(player, "description-updated");
            openSettings(player, team, adminView, adminContext);
        })));
        gui.getInventory().setItem(13, item(Material.LEATHER_CHESTPLATE, accent("Tag Color"), List.of("#ffffffCurrent: " + team.getColor(), "#ffffffClick to change the tag color.")));
        gui.setAction(13, event -> promptText(player, "prompt-tag-color", team.getColor(), input -> mutate(player, team, adminView, TeamAction.CHANGE_COLOR, () -> {
            if (!plugin.getTeamService().updateTagColor(team, input)) {
                plugin.getMessages().send(player, "tag-color-invalid");
                return;
            }
            plugin.getMessages().send(player, "tag-color-updated", Map.of("{color}", team.getColor()));
            openSettings(player, team, adminView, adminContext);
        })));
        gui.getInventory().setItem(15, item(Material.LEAD, accent("Relations"), List.of("#ffffffManage allies and enemies.")));
        gui.setAction(15, event -> openRelations(player, team, adminView, 0, "", adminContext));
        gui.getInventory().setItem(21, item(Material.TOTEM_OF_UNDYING, accent("Transfer Ownership"), List.of("#ffffffChoose the next team owner.", "#a7b8b0Requires transfer permission.")));
        gui.setAction(21, event -> openTransferOwnership(player, team, adminView, 0, adminContext));
        boolean pvpForceLock = plugin.getConfig().getBoolean("team-pvp-force-disable-all", false);
        boolean pvpProtected = !pvpForceLock && team.isTeamPvpProtectionEnabled();
        gui.getInventory().setItem(16, item(pvpProtected ? Material.LIME_DYE : Material.GRAY_DYE,
                pvpProtected ? "#3ecf8eTeam PvP Protection Enabled" : "#ff5d73Team PvP Protection Disabled",
                List.of(
                        "#ffffffEnabled: teammates cannot damage each other.",
                        "#ffffffDisabled: teammates can damage each other.",
                        pvpForceLock ? "#ff5d73Forced disabled by global config." : "#ffffffClick to toggle."
                )));
        gui.setAction(16, event -> {
            if (!adminView && !canManageTeamPvp(team, player.getUniqueId())) {
                plugin.getMessages().send(player, "no-permission-role");
                return;
            }
            if (plugin.getConfig().getBoolean("team-pvp-force-disable-all", false)) {
                return;
            }
            try {
                boolean enabled = !team.isTeamPvpProtectionEnabled();
                if (!plugin.getTeamService().setTeamPvpProtection(team, enabled)) {
                    plugin.getMessages().send(player, enabled ? "pvp-protection-already-enabled" : "pvp-protection-already-disabled");
                    return;
                }
                broadcastTeamMessage(team, enabled ? "pvp-protection-enabled" : "pvp-protection-disabled", Map.of());
                openSettings(player, team, adminView, adminContext);
            } catch (SQLException exception) {
                plugin.getLogger().log(Level.WARNING, "Failed to toggle PvP protection for team " + team.getId() + ".", exception);
                plugin.getMessages().send(player, "settings-save-failed");
            }
        });
        gui.getInventory().setItem(23, item(Material.REDSTONE_BLOCK, adminView ? "#ff5d73Delete Team" : "#ff5d73Disband Team", List.of("#ffffffClick to confirm.")));
        gui.setAction(23, event -> openConfirm(player, team, adminView, adminContext, ConfirmBackTarget.SETTINGS));
        setBackButton(gui, () -> {
            if (adminView) {
                openAdminEditor(player, team, contextOrDefault(adminContext));
            } else {
                openDashboard(player);
            }
        });
        player.openInventory(gui.getInventory());
        sound(player, "gui-open");
    }

    public void openMembers(Player player, Team team, boolean adminView) {
        openMembers(player, team, adminView, 0, null);
    }

    public void openMembers(Player player, Team team, boolean adminView, int page) {
        openMembers(player, team, adminView, page, null);
    }

    private void openMembers(Player player, Team team, boolean adminView, int page, AdminEditorContext adminContext) {
        List<MemberView> members = memberEntries(team);
        int rows = pagedRows(members.size());
        List<Integer> contentSlots = pagedContentSlots(rows);
        int pageSize = contentSlots.size();
        int maxPage = members.isEmpty() ? 0 : (members.size() - 1) / pageSize;
        int currentPage = Math.max(0, Math.min(page, maxPage));
        int start = currentPage * pageSize;

        FoGui gui = gui(rows * 9, plugin.getConfig().getString("gui.titles.members", "Members"));
        fill(gui);
        gui.getInventory().setItem(4, item(Material.NETHER_STAR, accent("Team Members"), List.of(
                "#ffffffLeft-click: promote or demote",
                "#ffffffRight-click: kick",
                "#ffffffShift-right: transfer ownership with confirm")));

        if (members.isEmpty()) {
            gui.getInventory().setItem(13, item(Material.GRAY_DYE, "#a7b8b0No Members", List.of("#ffffffThis team does not currently have members.")));
        } else {
            for (int index = 0; index < contentSlots.size(); index++) {
                int memberIndex = start + index;
                if (memberIndex >= members.size()) {
                    break;
                }
                MemberView member = members.get(memberIndex);
                int slot = contentSlots.get(index);
                gui.getInventory().setItem(slot, playerHead(member.id(), accent(member.role().displayName() + ": " + playerName(member.id())), memberActionLore(member)));
                gui.setAction(slot, event -> handleMemberClick(player, team, adminView, adminContext, member, event.getClick(), currentPage));
            }
        }

        int lastRowStart = gui.getInventory().getSize() - 9;
        if (currentPage > 0) {
            gui.getInventory().setItem(lastRowStart, previousPageButton(currentPage - 1, maxPage));
            gui.setAction(lastRowStart, event -> openMembers(player, team, adminView, currentPage - 1, adminContext));
        }
        setBackButton(gui, () -> {
            if (adminView) {
                openAdminEditor(player, team, contextOrDefault(adminContext));
            } else {
                openDashboard(player);
            }
        });
        if (start + pageSize < members.size()) {
            int nextSlot = gui.getInventory().getSize() - 1;
            gui.getInventory().setItem(nextSlot, nextPageButton(currentPage + 1, maxPage));
            gui.setAction(nextSlot, event -> openMembers(player, team, adminView, currentPage + 1, adminContext));
        }
        player.openInventory(gui.getInventory());
        sound(player, "gui-open");
    }

    private List<MemberView> memberEntries(Team team) {
        List<MemberView> members = new ArrayList<>();
        if (team.hasOwner()) {
            members.add(new MemberView(team.getOwnerId(), TeamRole.OWNER));
        }
        team.getAdmins().stream()
                .sorted()
                .map(playerId -> new MemberView(playerId, TeamRole.ADMIN))
                .forEach(members::add);
        team.getMembers().stream()
                .sorted()
                .map(playerId -> new MemberView(playerId, TeamRole.MEMBER))
                .forEach(members::add);
        return members;
    }

    private List<String> memberActionLore(MemberView member) {
        List<String> lore = new ArrayList<>();
        lore.add("#ffffffRole: #03fc88" + member.role().displayName());
        if (member.role() == TeamRole.OWNER) {
            lore.add("#a7b8b0Current owner.");
            return lore;
        }
        if (member.role() == TeamRole.ADMIN) {
            lore.add("#ffffffLeft-click to demote.");
        } else {
            lore.add("#ffffffLeft-click to promote.");
        }
        lore.add("#ffffffRight-click to kick.");
        lore.add("#ffffffShift-right to confirm ownership transfer.");
        return lore;
    }

    private void openTransferOwnership(Player player, Team team, boolean adminView) {
        openTransferOwnership(player, team, adminView, 0, null);
    }

    private void openTransferOwnership(Player player, Team team, boolean adminView, int page) {
        openTransferOwnership(player, team, adminView, page, null);
    }

    private void openTransferOwnership(Player player, Team team, boolean adminView, int page, AdminEditorContext adminContext) {
        if (!canTransferOwnership(player, team, adminView)) {
            return;
        }
        List<MemberView> candidates = transferOwnershipCandidates(team);
        int rows = pagedRows(candidates.size());
        List<Integer> contentSlots = pagedContentSlots(rows);
        int pageSize = contentSlots.size();
        int maxPage = candidates.isEmpty() ? 0 : (candidates.size() - 1) / pageSize;
        int currentPage = Math.max(0, Math.min(page, maxPage));
        int start = currentPage * pageSize;

        FoGui gui = gui(rows * 9, plugin.getConfig().getString("gui.titles.transfer-ownership", "Transfer Ownership"));
        fill(gui);
        if (candidates.isEmpty()) {
            gui.getInventory().setItem(13, item(Material.GRAY_DYE, "#a7b8b0No Transfer Targets", List.of(
                    "#ffffffInvite another player before transferring ownership.")));
        } else {
            for (int index = 0; index < contentSlots.size(); index++) {
                int candidateIndex = start + index;
                if (candidateIndex >= candidates.size()) {
                    break;
                }
                MemberView candidate = candidates.get(candidateIndex);
                int slot = contentSlots.get(index);
                if (candidate.role() == TeamRole.OWNER) {
                    gui.getInventory().setItem(slot, playerHead(candidate.id(), accent("Current Owner: " + playerName(candidate.id())), List.of(
                            "#ffffffCurrent role: #03fc88" + candidate.role().displayName(),
                            "#a7b8b0Already owns this team.")));
                    gui.setAction(slot, event -> plugin.getMessages().send(player, "member-owner-protected"));
                } else {
                    gui.getInventory().setItem(slot, playerHead(candidate.id(), accent(playerName(candidate.id())), List.of(
                            "#ffffffCurrent role: #03fc88" + candidate.role().displayName(),
                            "#ffffffClick to transfer ownership.")));
                    gui.setAction(slot, event -> openTransferOwnershipConfirm(player, team, adminView, adminContext, candidate.id(), currentPage, TransferConfirmOrigin.TRANSFER_GUI));
                }
            }
        }

        int lastRowStart = gui.getInventory().getSize() - 9;
        if (currentPage > 0) {
            gui.getInventory().setItem(lastRowStart, previousPageButton(currentPage - 1, maxPage));
            gui.setAction(lastRowStart, event -> openTransferOwnership(player, team, adminView, currentPage - 1, adminContext));
        }
        setBackButton(gui, () -> openSettings(player, team, adminView, adminContext));
        if (start + pageSize < candidates.size()) {
            int nextSlot = gui.getInventory().getSize() - 1;
            gui.getInventory().setItem(nextSlot, nextPageButton(currentPage + 1, maxPage));
            gui.setAction(nextSlot, event -> openTransferOwnership(player, team, adminView, currentPage + 1, adminContext));
        }
        player.openInventory(gui.getInventory());
        sound(player, "gui-open");
    }

    private void openTransferOwnershipConfirm(Player player, Team team, boolean adminView, UUID newOwner, int page) {
        openTransferOwnershipConfirm(player, team, adminView, null, newOwner, page, TransferConfirmOrigin.TRANSFER_GUI);
    }

    private void openTransferOwnershipConfirm(Player player, Team team, boolean adminView, AdminEditorContext adminContext, UUID newOwner, int page, TransferConfirmOrigin origin) {
        if (!canTransferOwnership(player, team, adminView)) {
            return;
        }
        if (!canTransferTo(team, newOwner)) {
            plugin.getMessages().send(player, "member-owner-protected");
            openTransferOrigin(player, team, adminView, adminContext, page, origin);
            return;
        }

        TeamRole targetRole = team.roleOf(newOwner);
        FoGui gui = gui(27, plugin.getConfig().getString("gui.titles.confirm", "Confirm"));
        fill(gui);
        gui.getInventory().setItem(11, item(Material.LIME_DYE, "#3ecf8eConfirm", List.of(
                "#ffffffTransfer ownership to #03fc88" + playerName(newOwner) + "#ffffff.")));
        gui.setAction(11, event -> confirmTransferOwnership(player, team, adminView, adminContext, newOwner, page, origin));
        gui.getInventory().setItem(13, playerHead(newOwner, accent(playerName(newOwner)), List.of(
                "#ffffffCurrent role: #03fc88" + targetRole.displayName(),
                "#ffffffThis player will become the owner.")));
        gui.getInventory().setItem(15, item(Material.RED_DYE, "#ff5d73Cancel", List.of("#ffffffReturn without changing ownership.")));
        gui.setAction(15, event -> openTransferOrigin(player, team, adminView, adminContext, page, origin));
        player.openInventory(gui.getInventory());
        sound(player, "gui-open");
    }

    private void confirmTransferOwnership(Player player, Team team, boolean adminView, UUID newOwner, int page) {
        confirmTransferOwnership(player, team, adminView, null, newOwner, page, TransferConfirmOrigin.TRANSFER_GUI);
    }

    private void confirmTransferOwnership(Player player, Team team, boolean adminView, AdminEditorContext adminContext, UUID newOwner, int page, TransferConfirmOrigin origin) {
        if (!canTransferOwnership(player, team, adminView)) {
            return;
        }
        if (!canTransferTo(team, newOwner)) {
            plugin.getMessages().send(player, "member-owner-protected");
            openTransferOrigin(player, team, adminView, adminContext, page, origin);
            return;
        }
        try {
            plugin.getTeamService().transfer(team, newOwner);
            plugin.getMessages().send(player, "transfer-success", Map.of("{player}", playerName(newOwner)));
            if (origin == TransferConfirmOrigin.MEMBERS) {
                openMembers(player, team, adminView, page, adminContext);
            } else {
                openSettings(player, team, adminView, adminContext);
            }
        } catch (SQLException exception) {
            plugin.getLogger().log(Level.WARNING, "Failed to transfer ownership for team " + team.getId() + ".", exception);
            plugin.getMessages().send(player, "member-action-failed");
        }
    }

    private void openTransferOrigin(Player player, Team team, boolean adminView, AdminEditorContext adminContext, int page, TransferConfirmOrigin origin) {
        if (origin == TransferConfirmOrigin.MEMBERS) {
            openMembers(player, team, adminView, page, adminContext);
        } else {
            openTransferOwnership(player, team, adminView, page, adminContext);
        }
    }

    private boolean canTransferOwnership(Player player, Team team, boolean adminView) {
        if (!adminView && !plugin.getTeamService().can(team, player.getUniqueId(), TeamAction.TRANSFER)) {
            plugin.getMessages().send(player, "no-permission-role");
            return false;
        }
        return true;
    }

    private boolean canTransferTo(Team team, UUID playerId) {
        TeamRole role = team.roleOf(playerId);
        return role != null && role != TeamRole.OWNER;
    }

    private List<MemberView> transferOwnershipCandidates(Team team) {
        List<MemberView> candidates = new ArrayList<>();
        if (team.hasOwner()) {
            candidates.add(new MemberView(team.getOwnerId(), TeamRole.OWNER));
        }
        team.getAdmins().stream()
                .sorted()
                .map(playerId -> new MemberView(playerId, TeamRole.ADMIN))
                .forEach(candidates::add);
        team.getMembers().stream()
                .sorted()
                .map(playerId -> new MemberView(playerId, TeamRole.MEMBER))
                .forEach(candidates::add);
        return candidates;
    }

    public void openWarps(Player player, Team team, boolean adminView) {
        openWarps(player, team, adminView, 0, null);
    }

    private void openWarps(Player player, Team team, boolean adminView, AdminEditorContext adminContext) {
        openWarps(player, team, adminView, 0, adminContext);
    }

    private void openWarps(Player player, Team team, boolean adminView, int page, AdminEditorContext adminContext) {
        List<Map.Entry<String, Location>> warps = team.getWarps().entrySet().stream().sorted(Map.Entry.comparingByKey()).toList();
        int rows = pagedRows(warps.size());
        List<Integer> contentSlots = pagedContentSlots(rows);
        int pageSize = contentSlots.size();
        int maxPage = warps.isEmpty() ? 0 : (warps.size() - 1) / pageSize;
        int currentPage = Math.max(0, Math.min(page, maxPage));
        int start = currentPage * pageSize;

        FoGui gui = gui(rows * 9, plugin.getConfig().getString("gui.titles.warps", "Warps"));
        fill(gui);
        Location home = team.getHome();
        gui.getInventory().setItem(4, item(Material.RECOVERY_COMPASS, accent("Team Home"), List.of(
                home == null ? "#ffffffNo home set." : "#ffffffHome is ready.",
                "#ffffffLeft-click: teleport",
                "#ffffffRight-click: set home",
                "#ffffffShift-right: delete home")));
        gui.setAction(4, event -> {
            try {
                if (event.getClick() == ClickType.LEFT) {
                    if (home == null) {
                        plugin.getMessages().send(player, "home-missing");
                        return;
                    }
                    if (!adminView && !plugin.getTeamService().can(team, player.getUniqueId(), TeamAction.USE_HOME)) {
                        plugin.getMessages().send(player, "no-permission-role");
                        return;
                    }
                    plugin.getTeleportDelayService().start(player, home, "home", () -> sound(player, "teleport"));
                    return;
                }
                if (event.getClick() == ClickType.RIGHT) {
                    mutate(player, team, adminView, TeamAction.SET_HOME, () -> {
                        plugin.getTeamService().setHome(team, player.getLocation());
                        plugin.getMessages().send(player, "home-set");
                        openWarps(player, team, adminView, currentPage, adminContext);
                    });
                    return;
                }
                if (event.getClick().isShiftClick()) {
                    mutate(player, team, adminView, TeamAction.DELETE_HOME, () -> {
                        plugin.getTeamService().deleteHome(team);
                        plugin.getMessages().send(player, "home-deleted");
                        openWarps(player, team, adminView, currentPage, adminContext);
                    });
                }
            } catch (Exception exception) {
                plugin.getLogger().log(Level.WARNING, "Failed to apply home action for team " + team.getId() + ".", exception);
                plugin.getMessages().send(player, "action-failed");
            }
        });
        for (int index = 0; index < contentSlots.size(); index++) {
            int warpIndex = start + index;
            if (warpIndex >= warps.size()) {
                break;
            }
            Map.Entry<String, Location> entry = warps.get(warpIndex);
            int slot = contentSlots.get(index);
            boolean passwordProtected = plugin.getTeamService().warpRequiresPassword(team, entry.getKey());
            gui.getInventory().setItem(slot, item(Material.ENDER_PEARL, accent(entry.getKey()), List.of(
                    passwordProtected && !adminView ? "#ffffffProtected warp. Use /team warp <warp> <password>" : "#ffffffLeft-click: teleport",
                    "#ffffffRight-click: delete warp")));
            gui.setAction(slot, event -> {
                if (event.getClick() == ClickType.LEFT) {
                    if (!adminView && !plugin.getTeamService().can(team, player.getUniqueId(), TeamAction.USE_WARP)) {
                        plugin.getMessages().send(player, "no-permission-role");
                        return;
                    }
                    if (!adminView && plugin.getTeamService().warpRequiresPassword(team, entry.getKey())) {
                        plugin.getMessages().send(player, "warp-password-required");
                        return;
                    }
                    plugin.getTeleportDelayService().start(player, entry.getValue(), entry.getKey(), () -> sound(player, "teleport"));
                } else if (event.getClick() == ClickType.RIGHT) {
                    mutate(player, team, adminView, TeamAction.DELETE_WARP, () -> {
                        plugin.getTeamService().deleteWarp(team, entry.getKey());
                        plugin.getMessages().send(player, "warp-deleted", Map.of("{warp}", entry.getKey()));
                        openWarps(player, team, adminView, currentPage, adminContext);
                    });
                }
            });
        }
        int lastRowStart = gui.getInventory().getSize() - 9;
        if (currentPage > 0) {
            gui.getInventory().setItem(lastRowStart, previousPageButton(currentPage - 1, maxPage));
            gui.setAction(lastRowStart, event -> openWarps(player, team, adminView, currentPage - 1, adminContext));
        }
        int createSlot = lastRowStart + 5;
        gui.getInventory().setItem(createSlot, item(Material.ANVIL, "#03fc88Create Warp", List.of("#ffffffClick to create a new warp at your location.")));
        gui.setAction(createSlot, event -> promptText(player, "prompt-warp-create-password", "", input -> mutate(player, team, adminView, TeamAction.SET_WARP, () -> {
            String[] split = input.trim().split("\\s+", 2);
            String warpName = split[0].toLowerCase(Locale.ROOT);
            String password = split.length > 1 ? split[1].trim() : null;
            if (warpName.isBlank()) {
                plugin.getMessages().send(player, "usage-setwarp");
                return;
            }
            if (!plugin.getTeamService().canAddWarp(team, warpName)) {
                plugin.getMessages().send(player, "warp-limit", Map.of("{max}", String.valueOf(plugin.getTeamService().maxWarps())));
                return;
            }
            plugin.getTeamService().setWarp(team, warpName, player.getLocation(), password);
            plugin.getMessages().send(player, "warp-set", Map.of("{warp}", warpName));
            openWarps(player, team, adminView, currentPage, adminContext);
        }), () -> openWarps(player, team, adminView, currentPage, adminContext)));
        setBackButton(gui, () -> {
            if (adminView) {
                openAdminEditor(player, team, contextOrDefault(adminContext));
            } else {
                openDashboard(player);
            }
        });
        if (start + pageSize < warps.size()) {
            int nextSlot = gui.getInventory().getSize() - 1;
            gui.getInventory().setItem(nextSlot, nextPageButton(currentPage + 1, maxPage));
            gui.setAction(nextSlot, event -> openWarps(player, team, adminView, currentPage + 1, adminContext));
        }
        player.openInventory(gui.getInventory());
        sound(player, "gui-open");
    }

    private int pagedRows(int itemCount) {
        int contentRows = Math.max(1, Math.min(4, (itemCount + 6) / 7));
        return contentRows + 2;
    }

    private List<Integer> pagedContentSlots(int rows) {
        List<Integer> slots = new ArrayList<>();
        for (int row = 1; row < rows - 1; row++) {
            for (int column = 1; column <= 7; column++) {
                slots.add(row * 9 + column);
            }
        }
        return slots;
    }

    public void openRelations(Player player, Team team, boolean adminView, int page) {
        openRelations(player, team, adminView, page, "", null);
    }

    public void openRelations(Player player, Team team, boolean adminView, int page, String search) {
        openRelations(player, team, adminView, page, search, null);
    }

    private void openRelations(Player player, Team team, boolean adminView, int page, String search, AdminEditorContext adminContext) {
        if (adminView && !plugin.getConfig().getBoolean("allow-admin-diplomacy-management", true)) {
            plugin.getMessages().send(player, "action-failed");
            return;
        }
        String normalizedSearch = normalizeSearch(search);
        List<Team> teams = plugin.getTeamService().teams().stream()
                .filter(candidate -> candidate.getId() != team.getId())
                .filter(candidate -> matchesTeamSearch(candidate, normalizedSearch))
                .sorted(Comparator.comparing(Team::getName, String.CASE_INSENSITIVE_ORDER))
                .toList();
        int rows = pagedRows(teams.size());
        List<Integer> contentSlots = pagedContentSlots(rows);
        int pageSize = contentSlots.size();
        int maxPage = teams.isEmpty() ? 0 : (teams.size() - 1) / pageSize;
        int currentPage = Math.max(0, Math.min(page, maxPage));
        int start = currentPage * pageSize;

        FoGui gui = gui(rows * 9, plugin.getConfig().getString("gui.titles.relations", "Relations"));
        fill(gui);
        gui.getInventory().setItem(4, item(Material.LEAD, accent("Relations"), List.of(
                "#ffffffLeft-click: ally",
                "#ffffffRight-click: enemy",
                normalizedSearch.isBlank() ? "#ffffffSearch: #a7b8b0None" : "#ffffffSearch: #03fc88" + normalizedSearch,
                "#ffffffShift-right: clear")));
        if (teams.isEmpty()) {
            gui.getInventory().setItem(13, item(Material.BARRIER, "#ff5d73No Teams Found", normalizedSearch.isBlank()
                    ? List.of("#ffffffThere are no other teams to manage.")
                    : List.of("#ffffffNo teams match: #03fc88" + normalizedSearch)));
        } else {
            for (int index = 0; index < contentSlots.size(); index++) {
                int teamIndex = start + index;
                if (teamIndex >= teams.size()) {
                    break;
                }
                Team other = teams.get(teamIndex);
                int slot = contentSlots.get(index);
                RelationType relation = plugin.getTeamService().relation(team, other);
                boolean incoming = plugin.getTeamService().hasIncomingAllyRequest(team, other);
                Material material = relation == RelationType.ALLY ? Material.LIME_WOOL
                        : relation == RelationType.ENEMY ? Material.RED_WOOL
                        : (relation == RelationType.ALLY_REQUEST || incoming) ? Material.YELLOW_WOOL
                        : Material.GRAY_WOOL;
                String status = relation == RelationType.ALLY ? "ally"
                        : relation == RelationType.ENEMY ? "enemy"
                        : incoming ? "ally request received"
                        : relation == RelationType.ALLY_REQUEST ? "ally request sent"
                        : "neutral";
                gui.getInventory().setItem(slot, item(material, accent(other.getName()), List.of(
                        "#ffffffStatus: " + status,
                        "#ffffffLeft: ally | Right: enemy | Shift-right: clear")));
                gui.setAction(slot, event -> mutate(player, team, adminView, TeamAction.MANAGE_RELATIONS, () -> {
                    if (event.getClick().isShiftClick()) {
                        plugin.getTeamService().clearRelation(team, other);
                        plugin.getMessages().send(player, "relation-cleared", Map.of("{other}", other.getName()));
                    } else if (event.getClick() == ClickType.LEFT) {
                        boolean accepted = plugin.getTeamService().requestOrAcceptAlly(team, other);
                        plugin.getMessages().send(player, accepted ? "ally-request-accepted" : "ally-request-sent", Map.of("{other}", other.getName()));
                        if (!accepted) {
                            notifyTeamLeaders(other, "ally-request-received", Map.of("{other}", team.getName()));
                        }
                    } else if (event.getClick() == ClickType.RIGHT) {
                        plugin.getTeamService().setEnemy(team, other);
                        plugin.getMessages().send(player, "relation-updated", Map.of("{other}", other.getName(), "{relation}", "enemy"));
                    }
                    openRelations(player, team, adminView, currentPage, normalizedSearch, adminContext);
                }));
            }
        }
        int lastRowStart = gui.getInventory().getSize() - 9;
        if (currentPage > 0) {
            gui.getInventory().setItem(lastRowStart, previousPageButton(currentPage - 1, maxPage));
            gui.setAction(lastRowStart, event -> openRelations(player, team, adminView, currentPage - 1, normalizedSearch, adminContext));
        }
        gui.getInventory().setItem(lastRowStart + 3, searchButton(normalizedSearch));
        gui.setAction(lastRowStart + 3, event -> promptRelationTeamSearch(player, team, adminView, normalizedSearch, adminContext));
        setBackButton(gui, () -> openSettings(player, team, adminView, adminContext));
        if (!normalizedSearch.isBlank()) {
            gui.getInventory().setItem(lastRowStart + 5, clearSearchButton("relations"));
            gui.setAction(lastRowStart + 5, event -> {
                plugin.getMessages().send(player, "editor-search-cleared");
                openRelations(player, team, adminView, 0, "", adminContext);
            });
        }
        if (start + pageSize < teams.size()) {
            int nextSlot = gui.getInventory().getSize() - 1;
            gui.getInventory().setItem(nextSlot, nextPageButton(currentPage + 1, maxPage));
            gui.setAction(nextSlot, event -> openRelations(player, team, adminView, currentPage + 1, normalizedSearch, adminContext));
        }
        player.openInventory(gui.getInventory());
        sound(player, "gui-open");
    }

    public void openInfo(Player viewer, Team team) {
        openInfo(viewer, team, 0, null);
    }

    public void openInfo(Player viewer, Team team, int page) {
        openInfo(viewer, team, page, null);
    }

    private void openInfo(Player viewer, Team team, int page, AdminEditorContext adminContext) {
        List<MemberView> members = memberEntries(team);
        int rows = pagedRows(members.size());
        List<Integer> contentSlots = pagedContentSlots(rows);
        int pageSize = contentSlots.size();
        int maxPage = members.isEmpty() ? 0 : (members.size() - 1) / pageSize;
        int currentPage = Math.max(0, Math.min(page, maxPage));
        int start = currentPage * pageSize;

        FoGui gui = gui(rows * 9, plugin.getConfig().getString("gui.titles.info", "Info"));
        fill(gui);
        gui.getInventory().setItem(2, item(Material.NAME_TAG, accent(team.getName()), teamInfoLore(team)));
        gui.getInventory().setItem(4, item(Material.BELL, "#3ecf8eAllies", alliesLore(team)));
        gui.getInventory().setItem(6, item(Material.PAPER, accent("Team Size"), List.of(plugin.getMessages().render("member-limit", "member-limit", Map.of(
                "{current}", String.valueOf(team.getMemberCount()),
                "{max}", String.valueOf(team.getMemberCap())
        )))));

        if (members.isEmpty()) {
            gui.getInventory().setItem(13, item(Material.GRAY_DYE, "#a7b8b0No Members", List.of("#ffffffThis team does not currently have members.")));
        } else {
            for (int index = 0; index < contentSlots.size(); index++) {
                int memberIndex = start + index;
                if (memberIndex >= members.size()) {
                    break;
                }
                MemberView member = members.get(memberIndex);
                int slot = contentSlots.get(index);
                gui.getInventory().setItem(slot, playerHead(member.id(), accent(member.role().displayName() + ": " + playerName(member.id())), memberInfoLore(member.id(), member.role())));
            }
        }
        int lastRowStart = gui.getInventory().getSize() - 9;
        if (currentPage > 0) {
            gui.getInventory().setItem(lastRowStart, previousPageButton(currentPage - 1, maxPage));
            gui.setAction(lastRowStart, event -> openInfo(viewer, team, currentPage - 1, adminContext));
        }
        if (adminContext != null) {
            setBackButton(gui, () -> openAdminEditor(viewer, team, adminContext));
        } else {
            gui.getInventory().setItem(lastRowStart + 4, emptyInfoPane());
        }
        if (start + pageSize < members.size()) {
            int nextSlot = gui.getInventory().getSize() - 1;
            gui.getInventory().setItem(nextSlot, nextPageButton(currentPage + 1, maxPage));
            gui.setAction(nextSlot, event -> openInfo(viewer, team, currentPage + 1, adminContext));
        }
        viewer.openInventory(gui.getInventory());
        sound(viewer, "gui-open");
    }

    public void openEditorHome(Player player) {
        FoGui gui = gui(27, plugin.getConfig().getString("gui.titles.editor", "Editor"));
        fill(gui);
        gui.getInventory().setItem(10, item(Material.BOOK, accent("Team Browser"), List.of(
                "#ffffffSearch and edit existing teams.",
                "#ffffffCreate ownerless admin teams.",
                "#ffffffClick to browse.")));
        gui.setAction(10, event -> openBrowser(player, 0));

        for (int index = 0; index < Math.min(configCategories.size(), EDITOR_HOME_SLOTS.length); index++) {
            ConfigCategory category = configCategories.get(index);
            int slot = EDITOR_HOME_SLOTS[index];
            List<String> lore = new ArrayList<>(category.lore());
            lore.add("#ffffffSettings: #03fc88" + category.settings().size());
            lore.add("#ffffffClick to edit.");
            gui.getInventory().setItem(slot, item(category.material(), accent(category.title()), lore));
            gui.setAction(slot, event -> {
                if ("team-level".equals(category.id()) && !plugin.getTeamLevelService().isFoLevelsAvailable()) {
                    plugin.getMessages().send(player, "team-level-unavailable");
                    return;
                }
                openConfigCategory(player, category.id());
            });
        }

        gui.getInventory().setItem(22, emptyInfoPane());
        player.openInventory(gui.getInventory());
        sound(player, "gui-open");
    }

    public void openBrowser(Player player, int page) {
        openBrowser(player, page, "");
    }

    public void openBrowser(Player player, int page, String search) {
        String normalizedSearch = normalizeSearch(search);
        FoGui gui = gui(54, plugin.getConfig().getString("gui.titles.browser", "Browser"));
        fill(gui);
        List<Team> teams = plugin.getTeamService().teams().stream()
                .filter(team -> matchesTeamSearch(team, normalizedSearch))
                .sorted(Comparator.comparing(Team::getName, String.CASE_INSENSITIVE_ORDER))
                .toList();
        int maxPage = teams.isEmpty() ? 0 : (teams.size() - 1) / EDITOR_CONTENT_SLOTS.length;
        int currentPage = Math.max(0, page);
        int start = Math.max(0, currentPage * EDITOR_CONTENT_SLOTS.length);
        if (start >= teams.size() && currentPage > 0) {
            currentPage = Math.max(0, (teams.size() - 1) / EDITOR_CONTENT_SLOTS.length);
            start = currentPage * EDITOR_CONTENT_SLOTS.length;
        }
        int pageForActions = currentPage;
        for (int index = start; index < Math.min(teams.size(), start + EDITOR_CONTENT_SLOTS.length); index++) {
            Team team = teams.get(index);
            int slot = EDITOR_CONTENT_SLOTS[index - start];
            gui.getInventory().setItem(slot, item(Material.BOOK, accent(team.getName()), List.of(
                    "#ffffffTag: " + tag(team),
                    "#ffffffOwner: " + playerName(team.getOwnerId()),
                    "#ffffffScore: " + team.getScore(),
                    "#ffffffBalance: " + Text.money(team.getBalance()))));
            int browserPage = currentPage;
            gui.setAction(slot, event -> openAdminEditor(player, team, normalizedSearch, browserPage));
        }
        if (teams.isEmpty()) {
            gui.getInventory().setItem(22, item(Material.BARRIER, "#ff5d73No Teams Found", normalizedSearch.isBlank()
                    ? List.of("#ffffffNo teams have been created yet.")
                    : List.of("#ffffffNo teams match: #03fc88" + normalizedSearch)));
        }
        setBackButton(gui, () -> openEditorHome(player));
        if (currentPage > 0) {
            int previousPage = currentPage - 1;
            gui.getInventory().setItem(46, previousPageButton(previousPage, maxPage));
            gui.setAction(46, event -> openBrowser(player, previousPage, normalizedSearch));
        }
        gui.getInventory().setItem(48, searchButton(normalizedSearch));
        gui.setAction(48, event -> promptTeamSearch(player, normalizedSearch));
        gui.getInventory().setItem(51, item(Material.ANVIL, "#03fc88Create Team", List.of("#ffffffCreate a team without an owner.")));
        gui.setAction(51, event -> promptText(player, "prompt-name", input -> {
            try {
                if (!validateTeamName(player, input)) {
                    return;
                }
                if (plugin.getTeamService().isNameTaken(input)) {
                    plugin.getMessages().send(player, "team-name-taken");
                    return;
                }
                Team team = plugin.getTeamService().createOwnerlessTeam(input);
                openAdminEditor(player, team, normalizedSearch, pageForActions);
            } catch (SQLException exception) {
                plugin.getLogger().log(Level.WARNING, "Failed to create ownerless team from editor.", exception);
                plugin.getMessages().send(player, "team-create-failed");
            }
        }));
        if (!normalizedSearch.isBlank()) {
            gui.getInventory().setItem(50, clearSearchButton("teams"));
            gui.setAction(50, event -> {
                plugin.getMessages().send(player, "editor-search-cleared");
                openBrowser(player, 0, "");
            });
        }
        if (start + EDITOR_CONTENT_SLOTS.length < teams.size()) {
            int nextPage = currentPage + 1;
            gui.getInventory().setItem(52, nextPageButton(nextPage, maxPage));
            gui.setAction(52, event -> openBrowser(player, nextPage, normalizedSearch));
        }
        gui.getInventory().setItem(53, emptyInfoPane());
        player.openInventory(gui.getInventory());
        sound(player, "gui-open");
    }

    private void openConfigCategory(Player player, String categoryId) {
        ConfigCategory category = configCategories.stream()
                .filter(candidate -> candidate.id().equals(categoryId))
                .findFirst()
                .orElse(null);
        if (category == null) {
            plugin.getMessages().send(player, "action-failed");
            openEditorHome(player);
            return;
        }
        FoGui gui = gui(configCategorySize(category), category.title());
        fill(gui);
        for (int index = 0; index < Math.min(category.settings().size(), EDITOR_CONTENT_SLOTS.length); index++) {
            ConfigSetting setting = category.settings().get(index);
            int slot = EDITOR_CONTENT_SLOTS[index];
            gui.getInventory().setItem(slot, configSettingItem(setting));
            gui.setAction(slot, event -> editConfigSetting(player, category.id(), setting));
        }
        setBackButton(gui, () -> openEditorHome(player));
        player.openInventory(gui.getInventory());
        sound(player, "gui-open");
    }

    private int configCategorySize(ConfigCategory category) {
        return switch (category.id()) {
            case "limits" -> 36;
            case "pvp-score", "upgrades", "admin", "chat", "team-level" -> 27;
            default -> 54;
        };
    }

    private void editConfigSetting(Player player, String categoryId, ConfigSetting setting) {
        if (setting.type() == ConfigValueType.BOOLEAN) {
            saveConfigSetting(player, categoryId, setting, !plugin.getConfig().getBoolean(setting.path(), false));
            return;
        }
        promptConfigValue(player, categoryId, setting);
    }

    private void promptConfigValue(Player player, String categoryId, ConfigSetting setting) {
        InputPrompt prompt = new InputPrompt(
                dialogId(setting),
                setting.label(),
                "Current value: {current}\nExpected input: {format}",
                "New value",
                rawConfigValue(setting),
                setting.expectedInput(),
                plugin.getMessages().render("editor-prompt-value", "editor-prompt-value", Map.of(
                        "{setting}", setting.label(),
                        "{format}", setting.expectedInput()
                )),
                maxLength(setting)
        );
        plugin.getInputGuiService().openInput(player, prompt, input -> {
            Object parsed = parseConfigSetting(setting, input);
            if (parsed == null) {
                plugin.getMessages().send(player, "editor-setting-invalid", Map.of(
                        "{setting}", setting.label(),
                        "{format}", setting.expectedInput()
                ));
                openConfigCategory(player, categoryId);
                return;
            }
            saveConfigSetting(player, categoryId, setting, parsed);
        }, () -> openConfigCategory(player, categoryId));
    }

    private void saveConfigSetting(Player player, String categoryId, ConfigSetting setting, Object value) {
        Object previous = plugin.getConfig().get(setting.path());
        plugin.getConfig().set(setting.path(), value);
        try {
            plugin.saveConfig();
            plugin.reloadPlugin();
            plugin.getMessages().send(player, "editor-setting-saved", Map.of(
                    "{setting}", setting.label(),
                    "{value}", displayConfigValue(setting, value)
            ));
        } catch (Exception exception) {
            plugin.getConfig().set(setting.path(), previous);
            try {
                plugin.saveConfig();
                plugin.reloadPlugin();
            } catch (Exception rollbackException) {
                plugin.getLogger().log(Level.WARNING, "Failed to roll back editor setting " + setting.path() + ".", rollbackException);
            }
            plugin.getLogger().log(Level.WARNING, "Failed to apply editor setting " + setting.path() + ".", exception);
            plugin.getMessages().send(player, "editor-setting-save-failed", Map.of("{setting}", setting.label()));
        }
        openConfigCategory(player, categoryId);
    }

    private ItemStack configSettingItem(ConfigSetting setting) {
        Object value = plugin.getConfig().get(setting.path());
        if (setting.type() == ConfigValueType.BOOLEAN) {
            boolean enabled = plugin.getConfig().getBoolean(setting.path(), false);
            return item(enabled ? Material.LIME_DYE : Material.GRAY_DYE,
                    (enabled ? "#3ecf8e" : "#ff5d73") + setting.label(),
                    List.of(
                            "#ffffffCurrent: " + (enabled ? "#3ecf8eEnabled" : "#ff5d73Disabled"),
                            "#ffffffClick to toggle."
                    ));
        }
        return item(setting.material(), accent(setting.label()), List.of(
                "#ffffffCurrent: #03fc88" + displayConfigValue(setting, value),
                "#ffffffInput: #a7b8b0" + setting.expectedInput(),
                "#ffffffClick to edit."
        ));
    }

    private Object parseConfigSetting(ConfigSetting setting, String input) {
        if (input == null) {
            return null;
        }
        try {
            return switch (setting.type()) {
                case BOOLEAN -> parseBooleanSetting(input);
                case INTEGER -> parseIntegerSetting(setting, input);
                case DOUBLE -> parseDoubleSetting(setting, input, false);
                case MONEY -> parseDoubleSetting(setting, input, true);
                case STRING -> input;
            };
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private Boolean parseBooleanSetting(String input) {
        String normalized = input.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "true", "yes", "on", "enable", "enabled" -> true;
            case "false", "no", "off", "disable", "disabled" -> false;
            default -> throw new IllegalArgumentException("invalid boolean");
        };
    }

    private Integer parseIntegerSetting(ConfigSetting setting, String input) {
        int value = Integer.parseInt(input.trim());
        if (value < setting.min() || setting.hasMax() && value > setting.max()) {
            throw new IllegalArgumentException("out of range");
        }
        return value;
    }

    private Double parseDoubleSetting(ConfigSetting setting, String input, boolean allowSuffix) {
        double value;
        if (allowSuffix) {
            var parsed = LargeNumberParser.parseDouble(input);
            if (parsed.isEmpty()) {
                throw new IllegalArgumentException("invalid number");
            }
            value = parsed.getAsDouble();
        } else {
            value = Double.parseDouble(input.trim());
        }
        if (!Double.isFinite(value) || value < setting.min() || setting.hasMax() && value > setting.max()) {
            throw new IllegalArgumentException("out of range");
        }
        return value;
    }

    private String displayConfigValue(ConfigSetting setting, Object value) {
        if (value == null) {
            return "not set";
        }
        if (setting.type() == ConfigValueType.MONEY && value instanceof Number number) {
            return Text.money(number.doubleValue());
        }
        if (setting.type() == ConfigValueType.BOOLEAN && value instanceof Boolean enabled) {
            return enabled ? "enabled" : "disabled";
        }
        return truncate(String.valueOf(value), 54);
    }

    private String truncate(String input, int maxLength) {
        if (input.length() <= maxLength) {
            return input;
        }
        return input.substring(0, Math.max(0, maxLength - 3)) + "...";
    }

    private void promptTeamSearch(Player player, String currentSearch) {
        InputPrompt prompt = new InputPrompt(
                "team-search",
                "Search Teams",
                "Search by team name, tag, or owner.",
                "Search",
                currentSearch,
                "team name, tag, or owner",
                plugin.getMessages().render("prompt-team-search"),
                80
        );
        plugin.getInputGuiService().openInput(player, prompt, input -> {
            String search = normalizeSearch(input);
            if (search.isBlank()) {
                plugin.getMessages().send(player, "editor-search-cleared");
                openBrowser(player, 0);
                return;
            }
            plugin.getMessages().send(player, "editor-search-applied", Map.of("{query}", search));
            openBrowser(player, 0, search);
        }, () -> openBrowser(player, 0, currentSearch));
    }

    private void promptRelationTeamSearch(Player player, Team team, boolean adminView, String currentSearch) {
        promptRelationTeamSearch(player, team, adminView, currentSearch, null);
    }

    private void promptRelationTeamSearch(Player player, Team team, boolean adminView, String currentSearch, AdminEditorContext adminContext) {
        InputPrompt prompt = new InputPrompt(
                "team-search",
                "Search Teams",
                "Search by team name, tag, or owner.",
                "Search",
                currentSearch,
                "team name, tag, or owner",
                plugin.getMessages().render("prompt-team-search"),
                80
        );
        plugin.getInputGuiService().openInput(player, prompt, input -> {
            String relationSearch = normalizeSearch(input);
            if (relationSearch.isBlank()) {
                plugin.getMessages().send(player, "editor-search-cleared");
                openRelations(player, team, adminView, 0, "", adminContext);
                return;
            }
            plugin.getMessages().send(player, "editor-search-applied", Map.of("{query}", relationSearch));
            openRelations(player, team, adminView, 0, relationSearch, adminContext);
        }, () -> openRelations(player, team, adminView, 0, currentSearch, adminContext));
    }

    private boolean matchesTeamSearch(Team team, String search) {
        if (search.isBlank()) {
            return true;
        }
        String lower = search.toLowerCase(Locale.ROOT);
        return String.valueOf(team.getName()).toLowerCase(Locale.ROOT).contains(lower)
                || String.valueOf(team.getTag()).toLowerCase(Locale.ROOT).contains(lower)
                || playerName(team.getOwnerId()).toLowerCase(Locale.ROOT).contains(lower);
    }

    private String normalizeSearch(String search) {
        return search == null ? "" : search.trim();
    }

    private List<ConfigCategory> createConfigCategories() {
        return List.of(
                new ConfigCategory("limits", "Team Limits", Material.PLAYER_HEAD, List.of("#ffffffCaps, names, invites, and storage."), List.of(
                        intSetting("max-members-per-team", "Max Members", Material.PLAYER_HEAD, "whole number, minimum 1", 1, 1000),
                        intSetting("default-members-per-team", "Default Members", Material.NAME_TAG, "whole number, minimum 1", 1, 1000),
                        intSetting("max-warps-per-team", "Max Warps", Material.ENDER_PEARL, "whole number, minimum 0", 0, 1000),
                        intSetting("teleport-delay-seconds", "Teleport Delay", Material.CLOCK, "seconds, 0 disables", 0, 3600),
                        intSetting("max-team-name-length", "Team Name Length", Material.OAK_SIGN, "whole number, 1-64", 1, 64),
                        intSetting("max-team-tag-length", "Team Tag Length", Material.NAME_TAG, "whole number, 1-16", 1, 16),
                        intSetting("invite-expiry-seconds", "Invite Expiry", Material.CLOCK, "seconds, minimum 1", 1, 86400),
                        intSetting("max-team-echest-rows", "Max Echest Rows", Material.ENDER_CHEST, "whole number, 1-6", 1, 6),
                        intSetting("default-team-echest-rows", "Default Echest Rows", Material.CHEST, "whole number, 1-6", 1, 6)
                )),
                new ConfigCategory("pvp-score", "PvP And Score", Material.IRON_SWORD, List.of("#ffffffDamage rules and score farming protection."), List.of(
                        boolSetting("team-pvp-default-enabled", "Default PvP Protection", Material.SHIELD),
                        boolSetting("team-pvp-force-disable-all", "Force Disable Team PvP", Material.BARRIER),
                        boolSetting("score.count-friendly-kills", "Count Friendly Kills", Material.IRON_SWORD),
                        boolSetting("score.count-ally-kills", "Count Ally Kills", Material.GOLDEN_SWORD),
                        intSetting("score.prevent-repeat-farming-window-seconds", "Repeat Kill Window", Material.CLOCK, "seconds, 0 disables", 0, 86400)
                )),
                new ConfigCategory("upgrades", "Upgrade Costs", Material.ANVIL, List.of("#ffffffTeam size and shared chest pricing."), List.of(
                        moneySetting("upgrade-costs.team-size.base", "Team Size Base Cost", Material.GOLD_INGOT, "number or shorthand like 50k", 0.01D, Double.NaN),
                        doubleSetting("upgrade-costs.team-size.multiplier", "Team Size Multiplier", Material.EMERALD, "decimal number, minimum 1", 1D, Double.NaN),
                        moneySetting("upgrade-costs.echest.base", "Echest Base Cost", Material.GOLD_BLOCK, "number or shorthand like 1.5m", 0.01D, Double.NaN),
                        doubleSetting("upgrade-costs.echest.multiplier", "Echest Multiplier", Material.EMERALD_BLOCK, "decimal number, minimum 1", 1D, Double.NaN)
                )),
                new ConfigCategory("admin", "Admin Access", Material.COMMAND_BLOCK, List.of("#ffffffAdmin editor safety toggles."), List.of(
                        boolSetting("allow-admin-diplomacy-management", "Admin Diplomacy", Material.LEAD),
                        boolSetting("allow-admin-echest-access", "Admin Echest Access", Material.ENDER_CHEST),
                        boolSetting("native-dialogs.enabled", "Native Dialogs", Material.WRITABLE_BOOK),
                        boolSetting("native-dialogs.warn-on-fallback", "Dialog Fallback Warning", Material.BELL)
                )),
                new ConfigCategory("chat", "Chat Options", Material.WRITABLE_BOOK, List.of("#ffffffChat shortcuts and placeholder fallbacks."), List.of(
                        boolSetting("chat.prefix-shortcuts-enabled", "Chat Prefix Shortcuts", Material.FEATHER),
                        stringSetting("placeholders.no-team", "No Team Placeholder", Material.PAPER, "short fallback text"),
                        stringSetting("placeholders.no-rank", "No Rank Placeholder", Material.MAP, "short fallback text")
                )),
                new ConfigCategory("team-level", "Team Levels", Material.EXPERIENCE_BOTTLE, List.of("#ffffffFoLevels integration and XP formula."), List.of(
                        boolSetting("team-level.enabled", "Team Levels", Material.EXPERIENCE_BOTTLE),
                        boolSetting("team-level.show-xp-gain", "Show XP Gain", Material.LIME_DYE),
                        intSetting("team-level.autosave-seconds", "Autosave Seconds", Material.CLOCK, "seconds, minimum 5", 5, 86400),
                        doubleSetting("team-level.formula.base-required-xp", "Base Required XP", Material.EXPERIENCE_BOTTLE, "decimal number, minimum 1", 1D, Double.NaN),
                        doubleSetting("team-level.formula.multiplier", "XP Multiplier", Material.EMERALD, "decimal number, minimum 1.01", 1.01D, Double.NaN),
                        intSetting("team-level.formula.max-level", "Max Level", Material.NETHER_STAR, "whole number, 0 means uncapped", 0, 100000)
                ))
        );
    }

    private ConfigSetting boolSetting(String path, String label, Material material) {
        return new ConfigSetting(path, label, material, ConfigValueType.BOOLEAN, "true/false or on/off", 0D, Double.NaN);
    }

    private ConfigSetting intSetting(String path, String label, Material material, String expectedInput, double min, double max) {
        return new ConfigSetting(path, label, material, ConfigValueType.INTEGER, expectedInput, min, max);
    }

    private ConfigSetting doubleSetting(String path, String label, Material material, String expectedInput, double min, double max) {
        return new ConfigSetting(path, label, material, ConfigValueType.DOUBLE, expectedInput, min, max);
    }

    private ConfigSetting moneySetting(String path, String label, Material material, String expectedInput, double min, double max) {
        return new ConfigSetting(path, label, material, ConfigValueType.MONEY, expectedInput, min, max);
    }

    private ConfigSetting stringSetting(String path, String label, Material material, String expectedInput) {
        return new ConfigSetting(path, label, material, ConfigValueType.STRING, expectedInput, 0D, Double.NaN);
    }

    public void openAdminEditor(Player player, Team team) {
        openAdminEditor(player, team, "", 0);
    }

    private void openAdminEditor(Player player, Team team, String browserSearch, int browserPage) {
        openAdminEditor(player, team, new AdminEditorContext(browserSearch, browserPage));
    }

    private void openAdminEditor(Player player, Team team, AdminEditorContext adminContext) {
        FoGui gui = gui(36, plugin.getConfig().getString("gui.titles.editor", "Editor"));
        fill(gui);
        gui.getInventory().setItem(10, item(Material.NAME_TAG, "#03fc88Open Public Info", List.of("#ffffffPreview the player-facing team info.")));
        gui.setAction(10, event -> openInfo(player, team, 0, adminContext));
        gui.getInventory().setItem(11, item(Material.COMPARATOR, "#03fc88Edit Settings", List.of("#ffffffEdit team metadata and access.")));
        gui.setAction(11, event -> openSettings(player, team, true, adminContext));
        gui.getInventory().setItem(12, item(Material.PLAYER_HEAD, "#03fc88Edit Members", List.of("#ffffffManage owner, admins, and members.")));
        gui.setAction(12, event -> openMembers(player, team, true, 0, adminContext));
        gui.getInventory().setItem(13, item(Material.ENDER_PEARL, "#03fc88Edit Warps", List.of("#ffffffManage team home and warps.")));
        gui.setAction(13, event -> openWarps(player, team, true, adminContext));
        gui.getInventory().setItem(14, item(Material.GOLD_INGOT, "#03fc88Edit Team Bank", List.of("#ffffffCurrent: " + Text.money(team.getBalance()), "#ffffffClick to set the team balance.")));
        gui.setAction(14, event -> promptText(player, "prompt-money", String.valueOf(team.getBalance()), input -> {
            try {
                double amount = Double.parseDouble(input);
                if (!Double.isFinite(amount) || amount < 0) {
                    plugin.getMessages().send(player, "amount-non-negative");
                    openAdminEditor(player, team, adminContext);
                    return;
                }
                plugin.getTeamService().setBalance(team, amount);
                openAdminEditor(player, team, adminContext);
            } catch (NumberFormatException exception) {
                plugin.getMessages().send(player, "money-invalid");
                openAdminEditor(player, team, adminContext);
            } catch (SQLException exception) {
                plugin.getLogger().log(Level.WARNING, "Failed to set balance for team " + team.getId() + ".", exception);
                plugin.getMessages().send(player, "settings-save-failed");
                openAdminEditor(player, team, adminContext);
            }
        }, () -> openAdminEditor(player, team, adminContext)));
        gui.getInventory().setItem(15, item(Material.EMERALD, "#03fc88Edit Team Score", List.of("#ffffffCurrent: " + team.getScore(), "#ffffffClick to set the team score.")));
        gui.setAction(15, event -> promptText(player, "prompt-score", String.valueOf(team.getScore()), input -> {
            try {
                int score = Integer.parseInt(input);
                if (score < 0) {
                    plugin.getMessages().send(player, "score-non-negative");
                    return;
                }
                plugin.getTeamService().setScore(team, score);
                openAdminEditor(player, team, adminContext);
            } catch (NumberFormatException exception) {
                plugin.getMessages().send(player, "score-invalid");
                openAdminEditor(player, team, adminContext);
            } catch (SQLException exception) {
                plugin.getLogger().log(Level.WARNING, "Failed to set score for team " + team.getId() + ".", exception);
                plugin.getMessages().send(player, "settings-save-failed");
                openAdminEditor(player, team, adminContext);
            }
        }, () -> openAdminEditor(player, team, adminContext)));
        gui.getInventory().setItem(16, item(Material.ENDER_CHEST, "#03fc88Edit Team Chest", List.of("#ffffffInspect the team storage.")));
        gui.setAction(16, event -> openSharedChest(player, team, true));
        gui.getInventory().setItem(22, item(Material.REDSTONE_BLOCK, "#ff5d73Purge Team", List.of("#ffffffClick to permanently delete this team.")));
        gui.setAction(22, event -> openConfirm(player, team, true, adminContext, ConfirmBackTarget.ADMIN_EDITOR));
        setBackButton(gui, () -> openBrowser(player, adminContext.browserPage(), adminContext.browserSearch()));
        player.openInventory(gui.getInventory());
        sound(player, "gui-open");
    }

    private AdminEditorContext contextOrDefault(AdminEditorContext adminContext) {
        return adminContext == null ? new AdminEditorContext("", 0) : adminContext;
    }

    public void openSharedChest(Player player, Team team, boolean adminView) {
        if (adminView && !plugin.getConfig().getBoolean("allow-admin-echest-access", true)) {
            plugin.getMessages().send(player, "action-failed");
            return;
        }
        if (!adminView && !plugin.getTeamService().can(team, player.getUniqueId(), TeamAction.USE_ECHEST)) {
            plugin.getMessages().send(player, "no-permission-role");
            return;
        }
        SharedChestHolder holder = sharedChests.computeIfAbsent(team.getId(), ignored -> createSharedChest(team));
        player.openInventory(holder.getInventory());
    }

    public void openUpgrades(Player player, Team team, boolean adminView) {
        if (!adminView && !canManageUpgrades(team, player.getUniqueId())) {
            plugin.getMessages().send(player, "no-permission-role");
            return;
        }
        if (!plugin.getEconomyService().isEnabled()) {
            plugin.getMessages().send(player, "upgrades-economy-disabled");
            return;
        }
        FoGui gui = gui(27, plugin.getConfig().getString("gui.titles.upgrades", "Team Upgrades"));
        fill(gui);

        double sizeCost = plugin.getTeamService().nextMemberCapUpgradeCost(team);
        boolean sizeMaxed = sizeCost < 0;
        gui.getInventory().setItem(11, item(sizeMaxed ? Material.BARRIER : Material.PLAYER_HEAD, accent("Team Size Upgrade"), List.of(
                "#ffffffCurrent cap: #03fc88" + team.getMemberCap(),
                sizeMaxed ? "#ff5d73Already at maximum cap." : "#ffffffNext cap: #03fc88" + (team.getMemberCap() + 1),
                sizeMaxed ? "#a7b8b0No further upgrades available." : "#ffffffCost: #03fc88" + Text.money(sizeCost),
                sizeMaxed ? "#a7b8b0This upgrade is complete." : "#ffffffClick to purchase this upgrade."
        )));
        gui.setAction(11, event -> {
            if (!adminView && !canManageUpgrades(team, player.getUniqueId())) {
                plugin.getMessages().send(player, "no-permission-role");
                return;
            }
            if (!plugin.getEconomyService().isEnabled()) {
                plugin.getMessages().send(player, "upgrades-economy-disabled");
                return;
            }
            double cost = plugin.getTeamService().nextMemberCapUpgradeCost(team);
            if (cost < 0) {
                plugin.getMessages().send(player, "upgrade-team-size-max", Map.of("{max}", String.valueOf(plugin.getTeamService().maxMembers())));
                return;
            }
            if (plugin.getEconomyService().balance(player) < cost) {
                plugin.getMessages().send(player, "upgrade-insufficient-funds", Map.of("{cost}", Text.money(cost)));
                return;
            }
            if (!plugin.getEconomyService().withdraw(player, cost)) {
                plugin.getMessages().send(player, "economy-transaction-failed");
                return;
            }
            try {
                if (!plugin.getTeamService().upgradeMemberCap(team)) {
                    plugin.getEconomyService().deposit(player, cost);
                    plugin.getMessages().send(player, "upgrade-team-size-max", Map.of("{max}", String.valueOf(plugin.getTeamService().maxMembers())));
                    return;
                }
                plugin.getMessages().send(player, "upgrade-team-size-success", Map.of("{cost}", Text.money(cost), "{cap}", String.valueOf(team.getMemberCap())));
                openUpgrades(player, team, adminView);
            } catch (SQLException exception) {
                plugin.getLogger().log(Level.WARNING, "Failed to purchase member-cap upgrade for team " + team.getId() + ".", exception);
                if (plugin.getEconomyService().deposit(player, cost)) {
                    plugin.getMessages().send(player, "upgrade-purchase-failed-refunded");
                } else {
                    plugin.getMessages().send(player, "upgrade-purchase-failed");
                }
            }
        });

        double echestCost = plugin.getTeamService().nextEchestRowsUpgradeCost(team);
        boolean echestMaxed = echestCost < 0;
        gui.getInventory().setItem(15, item(echestMaxed ? Material.BARRIER : Material.ENDER_CHEST, accent("Team Echest Upgrade"), List.of(
                "#ffffffCurrent rows: #03fc88" + team.getEchestRows(),
                "#ffffffMaximum rows: #03fc88" + plugin.getTeamService().maxEchestRows(),
                echestMaxed ? "#ff5d73Already at maximum rows." : "#ffffffNext rows: #03fc88" + (team.getEchestRows() + 1),
                echestMaxed ? "#a7b8b0No further upgrades available." : "#ffffffCost: #03fc88" + Text.money(echestCost),
                echestMaxed ? "#a7b8b0This upgrade is complete." : "#ffffffClick to purchase this upgrade."
        )));
        gui.setAction(15, event -> {
            if (!adminView && !canManageUpgrades(team, player.getUniqueId())) {
                plugin.getMessages().send(player, "no-permission-role");
                return;
            }
            if (!plugin.getEconomyService().isEnabled()) {
                plugin.getMessages().send(player, "upgrades-economy-disabled");
                return;
            }
            double cost = plugin.getTeamService().nextEchestRowsUpgradeCost(team);
            if (cost < 0) {
                plugin.getMessages().send(player, "upgrade-echest-max", Map.of("{max}", String.valueOf(plugin.getTeamService().maxEchestRows())));
                return;
            }
            if (plugin.getEconomyService().balance(player) < cost) {
                plugin.getMessages().send(player, "upgrade-insufficient-funds", Map.of("{cost}", Text.money(cost)));
                return;
            }
            if (!plugin.getEconomyService().withdraw(player, cost)) {
                plugin.getMessages().send(player, "economy-transaction-failed");
                return;
            }
            try {
                if (!plugin.getTeamService().upgradeEchestRows(team)) {
                    plugin.getEconomyService().deposit(player, cost);
                    plugin.getMessages().send(player, "upgrade-echest-max", Map.of("{max}", String.valueOf(plugin.getTeamService().maxEchestRows())));
                    return;
                }
                plugin.getMessages().send(player, "upgrade-echest-success", Map.of("{cost}", Text.money(cost), "{rows}", String.valueOf(team.getEchestRows())));
                openUpgrades(player, team, adminView);
            } catch (SQLException exception) {
                plugin.getLogger().log(Level.WARNING, "Failed to purchase shared-chest upgrade for team " + team.getId() + ".", exception);
                if (plugin.getEconomyService().deposit(player, cost)) {
                    plugin.getMessages().send(player, "upgrade-purchase-failed-refunded");
                } else {
                    plugin.getMessages().send(player, "upgrade-purchase-failed");
                }
            }
        });

        setBackButton(gui, () -> {
            if (adminView) {
                openSettings(player, team, true);
            } else {
                openDashboard(player);
            }
        });
        player.openInventory(gui.getInventory());
        sound(player, "gui-open");
    }

    public void queueSharedChestSave(SharedChestHolder holder) {
        if (!pendingSharedChestSaves.add(holder)) {
            return;
        }
        plugin.getCore().scheduler().runGlobal(() -> {
            pendingSharedChestSaves.remove(holder);
            saveSharedChest(holder);
        });
    }

    public void handleSharedChestClose(SharedChestHolder holder) {
        saveSharedChest(holder);
        plugin.getCore().scheduler().runGlobal(() -> {
            if (holder.getInventory().getViewers().isEmpty()) {
                sharedChests.remove(holder.getTeamId(), holder);
                pendingSharedChestSaves.remove(holder);
            }
        });
    }

    public void saveOpenSharedChests() {
        for (SharedChestHolder holder : new ArrayList<>(sharedChests.values())) {
            saveSharedChest(holder);
        }
    }

    public void closeSharedChest(Team team) {
        SharedChestHolder holder = sharedChests.remove(team.getId());
        if (holder == null) {
            return;
        }
        pendingSharedChestSaves.remove(holder);
        saveSharedChest(holder);
        for (HumanEntity viewer : new ArrayList<>(holder.getInventory().getViewers())) {
            viewer.closeInventory();
        }
    }

    public void saveSharedChest(SharedChestHolder holder) {
        plugin.getTeamService().byId(holder.getTeamId()).ifPresent(team -> {
            team.getEchestContents().clear();
            for (ItemStack item : holder.getInventory().getContents()) {
                team.getEchestContents().add(cloneItem(item));
            }
            try {
                plugin.getTeamService().saveTeamEchest(team);
            } catch (SQLException exception) {
                plugin.getLogger().log(Level.WARNING, "Failed to save shared chest for team " + team.getName() + ".", exception);
            }
        });
    }

    public void openConfirm(Player player, Team team, boolean adminView) {
        openConfirm(player, team, adminView, null, ConfirmBackTarget.SETTINGS);
    }

    private void openConfirm(Player player, Team team, boolean adminView, AdminEditorContext adminContext, ConfirmBackTarget backTarget) {
        FoGui gui = gui(27, plugin.getConfig().getString("gui.titles.confirm", "Confirm"));
        fill(gui);
        gui.getInventory().setItem(11, item(Material.LIME_DYE, "#3ecf8eConfirm", List.of("#ffffffClick to continue.")));
        gui.setAction(11, event -> {
            try {
                if (!adminView) {
                    mutate(player, team, false, TeamAction.DISBAND, () -> {
                        plugin.getTeamChatService().clearModes(plugin.getTeamService().allMembers(team));
                        closeSharedChest(team);
                        plugin.getTeamService().disband(team);
                        plugin.getMessages().send(player, "team-disbanded", Map.of("{team}", team.getName()));
                        player.closeInventory();
                    });
                } else {
                    plugin.getTeamChatService().clearModes(plugin.getTeamService().allMembers(team));
                    closeSharedChest(team);
                    plugin.getTeamService().disband(team);
                    plugin.getMessages().send(player, "team-deleted", Map.of("{team}", team.getName()));
                    AdminEditorContext context = contextOrDefault(adminContext);
                    openBrowser(player, context.browserPage(), context.browserSearch());
                }
            } catch (Exception exception) {
                plugin.getLogger().log(Level.WARNING, "Failed to delete team " + team.getId() + ".", exception);
                plugin.getMessages().send(player, "action-failed");
            }
        });
        gui.getInventory().setItem(15, item(Material.RED_DYE, "#ff5d73Cancel", List.of("#ffffffReturn without changing anything.")));
        gui.setAction(15, event -> {
            if (adminView) {
                if (backTarget == ConfirmBackTarget.SETTINGS) {
                    openSettings(player, team, true, adminContext);
                } else {
                    openAdminEditor(player, team, contextOrDefault(adminContext));
                }
            } else {
                openSettings(player, team, false);
            }
        });
        player.openInventory(gui.getInventory());
        sound(player, "gui-open");
    }

    private void handleMemberClick(Player actor, Team team, boolean adminView, AdminEditorContext adminContext, MemberView entry, ClickType clickType, int page) {
        try {
            if (entry.role() == TeamRole.OWNER && !adminView) {
                plugin.getMessages().send(actor, "member-owner-protected");
                return;
            }
            if (clickType.isShiftClick() && clickType.isRightClick()) {
                if (!adminView && !plugin.getTeamService().can(team, actor.getUniqueId(), TeamAction.TRANSFER)) {
                    plugin.getMessages().send(actor, "no-permission-role");
                    return;
                }
                if (entry.role() == TeamRole.OWNER) {
                    plugin.getMessages().send(actor, "member-owner-protected");
                    return;
                }
                openTransferOwnershipConfirm(actor, team, adminView, adminContext, entry.id(), page, TransferConfirmOrigin.MEMBERS);
                return;
            }
            if (clickType == ClickType.RIGHT) {
                if (!adminView && !plugin.getTeamService().can(team, actor.getUniqueId(), TeamAction.KICK)) {
                    plugin.getMessages().send(actor, "no-permission-role");
                    return;
                }
                if (entry.role() == TeamRole.OWNER) {
                    plugin.getMessages().send(actor, "member-owner-protected");
                    return;
                }
                plugin.getTeamService().kick(team, entry.id());
                plugin.getMessages().send(actor, "kick-success", Map.of("{player}", playerName(entry.id())));
                Player kicked = Bukkit.getPlayer(entry.id());
                if (kicked != null) {
                    plugin.getMessages().send(kicked, "kick-target", Map.of("{team}", team.getName()));
                }
                openMembers(actor, team, adminView, page, adminContext);
                return;
            }
            if (clickType == ClickType.LEFT) {
                if (entry.role() == TeamRole.MEMBER) {
                    if (!adminView && !plugin.getTeamService().can(team, actor.getUniqueId(), TeamAction.PROMOTE)) {
                        plugin.getMessages().send(actor, "no-permission-role");
                        return;
                    }
                    if (!plugin.getTeamService().promote(team, entry.id())) {
                        plugin.getMessages().send(actor, "admin-limit", Map.of("{max}", String.valueOf(plugin.getTeamService().maxAdmins())));
                        return;
                    }
                    plugin.getMessages().send(actor, "promote-success", Map.of("{player}", playerName(entry.id()), "{role}", "admin"));
                } else if (entry.role() == TeamRole.ADMIN) {
                    if (!adminView && !plugin.getTeamService().can(team, actor.getUniqueId(), TeamAction.DEMOTE)) {
                        plugin.getMessages().send(actor, "no-permission-role");
                        return;
                    }
                    plugin.getTeamService().demote(team, entry.id());
                    plugin.getMessages().send(actor, "demote-success", Map.of("{player}", playerName(entry.id()), "{role}", "member"));
                }
                openMembers(actor, team, adminView, page, adminContext);
            }
        } catch (SQLException exception) {
            plugin.getLogger().log(Level.WARNING, "Failed to apply member action for team " + team.getId() + ".", exception);
            plugin.getMessages().send(actor, "member-action-failed");
        }
    }

    private void promptText(Player player, String key, java.util.function.Consumer<String> consumer) {
        promptText(player, key, "", consumer);
    }

    private void promptText(Player player, String key, String currentValue, java.util.function.Consumer<String> consumer) {
        promptText(player, key, currentValue, consumer, () -> {
        });
    }

    private void promptText(Player player, String key, String currentValue, java.util.function.Consumer<String> consumer, Runnable onCancel) {
        plugin.getInputGuiService().openInput(player, promptFor(key, currentValue), consumer, onCancel);
    }

    private boolean validateTeamName(Player player, String name) {
        if (!plugin.getTeamService().isValidTeamNameCharacters(name)) {
            plugin.getMessages().send(player, "name-invalid");
            return false;
        }
        if (!plugin.getTeamService().isValidTeamNameLength(name)) {
            plugin.getMessages().send(player, "name-too-long", Map.of("{max}", String.valueOf(plugin.getTeamService().maxNameLength())));
            return false;
        }
        return validateAllowedTeamText(player, name);
    }

    private boolean validateAllowedTeamText(Player player, String text) {
        if (plugin.getSwearFilterService().containsBlockedWord(text)) {
            plugin.getMessages().send(player, "blocked-word");
            return false;
        }
        return true;
    }

    private InputPrompt promptFor(String key, String currentValue) {
        String chatPrompt = plugin.getMessages().render(key);
        return switch (key) {
            case "prompt-name" -> new InputPrompt(
                    "team-name",
                    "Team Name",
                    "Use letters, numbers, and underscores.",
                    "Team name",
                    currentValue,
                    "team name",
                    chatPrompt,
                    Math.max(1, plugin.getTeamService().maxNameLength())
            );
            case "prompt-tag" -> new InputPrompt(
                    "team-tag",
                    "Team Tag",
                    "Type a short team tag with no spaces.",
                    "Team tag",
                    currentValue,
                    "team tag",
                    chatPrompt,
                    Math.max(1, plugin.getTeamService().maxTagLength())
            );
            case "prompt-description" -> new InputPrompt(
                    "team-description",
                    "Team Description",
                    "Type the public team description.",
                    "Description",
                    currentValue,
                    "description text",
                    chatPrompt,
                    160
            );
            case "prompt-tag-color" -> new InputPrompt(
                    "tag-color",
                    "Tag Color",
                    "Use &e, yellow, or #03fc88.",
                    "Color",
                    currentValue,
                    "color name, legacy code, or hex color",
                    chatPrompt,
                    32
            );
            case "prompt-warp-create-password" -> new InputPrompt(
                    "warp-create",
                    "Create Warp",
                    "Use <name> [password].",
                    "Warp",
                    currentValue,
                    "<name> [password]",
                    chatPrompt,
                    80
            );
            case "prompt-money" -> new InputPrompt(
                    "editor-money-input",
                    "Team Balance",
                    "Set the team balance.",
                    "Amount",
                    currentValue,
                    "number, minimum 0",
                    chatPrompt,
                    32
            );
            case "prompt-score" -> new InputPrompt(
                    "editor-number-input",
                    "Team Score",
                    "Set the team score.",
                    "Score",
                    currentValue,
                    "whole number, minimum 0",
                    chatPrompt,
                    32
            );
            default -> new InputPrompt(
                    "editor-text-input",
                    "Input",
                    "Type the new value.",
                    "Value",
                    currentValue,
                    "text",
                    chatPrompt,
                    128
            );
        };
    }

    private String dialogId(ConfigSetting setting) {
        return switch (setting.type()) {
            case INTEGER, DOUBLE -> "editor-number-input";
            case MONEY -> "editor-money-input";
            case STRING -> "editor-text-input";
            case BOOLEAN -> "editor-text-input";
        };
    }

    private String rawConfigValue(ConfigSetting setting) {
        Object value = plugin.getConfig().get(setting.path());
        return value == null ? "" : String.valueOf(value);
    }

    private int maxLength(ConfigSetting setting) {
        return switch (setting.type()) {
            case INTEGER, DOUBLE, MONEY -> 32;
            case STRING -> 128;
            case BOOLEAN -> 16;
        };
    }

    private void mutate(Player player, Team team, boolean adminView, TeamAction action, CheckedAction checkedAction) {
        try {
            if (!adminView && !plugin.getTeamService().can(team, player.getUniqueId(), action)) {
                plugin.getMessages().send(player, "no-permission-role");
                return;
            }
            checkedAction.run();
        } catch (Exception exception) {
            plugin.getLogger().log(Level.WARNING, "GUI action failed for team " + team.getId() + ".", exception);
            plugin.getMessages().send(player, "action-failed");
        }
    }

    private FoGui gui(int size, String title) {
        return new FoGui(size, title(title));
    }

    private SharedChestHolder createSharedChest(Team team) {
        SharedChestHolder holder = new SharedChestHolder(team.getId(), team.getEchestRows(), title("Team Chest"));
        Inventory inventory = holder.getInventory();
        for (int slot = 0; slot < Math.min(inventory.getSize(), team.getEchestContents().size()); slot++) {
            inventory.setItem(slot, cloneItem(team.getEchestContents().get(slot)));
        }
        return holder;
    }

    private ItemStack cloneItem(ItemStack item) {
        return item == null ? null : FoItemStacks.cloneItem(item);
    }

    private String title(String input) {
        return org.bukkit.ChatColor.DARK_GRAY + GuiTitles.format(input);
    }

    private void fill(FoGui gui) {
        Material filler = Material.matchMaterial(plugin.getConfig().getString("gui.filler-material", "GRAY_STAINED_GLASS_PANE"));
        ItemStack pane = EditorItemFactory.item(filler == null ? Material.GRAY_STAINED_GLASS_PANE : filler, " ", List.of());
        for (int slot = 0; slot < gui.getInventory().getSize(); slot++) {
            gui.getInventory().setItem(slot, pane);
        }
    }

    private ItemStack item(Material material, String name, List<String> lore) {
        return EditorItemFactory.item(material, name, lore);
    }

    private ItemStack emptyInfoPane() {
        return EditorItemFactory.item(Material.GRAY_STAINED_GLASS_PANE, " ", List.of());
    }

    private ItemStack backButton() {
        return buttons.back();
    }

    private void setBackButton(FoGui gui, Runnable action) {
        int slot = gui.getInventory().getSize() - 5;
        gui.getInventory().setItem(slot, backButton());
        gui.setAction(slot, event -> action.run());
    }

    private ItemStack previousPageButton(int targetPage, int maxPage) {
        return buttons.previousPage(Math.max(0, targetPage), Math.max(0, maxPage));
    }

    private ItemStack nextPageButton(int targetPage, int maxPage) {
        return buttons.nextPage(Math.max(0, targetPage), Math.max(0, maxPage));
    }

    private ItemStack searchButton(String filter) {
        return buttons.search(filter);
    }

    private ItemStack clearSearchButton(String target) {
        return buttons.clearSearch(target);
    }

    private ItemStack playerHead(UUID playerId, String name, List<String> lore) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) head.getItemMeta();
        if (!Team.NO_OWNER_UUID.equals(playerId)) {
            Player online = Bukkit.getPlayer(playerId);
            meta.setOwningPlayer(online == null ? Bukkit.getOfflinePlayer(playerId) : online);
        }
        meta.setDisplayName(FoText.color(name));
        meta.setLore(FoText.color(lore));
        head.setItemMeta(meta);
        return head;
    }

    private List<String> teamInfoLore(Team team) {
        List<String> lore = new ArrayList<>();
        lore.add("#ffffffTag: " + tag(team));
        lore.add("#ffffffDescription: #ffffff" + team.getDescription());
        lore.add("#ffffffScore: " + team.getScore());
        if (plugin.getTeamLevelService().isEnabled()) {
            lore.add("#ffffffLevel: #03fc88" + team.getTeamLevel() + " #a7b8b0(" + team.getTeamXp() + "/" + plugin.getTeamLevelService().requiredXp(team) + " XP)");
        }
        lore.add("#ffffffBalance: " + Text.money(team.getBalance()));
        return lore;
    }

    private List<String> alliesLore(Team team) {
        List<Team> allies = plugin.getTeamService().alliesOf(team).stream()
                .sorted(Comparator.comparing(Team::getName, String.CASE_INSENSITIVE_ORDER))
                .toList();
        if (allies.isEmpty()) {
            return List.of("#ffffffAllies: #a7b8b0None");
        }
        List<String> lore = new ArrayList<>();
        lore.add("#ffffffAllies:");
        for (Team ally : allies) {
            lore.add("#a7b8b0- " + ally.getName());
        }
        return lore;
    }

    private List<String> memberInfoLore(UUID playerId, TeamRole role) {
        return List.of("#ffffffRole: #03fc88" + role.displayName(), playerStatusLore(playerId));
    }

    private String playerStatusLore(UUID playerId) {
        if (Team.NO_OWNER_UUID.equals(playerId)) {
            return "#ffffffStatus: #ff5d73No owner";
        }
        return Bukkit.getPlayer(playerId) == null ? "#ffffffStatus: #ff5d73Offline" : "#ffffffStatus: #3ecf8eOnline";
    }

    private String accent(String text) {
        return "#03fc88" + text;
    }

    private String tag(Team team) {
        String normalized = TagColorUtil.normalize(team.getColor());
        return (normalized == null ? "#03fc88" : normalized) + team.getTag();
    }

    private void notifyTeamLeaders(Team team, String messageKey, Map<String, String> replacements) {
        Player owner = team.hasOwner() ? Bukkit.getPlayer(team.getOwnerId()) : null;
        if (owner != null) {
            plugin.getMessages().send(owner, messageKey, replacements);
        }
        for (UUID adminId : team.getAdmins()) {
            Player admin = Bukkit.getPlayer(adminId);
            if (admin != null) {
                plugin.getMessages().send(admin, messageKey, replacements);
            }
        }
    }

    private String playerName(UUID playerId) {
        if (Team.NO_OWNER_UUID.equals(playerId)) {
            return "None";
        }
        Player online = Bukkit.getPlayer(playerId);
        if (online != null) {
            return online.getName();
        }
        OfflinePlayer player = Bukkit.getOfflinePlayer(playerId);
        return player.getName() == null ? playerId.toString().substring(0, 8) : player.getName();
    }

    private boolean canManageTeamPvp(Team team, UUID playerId) {
        TeamRole role = team.roleOf(playerId);
        return role == TeamRole.OWNER || role == TeamRole.ADMIN;
    }

    private boolean canManageUpgrades(Team team, UUID playerId) {
        TeamRole role = team.roleOf(playerId);
        return role == TeamRole.OWNER || role == TeamRole.ADMIN;
    }

    private void broadcastTeamMessage(Team team, String messageKey, Map<String, String> replacements) {
        for (UUID memberId : plugin.getTeamService().allMembers(team)) {
            Player target = Bukkit.getPlayer(memberId);
            if (target != null) {
                plugin.getMessages().send(target, messageKey, replacements);
            }
        }
    }

    private void sound(Player player, String key) {
        FileConfiguration configuration = plugin.getConfig();
        String value = configuration.getString("sounds." + key);
        if (value == null) {
            return;
        }
        try {
            SoundTypes.resolve(value).ifPresent(sound -> player.playSound(player.getLocation(), sound, 0.8f, 1f));
        } catch (RuntimeException exception) {
            plugin.getLogger().log(Level.FINE, "Failed to play configured GUI sound " + key + ".", exception);
        }
    }

    private enum ConfigValueType {
        BOOLEAN,
        INTEGER,
        DOUBLE,
        MONEY,
        STRING
    }

    private record ConfigCategory(String id, String title, Material material, List<String> lore, List<ConfigSetting> settings) {
    }

    private record ConfigSetting(String path, String label, Material material, ConfigValueType type, String expectedInput, double min, double max) {
        private boolean hasMax() {
            return !Double.isNaN(max);
        }
    }

    private record MemberView(UUID id, TeamRole role) {
    }

    private record AdminEditorContext(String browserSearch, int browserPage) {
        AdminEditorContext {
            browserSearch = browserSearch == null ? "" : browserSearch;
            browserPage = Math.max(0, browserPage);
        }
    }

    private enum ConfirmBackTarget {
        SETTINGS,
        ADMIN_EDITOR
    }

    private enum TransferConfirmOrigin {
        TRANSFER_GUI,
        MEMBERS
    }

    @FunctionalInterface
    private interface CheckedAction {
        void run() throws Exception;
    }
}
