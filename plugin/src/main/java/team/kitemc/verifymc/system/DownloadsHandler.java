package team.kitemc.verifymc.system;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.json.JSONArray;
import org.json.JSONObject;
import team.kitemc.verifymc.platform.ConfigManager;
import team.kitemc.verifymc.platform.WebResponseHelper;

public class DownloadsHandler implements HttpHandler {
    private final ConfigManager configManager;
    private final Consumer<String> debugLogger;

    public DownloadsHandler(
            ConfigManager configManager,
            Consumer<String> debugLogger
    ) {
        this.configManager = configManager;
        this.debugLogger = debugLogger;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        if (!WebResponseHelper.requireMethod(exchange, "GET")) return;

        JSONObject resp = new JSONObject();
        
        try {
            List<Map<String, Object>> resources = configManager.getDownloadResources();
            
            resp.put("success", true);
            JSONArray resourcesArray = new JSONArray();
            
            for (Map<String, Object> resource : resources) {
                JSONObject resourceJson = new JSONObject();
                resourceJson.put("id", resource.get("id"));
                resourceJson.put("name", resource.get("name"));
                resourceJson.put("description", resource.get("description"));
                resourceJson.put("version", resource.get("version"));
                resourceJson.put("size", resource.get("size"));
                resourceJson.put("url", resource.get("url"));
                resourceJson.put("icon", resource.get("icon"));
                resourcesArray.put(resourceJson);
            }
            
            resp.put("resources", resourcesArray);
        } catch (Exception e) {
            if (debugLogger != null) {
                debugLogger.accept("Error getting download resources: " + e.getMessage());
            }
            resp.put("success", false);
            resp.put("message", "Failed to get download resources");
        }
        
        WebResponseHelper.sendJson(exchange, resp);
    }
}

