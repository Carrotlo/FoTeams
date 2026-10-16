package me.foesio.foTeams.service;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TeamSoundMigrationTest {
    @Test
    void legacySoundOverlayPreservesOwnerChangesAndUsesCurrentDefaultForInvalidValues() {
        YamlConfiguration prior = new YamlConfiguration();
        prior.set("sounds.gui-open", "UI_BUTTON_CLICK");
        prior.set("sounds.success", "ENTITY_EXPERIENCE_ORB_PICKUP");
        prior.set("sounds.failure", "ENTITY_VILLAGER_NO");
        prior.set("sounds.teleport", "ENTITY_ENDERMAN_TELEPORT");

        YamlConfiguration owner = new YamlConfiguration();
        owner.set("sounds.gui-open", "BLOCK_AMETHYST_BLOCK_CHIME");
        owner.set("sounds.success", "NOT_A_SOUND");
        owner.set("sounds.failure", "");
        // An absent old owner key must stay disabled instead of regaining a default.

        YamlConfiguration newest = new YamlConfiguration();
        newest.set("gui.open.sound", "BLOCK_VAULT_OPEN_SHUTTER");
        newest.set("gui.open.enabled", true);
        newest.set("team.success.sound", "ENTITY_PLAYER_LEVELUP");
        newest.set("team.success.enabled", true);
        newest.set("team.failure.enabled", true);
        newest.set("team.teleport.enabled", true);

        TeamSoundMigration.overlay(null, owner, prior, newest);

        assertEquals("BLOCK_AMETHYST_BLOCK_CHIME", newest.getString("gui.open.sound"));
        assertEquals("ENTITY_PLAYER_LEVELUP", newest.getString("team.success.sound"));
        assertFalse(newest.getBoolean("team.failure.enabled"));
        assertFalse(newest.getBoolean("team.teleport.enabled"));
        assertTrue(TeamSoundMigration.validLegacySound("BLOCK_AMETHYST_BLOCK_CHIME"));
        assertFalse(TeamSoundMigration.validLegacySound("NOT_A_SOUND"));
    }
}
