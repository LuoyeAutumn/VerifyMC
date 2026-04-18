package team.kitemc.verifymc.user;

import team.kitemc.verifymc.audit.AuditService;

public class UnbanUserUseCase {
    private final UserRepository userRepository;
    private final AuditService auditService;
    private final UserAccessSyncPort userAccessSyncPort;

    public UnbanUserUseCase(
            UserRepository userRepository,
            AuditService auditService,
            UserAccessSyncPort userAccessSyncPort
    ) {
        this.userRepository = userRepository;
        this.auditService = auditService;
        this.userAccessSyncPort = userAccessSyncPort;
    }

    public AdminUserResult execute(AdminUserCommand command) {
        boolean updated = userRepository.restoreStatusFromBan(
                command.username(),
                UserStatus.APPROVED,
                command.operator()
        );
        if (!updated) {
            return new AdminUserResult(false, "admin.unban_failed");
        }

        userAccessSyncPort.grantApprovedAccess(command.username());
        auditService.recordUnban(command.operator(), command.username());
        return new AdminUserResult(true, "admin.unban_success");
    }
}
