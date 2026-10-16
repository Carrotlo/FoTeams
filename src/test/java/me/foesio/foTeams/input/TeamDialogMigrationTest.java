package me.foesio.foTeams.input;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TeamDialogMigrationTest {
    @TempDir Path directory;

    @Test
    void backupAndRollbackRestoreOriginalBytesAndRemoveNewFile() throws Exception {
        Path old = directory.resolve("dialogs/team-name.yml");
        Path fresh = directory.resolve("dialogs/balance.yml");
        Files.createDirectories(old.getParent());
        byte[] original = "title: Équipe\n".getBytes(StandardCharsets.UTF_8);
        Files.write(old, original);
        Map<Path, byte[]> originals = Map.of(old, original);
        Map<Path, byte[]> proposals = Map.of(old, "title: Changed\n".getBytes(StandardCharsets.UTF_8),
                fresh, "title: Bank\n".getBytes(StandardCharsets.UTF_8));
        var allOriginals = new java.util.LinkedHashMap<Path, byte[]>(originals);
        allOriginals.put(fresh, null);

        TeamDialogMigration.commit(proposals, allOriginals, 6);
        assertEquals("title: Changed\n", Files.readString(old));
        assertEquals("title: Équipe\n", Files.readString(old.resolveSibling("team-name.yml.dialog-v6-backup")));
        TeamDialogMigration.rollback(allOriginals);
        assertEquals("title: Équipe\n", Files.readString(old));
        org.junit.jupiter.api.Assertions.assertFalse(Files.exists(fresh));
    }

    @Test
    void overlaysOnlyOldOwnerChangesOntoNewestDialog() throws Exception {
        YamlConfiguration oldDefault = resource("legacy/dialogs-v5/team-name.yml");
        YamlConfiguration owner = resource("legacy/dialogs-v5/team-name.yml");
        owner.set("title", "Équipe {current}");
        owner.set("body", List.of());
        owner.set("confirm-button", "{good}Enregistrer");
        YamlConfiguration newest = resource("dialogs/team-name.yml");

        TeamDialogMigration.overlay(owner, oldDefault, newest);

        assertEquals("Équipe {current}", newest.getString("title"));
        assertEquals(List.of(), newest.getStringList("body"));
        assertEquals("{good}Enregistrer", newest.getString("confirm-button.label"));
        assertEquals("emerald", newest.getString("confirm-button.icon"));
        TeamTextDialogs.parse(newest);
    }

    @Test
    void explicitOwnerButtonSectionCanRemoveNewOptionalIcon() throws Exception {
        YamlConfiguration oldDefault = resource("legacy/dialogs-v5/team-name.yml");
        YamlConfiguration owner = resource("legacy/dialogs-v5/team-name.yml");
        owner.set("confirm-button", null);
        owner.set("confirm-button.label", "{good}Enregistrer");
        YamlConfiguration newest = resource("dialogs/team-name.yml");

        TeamDialogMigration.overlay(owner, oldDefault, newest);

        assertEquals("", TeamTextDialogs.parse(newest).submitButton().icon());
    }

    private YamlConfiguration resource(String path) throws Exception {
        try (var stream = getClass().getClassLoader().getResourceAsStream(path)) {
            YamlConfiguration yaml = new YamlConfiguration();
            yaml.load(new InputStreamReader(stream, StandardCharsets.UTF_8));
            return yaml;
        }
    }
}
