package team.kitemc.verifymc.review;

import team.kitemc.verifymc.audit.AuditService;
import team.kitemc.verifymc.user.UserRepository;
import team.kitemc.verifymc.user.UserStatus;

public class ApproveUserUseCase {
    private final UserRepository userRepository;
    private final AuditService auditService;
    private final ReviewNotificationPort reviewNotificationPort;
    private final ReviewApprovalPort reviewApprovalPort;
    private final ReviewEventPublisher reviewEventPublisher;
    private final ReviewLanguageProvider reviewLanguageProvider;

    public ApproveUserUseCase(
            UserRepository userRepository,
            AuditService auditService,
            ReviewNotificationPort reviewNotificationPort,
            ReviewApprovalPort reviewApprovalPort,
            ReviewEventPublisher reviewEventPublisher,
            ReviewLanguageProvider reviewLanguageProvider
    ) {
        this.userRepository = userRepository;
        this.auditService = auditService;
        this.reviewNotificationPort = reviewNotificationPort;
        this.reviewApprovalPort = reviewApprovalPort;
        this.reviewEventPublisher = reviewEventPublisher;
        this.reviewLanguageProvider = reviewLanguageProvider;
    }

    public ReviewUserResult execute(ReviewUserCommand command) {
        boolean updated = userRepository.updateStatus(command.username(), UserStatus.APPROVED, command.operator());
        if (!updated) {
            return new ReviewUserResult(false, "review.failed");
        }

        reviewApprovalPort.provisionApprovedUser(command.username());
        userRepository.findByUsernameExact(command.username()).ifPresent(user -> {
            if (user.email() != null && !user.email().isBlank()) {
                reviewNotificationPort.sendReviewResult(
                        user.email(),
                        command.username(),
                        true,
                        "",
                        reviewLanguageProvider.currentLanguage()
                );
            }
        });
        auditService.recordApproval(command.operator(), command.username());
        reviewEventPublisher.publishUserApproved(command.username());
        return new ReviewUserResult(true, "review.approve_success");
    }
}
