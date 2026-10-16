package me.foesio.foTeams.service;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PublicChatPresentationTest {
    @Test
    void startupRepairsOnlyBadRequiredFieldsInMemoryAndKeepsOwnerText() throws Exception {
        YamlConfiguration defaults = yaml("""
                chat:
                  team-format: '&a{player}: {message}'
                  ally-format: '&b{player}: {message}'
                  spy-format: '&7{player}: {message}'
                placeholders:
                  no-team: '-'
                  no-rank: '-'
                """);
        YamlConfiguration owner = yaml("""
                chat:
                  team-format: '&b{message} — {player} ✓'
                  ally-format: 42
                  spy-format: '&7{message}'
                placeholders:
                  no-team: 'Geen team'
                """);
        String before = owner.saveToString();
        List<String> warnings = new ArrayList<>();
        PublicChatPresentation presentation = new PublicChatPresentation();

        presentation.initialize(owner, defaults, warnings::add);

        assertEquals("&b{message} — {player} ✓", presentation.format("team"));
        assertEquals("&b{player}: {message}", presentation.format("ally"));
        assertEquals("&7{message}", presentation.format("spy"));
        assertEquals("Geen team", presentation.placeholder("no-team"));
        assertEquals("", presentation.placeholder("no-rank"));
        assertEquals(before, owner.saveToString());
        assertTrue(warnings.stream().anyMatch(warning -> warning.contains("chat.ally-format")));
    }

    @Test
    void invalidReloadKeepsPreviousSnapshotAndOptionalRemovalStaysRemoved() throws Exception {
        YamlConfiguration valid = yaml("""
                chat:
                  team-format: '{message} {player}'
                  ally-format: '{message}'
                  spy-format: '{player}'
                placeholders:
                  no-team: 'Teamloos'
                """);
        PublicChatPresentation presentation = new PublicChatPresentation();
        presentation.reload(valid);

        valid.set("chat.ally-format", "");
        assertThrows(IllegalArgumentException.class, () -> presentation.reload(valid));
        assertEquals("{message}", presentation.format("ally"));
        assertEquals("Teamloos", presentation.placeholder("no-team"));

        valid.set("chat.ally-format", "&d{player}");
        valid.set("placeholders.no-team", null);
        presentation.reload(valid);
        assertEquals("&d{player}", presentation.format("ally"));
        assertEquals("", presentation.placeholder("no-team"));
    }

    private YamlConfiguration yaml(String content) throws Exception {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.loadFromString(content);
        return yaml;
    }
}
