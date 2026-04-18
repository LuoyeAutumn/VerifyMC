package team.kitemc.verifymc.user;

import team.kitemc.verifymc.registration.VerificationCodeNotifier;
import team.kitemc.verifymc.registration.VerifyCodePurpose;
import team.kitemc.verifymc.registration.VerifyCodeService;

public class SendUserPasswordCodeUseCase {
    private final UserRepository userRepository;
    private final VerifyCodeService verifyCodeService;
    private final VerificationCodeNotifier verificationCodeNotifier;

    public SendUserPasswordCodeUseCase(
            UserRepository userRepository,
            VerifyCodeService verifyCodeService,
            VerificationCodeNotifier verificationCodeNotifier
    ) {
        this.userRepository = userRepository;
        this.verifyCodeService = verifyCodeService;
        this.verificationCodeNotifier = verificationCodeNotifier;
    }

    public PasswordCodeSendResult execute(String username, String language) {
        UserRecord user = userRepository.findByUsernameConfigured(username).orElse(null);
        if (user == null) {
            return PasswordCodeSendResult.failure("error.user_not_found");
        }
        String email = user.email();
        if (email == null || email.isBlank()) {
            return PasswordCodeSendResult.failure("user.email_not_bound");
        }

        VerifyCodeService.CodeIssueResult issueResult = verifyCodeService.issueCode(VerifyCodePurpose.CHANGE_PASSWORD, email);
        if (!issueResult.issued()) {
            return PasswordCodeSendResult.rateLimited("email.rate_limited", issueResult.remainingSeconds());
        }

        boolean sent = verificationCodeNotifier.sendVerificationCode(email, issueResult.code(), language);
        if (!sent) {
            verifyCodeService.revokeCode(VerifyCodePurpose.CHANGE_PASSWORD, email);
            return PasswordCodeSendResult.failure("email.failed");
        }
        return PasswordCodeSendResult.success("email.sent", issueResult.remainingSeconds());
    }
}
