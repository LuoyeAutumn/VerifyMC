package team.kitemc.verifymc.user;

import com.sun.net.httpserver.HttpHandler;
import java.util.function.BiConsumer;

public class UserRoutes {
    private final HttpHandler loginHandler;
    private final HttpHandler adminLoginHandler;
    private final HttpHandler userStatusHandler;
    private final HttpHandler userUpdateHandler;
    private final HttpHandler userPasswordHandler;
    private final HttpHandler userPasswordCodeHandler;
    private final HttpHandler forgotPasswordCodeHandler;
    private final HttpHandler forgotPasswordResetHandler;

    public UserRoutes(
            HttpHandler loginHandler,
            HttpHandler adminLoginHandler,
            HttpHandler userStatusHandler,
            HttpHandler userUpdateHandler,
            HttpHandler userPasswordHandler,
            HttpHandler userPasswordCodeHandler,
            HttpHandler forgotPasswordCodeHandler,
            HttpHandler forgotPasswordResetHandler
    ) {
        this.loginHandler = loginHandler;
        this.adminLoginHandler = adminLoginHandler;
        this.userStatusHandler = userStatusHandler;
        this.userUpdateHandler = userUpdateHandler;
        this.userPasswordHandler = userPasswordHandler;
        this.userPasswordCodeHandler = userPasswordCodeHandler;
        this.forgotPasswordCodeHandler = forgotPasswordCodeHandler;
        this.forgotPasswordResetHandler = forgotPasswordResetHandler;
    }

    public void register(BiConsumer<String, HttpHandler> registerApiRoute) {
        registerApiRoute.accept("/api/login", loginHandler);
        registerApiRoute.accept("/api/admin/login", adminLoginHandler);
        registerApiRoute.accept("/api/user/status", userStatusHandler);
        registerApiRoute.accept("/api/user/update", userUpdateHandler);
        registerApiRoute.accept("/api/user/password", userPasswordHandler);
        registerApiRoute.accept("/api/user/password/code", userPasswordCodeHandler);
        registerApiRoute.accept("/api/password/forgot/code", forgotPasswordCodeHandler);
        registerApiRoute.accept("/api/password/forgot/reset", forgotPasswordResetHandler);
    }
}
