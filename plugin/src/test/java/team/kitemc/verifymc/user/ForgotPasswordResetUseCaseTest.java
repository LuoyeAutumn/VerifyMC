package team.kitemc.verifymc.user;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import team.kitemc.verifymc.audit.AuditService;
import team.kitemc.verifymc.audit.AuditDao;
import team.kitemc.verifymc.registration.VerifyCodePurpose;
import team.kitemc.verifymc.registration.VerifyCodeService;

@ExtendWith(MockitoExtension.class)
class ForgotPasswordResetUseCaseTest {
    @Mock
    private UserRepository userRepository;
    @Mock
    private UserAccessSyncPort userAccessSyncPort;

    private VerifyCodeService verifyCodeService;
    private ForgotPasswordResetUseCase useCase;
    private AuditService auditService;

    @BeforeEach
    void setUp() {
        verifyCodeService = new VerifyCodeService();
        auditService = new AuditService(new InMemoryAuditDao());
        useCase = new ForgotPasswordResetUseCase(
                userRepository,
                verifyCodeService,
                () -> "^[a-zA-Z0-9_]{8,26}$",
                userAccessSyncPort,
                auditService
        );
    }

    @AfterEach
    void tearDown() {
        verifyCodeService.stop();
        auditService.close();
    }

    @Test
    void shouldResetPasswordWhenEmailIsUniqueAndCodeIsValid() {
        when(userRepository.countByEmail("steve@example.com")).thenReturn(1L);
        when(userRepository.findAllByEmail("steve@example.com")).thenReturn(java.util.List.of(user("Steve", "steve@example.com")));
        when(userRepository.updatePassword("Steve", "Password_123")).thenReturn(true);

        String code = verifyCodeService.issueCode(VerifyCodePurpose.FORGOT_PASSWORD, "steve@example.com").code();
        ForgotPasswordResetResult result = useCase.execute("steve@example.com", code, "Password_123");

        assertTrue(result.success());
        assertEquals("forgot_password.reset_success", result.messageKey());
        verify(userRepository).updatePassword("Steve", "Password_123");
        verify(userAccessSyncPort).syncPasswordChange("Steve", "Password_123");
    }

    @Test
    void shouldRejectWhenEmailHasMultipleAccounts() {
        when(userRepository.countByEmail("shared@example.com")).thenReturn(2L);
        when(userRepository.findAllByEmail("shared@example.com")).thenReturn(java.util.List.of(
                user("Steve", "shared@example.com"),
                user("Alex", "shared@example.com")
        ));
        when(userRepository.updatePassword("Steve", "Password_123")).thenReturn(true);
        when(userRepository.updatePassword("Alex", "Password_123")).thenReturn(true);

        String code = verifyCodeService.issueCode(VerifyCodePurpose.FORGOT_PASSWORD, "shared@example.com").code();
        ForgotPasswordResetResult result = useCase.execute("shared@example.com", code, "Password_123");

        assertTrue(result.success());
        assertEquals("forgot_password.reset_success", result.messageKey());
        verify(userRepository).updatePassword("Steve", "Password_123");
        verify(userRepository).updatePassword("Alex", "Password_123");
    }

    private UserRecord user(String username, String email) {
        return new UserRecord(
                username,
                email,
                UserStatus.APPROVED,
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

    private static final class InMemoryAuditDao implements AuditDao {
        @Override
        public void addAudit(team.kitemc.verifymc.audit.AuditRecord record) {
        }

        @Override
        public team.kitemc.verifymc.audit.AuditPage query(team.kitemc.verifymc.audit.AuditQuery query) {
            return new team.kitemc.verifymc.audit.AuditPage(java.util.List.of(), query.page(), query.size(), 0);
        }

        @Override
        public void close() {
        }
    }
}
