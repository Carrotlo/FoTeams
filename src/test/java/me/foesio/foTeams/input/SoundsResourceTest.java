package me.foesio.foTeams.input;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class SoundsResourceTest {
    @Test
    void publicSoundPathsResolveThroughCoreConfigurationPaths() throws Exception {
        try (var stream = getClass().getClassLoader().getResourceAsStream("sounds.yml")) {
            var yaml = new YamlConfiguration();
            yaml.load(new InputStreamReader(stream, StandardCharsets.UTF_8));
            for (String path : new String[]{"gui.open", "gui.back", "team.created", "team.teleport", "team.failure"}) {
                assertNotNull(yaml.getConfigurationSection(path), path);
            }
        }
    }
}
