package me.foesio.foTeams.gui;

import me.foesio.core.gui.publicgui.PublicGuiSchema;
import me.foesio.core.gui.publicgui.PublicGuiText;
import me.foesio.core.gui.publicgui.PublicGuiPlanner;
import me.foesio.core.gui.publicgui.PublicGuiRenderInput;
import me.foesio.core.gui.publicgui.GuiSnapshot;
import me.foesio.core.gui.publicgui.ClickKind;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CorePublicMenusTest {
    @Test
    void allBundledScreensValidateAgainstTheShadedCoreSchema() throws Exception {
        for (String menu : CorePublicMenus.MENUS) {
            String path = "guis/" + menu + ".yml";
            try (var stream = getClass().getClassLoader().getResourceAsStream(path)) {
                assertNotNull(stream, path);
                var yaml = YamlConfiguration.loadConfiguration(new InputStreamReader(stream, StandardCharsets.UTF_8));
                var definition = PublicGuiSchema.parse("teams." + menu, path, yaml,
                        CorePublicMenus.contract(menu));
                var values = CorePublicMenus.titleValues(Map.of());
                for (var state : definition.states().entrySet()) {
                    PublicGuiText.scalar(state.getValue().name(), values,
                            "states." + state.getKey() + ".name");
                    PublicGuiText.lore(state.getValue().lore(), values, null, null,
                            "states." + state.getKey() + ".lore");
                }
            }
        }
    }

    @Test
    void configuredStateTextPreservesReorderedPlaceholdersAndEmptyLore() throws Exception {
        String path = "guis/dashboard-team.yml";
        try (var stream = getClass().getClassLoader().getResourceAsStream(path)) {
            assertNotNull(stream);
            var yaml = YamlConfiguration.loadConfiguration(new InputStreamReader(stream, StandardCharsets.UTF_8));
            yaml.set("states.info.name", "Équipe {team}: {tag}");
            yaml.set("states.info.lore", List.of());
            var definition = PublicGuiSchema.parse("teams.dashboard-team", path, yaml,
                    CorePublicMenus.contract("dashboard-team"));
            var state = definition.states().get("info");
            assertEquals("Équipe Renards: R", PublicGuiText.scalar(state.name(),
                    Map.of("team", "Renards", "tag", "R"), "states.info.name"));
            assertEquals(List.of(), PublicGuiText.lore(state.lore(), Map.of(), null, null,
                    "states.info.lore"));
        }
    }

    @Test
    void movedDynamicControlKeepsItsConfiguredStateAndClickBinding() throws Exception {
        String path = "guis/dashboard-team.yml";
        try (var stream = getClass().getClassLoader().getResourceAsStream(path)) {
            assertNotNull(stream);
            var yaml = YamlConfiguration.loadConfiguration(new InputStreamReader(stream, StandardCharsets.UTF_8));
            yaml.set("entries.info.slots", List.of(11));
            yaml.set("states.info.name", ":diamond: Équipe {team}");
            yaml.set("states.info.lore", List.of());
            yaml.set("states.info.custom-model-data", 8123);
            var definition = PublicGuiSchema.parse("teams.dashboard-team", path, yaml,
                    CorePublicMenus.contract("dashboard-team"));
            assertNotNull(definition.entries().get("info").sourceItem());
            ItemStack source = org.mockito.Mockito.mock(ItemStack.class);
            org.mockito.Mockito.when(source.clone()).thenReturn(source);
            var input = new PublicGuiRenderInput(Map.of(), Map.of(), Map.of(), Map.of(
                    "info", Map.of(11, new PublicGuiRenderInput.EntryValue("old-slot",
                            source, Map.of("team", "Renards")))));
            assertNotNull(input.entries().get("info").get(11).source());
            var frame = PublicGuiPlanner.plan(new GuiSnapshot<>(7L,
                    Map.of("teams.dashboard-team", definition)), "teams.dashboard-team", input);

            assertFalse(frame.bindings().containsKey(10));
            assertEquals("click", frame.bindings().get(11).actionFor(ClickKind.LEFT));
            assertEquals("old-slot", frame.bindings().get(11).entryId());
            assertNotNull(frame.visuals().get(11).sourceConfig());
            assertEquals(8123, definition.states().get("info").customModelData());
            assertEquals(":diamond: Équipe Renards", PublicGuiText.scalar(
                    definition.states().get("info").name(), frame.visuals().get(11).placeholders(),
                    "states.info.name"));
            assertTrue(definition.states().get("info").lore().isEmpty());
        }
    }

    @Test
    void ownerEditedTitleReceivesTeamAndPageContext() throws Exception {
        String path = "guis/members.yml";
        try (var stream = getClass().getClassLoader().getResourceAsStream(path)) {
            assertNotNull(stream);
            var yaml = YamlConfiguration.loadConfiguration(new InputStreamReader(stream, StandardCharsets.UTF_8));
            yaml.set("title", "Équipe {team} — {page}/{max_page}");
            var definition = PublicGuiSchema.parse("teams.members", path, yaml,
                    CorePublicMenus.contract("members"));
            var input = new PublicGuiRenderInput(CorePublicMenus.titleValues(Map.of(
                    "team", "Renards", "page", "2", "max_page", "3")), Map.of(), Map.of(), Map.of());
            var frame = PublicGuiPlanner.plan(new GuiSnapshot<>(9L,
                    Map.of("teams.members", definition)), "teams.members", input);

            assertEquals("Équipe Renards — 2/3", frame.title());
        }
    }

    @Test
    void ownerEditedSharedChestTitleAndLockedLoreAcceptTeamContext() throws Exception {
        String path = "guis/chest.yml";
        try (var stream = getClass().getClassLoader().getResourceAsStream(path)) {
            assertNotNull(stream);
            var yaml = YamlConfiguration.loadConfiguration(new InputStreamReader(stream, StandardCharsets.UTF_8));
            yaml.set("title", "Coffre de {team}");
            yaml.set("states.locked.lore", List.of("Équipe {team}"));
            var definition = PublicGuiSchema.parse("teams.chest", path, yaml,
                    CorePublicMenus.contract("chest"));
            var values = CorePublicMenus.titleValues(Map.of("team", "Renards"));
            var frame = PublicGuiPlanner.plan(new GuiSnapshot<>(10L,
                    Map.of("teams.chest", definition)), "teams.chest",
                    new PublicGuiRenderInput(values, Map.of(), Map.of(), Map.of()));

            assertEquals("Coffre de Renards", frame.title());
            assertEquals(List.of("Équipe Renards"), PublicGuiText.lore(
                    definition.states().get("locked").lore(), values, null, null, "states.locked.lore"));
        }
    }
}
