package team.kitemc.verifymc.user;

import team.kitemc.verifymc.audit.AuditService;
import team.kitemc.verifymc.registration.VerifyCodePurpose;
import team.kitemc.verifymc.registration.VerifyCodeService;
import team.kitemc.verifymc.shared.EmailAddressUtil;

public class ForgotPasswordResetUseCase {
    private static final String AUDIT_DETAIL = "Forgot password via email verification";

    private final UserRepository userRepository;
    private final VerifyCodeService verifyCodeService;
    private final UserPasswordPolicy userPasswordPolicy;
    private final UserAccessSyncPort userAccessSyncPort;
    private final AuditService auditService;

    public ForgotPasswordResetUseCase(
            UserRepository userRepository,
            VerifyCodeService verifyCodeService,
            UserPasswordPolicy userPasswordPolicy,
            UserAccessSyncPort userAccessSyncPort,
            AuditService auditService
    ) {
        this.userRepository = userRepository;
        this.verifyCodeService = verifyCodeService;
        this.userPasswordPolicy = userPasswordPolicy;
        this.userAccessSyncPort = userAccessSyncPort;
        this.auditService = auditService;
    }

    public ForgotPasswordResetResult execute(String email, String code, String newPassword) {
        String normalizedEmail = EmailAddressUtil.normalize(email);
        if (!EmailAddressUtil.isValid(normalizedEmail)) {
            return new ForgotPasswordResetResult(false, "email.invalid_format");
        }
        if (newPassword == null || newPassword.isBlank()) {
            return new ForgotPasswordResetResult(false, "user.new_password_required");
        }

        String passwordRegex = userPasswordPolicy.getPasswordRegex();
        if (!newPassword.matches(passwordRegex)) {
            return new ForgotPasswordResetResult(false, "admin.invalid_password");
        }

        long matchedAccounts = userRepository.countByEmail(normalizedEmail);
        if (matchedAccounts <= 0) {
            return new ForgotPasswordResetResult(false, "forgot_password.email_not_found");
        }
        if (code == null || code.isBlank()) {
            return new ForgotPasswordResetResult(false, "verify.code_required");
        }
        if (!verifyCodeService.checkCode(VerifyCodePurpose.FORGOT_PASSWORD, normalizedEmail, code)) {
            return new ForgotPasswordResetResult(false, "verify.wrong_code");
        }

        java.util.List<UserRecord> users = userRepository.findAllByEmail(normalizedEmail);
        if (users.isEmpty()) {
            return new ForgotPasswordResetResult(false, "error.user_not_found");
        }

        int updatedCount = 0;
        for (UserRecord user : users) {
            if (user == null || user.username() == null || user.username().isBlank()) {
                continue;
            }
            boolean updated = userRepository.updatePassword(user.username(), newPassword);
            if (!updated) {
                return new ForgotPasswordResetResult(false, "admin.password_change_failed");
            }
            updatedCount++;
            userAccessSyncPort.syncPasswordChange(user.username(), newPassword);
            auditService.recordPasswordChange("system", user.username(), AUDIT_DETAIL);
        }
        if (updatedCount <= 0) {
            return new ForgotPasswordResetResult(false, "admin.password_change_failed");
        }
        return new ForgotPasswordResetResult(true, "forgot_password.reset_success");
    }
}
