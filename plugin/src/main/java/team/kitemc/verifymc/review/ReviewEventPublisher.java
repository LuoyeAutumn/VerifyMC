package team.kitemc.verifymc.review;

public interface ReviewEventPublisher {
    void publishUserApproved(String username);

    void publishUserRejected(String username);
}
