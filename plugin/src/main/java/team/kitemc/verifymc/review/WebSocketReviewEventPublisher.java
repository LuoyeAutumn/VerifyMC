package team.kitemc.verifymc.review;

import org.json.JSONObject;

public class WebSocketReviewEventPublisher implements ReviewEventPublisher {
    private final ReviewWebSocketServer wsServer;

    public WebSocketReviewEventPublisher(ReviewWebSocketServer wsServer) {
        this.wsServer = wsServer;
    }

    @Override
    public void publishUserApproved(String username) {
        broadcast("user_approved", username);
    }

    @Override
    public void publishUserRejected(String username) {
        broadcast("user_rejected", username);
    }

    private void broadcast(String type, String username) {
        if (wsServer == null) {
            return;
        }
        wsServer.broadcastMessage(new JSONObject()
                .put("type", type)
                .put("username", username)
                .toString());
    }
}
