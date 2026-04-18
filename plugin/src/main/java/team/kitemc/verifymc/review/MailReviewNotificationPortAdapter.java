package team.kitemc.verifymc.review;

import team.kitemc.verifymc.integration.MailService;

public class MailReviewNotificationPortAdapter implements ReviewNotificationPort {
    private final MailService mailService;

    public MailReviewNotificationPortAdapter(MailService mailService) {
        this.mailService = mailService;
    }

    @Override
    public void sendReviewResult(String email, String username, boolean approved, String reason, String language) {
        if (mailService != null && email != null && !email.isBlank()) {
            mailService.sendReviewResult(email, username, approved, reason, language);
        }
    }
}
