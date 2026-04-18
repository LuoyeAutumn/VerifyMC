package team.kitemc.verifymc.system;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import java.io.IOException;
import org.bukkit.plugin.Plugin;
import org.json.JSONObject;
import team.kitemc.verifymc.system.VersionCheckService;
import team.kitemc.verifymc.platform.WebResponseHelper;

/**
 * Returns the current plugin version and checks for updates.
 */
public class VersionHandler implements HttpHandler {
    private final Plugin plugin;
    private final VersionCheckService versionCheckService;

    public VersionHandler(
            Plugin plugin,
            VersionCheckService versionCheckService
    ) {
        this.plugin = plugin;
        this.versionCheckService = versionCheckService;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        if (!WebResponseHelper.requireMethod(exchange, "GET")) return;

        JSONObject resp = new JSONObject();
        resp.put("success", true);
        resp.put("currentVersion", plugin.getDescription().getVersion());

        if (versionCheckService != null) {
            JSONObject info = versionCheckService.getVersionInfo();
            if (info != null) {
                resp.put("latestVersion", info.optString("latestVersion", ""));
                resp.put("updateAvailable", info.optBoolean("updateAvailable", false));
                resp.put("releasesUrl", info.optString("releasesUrl", ""));
            }
        }

        WebResponseHelper.sendJson(exchange, resp);
    }
}

