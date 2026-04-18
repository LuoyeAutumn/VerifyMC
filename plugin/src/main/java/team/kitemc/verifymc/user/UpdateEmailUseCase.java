package team.kitemc.verifymc.user;

import team.kitemc.verifymc.audit.AuditService;
import team.kitemc.verifymc.shared.EmailAddressUtil;

public class UpdateEmailUseCase {
    private final UserEmailPolicy userEmailPolicy;
    private final UserRepository userRepository;
    private final AuditService auditService;

    public UpdateEmailUseCase(
            UserEmailPolicy userEmailPolicy,
            UserRepository userRepository,
            AuditService auditService
    ) {
        this.userEmailPolicy = userEmailPolicy;
        this.userRepository = userRepository;
        this.auditService = auditService;
    }

    public UpdateEmailResult execute(UpdateEmailCommand command) {
        String newEmail = command.email();
        if (newEmail == null || newEmail.isBlank() || !EmailAddressUtil.isValid(newEmail)) {
            return new UpdateEmailResult(false, "register.invalid_email");
        }

        UserRecord user = userRepository.findByUsernameConfigured(command.username()).orElse(null);
        if (user == null) {
            return new UpdateEmailResult(false, "error.user_not_found");
        }
        if (newEmail.equalsIgnoreCase(user.email())) {
            return new UpdateEmailResult(true, "user.update_success");
        }
        if (userEmailPolicy.requiresVerificationForEmailChange()) {
            return new UpdateEmailResult(false, "user.email_update_requires_verification");
        }
        if (userEmailPolicy.isEmailAliasLimitEnabled() && EmailAddressUtil.hasAlias(newEmail)) {
            return new UpdateEmailResult(false, "register.alias_not_allowed");
        }
        if (userEmailPolicy.isEmailDomainWhitelistEnabled()) {
            String domain = EmailAddressUtil.extractDomain(newEmail);
            if (!userEmailPolicy.getEmailDomainWhitelist().contains(domain)) {
                return new UpdateEmailResult(false, "register.domain_not_allowed");
            }
        }
        if (userRepository.countByEmail(newEmail) >= userEmailPolicy.getMaxAccountsPerEmail()) {
            return new UpdateEmailResult(false, "register.email_limit");
        }
        boolean updated = userRepository.updateEmail(command.username(), newEmail);
        if (!updated) {
            return new UpdateEmailResult(false, "user.update_failed");
        }
        auditService.recordEmailUpdate(command.username(), newEmail);
        return new UpdateEmailResult(true, "user.update_success");
    }
}
