package team.kitemc.verifymc.admin;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import org.json.JSONArray;
import org.json.JSONObject;
import team.kitemc.verifymc.platform.WebResponseHelper;
import team.kitemc.verifymc.user.ListUsersUseCase;
import team.kitemc.verifymc.user.UserPage;
import team.kitemc.verifymc.user.UserQuery;
import team.kitemc.verifymc.user.UserStatus;
import team.kitemc.verifymc.user.UserSummary;

/**
 * Handles admin user listing with pagination and search.
 * Extracted from WebServer.start() — the "/api/admin/users" context.
 */
public class AdminUserListHandler implements HttpHandler {
    private final AuthenticatedRequestContext authContext;
    private final ListUsersUseCase listUsersUseCase;

    public AdminUserListHandler(
            AuthenticatedRequestContext authContext,
            ListUsersUseCase listUsersUseCase
    ) {
        this.authContext = authContext;
        this.listUsersUseCase = listUsersUseCase;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        if (!WebResponseHelper.requireMethod(exchange, "GET")) return;

        // Require admin privileges
        if (AdminAuthUtil.requireAdmin(exchange, authContext, AdminAction.LIST) == null) return;

        // Parse query params
        String query = exchange.getRequestURI().getQuery();
        int page = 1, size = 20;
        String search = null, status = null;
        if (query != null) {
            for (String param : query.split("&")) {
                String[] kv = param.split("=", 2);
                if (kv.length != 2) continue;
                switch (kv[0]) {
                    case "page" -> { try { page = Integer.parseInt(kv[1]); } catch (NumberFormatException ignored) {} }
                    case "size" -> { try { size = Integer.parseInt(kv[1]); } catch (NumberFormatException ignored) {} }
                    case "search" -> search = URLDecoder.decode(kv[1], StandardCharsets.UTF_8);
                    case "status" -> status = URLDecoder.decode(kv[1], StandardCharsets.UTF_8);
                }
            }
        }

        UserStatus userStatus = UserStatus.fromValue(status).orElse(null);
        UserPage<UserSummary> usersPage = listUsersUseCase.execute(new UserQuery(page, size, search, userStatus));

        JSONArray usersArray = new JSONArray();
        for (UserSummary user : usersPage.items()) {
            JSONObject userJson = new JSONObject();
            userJson.put("username", user.username());
            userJson.put("email", user.email());
            userJson.put("status", user.status().value());
            userJson.put("regTime", user.regTime());
            if (user.discordId() != null) {
                userJson.put("discordId", user.discordId());
            }
            if (user.questionnaireScore() != null) {
                userJson.put("questionnaireScore", user.questionnaireScore());
            }
            if (user.questionnairePassed() != null) {
                userJson.put("questionnairePassed", user.questionnairePassed());
            }
            if (user.questionnaireReviewSummary() != null) {
                userJson.put("questionnaireReviewSummary", user.questionnaireReviewSummary());
            }
            if (user.questionnaireScoredAt() != null) {
                userJson.put("questionnaireScoredAt", user.questionnaireScoredAt());
            }
            usersArray.put(userJson);
        }

        JSONObject pagination = new JSONObject();
        pagination.put("currentPage", usersPage.currentPage());
        pagination.put("pageSize", usersPage.pageSize());
        pagination.put("totalCount", usersPage.totalCount());
        pagination.put("totalPages", usersPage.totalPages());
        pagination.put("hasNext", usersPage.hasNext());
        pagination.put("hasPrev", usersPage.hasPrev());

        JSONObject resp = new JSONObject();
        resp.put("success", true);
        resp.put("users", usersArray);
        resp.put("pagination", pagination);
        WebResponseHelper.sendJson(exchange, resp);
    }
}

