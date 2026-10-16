package me.foesio.foTeams;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FoTeamsInstallStateTest {
    @Test
    void emptyPrecreatedDirectoryIsFreshButAnyOwnerFileIsNot(@TempDir Path root) throws Exception {
        Path pluginFolder = root.resolve("FoTeams");
        assertTrue(FoTeams.isFreshDataFolder(pluginFolder));
        Files.createDirectories(pluginFolder.resolve("guis"));
        assertTrue(FoTeams.isFreshDataFolder(pluginFolder));
        Files.writeString(pluginFolder.resolve("config.yml"), "chat:\n  team-format: '{message}'\n");
        assertFalse(FoTeams.isFreshDataFolder(pluginFolder));
    }
}
