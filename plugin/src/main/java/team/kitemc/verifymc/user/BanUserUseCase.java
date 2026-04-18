package team.kitemc.verifymc.user;

import team.kitemc.verifymc.audit.AuditService;

public class BanUserUseCase {
    private final UserRepository userRepository;
    private final AuditService auditService;
    private final UserAccessSyncPort userAccessSyncPort;

    public BanUserUseCase(
            UserRepository userRepository,
            AuditService auditService,
            UserAccessSyncPort userAccessSyncPort
    ) {
        this.userRepository = userRepository;
        this.auditService = auditService;
        this.userAccessSyncPort = userAccessSyncPort;
    }

    public AdminUserResult execute(AdminUserCommand command) {
        UserRecord user = userRepository.findByUsernameExact(command.username()).orElse(null);
        if (user == null || user.status() == UserStatus.BANNED) {
            return new AdminUserResult(false, "admin.ban_failed");
        }

        boolean updated = userRepository.updateStatusForBan(command.username(), command.operator());
        if (!updated) {
            return new AdminUserResult(false, "admin.ban_failed");
        }

        userAccessSyncPort.removeUserAccess(command.username());
        auditService.recordBan(command.operator(), command.username(), command.reason());
        return new AdminUserResult(true, "admin.ban_success");
    }
}
