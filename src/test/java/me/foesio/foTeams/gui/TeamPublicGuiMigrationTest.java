package me.foesio.foTeams.gui;

import me.foesio.core.gui.publicgui.PublicGuiSchema;
import me.foesio.core.migration.FoMigrationStore;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import sun.misc.Unsafe;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TeamPublicGuiMigrationTest {
    @TempDir Path directory;

    @Test
    void releasedV3UpgradeStagesEveryScreenAndKeepsExactOldBackup() throws Exception {
        Path guis = Files.createDirectories(directory.resolve("guis"));
        Path oldFile = guis.resolve("team.yml");
        Files.copy(Path.of("src/main/resources/legacy/team-v3.yml"), oldFile);
        YamlConfiguration owner = YamlConfiguration.loadConfiguration(oldFile.toFile());
        owner.set("titles.dashboard", "Owner dashboard");
        owner.set("buttons.dashboard.invites.name", "Owner invites");
        owner.set("buttons.dashboard.invites.material", "NOT_A_MATERIAL");
        owner.save(oldFile.toFile());
        byte[] oldBytes = Files.readAllBytes(oldFile);
        Files.writeString(directory.resolve("config.yml"), "config-version: 3\n");
        TestPlugin plugin = plugin();
        FoMigrationStore versions = FoMigrationStore.create(plugin);

        assertTrue(TeamPublicGuiMigration.install(plugin, versions));
        assertEquals(6, versions.version());
        assertArrayEquals(oldBytes, Files.readAllBytes(guis.resolve("team.yml.gui-v3-backup")));
        assertArrayEquals(oldBytes, Files.readAllBytes(oldFile));
        YamlConfiguration dashboard = YamlConfiguration.loadConfiguration(
                guis.resolve("dashboard-empty.yml").toFile());
        assertEquals("Owner dashboard", dashboard.getString("title"));
        assertEquals("Owner invites", dashboard.getString("states.invites-empty.name"));
        assertEquals("WRITABLE_BOOK", dashboard.getString("states.invites-empty.material"));
        assertEquals(List.of("{invite_list}", "#a7b8b0Click to join the oldest invite."),
                dashboard.getStringList("states.invites.lore"));
        assertTrue(Files.isRegularFile(guis.resolve("public-text.yml")));
        byte[] first = Files.readAllBytes(guis.resolve("dashboard-empty.yml"));
        assertTrue(TeamPublicGuiMigration.install(plugin, versions));
        assertArrayEquals(first, Files.readAllBytes(guis.resolve("dashboard-empty.yml")));
    }

    @Test
    void unchangedReleasedV3GuiGetsNewestDefaultsOnEverySplitScreen() throws Exception {
        YamlConfiguration released = resource("legacy/team-v3.yml");
        for (String menu : CorePublicMenus.MENUS) {
            YamlConfiguration bundled = resource("guis/" + menu + ".yml");
            YamlConfiguration migrated = resource("guis/" + menu + ".yml");
            TeamPublicGuiMigration.overlayScreen(menu, released, released, migrated, 3);
            assertEquals(bundled.saveToString(), migrated.saveToString(), menu);
            PublicGuiSchema.parse("teams." + menu, "guis/" + menu + ".yml", migrated,
                    CorePublicMenus.contract(menu));
        }
    }

    @Test
    void releasedV3DefaultsAndOwnerInviteEditsReachTheRightNewStates() throws Exception {
        YamlConfiguration oldDefault = resource("legacy/team-v3.yml");
        YamlConfiguration owner = resource("legacy/team-v3.yml");
        owner.set("titles.dashboard", "TEAMS MIGRATION TEST");
        owner.set("buttons.dashboard.invites.material", "NOT_A_MATERIAL");
        owner.set("buttons.dashboard.invites.name", "Owner invites");
        owner.set("buttons.dashboard.invites.lore", List.of("Owner no-invites text"));
        YamlConfiguration newest = resource("guis/dashboard-empty.yml");
        YamlConfiguration migrated = resource("guis/dashboard-empty.yml");
        List<String> latestCreateLore = newest.getStringList("states.create.lore");

        TeamPublicGuiMigration.overlayScreen("dashboard-empty", owner, oldDefault, migrated, 3);
        TeamPublicGuiMigration.repairInvalid(null, "dashboard-empty", migrated, newest);

        assertEquals("TEAMS MIGRATION TEST", migrated.getString("title"));
        assertEquals("Owner invites", migrated.getString("states.invites.name"));
        assertEquals("Owner invites", migrated.getString("states.invites-empty.name"));
        assertEquals(List.of("Owner no-invites text"), migrated.getStringList("states.invites-empty.lore"));
        assertEquals(newest.getStringList("states.invites.lore"), migrated.getStringList("states.invites.lore"));
        assertEquals("WRITABLE_BOOK", migrated.getString("states.invites.material"));
        assertEquals("WRITABLE_BOOK", migrated.getString("states.invites-empty.material"));
        assertEquals(latestCreateLore, migrated.getStringList("states.create.lore"));
        PublicGuiSchema.parse("teams.dashboard-empty", "guis/dashboard-empty.yml", migrated,
                CorePublicMenus.contract("dashboard-empty"));
    }

    @Test
    void releasedV3RemovedLoreStaysRemoved() throws Exception {
        YamlConfiguration oldDefault = resource("legacy/team-v3.yml");
        YamlConfiguration owner = resource("legacy/team-v3.yml");
        owner.set("buttons.dashboard.create.lore", null);
        YamlConfiguration migrated = resource("guis/dashboard-empty.yml");

        TeamPublicGuiMigration.overlayScreen("dashboard-empty", owner, oldDefault, migrated, 3);

        assertFalse(migrated.contains("states.create.lore", false));
        PublicGuiSchema.parse("teams.dashboard-empty", "guis/dashboard-empty.yml", migrated,
                CorePublicMenus.contract("dashboard-empty"));
    }

    @Test
    void v4SharedInviteLoreAndV5SeparateInviteStatesStayDistinct() throws Exception {
        YamlConfiguration v5Default = resource("legacy/team-v5.yml");
        YamlConfiguration v4Owner = resource("legacy/team-v5.yml");
        v4Owner.set("buttons.dashboard.invites.lore", List.of("Owner empty invites"));
        YamlConfiguration migratedV4 = resource("guis/dashboard-empty.yml");
        List<String> pendingDefault = migratedV4.getStringList("states.invites.lore");
        TeamPublicGuiMigration.overlayScreen("dashboard-empty", v4Owner, v5Default, migratedV4, 4);
        assertEquals(pendingDefault, migratedV4.getStringList("states.invites.lore"));
        assertEquals(List.of("Owner empty invites"), migratedV4.getStringList("states.invites-empty.lore"));

        YamlConfiguration v5Owner = resource("legacy/team-v5.yml");
        v5Owner.set("buttons.dashboard.invites.name", "Pending invites");
        v5Owner.set("buttons.dashboard.invites-empty.name", "Empty invites");
        YamlConfiguration migratedV5 = resource("guis/dashboard-empty.yml");
        TeamPublicGuiMigration.overlayScreen("dashboard-empty", v5Owner, v5Default, migratedV5, 5);
        assertEquals("Pending invites", migratedV5.getString("states.invites.name"));
        assertEquals("Empty invites", migratedV5.getString("states.invites-empty.name"));
    }

    @Test
    void releasedConfigV3GuiSettingsReachSplitPublicMenus() throws Exception {
        YamlConfiguration old = resource("legacy/config-v3.yml");
        old.set("gui.titles.dashboard", "Mes équipes");
        old.set("gui.filler-material", "BLUE_STAINED_GLASS_PANE");
        old.set("gui.confirm-material", "DIAMOND");
        YamlConfiguration dashboard = resource("guis/dashboard-team.yml");
        YamlConfiguration disband = resource("guis/disband-confirm.yml");

        TeamPublicGuiMigration.overlayOldConfig(old, "dashboard-team", dashboard);
        TeamPublicGuiMigration.overlayOldConfig(old, "disband-confirm", disband);

        assertEquals("Mes équipes", dashboard.getString("title"));
        assertEquals("BLUE_STAINED_GLASS_PANE", dashboard.getString("filler.material"));
        assertEquals("DIAMOND", disband.getString("states.confirm.material"));
        PublicGuiSchema.parse("teams.dashboard-team", "guis/dashboard-team.yml", dashboard,
                CorePublicMenus.contract("dashboard-team"));
        PublicGuiSchema.parse("teams.disband-confirm", "guis/disband-confirm.yml", disband,
                CorePublicMenus.contract("disband-confirm"));
    }

    @Test
    void carriesOwnerTextAndRemovalsWhileRepairingOnlyInvalidField() throws Exception {
        YamlConfiguration oldDefault = resource("legacy/team-v5.yml");
        YamlConfiguration owner = resource("legacy/team-v5.yml");
        owner.set("titles.dashboard", "Équipe {team}");
        owner.set("buttons.dashboard.info.material", "INVALID_BLOCK");
        owner.set("buttons.dashboard.info.name", "Détails {team}");
        owner.set("buttons.dashboard.info.lore", List.of());
        owner.set("buttons.dashboard.info.custom-model-data", null);

        YamlConfiguration bundled = resource("guis/dashboard-team.yml");
        YamlConfiguration migrated = resource("guis/dashboard-team.yml");
        TeamPublicGuiMigration.overlayScreen("dashboard-team", owner, oldDefault, migrated, 5);
        TeamPublicGuiMigration.repairInvalid(null, "dashboard-team", migrated, bundled);

        assertEquals("Équipe {team}", migrated.getString("title"));
        assertEquals("Détails {team}", migrated.getString("states.info.name"));
        assertEquals(List.of(), migrated.getStringList("states.info.lore"));
        assertFalse(migrated.contains("states.info.custom-model-data", false));
        assertEquals(bundled.getString("states.info.material"), migrated.getString("states.info.material"));
        PublicGuiSchema.parse("teams.dashboard-team", "guis/dashboard-team.yml", migrated,
                CorePublicMenus.contract("dashboard-team"));
    }

    @Test
    void v4DefaultLoreUpdatesButTranslatedLoreSurvives() throws Exception {
        YamlConfiguration oldDefault = resource("legacy/team-v5.yml");
        YamlConfiguration owner = resource("legacy/team-v5.yml");
        owner.set("buttons.dashboard.info.lore", List.of("#ffffffView your team information."));
        owner.set("buttons.dashboard.members.lore", List.of("Mes membres personnalisés"));
        YamlConfiguration target = resource("guis/dashboard-team.yml");
        List<String> newestInfo = target.getStringList("states.info.lore");

        TeamPublicGuiMigration.overlayScreen("dashboard-team", owner, oldDefault, target, 4);

        assertEquals(newestInfo, target.getStringList("states.info.lore"));
        assertEquals(List.of("Mes membres personnalisés"), target.getStringList("states.members.lore"));
    }

    private YamlConfiguration resource(String path) throws Exception {
        try (var stream = getClass().getClassLoader().getResourceAsStream(path)) {
            if (stream == null) {
                throw new IllegalArgumentException("Missing test resource " + path);
            }
            YamlConfiguration yaml = new YamlConfiguration();
            yaml.load(new InputStreamReader(stream, StandardCharsets.UTF_8));
            return yaml;
        }
    }

    private TestPlugin plugin() throws Exception {
        Field field = Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        Unsafe unsafe = (Unsafe) field.get(null);
        TestPlugin plugin = (TestPlugin) unsafe.allocateInstance(TestPlugin.class);
        Field dataFolder = JavaPlugin.class.getDeclaredField("dataFolder");
        dataFolder.setAccessible(true);
        dataFolder.set(plugin, directory.toFile());
        plugin.config = YamlConfiguration.loadConfiguration(directory.resolve("config.yml").toFile());
        return plugin;
    }

    private static final class TestPlugin extends JavaPlugin {
        private static final Logger LOGGER = Logger.getLogger("FoTeamsGuiMigrationTest");
        private FileConfiguration config;

        @Override public FileConfiguration getConfig() { return config; }
        @Override public Logger getLogger() { return LOGGER; }
        @Override public InputStream getResource(String name) {
            try {
                return Files.newInputStream(Path.of("src/main/resources").resolve(name));
            } catch (IOException exception) {
                return null;
            }
        }
    }
}
