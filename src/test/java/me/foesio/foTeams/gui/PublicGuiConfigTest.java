package me.foesio.foTeams.gui;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PublicGuiConfigTest {

    @Test
    void schemaMigrationBackfillsNewTemplatesAndOnlyReplacesKnownOldDefaults() throws Exception {
        YamlConfiguration target = new YamlConfiguration();
        target.loadFromString("""
                titles:
                  dashboard: 'My translated teams'
                buttons:
                  dashboard:
                    invites:
                      lore: ['My custom invite text']
                    info:
                      lore: ['#ffffffView your team information.']
                  confirm:
                    cancel:
                      lore: []
                """);
        YamlConfiguration defaults = defaults();

        assertTrue(PublicGuiConfig.applySchemaV5(target, defaults));
        assertEquals("My translated teams", target.getString("titles.dashboard"));
        assertEquals(List.of("My custom invite text"), target.getStringList("buttons.dashboard.invites.lore"));
        assertEquals(defaults.getStringList("buttons.dashboard.info.lore"),
                target.getStringList("buttons.dashboard.info.lore"));
        assertEquals(List.of(), target.getStringList("buttons.confirm.cancel.lore"));
        assertEquals("IRON_DOOR", target.getString("navigation.back.material"));
        assertEquals("LIME_WOOL", target.getString("buttons.relations.entry-ally.material"));
        assertEquals("BARRIER", target.getString("buttons.upgrades.size-maxed.material"));

        assertFalse(PublicGuiConfig.applySchemaV5(target, defaults));
    }

    @Test
    void linePlaceholderExpandsDynamicListsWithoutChangingOtherLines() {
        List<String> rendered = PublicGuiConfig.expandLines(
                List.of("Header", "{entries}", "Footer {count}"),
                Map.of("entries", List.of("First", "Second"))
        );

        assertEquals(List.of("Header", "First", "Second", "Footer {count}"), rendered);
    }

    @Test
    void auxiliaryStartupRepairKeepsValidNumberAndKeywordSiblings() throws Exception {
        YamlConfiguration defaults = resource("guis/public-text.yml");
        YamlConfiguration owner = resource("guis/public-text.yml");
        owner.set("numbers.decimal-separator", ",");
        owner.set("numbers.compact-suffixes", List.of("wrong size"));
        owner.set("labels.chat.cancel-keyword", "annuleren");
        owner.set("labels.bank-actions.deposit-keyword", "storten");
        owner.set("labels.bank-actions.withdraw-keyword", "storten");
        List<String> warnings = new ArrayList<>();

        PublicGuiConfig.repairNumberFormat(owner, defaults, warnings::add);
        PublicGuiConfig.repairChatKeywords(owner, defaults, warnings::add);

        assertEquals(",", owner.getString("numbers.decimal-separator"));
        assertEquals(defaults.getStringList("numbers.compact-suffixes"),
                owner.getStringList("numbers.compact-suffixes"));
        assertEquals("annuleren", owner.getString("labels.chat.cancel-keyword"));
        assertEquals(defaults.getString("labels.bank-actions.deposit-keyword"),
                owner.getString("labels.bank-actions.deposit-keyword"));
        assertEquals(defaults.getString("labels.bank-actions.withdraw-keyword"),
                owner.getString("labels.bank-actions.withdraw-keyword"));
        assertEquals(3, warnings.size());
    }

    private YamlConfiguration defaults() throws Exception {
        return resource("legacy/team-v5.yml");
    }

    private YamlConfiguration resource(String path) throws Exception {
        YamlConfiguration defaults = new YamlConfiguration();
        try (var input = getClass().getClassLoader().getResourceAsStream(path)) {
            if (input == null) {
                throw new IllegalStateException("Missing " + path + " test resource");
            }
            defaults.load(new InputStreamReader(input, StandardCharsets.UTF_8));
        }
        return defaults;
    }
}
