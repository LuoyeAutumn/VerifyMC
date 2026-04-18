package team.kitemc.verifymc.review;

public interface ReviewNotificationPort {
    void sendReviewResult(String email, String username, boolean approved, String reason, String language);
}
