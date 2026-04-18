package team.kitemc.verifymc.user;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import java.nio.file.Path;
import java.util.List;
import java.util.logging.Logger;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;

class FileUserDaoTest {
    @TempDir
    Path tempDir;

    @Test
    void shouldTreatUsernamesCaseInsensitivelyWhenConfigured() {
        try (FileUserDao dao = new FileUserDao(tempDir.resolve("users.json").toFile(), mockPlugin(), false)) {
            assertTrue(dao.create(newUser("Steve")));
            assertFalse(dao.create(newUser("steve")));
            assertTrue(dao.findByUsernameConfigured("steve").isPresent());
            assertTrue(dao.findByUsernameExact("Steve").isPresent());
            assertTrue(dao.findByUsernameExact("steve").isEmpty());
        }
    }

    @Test
    void shouldTreatUsernamesCaseSensitivelyWhenConfigured() {
        try (FileUserDao dao = new FileUserDao(tempDir.resolve("users.json").toFile(), mockPlugin(), true)) {
            assertTrue(dao.create(newUser("Steve")));
            assertTrue(dao.create(newUser("steve")));
            assertTrue(dao.findByUsernameConfigured("Steve").isPresent());
            assertTrue(dao.findByUsernameConfigured("steve").isPresent());
            assertEquals(List.of(List.of("Steve", "steve")), dao.findUsernameCaseConflictGroups());
        }
    }

    private Plugin mockPlugin() {
        Plugin plugin = Mockito.mock(Plugin.class);
        FileConfiguration config = Mockito.mock(FileConfiguration.class);
        when(plugin.getConfig()).thenReturn(config);
        when(plugin.getDataFolder()).thenReturn(tempDir.toFile());
        when(plugin.getLogger()).thenReturn(Logger.getLogger("FileUserDaoTest"));
        when(config.getBoolean("debug", false)).thenReturn(false);
        when(config.getBoolean("username_case_sensitive", false)).thenReturn(false);
        return plugin;
    }

    private NewUserRecord newUser(String username) {
        return new NewUserRecord(
                username,
                username.toLowerCase() + "@example.com",
                UserStatus.PENDING,
                null,
                "password",
                false,
                null,
                null,
                null,
                null
        );
    }
}
