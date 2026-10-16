package me.foesio.foTeams;

import me.foesio.core.FoCoreContext;
import me.foesio.core.FoPluginCore;
import me.foesio.core.economy.VaultEconomyBridge;
import me.foesio.core.dialog.NativeDialogConfigDefaults;
import me.foesio.core.dialog.DialogIcons;
import me.foesio.core.message.FoMessageMigrations;
import me.foesio.core.message.FoMessageService;
import me.foesio.core.sound.FoAdminSounds;
import me.foesio.core.sound.FoEditorSounds;
import me.foesio.core.sound.FoGuiSounds;
import me.foesio.core.sound.FoSoundService;
import me.foesio.core.update.UpdateNoticeService;
import me.foesio.core.text.PromptNormalizer;
import me.foesio.foTeams.command.AdminCommand;
import me.foesio.foTeams.command.TeamCommand;
import me.foesio.foTeams.config.PluginConfigs;
import me.foesio.foTeams.gui.GuiService;
import me.foesio.foTeams.gui.PublicGuiConfig;
import me.foesio.foTeams.hook.FoLevelsTeamXpHook;
import me.foesio.foTeams.hook.FoTeamsPlaceholders;
import me.foesio.foTeams.input.InputGuiService;
import me.foesio.foTeams.input.InputGuiServices;
import me.foesio.foTeams.input.CoreInputGuiService;
import me.foesio.foTeams.input.BalanceDialogConfig;
import me.foesio.foTeams.input.TeamDialogMigration;
import me.foesio.foTeams.service.TeamSoundMigration;
import me.foesio.foTeams.listener.ChatListener;
import me.foesio.foTeams.listener.CombatListener;
import me.foesio.foTeams.listener.GuiListener;
import me.foesio.foTeams.listener.PlayerListener;
import me.foesio.foTeams.service.PromptService;
import me.foesio.foTeams.service.PublicChatPresentation;
import me.foesio.foTeams.service.RolePermissionService;
import me.foesio.foTeams.service.SwearFilterService;
import me.foesio.foTeams.service.TeamChatService;
import me.foesio.foTeams.service.TeamLevelService;
import me.foesio.foTeams.service.TeamService;
import me.foesio.foTeams.service.TeleportDelayService;
import me.foesio.foTeams.storage.Database;
import me.foesio.foTeams.storage.TeamRepository;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

public final class FoTeams extends JavaPlugin {
    private static final int BSTATS_PLUGIN_ID = 32761;
    private static final List<String> TEAM_LEVEL_CONFIG_DEFAULT_PATHS = List.of(
            "team-level.enabled",
            "team-level.show-xp-gain",
            "team-level.autosave-seconds",
            "team-level.formula.base-required-xp",
            "team-level.formula.multiplier",
            "team-level.formula.max-level",
            "team-level.rewards.2.messages",
            "team-level.rewards.2.console-commands",
            "team-level.rewards.2.member-commands"
    );

    private FoCoreContext core;
    private PluginConfigs pluginConfigs;
    private InputGuiService inputGuiService;
    private FoMessageService messages;
    private RolePermissionService rolePermissions;
    private SwearFilterService swearFilterService;
    private Database database;
    private TeamRepository repository;
    private TeamService teamService;
    private VaultEconomyBridge economyService;
    private PromptService promptService;
    private TeamChatService teamChatService;
    private TeamLevelService teamLevelService;
    private TeleportDelayService teleportDelayService;
    private GuiService guiService;
    private TeamCommand commandTeam;
    private AdminCommand commandAdmin;
    private ChatListener chatListener;
    private CombatListener combatListener;
    private boolean placeholdersRegistered;
    private BukkitTask teamLevelAutosaveTask;
    private UpdateNoticeService updates;
    private FoSoundService sounds;
    private FoAdminSounds adminSounds;
    private FoEditorSounds editorSounds;
    private FoGuiSounds guiSounds;
    private PublicGuiConfig publicGui;
    private PublicChatPresentation publicChat;
    private boolean freshDataFolder;
    private int migrationStartVersion;

    @Override
    public void onEnable() {
        freshDataFolder = isFreshDataFolder(getDataFolder().toPath());
        saveDefaultConfig();
        ensureConfigDefaults();
        try {
            bootstrap();
            freshDataFolder = false;
            getLogger().info("FoTeams enabled.");
        } catch (Exception exception) {
            getLogger().log(Level.SEVERE, "Failed to enable FoTeams.", exception);
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    static boolean isFreshDataFolder(Path folder) {
        if (!Files.exists(folder)) {
            return true;
        }
        try (var paths = Files.walk(folder)) {
            return paths.noneMatch(Files::isRegularFile);
        } catch (IOException failure) {
            return false;
        }
    }

    private void bootstrap() throws Exception {
        pluginConfigs = new PluginConfigs(this);
        pluginConfigs.loadAll();
        ensureAuxiliaryConfigDefaults();
        swearFilterService = new SwearFilterService(pluginConfigs.swearWords());
        core = FoPluginCore.create(this);
        migrationStartVersion = core.migrations().version();
        core.warnIfNativeDialogsUnavailable();
        if (!new java.io.File(getDataFolder(), "messages.yml").exists()) {
            saveResource("messages.yml", false);
        }
        messages = FoMessageService.loadPreservingRemovals(this, messageMigrations());
        migrateSprites();
        publicGui = new PublicGuiConfig(this);
        publicGui.initialize(core.migrations());
        publicChat = new PublicChatPresentation();
        publicChat.initialize(getConfig(), bundledConfig(), getLogger()::warning);
        if (!TeamDialogMigration.install(this, core.migrations())) {
            getLogger().warning("Public dialog migration is pending; installed dialogs will use safe available paths.");
        }
        migratePublicMessageKeys();
        if (!TeamSoundMigration.install(this, core.migrations())) {
            getLogger().warning("Public sound migration is pending; installed sounds will use safe available paths.");
        }
        sounds = FoSoundService.loadPreservingRemovals(this);
        adminSounds = FoAdminSounds.create(sounds);
        editorSounds = FoEditorSounds.create(sounds);
        guiSounds = FoGuiSounds.create(sounds);
        startMetrics();
        updates = core.createUpdateNotices(messages, "foteams", adminSounds).start();
        rolePermissions = new RolePermissionService(pluginConfigs.permissions());
        database = new Database(this);
        database.connect();
        repository = new TeamRepository(database);
        teamService = createTeamService();
        startTeamLevelAutosaveTask();
        economyService = core.createVaultEconomy();
        promptService = new PromptService();
        inputGuiService = InputGuiServices.create(this, core);
        teamChatService = new TeamChatService();
        teamLevelService = new TeamLevelService(this);
        teleportDelayService = new TeleportDelayService(this);
        guiService = new GuiService(this);
        chatListener = new ChatListener(this);
        combatListener = new CombatListener(this);
        commandTeam = new TeamCommand(this, chatListener);
        commandAdmin = new AdminCommand(this);
        registerCommands();
        registerListeners();
        registerPlaceholders();
    }

    private FoMessageMigrations messageMigrations() {
        return FoMessageMigrations.create()
                .add(config -> {
                    boolean changed = false;
                    if (config.contains("prefix") && !config.contains("tokens.prefix")) {
                        config.set("tokens.prefix", config.getString("prefix"));
                        changed = true;
                    }
                    if (config.contains("prefix")) {
                        config.set("prefix", null);
                        changed = true;
                    }
                    return changed;
                })
                .removeExact("native-dialogs-fallback", "{prefix}{bad}Native dialogs are not supported on this server. Using chat input.")
                .remove("update-checking")
                .remove("update-current")
                .remove("update-failed")
                .remove("update-available")
                .replaceExact(
                        "tag-color-invalid",
                        "{prefix}{bad}Use a tag color like {theme}&e{bad}, {theme}yellow{bad}, or {theme}#03fc88{bad}.",
                        "{prefix}{bad}Use a tag color like {theme}&&e{theme}e{bad}, {theme}yellow{bad}, or {theme}##03fc88{theme}03fc88{bad}."
                )
                .replaceExact(
                        "tag-color-invalid",
                        "{prefix}{bad}Use a tag color like {theme}&\u200Be{bad}, {theme}yellow{bad}, or {theme}#\u200B03fc88{bad}.",
                        "{prefix}{bad}Use a tag color like {theme}&&e{theme}e{bad}, {theme}yellow{bad}, or {theme}##03fc88{theme}03fc88{bad}."
                )
                .replaceExact(
                        "tag-color-invalid",
                        "{prefix}{bad}Use a tag color like {theme}&\uFE0Ee{bad}, {theme}yellow{bad}, or {theme}#\uFE0E03fc88{bad}.",
                        "{prefix}{bad}Use a tag color like {theme}&&e{theme}e{bad}, {theme}yellow{bad}, or {theme}##03fc88{theme}03fc88{bad}."
                )
                .replaceExact(
                        "prompt-tag-color",
                        "{prefix}{muted}Type a tag color like {white}&e{muted}, {white}yellow{muted}, or {white}#03fc88{muted}. Type {white}cancel {muted}to abort.",
                        "{prefix}{muted}Type a tag color like {white}&&e{white}e{muted}, {white}yellow{muted}, or {white}##03fc88{white}03fc88{muted}. Type {white}cancel {muted}to abort."
                )
                .replaceExact(
                        "prompt-tag-color",
                        "{prefix}{muted}Type a tag color like {white}&\u200Be{muted}, {white}yellow{muted}, or {white}#\u200B03fc88{muted}. Type {white}cancel {muted}to abort.",
                        "{prefix}{muted}Type a tag color like {white}&&e{white}e{muted}, {white}yellow{muted}, or {white}##03fc88{white}03fc88{muted}. Type {white}cancel {muted}to abort."
                )
                .replaceExact(
                        "prompt-tag-color",
                        "{prefix}{muted}Type a tag color like {white}&\uFE0Ee{muted}, {white}yellow{muted}, or {white}#\uFE0E03fc88{muted}. Type {white}cancel {muted}to abort.",
                        "{prefix}{muted}Type a tag color like {white}&&e{white}e{muted}, {white}yellow{muted}, or {white}##03fc88{white}03fc88{muted}. Type {white}cancel {muted}to abort."
                )
                .replaceExact(
                        "money-invalid",
                        "{prefix}{bad}That amount is not a valid number. Use digits like {white}100{bad} or {white}25.5{bad}.",
                        "{prefix}{bad}That amount is not valid. Use {white}100{bad}, {white}25.5{bad}, {white}10k{bad}, {white}2M{bad}, {white}1Qa{bad}, or {white}1Td{bad}."
                )
                .build();
    }

    private void migrateSprites() {
        messages.migrateToVersion(core.migrations(), 1, config -> {
            boolean changed = false;
            changed |= FoMessageService.addMissingToken(config, "tokens.prefix", ":diamond_helmet:", null);
            changed |= FoMessageService.addMissingToken(config, "team-created", ":emerald:");
            changed |= FoMessageService.addMissingToken(config, "team-disbanded", ":lava_bucket:");
            changed |= FoMessageService.addMissingToken(config, "team-deleted", ":lava_bucket:");
            changed |= FoMessageService.addMissingToken(config, "invite-sent", ":paper:");
            changed |= FoMessageService.addMissingToken(config, "join-success", ":emerald:");
            changed |= FoMessageService.addMissingToken(config, "home-set", ":compass:");
            changed |= FoMessageService.addMissingToken(config, "home-deleted", ":lava_bucket:");
            changed |= FoMessageService.addMissingToken(config, "warp-set", ":compass:");
            changed |= FoMessageService.addMissingToken(config, "warp-deleted", ":lava_bucket:");
            changed |= FoMessageService.addMissingToken(config, "relation-updated", ":compass:");
            changed |= FoMessageService.addMissingToken(config, "upgrade-team-size-success", ":emerald:");
            changed |= FoMessageService.addMissingToken(config, "upgrade-echest-success", ":ender_chest:");
            changed |= FoMessageService.addMissingToken(config, "reload-success", ":emerald:");
            changed |= FoMessageService.addMissingToken(config, "editor-opened", ":book:");
            changed |= FoMessageService.addMissingToken(config, "editor-setting-saved", ":emerald:");
            changed |= FoMessageService.addMissingToken(config, "editor-setting-save-failed", ":redstone:");
            return changed;
        });
        messages.migrateToVersion(core.migrations(), 2, config -> {
            String oldPrefix = ":shield: {theme}FoTeams &8» {muted}";
            if (!oldPrefix.equals(config.getString("tokens.prefix"))) {
                return false;
            }
            config.set("tokens.prefix", ":diamond_helmet: {theme}FoTeams &8» {muted}");
            return true;
        });
        messages.migrateToVersion(core.migrations(), 3, config -> {
            String prefix = config.getString("tokens.prefix");
            if (prefix == null || !DialogIcons.containsToken(prefix, ":shield:")) {
                return false;
            }
            String cleaned = prefix.replaceAll("(?i)(?::shield:|<sprite:shield>)", "").stripLeading();
            if (!DialogIcons.containsToken(cleaned, ":diamond_helmet:")) {
                cleaned = ":diamond_helmet: " + cleaned;
            }
            config.set("tokens.prefix", cleaned);
            return true;
        });
        messages.reload();
    }

    private void migratePublicMessageKeys() {
        int originalVersion = core.migrations().version();
        if (originalVersion < 7 || originalVersion >= 9) {
            return;
        }
        List<String> keys = List.of("pvp-protection-locked", "score-top-header", "score-top-entry",
                "bank-balance", "balance-top-header", "balance-top-entry");
        List<String> promptKeys = List.of("prompt-name", "prompt-tag", "prompt-description",
                "prompt-tag-color", "prompt-warp-create", "prompt-warp-create-password",
                "prompt-team-target", "prompt-player-target", "prompt-score", "prompt-money",
                "prompt-bank-action", "prompt-team-search");
        YamlConfiguration defaults;
        YamlConfiguration oldV8;
        YamlConfiguration oldV3;
        try {
            defaults = bundledMessages("messages.yml");
            oldV8 = bundledMessages("legacy/messages-v8.yml");
            oldV3 = bundledMessages("legacy/messages-v3.yml");
        } catch (Exception failure) {
            getLogger().warning("Public message migration remains pending: " + failure.getMessage());
            return;
        }
        Path file = getDataFolder().toPath().resolve("messages.yml");
        byte[] original;
        try {
            original = Files.readAllBytes(file);
            boolean changing = keys.stream().anyMatch(key -> !messages.config().contains(key, false))
                    || promptKeys.stream().anyMatch(key -> matchesKnownMessageDefault(messages.config(), key, oldV8, oldV3)
                            && !java.util.Objects.equals(messages.config().get(key), defaults.get(key)));
            if (changing) {
                Path backup = file.resolveSibling("messages.yml.gui-v" + migrationStartVersion + "-backup");
                if (Files.exists(backup)) {
                    if (!java.util.Arrays.equals(original, Files.readAllBytes(backup))) {
                        throw new IOException("Existing original-version message backup differs");
                    }
                } else {
                    Files.write(backup, original);
                }
            }
        } catch (IOException failure) {
            getLogger().warning("Public message migration remains pending: " + failure.getMessage());
            return;
        }
        if (!messages.migrateToVersion(core.migrations(), 9, owner -> {
            boolean changed = false;
            for (String key : keys) {
                if (!owner.contains(key, false)) {
                    owner.set(key, defaults.get(key));
                    changed = true;
                }
            }
            for (String key : promptKeys) {
                if (matchesKnownMessageDefault(owner, key, oldV8, oldV3)
                        && !java.util.Objects.equals(owner.get(key), defaults.get(key))) {
                    owner.set(key, defaults.get(key));
                    changed = true;
                }
            }
            return changed;
        })) {
            try {
                Files.write(file, original);
                messages.reload();
            } catch (IOException rollbackFailure) {
                getLogger().severe("Could not roll back public messages: " + rollbackFailure.getMessage());
            }
            getLogger().warning("Public message migration remains pending; older messages remain available.");
        }
    }

    private boolean matchesKnownMessageDefault(org.bukkit.configuration.file.FileConfiguration owner,
                                               String key, YamlConfiguration oldV8, YamlConfiguration oldV3) {
        return owner.contains(key, false) && (java.util.Objects.equals(owner.get(key), oldV8.get(key))
                || java.util.Objects.equals(owner.get(key), oldV3.get(key)));
    }

    private YamlConfiguration bundledMessages(String path) throws Exception {
        try (InputStream stream = getResource(path)) {
            if (stream == null) {
                throw new IOException("Bundled " + path + " is missing");
            }
            YamlConfiguration yaml = new YamlConfiguration();
            yaml.load(new InputStreamReader(stream, StandardCharsets.UTF_8));
            return yaml;
        }
    }

    private YamlConfiguration bundledConfig() {
        try {
            return bundledMessages("config.yml");
        } catch (Exception failure) {
            throw new IllegalStateException("Bundled config.yml is unavailable", failure);
        }
    }

    private void registerCommands() {
        PluginCommand team = getCommand("foteams");
        PluginCommand admin = getCommand("foteamsadmin");
        if (team != null) {
            team.setExecutor(commandTeam);
            team.setTabCompleter(commandTeam);
        }
        if (admin != null) {
            admin.setExecutor(commandAdmin);
            admin.setTabCompleter(commandAdmin);
        }
    }

    private void registerListeners() {
        // CorePublicMenus owns the sole GuiListener registration for public and shared inventories.
        getServer().getPluginManager().registerEvents(chatListener, this);
        getServer().getPluginManager().registerEvents(combatListener, this);
        getServer().getPluginManager().registerEvents(new PlayerListener(this), this);
        if (teamLevelService.isFoLevelsAvailable()) {
            new FoLevelsTeamXpHook(this).register();
        } else if (teamLevelService.isConfiguredEnabled()) {
            getLogger().warning("Team levels are enabled in config, but FoLevels is not installed or enabled. Team levels will stay disabled.");
        }
    }

    private void registerPlaceholders() {
        if (getServer().getPluginManager().getPlugin("PlaceholderAPI") == null) {
            placeholdersRegistered = false;
            return;
        }
        if (placeholdersRegistered) {
            return;
        }
        placeholdersRegistered = FoTeamsPlaceholders.register(this);
    }

    public void reloadPlugin() throws Exception {
        if (publicChat != null && !publicChat.validateInstalled(
                getDataFolder().toPath().resolve("config.yml").toFile(), getLogger()::warning)) {
            throw new IllegalStateException("Installed public chat text is invalid; current text remains active.");
        }
        if (guiService != null && !guiService.validatePublicMenus()) {
            throw new IllegalStateException("Installed public GUI files are invalid; current menus remain active.");
        }
        if (publicGui != null && !publicGui.validateInstalled()) {
            throw new IllegalStateException("Installed public text is invalid; current text remains active.");
        }
        if (!CoreInputGuiService.validateInstalled(this)) {
            throw new IllegalStateException("Installed public dialogs are invalid; current dialogs remain active.");
        }
        if (!BalanceDialogConfig.validateInstalled(this)) {
            throw new IllegalStateException("Installed balance dialog is invalid; current dialog remains active.");
        }
        if (sounds != null && new java.io.File(getDataFolder(), "sounds.yml").isFile()
                && !sounds.validateInstalled()) {
            throw new IllegalStateException("Installed sounds are invalid; current sounds remain active.");
        }
        if (guiService != null) {
            guiService.closeOpenSharedChestsForReload();
        }
        if (teleportDelayService != null) {
            teleportDelayService.cancelAll();
        }
        teamService.saveAll();
        reloadConfig();
        ensureConfigDefaults();
        publicChat.reload(getConfig());
        pluginConfigs.reloadAll();
        ensureAuxiliaryConfigDefaults();
        swearFilterService = new SwearFilterService(pluginConfigs.swearWords());
        if (inputGuiService != null) {
            inputGuiService.close();
        }
        if (core != null) {
            core.close();
        }
        core = FoPluginCore.create(this);
        core.warnIfNativeDialogsUnavailable();
        sounds.reload();
        messages.reload();
        if (!publicGui.reload()) {
            throw new IllegalStateException("Public text reload failed; previous values remain active.");
        }
        guiService.reloadPublicMenus();
        startMetrics();
        inputGuiService = InputGuiServices.create(this, core);
        teleportDelayService = new TeleportDelayService(this);
        rolePermissions = new RolePermissionService(pluginConfigs.permissions());
        teamService = createTeamService();
        startTeamLevelAutosaveTask();
        economyService = core.createVaultEconomy();
        registerPlaceholders();
    }

    public PublicGuiConfig publicGui() {
        return publicGui;
    }

    public PublicChatPresentation publicChat() {
        return publicChat;
    }

    public void sendMessage(CommandSender sender, String path) {
        sendMessage(sender, path, Map.of());
    }

    public void sendMessage(CommandSender sender, String path, Map<String, String> replacements) {
        if (messages.config().isString(path) && !messages.config().getString(path, "").isEmpty()) {
            messages.send(sender, path, "", replacements);
        } else if (messages.config().isList(path) && !messages.config().getStringList(path).isEmpty()) {
            messages.sendList(sender, path, List.of(), replacements);
        }
    }

    public void sendMessageList(CommandSender sender, String path) {
        if (messages.config().isList(path) && !messages.config().getStringList(path).isEmpty()) {
            messages.sendList(sender, path, List.of(), Map.of());
        }
    }

    public boolean isCancelInput(String input) {
        String configured = publicGui == null ? "cancel" : publicGui.label("chat.cancel-keyword", "cancel");
        return PromptNormalizer.isCancel(input)
                || (!configured.isBlank() && PromptNormalizer.normalize(input).equalsIgnoreCase(configured));
    }

    public boolean freshDataFolder() {
        return freshDataFolder;
    }

    public int migrationStartVersion() {
        return migrationStartVersion;
    }

    private TeamService createTeamService() throws SQLException {
        return new TeamService(repository, rolePermissions, getLogger(), teamServiceSettings());
    }

    private void startMetrics() {
        core.metrics(BSTATS_PLUGIN_ID);
    }

    private TeamService.Settings teamServiceSettings() {
        return new TeamService.Settings(
                getConfig().getInt("max-members-per-team", 30),
                getConfig().getInt("default-members-per-team", 20),
                getConfig().getInt("max-warps-per-team", 5),
                getConfig().getInt("max-team-name-length", 12),
                getConfig().getInt("max-team-tag-length", 4),
                getConfig().getInt("invite-expiry-seconds", 300),
                getConfig().getBoolean("team-pvp-default-enabled", true),
                getConfig().getInt("max-team-echest-rows", 6),
                getConfig().getInt("default-team-echest-rows", 3),
                getConfig().getDouble("upgrade-costs.team-size.base", 250000D),
                getConfig().getDouble("upgrade-costs.team-size.multiplier", 1.35D),
                getConfig().getDouble("upgrade-costs.echest.base", 10000000D),
                getConfig().getDouble("upgrade-costs.echest.multiplier", 1.5D));
    }

    private void ensureConfigDefaults() {
        boolean missingTeamLevelDefaults = missingTeamLevelConfigDefaults();
        boolean changed = needsNativeDialogDefaultSave();
        NativeDialogConfigDefaults.addDefaults(getConfig());
        changed |= migrateLegacyPvpForceKey();
        changed |= copyMissingConfigDefaultsFromResource();
        if (missingTeamLevelDefaults) {
            changed |= applyTeamLevelConfigComments();
        }
        if (changed) {
            saveConfig();
        }
    }

    private boolean needsNativeDialogDefaultSave() {
        return !getConfig().isSet(NativeDialogConfigDefaults.ENABLED_PATH)
                || !getConfig().isSet(NativeDialogConfigDefaults.WARN_ON_FALLBACK_PATH)
                || getConfig().getComments("native-dialogs").isEmpty()
                || getConfig().getComments(NativeDialogConfigDefaults.ENABLED_PATH).isEmpty()
                || getConfig().getComments(NativeDialogConfigDefaults.WARN_ON_FALLBACK_PATH).isEmpty();
    }

    private boolean migrateLegacyPvpForceKey() {
        boolean changed = false;
        if (!getConfig().isSet("team-pvp-force-disable-all") && getConfig().isSet("team-pvp-force-enable-all")) {
            // Migrate legacy key if present; true means team PvP protection is globally forced off.
            boolean legacy = getConfig().getBoolean("team-pvp-force-enable-all", false);
            getConfig().set("team-pvp-force-disable-all", legacy);
            changed = true;
        }
        if (getConfig().isSet("team-pvp-force-enable-all")) {
            getConfig().set("team-pvp-force-enable-all", null);
            changed = true;
        }
        return changed;
    }

    private boolean copyMissingConfigDefaultsFromResource() {
        try (InputStream stream = getResource("config.yml")) {
            if (stream == null) {
                return false;
            }
            YamlConfiguration defaults = YamlConfiguration.loadConfiguration(new InputStreamReader(stream, StandardCharsets.UTF_8));
            boolean changed = false;
            for (String path : defaults.getKeys(true)) {
                if (path == null || path.isEmpty() || defaults.isConfigurationSection(path)) {
                    continue;
                }
                // The versioned public GUI migration must see owner omissions in
                // the released config.yml, not a value just backfilled here.
                if (path.startsWith("gui.") || path.equals("chat.team-format")
                        || path.equals("chat.ally-format") || path.equals("chat.spy-format")
                        || path.equals("placeholders.no-team") || path.equals("placeholders.no-rank")) {
                    continue;
                }
                if (!getConfig().isSet(path)) {
                    // Only fill absent leaves so existing server config values stay intact.
                    getConfig().set(path, defaults.get(path));
                    changed = true;
                }
            }
            if (changed) {
                getLogger().info("Added missing default entries to config.yml");
            }
            return changed;
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to merge defaults for config.yml", exception);
        }
    }

    private boolean missingTeamLevelConfigDefaults() {
        for (String path : TEAM_LEVEL_CONFIG_DEFAULT_PATHS) {
            if (!getConfig().isSet(path)) {
                return true;
            }
        }
        return false;
    }

    private boolean applyTeamLevelConfigComments() {
        boolean changed = false;
        changed |= applyConfigComments("team-level", List.of(
                "Team level integration. Requires FoLevels to be installed and enabled.",
                "Existing teams are kept intact; old databases get team_level and team_xp columns added automatically."
        ));
        changed |= applyConfigComments("team-level.enabled", List.of("Enables team XP gain from FoLevels XP events."));
        changed |= applyConfigComments("team-level.show-xp-gain", List.of("Sends team-level-xp-gained to the player who earned XP."));
        changed |= applyConfigComments("team-level.autosave-seconds", List.of(
                "How often dirty team_level/team_xp changes are saved.",
                "Minimum effective value is 5 seconds. Full team saves still happen on shutdown/reload."
        ));
        changed |= applyConfigComments("team-level.formula.base-required-xp", List.of("XP required for a level 1 team to reach level 2."));
        changed |= applyConfigComments("team-level.formula.multiplier", List.of("Required XP is multiplied by this value for each later level."));
        changed |= applyConfigComments("team-level.formula.max-level", List.of("0 means no level cap."));
        changed |= applyConfigComments("team-level.rewards.2", List.of("Copy this block and change the number to add rewards for another level."));
        changed |= applyConfigComments("team-level.rewards.2.messages", List.of("Messages sent to all online team members when this level is reached."));
        changed |= applyConfigComments("team-level.rewards.2.console-commands", List.of(
                "Console commands run once when this level is reached.",
                "Placeholders: {team}, {team_id}, {level}, {xp}, {current}, {required}, {source}, {source_uuid}, {reason}, {source_key}, {owner}"
        ));
        changed |= applyConfigComments("team-level.rewards.2.member-commands", List.of(
                "Console commands run once for each online team member.",
                "Adds per-member placeholders: {player}, {player_uuid}"
        ));
        return changed;
    }

    private boolean applyConfigComments(String path, List<String> comments) {
        if (comments.equals(getConfig().getComments(path))) {
            return false;
        }
        getConfig().setComments(path, comments);
        return true;
    }

    private void ensureAuxiliaryConfigDefaults() {
        boolean permissionsChanged = pluginConfigs.permissions().copyMissingDefaultsFromResource();
        boolean swearWordsChanged = pluginConfigs.swearWords().copyMissingDefaultsFromResource();
        if (permissionsChanged) {
            getLogger().info("Added missing default entries to permissions.yml");
            pluginConfigs.permissions().reload();
        }
        if (swearWordsChanged) {
            getLogger().info("Added missing default entries to swear-words.yml");
            pluginConfigs.swearWords().reload();
        }
    }

    @Override
    public void onDisable() {
        stopTeamLevelAutosaveTask();
        if (guiService != null) {
            guiService.saveOpenSharedChests();
            guiService.closePublicMenus();
        }
        if (teleportDelayService != null) {
            teleportDelayService.cancelAll();
        }
        if (teamService != null) {
            try {
                teamService.saveAll();
            } catch (Exception exception) {
                getLogger().log(Level.WARNING, "Failed to save teams.", exception);
            }
        }
        if (inputGuiService != null) {
            inputGuiService.close();
        }
        if (core != null) {
            core.close();
            core = null;
        }
        if (database != null) {
            database.close();
        }
    }

    private void startTeamLevelAutosaveTask() {
        stopTeamLevelAutosaveTask();
        if (teamService == null) {
            return;
        }
        if (!getConfig().getBoolean("team-level.enabled", false)) {
            return;
        }

        long seconds = Math.max(5L, getConfig().getLong("team-level.autosave-seconds", 30L));
        long ticks = seconds * 20L;
        teamLevelAutosaveTask = getServer().getScheduler().runTaskTimer(this, this::flushDirtyTeamLevels, ticks, ticks);
    }

    private void stopTeamLevelAutosaveTask() {
        if (teamLevelAutosaveTask != null) {
            teamLevelAutosaveTask.cancel();
            teamLevelAutosaveTask = null;
        }
    }

    private void flushDirtyTeamLevels() {
        if (teamService == null) {
            return;
        }

        try {
            teamService.flushDirtyTeamLevels();
        } catch (Exception exception) {
            getLogger().log(Level.WARNING, "Failed to save dirty team levels.", exception);
        }
    }

    public PluginConfigs getPluginConfigs() {
        return pluginConfigs;
    }

    public FoCoreContext getCore() {
        return core;
    }

    public InputGuiService getInputGuiService() {
        return inputGuiService;
    }

    public FoMessageService getMessages() {
        return messages;
    }

    public RolePermissionService getRolePermissions() {
        return rolePermissions;
    }

    public SwearFilterService getSwearFilterService() {
        return swearFilterService;
    }

    public TeamService getTeamService() {
        return teamService;
    }

    public VaultEconomyBridge getEconomyService() {
        return economyService;
    }

    public PromptService getPromptService() {
        return promptService;
    }

    public TeamChatService getTeamChatService() {
        return teamChatService;
    }

    public TeamLevelService getTeamLevelService() {
        return teamLevelService;
    }

    public TeleportDelayService getTeleportDelayService() {
        return teleportDelayService;
    }

    public GuiService getGuiService() {
        return guiService;
    }

    public TeamCommand getCommandTeam() {
        return commandTeam;
    }

    public boolean placeholdersRegistered() {
        return placeholdersRegistered;
    }

    public UpdateNoticeService getUpdates() {
        return updates;
    }

    public FoSoundService getSounds() {
        return sounds;
    }

    public FoAdminSounds getAdminSounds() {
        return adminSounds;
    }

    public FoEditorSounds getEditorSounds() {
        return editorSounds;
    }

    public FoGuiSounds getGuiSounds() {
        return guiSounds;
    }

}
