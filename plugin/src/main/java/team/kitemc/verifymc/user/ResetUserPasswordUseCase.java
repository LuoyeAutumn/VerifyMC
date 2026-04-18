package team.kitemc.verifymc.user;

import team.kitemc.verifymc.audit.AuditService;

public class ResetUserPasswordUseCase {
    private static final String AUDIT_DETAIL = "Admin changed user password";

    private final UserRepository userRepository;
    private final AuditService auditService;
    private final UserAccessSyncPort userAccessSyncPort;

    public ResetUserPasswordUseCase(
            UserRepository userRepository,
            AuditService auditService,
            UserAccessSyncPort userAccessSyncPort
    ) {
        this.userRepository = userRepository;
        this.auditService = auditService;
        this.userAccessSyncPort = userAccessSyncPort;
    }

    public AdminUserResult execute(ResetUserPasswordCommand command) {
        boolean updated = userRepository.updatePassword(command.username(), command.password());
        if (!updated) {
            return new AdminUserResult(false, "admin.password_change_failed");
        }

        userAccessSyncPort.syncPasswordChange(command.username(), command.password());
        auditService.recordPasswordChange(command.operator(), command.username(), AUDIT_DETAIL);
        return new AdminUserResult(true, "admin.password_change_success");
    }
}
