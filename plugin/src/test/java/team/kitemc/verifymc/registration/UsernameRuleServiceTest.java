package team.kitemc.verifymc.registration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import team.kitemc.verifymc.platform.ConfigManager;
import team.kitemc.verifymc.user.UserRecord;
import team.kitemc.verifymc.user.UserRepository;
import team.kitemc.verifymc.user.UserStatus;

@ExtendWith(MockitoExtension.class)
class UsernameRuleServiceTest {
    @Mock
    private ConfigManager configManager;

    @Mock
    private UserRepository userRepository;

    private UsernameRuleService usernameRuleService;

    @BeforeEach
    void setUp() {
        lenient().when(configManager.getUsernameRegex()).thenReturn("^[a-zA-Z0-9_-]{3,16}$");
        when(configManager.isBedrockEnabled()).thenReturn(true);
        when(configManager.getBedrockPrefix()).thenReturn(".");
        usernameRuleService = new UsernameRuleService(configManager);
    }

    @Test
    void shouldNormalizeBedrockRegistrationUsernameToSinglePrefix() {
        assertEquals(".Steve", usernameRuleService.normalize("..Steve", "bedrock"));
        assertEquals(".Steve", usernameRuleService.normalize("Steve", "bedrock"));
        assertEquals("Steve", usernameRuleService.normalize("Steve", "java"));
    }

    @Test
    void shouldValidateBedrockUsernameAgainstUnifiedRegexWithoutPrefix() {
        when(configManager.getUsernameRegex()).thenReturn("^[a-zA-Z0-9_-]{3,16}$");

        assertTrue(usernameRuleService.isValid("..Steve", "bedrock"));
        assertFalse(usernameRuleService.isValid(".ab", "bedrock"));
        assertFalse(usernameRuleService.isValid(".Steve!", "bedrock"));
    }

    @Test
    void shouldResolveRepeatedPrefixAdminTargetToStoredBedrockUsername() {
        UserRecord bedrockUser = user(".Steve");
        when(userRepository.findByUsernameExact("..Steve")).thenReturn(Optional.empty());
        when(userRepository.findByUsername("..Steve")).thenReturn(Optional.empty());
        when(userRepository.findByUsernameExact(".Steve")).thenReturn(Optional.of(bedrockUser));

        assertEquals(".Steve", usernameRuleService.resolveAdminTarget("..Steve", userRepository));
    }

    @Test
    void shouldResolveBareAdminTargetToExistingBedrockUserWhenOnlyPrefixedUserExists() {
        UserRecord bedrockUser = user(".Steve");
        when(userRepository.findByUsernameExact("Steve")).thenReturn(Optional.empty());
        when(userRepository.findByUsername("Steve")).thenReturn(Optional.empty());
        when(userRepository.findByUsernameExact(".Steve")).thenReturn(Optional.of(bedrockUser));

        assertEquals(".Steve", usernameRuleService.resolveAdminTarget("Steve", userRepository));
    }

    @Test
    void shouldAllowLegacyAdminTargetWhenExistingUserDoesNotMatchUnifiedRegex() {
        UserRecord legacyUser = user("old.name");
        when(userRepository.findByUsernameExact("old.name")).thenReturn(Optional.of(legacyUser));

        assertEquals("old.name", usernameRuleService.resolveAdminTarget("old.name", userRepository));
        assertTrue(usernameRuleService.canOperateAdminTarget("old.name", userRepository));
    }

    @Test
    void shouldRejectInvalidAdminTargetWhenUserDoesNotExist() {
        when(userRepository.findByUsernameExact("old.name")).thenReturn(Optional.empty());
        when(userRepository.findByUsername("old.name")).thenReturn(Optional.empty());

        assertEquals("", usernameRuleService.resolveAdminTarget("old.name", userRepository));
        assertFalse(usernameRuleService.canOperateAdminTarget("old.name", userRepository));
    }

    private UserRecord user(String username) {
        return new UserRecord(
                username,
                "player@example.com",
                UserStatus.PENDING,
                null,
                "hash",
                0L,
                null,
                null,
                null,
                null,
                null
        );
    }
}
