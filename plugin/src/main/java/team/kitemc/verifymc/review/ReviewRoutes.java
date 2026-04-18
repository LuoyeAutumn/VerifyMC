package team.kitemc.verifymc.review;

import com.sun.net.httpserver.HttpHandler;
import java.util.function.BiConsumer;

public class ReviewRoutes {
    private final HttpHandler reviewStatusHandler;

    public ReviewRoutes(HttpHandler reviewStatusHandler) {
        this.reviewStatusHandler = reviewStatusHandler;
    }

    public void register(BiConsumer<String, HttpHandler> registerApiRoute) {
        registerApiRoute.accept("/api/review/status", reviewStatusHandler);
    }
}
