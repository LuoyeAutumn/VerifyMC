package team.kitemc.verifymc.registration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.mockito.junit.jupiter.MockitoExtension;
import team.kitemc.verifymc.platform.WhitelistService;
import team.kitemc.verifymc.questionnaire.QuestionnaireService;
import team.kitemc.verifymc.user.UserRecord;
import team.kitemc.verifymc.user.UserRepository;
import team.kitemc.verifymc.user.UserStatus;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RegisterUserUseCaseTest {
    @Mock
    private RegistrationPolicy registrationPolicy;
    @Mock
    private VerifyCodeService codeService;
    @Mock
    private UserRepository userRepository;
    @Mock
    private RegistrationAuthPort registrationAuthPort;
    @Mock
    private CaptchaService captchaService;
    @Mock
    private QuestionnaireService questionnaireService;
    @Mock
    private RegistrationDiscordPort registrationDiscordPort;
    @Mock
    private WhitelistService whitelistService;
    @Mock
    private UsernameRuleService usernameRuleService;
    @Mock
    private QuestionnaireSubmissionStore questionnaireSubmissionStore;

    private RegisterUserUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new RegisterUserUseCase(
                registrationPolicy,
                codeService,
                userRepository,
                registrationAuthPort,
                captchaService,
                questionnaireService,
                registrationDiscordPort,
                whitelistService,
                usernameRuleService,
                questionnaireSubmissionStore,
                null
        );

        when(usernameRuleService.isValid("Steve", "java")).thenReturn(true);
        when(usernameRuleService.getUnifiedRegex()).thenReturn("^[a-zA-Z0-9_-]{3,16}$");
        when(registrationAuthPort.isValidPassword("Password_123")).thenReturn(true);
        when(questionnaireService.isEnabled()).thenReturn(false);
        when(registrationDiscordPort.isRequired()).thenReturn(false);
        when(registrationPolicy.usesCaptchaVerification()).thenReturn(false);
        when(registrationPolicy.usesEmailVerification()).thenReturn(false);
        when(registrationPolicy.isEmailAliasLimitEnabled()).thenReturn(false);
        when(registrationPolicy.isEmailDomainWhitelistEnabled()).thenReturn(false);
        when(registrationPolicy.getMaxAccountsPerEmail()).thenReturn(2L);
        when(registrationPolicy.isAutoApprove()).thenReturn(false);
        when(userRepository.countByEmail("steve@example.com")).thenReturn(0L);
    }

    @Test
    void shouldAllowCaseVariantRegistrationInCaseSensitiveMode() {
        when(registrationPolicy.isUsernameCaseSensitive()).thenReturn(true);
        when(userRepository.findByUsernameExact("Steve")).thenReturn(Optional.empty());
        when(userRepository.create(any())).thenReturn(true);

        RegisterUserResult result = useCase.execute(new RegisterUserCommand(request(), "req-1"));

        assertTrue(result.success());
        verify(userRepository, never()).findByUsernameIgnoreCase("Steve");
    }

    @Test
    void shouldRejectCaseVariantRegistrationInCaseInsensitiveMode() {
        when(registrationPolicy.isUsernameCaseSensitive()).thenReturn(false);
        when(userRepository.findByUsernameIgnoreCase("Steve")).thenReturn(Optional.of(existingUser("steve")));

        RegisterUserResult result = useCase.execute(new RegisterUserCommand(request(), "req-2"));

        assertEquals("username.case_conflict", result.messageKey());
        verify(userRepository, never()).create(any());
    }

    private RegistrationRequest request() {
        return new RegistrationRequest(
                "steve@example.com",
                "",
                "Steve",
                "Steve",
                "Password_123",
                "",
                "",
                "zh",
                "java",
                (JSONObject) null
        );
    }

    private UserRecord existingUser(String username) {
        return new UserRecord(
                username,
                username + "@example.com",
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
