package team.kitemc.verifymc.review;

import team.kitemc.verifymc.audit.AuditService;
import team.kitemc.verifymc.user.UserRepository;
import team.kitemc.verifymc.user.UserStatus;

public class RejectUserUseCase {
    private final UserRepository userRepository;
    private final AuditService auditService;
    private final ReviewNotificationPort reviewNotificationPort;
    private final ReviewEventPublisher reviewEventPublisher;
    private final ReviewLanguageProvider reviewLanguageProvider;

    public RejectUserUseCase(
            UserRepository userRepository,
            AuditService auditService,
            ReviewNotificationPort reviewNotificationPort,
            ReviewEventPublisher reviewEventPublisher,
            ReviewLanguageProvider reviewLanguageProvider
    ) {
        this.userRepository = userRepository;
        this.auditService = auditService;
        this.reviewNotificationPort = reviewNotificationPort;
        this.reviewEventPublisher = reviewEventPublisher;
        this.reviewLanguageProvider = reviewLanguageProvider;
    }

    public ReviewUserResult execute(ReviewUserCommand command) {
        boolean updated = userRepository.updateStatus(command.username(), UserStatus.REJECTED, command.operator());
        if (!updated) {
            return new ReviewUserResult(false, "review.failed");
        }

        userRepository.findByUsernameExact(command.username()).ifPresent(user -> {
            if (user.email() != null && !user.email().isBlank()) {
                reviewNotificationPort.sendReviewResult(
                        user.email(),
                        command.username(),
                        false,
                        command.reason(),
                        reviewLanguageProvider.currentLanguage()
                );
            }
        });
        auditService.recordRejection(command.operator(), command.username(), command.reason());
        reviewEventPublisher.publishUserRejected(command.username());
        return new ReviewUserResult(true, "review.reject_success");
    }
}
