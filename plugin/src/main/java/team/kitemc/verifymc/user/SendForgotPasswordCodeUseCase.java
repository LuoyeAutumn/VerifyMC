package team.kitemc.verifymc.user;

import team.kitemc.verifymc.registration.VerificationCodeNotifier;
import team.kitemc.verifymc.registration.VerifyCodePurpose;
import team.kitemc.verifymc.registration.VerifyCodeService;
import team.kitemc.verifymc.shared.EmailAddressUtil;

public class SendForgotPasswordCodeUseCase {
    private final UserRepository userRepository;
    private final VerifyCodeService verifyCodeService;
    private final VerificationCodeNotifier verificationCodeNotifier;

    public SendForgotPasswordCodeUseCase(
            UserRepository userRepository,
            VerifyCodeService verifyCodeService,
            VerificationCodeNotifier verificationCodeNotifier
    ) {
        this.userRepository = userRepository;
        this.verifyCodeService = verifyCodeService;
        this.verificationCodeNotifier = verificationCodeNotifier;
    }

    public PasswordCodeSendResult execute(String email, String language) {
        String normalizedEmail = EmailAddressUtil.normalize(email);
        if (!EmailAddressUtil.isValid(normalizedEmail)) {
            return PasswordCodeSendResult.failure("email.invalid_format");
        }

        long matchedAccounts = userRepository.countByEmail(normalizedEmail);
        if (matchedAccounts <= 0) {
            return PasswordCodeSendResult.failure("forgot_password.email_not_found");
        }

        VerifyCodeService.CodeIssueResult issueResult = verifyCodeService.issueCode(VerifyCodePurpose.FORGOT_PASSWORD, normalizedEmail);
        if (!issueResult.issued()) {
            return PasswordCodeSendResult.rateLimited("email.rate_limited", issueResult.remainingSeconds());
        }

        boolean sent = verificationCodeNotifier.sendVerificationCode(normalizedEmail, issueResult.code(), language);
        if (!sent) {
            verifyCodeService.revokeCode(VerifyCodePurpose.FORGOT_PASSWORD, normalizedEmail);
            return PasswordCodeSendResult.failure("email.failed");
        }
        return PasswordCodeSendResult.success("email.sent", issueResult.remainingSeconds(), matchedAccounts > 1);
    }
}
