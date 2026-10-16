package me.foesio.foTeams.input;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Logger;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PublicDialogsTest {
    @Test
    void removedOptionalTextAndIconStayAbsentInCoreRequest() throws Exception {
        YamlConfiguration owner = resource("dialogs/team-search.yml");
        owner.set("body", List.of());
        owner.set("confirm-button.tooltip", null);
        owner.set("confirm-button.icon", null);
        var request = TeamTextDialogs.parse(owner);
        assertEquals(List.of(), request.body());
        assertEquals("", request.submitButton().tooltip());
        assertEquals("", request.submitButton().icon());
        owner.set("title", 17);
        assertThrows(IllegalArgumentException.class, () -> TeamTextDialogs.parse(owner));
    }

    @Test
    void balanceDialogKeepsOwnerTooltipsAndEmptyBody() throws Exception {
        YamlConfiguration owner = resource("dialogs/balance.yml");
        owner.set("body", List.of());
        owner.set("deposit-button.tooltip", "Déposer {team_balance}");
        owner.set("deposit-button.icon", null);
        var request = BalanceDialogConfig.parse(owner);
        assertEquals(List.of(), request.body());
        assertEquals("Déposer {team_balance}", request.depositButton().tooltip());
        assertEquals("", request.depositButton().icon());
    }

    @Test
    void coldStartKeepsValidDialogAndRepairsOnlyParseableBadField(@TempDir Path data) throws Exception {
        Path directory = Files.createDirectories(data.resolve("dialogs"));
        Files.writeString(directory.resolve("team-name.yml"), "title: [unterminated");
        YamlConfiguration custom = resource("dialogs/team-tag.yml");
        custom.set("title", 42);
        custom.set("field-label", "Étiquette personnalisée");
        Path tag = directory.resolve("team-tag.yml");
        Files.writeString(tag, custom.saveToString());
        TeamTextDialogs dialogs = new TeamTextDialogs(data.toFile(), Logger.getLogger("PublicDialogsTest"));

        assertNull(dialogs.request("team-name"));
        assertNotNull(dialogs.request("team-tag"));
        assertEquals("Étiquette personnalisée", dialogs.request("team-tag").fieldLabel());
        assertEquals(resource("dialogs/team-tag.yml").getString("title"), dialogs.request("team-tag").title());
        assertEquals(custom.saveToString(), Files.readString(tag));

        assertFalse(dialogs.reload());
        assertEquals("Étiquette personnalisée", dialogs.request("team-tag").fieldLabel());
        custom.set("title", "Nouveau titre");
        Files.writeString(tag, custom.saveToString());
        Files.delete(directory.resolve("team-name.yml"));
        assertTrue(dialogs.reload());
        assertEquals("Nouveau titre", dialogs.request("team-tag").title());
    }

    private YamlConfiguration resource(String path) throws Exception {
        try (var stream = getClass().getClassLoader().getResourceAsStream(path)) {
            YamlConfiguration yaml = new YamlConfiguration();
            yaml.load(new InputStreamReader(stream, StandardCharsets.UTF_8));
            return yaml;
        }
    }
}
