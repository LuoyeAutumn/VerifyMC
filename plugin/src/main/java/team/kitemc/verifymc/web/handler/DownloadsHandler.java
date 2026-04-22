package team.kitemc.verifymc.web.handler;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import org.json.JSONArray;
import org.json.JSONObject;
import team.kitemc.verifymc.core.PluginContext;
import team.kitemc.verifymc.web.WebResponseHelper;

import java.io.IOException;
import java.util.List;
import java.util.Map;

public class DownloadsHandler implements HttpHandler {
    private final PluginContext ctx;

    public DownloadsHandler(PluginContext ctx) {
        this.ctx = ctx;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        if (!WebResponseHelper.requireMethod(exchange, "GET")) return;

        JSONObject resp = new JSONObject();
        
        try {
            List<Map<String, Object>> resources = ctx.getConfigManager().getDownloadResources();
            
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
            ctx.debugLog("Error getting download resources: " + e.getMessage());
            resp.put("success", false);
            resp.put("message", "Failed to get download resources");
        }
        
        WebResponseHelper.sendJson(exchange, resp);
    }
}
