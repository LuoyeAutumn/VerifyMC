package team.kitemc.verifymc.platform;

import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import team.kitemc.verifymc.admin.AdminRoutes;
import team.kitemc.verifymc.integration.IntegrationRoutes;
import team.kitemc.verifymc.questionnaire.QuestionnaireRoutes;
import team.kitemc.verifymc.registration.RegistrationRoutes;
import team.kitemc.verifymc.review.ReviewRoutes;
import team.kitemc.verifymc.system.SystemRoutes;
import team.kitemc.verifymc.user.UserRoutes;

public class ApiRouter {
    private final PlatformServices platform;
    private final RegistrationRoutes registrationRoutes;
    private final QuestionnaireRoutes questionnaireRoutes;
    private final ReviewRoutes reviewRoutes;
    private final UserRoutes userRoutes;
    private final AdminRoutes adminRoutes;
    private final IntegrationRoutes integrationRoutes;
    private final SystemRoutes systemRoutes;

    public ApiRouter(
            PlatformServices platform,
            RegistrationRoutes registrationRoutes,
            QuestionnaireRoutes questionnaireRoutes,
            ReviewRoutes reviewRoutes,
            UserRoutes userRoutes,
            AdminRoutes adminRoutes,
            IntegrationRoutes integrationRoutes,
            SystemRoutes systemRoutes
    ) {
        this.platform = platform;
        this.registrationRoutes = registrationRoutes;
        this.questionnaireRoutes = questionnaireRoutes;
        this.reviewRoutes = reviewRoutes;
        this.userRoutes = userRoutes;
        this.adminRoutes = adminRoutes;
        this.integrationRoutes = integrationRoutes;
        this.systemRoutes = systemRoutes;
    }

    public void registerRoutes(HttpServer server) {
        registrationRoutes.register((path, handler) -> registerApiRoute(server, path, handler));
        questionnaireRoutes.register((path, handler) -> registerApiRoute(server, path, handler));
        reviewRoutes.register((path, handler) -> registerApiRoute(server, path, handler));
        userRoutes.register((path, handler) -> registerApiRoute(server, path, handler));
        adminRoutes.register((path, handler) -> registerApiRoute(server, path, handler));
        integrationRoutes.register((path, handler) -> registerApiRoute(server, path, handler));
        systemRoutes.register((path, handler) -> registerApiRoute(server, path, handler));

        if (platform.getConfigManager().isServeStaticEnabled() && systemRoutes.staticFileHandler() != null) {
            server.createContext("/", new CorsHandler(platform, systemRoutes.staticFileHandler()));
        }
    }

    private void registerApiRoute(HttpServer server, String path, HttpHandler handler) {
        server.createContext(path, new CorsHandler(platform, handler));
    }
}
