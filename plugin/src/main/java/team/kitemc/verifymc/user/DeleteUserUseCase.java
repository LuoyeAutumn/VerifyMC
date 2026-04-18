package team.kitemc.verifymc.user;

import team.kitemc.verifymc.audit.AuditService;

public class DeleteUserUseCase {
    private final UserRepository userRepository;
    private final AuditService auditService;
    private final UserAccessSyncPort userAccessSyncPort;

    public DeleteUserUseCase(
            UserRepository userRepository,
            AuditService auditService,
            UserAccessSyncPort userAccessSyncPort
    ) {
        this.userRepository = userRepository;
        this.auditService = auditService;
        this.userAccessSyncPort = userAccessSyncPort;
    }

    public AdminUserResult execute(AdminUserCommand command) {
        boolean deleted = userRepository.delete(command.username());
        if (!deleted) {
            return new AdminUserResult(false, "admin.delete_failed");
        }

        userAccessSyncPort.removeUserAccess(command.username());
        auditService.recordDeletion(command.operator(), command.username());
        return new AdminUserResult(true, "admin.delete_success");
    }
}
