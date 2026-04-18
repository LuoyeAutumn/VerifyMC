package team.kitemc.verifymc.system;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.util.Collection;
import java.util.function.Consumer;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.json.JSONArray;
import org.json.JSONObject;
import team.kitemc.verifymc.admin.AdminAuthUtil;
import team.kitemc.verifymc.admin.AuthenticatedRequestContext;
import team.kitemc.verifymc.platform.WebResponseHelper;

public class ServerStatusHandler implements HttpHandler {
    private final Plugin plugin;
    private final AuthenticatedRequestContext authContext;
    private final Consumer<String> debugLogger;

    public ServerStatusHandler(
            Plugin plugin,
            AuthenticatedRequestContext authContext,
            Consumer<String> debugLogger
    ) {
        this.plugin = plugin;
        this.authContext = authContext;
        this.debugLogger = debugLogger;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        if (!WebResponseHelper.requireMethod(exchange, "GET")) return;

        JSONObject resp = new JSONObject();
        
        try {
            Server server = plugin.getServer();
            
            resp.put("success", true);
            
            JSONObject data = new JSONObject();
            data.put("online", true);
            
            JSONObject players = new JSONObject();
            players.put("online", server.getOnlinePlayers().size());
            players.put("max", server.getMaxPlayers());
            
            String authenticatedUser = AdminAuthUtil.getAuthenticatedUserQuietly(exchange, authContext);
            if (authenticatedUser != null) {
                Collection<? extends Player> onlinePlayers = server.getOnlinePlayers();
                if (!onlinePlayers.isEmpty()) {
                    JSONArray playerList = new JSONArray();
                    for (Player player : onlinePlayers) {
                        JSONObject playerInfo = new JSONObject();
                        playerInfo.put("name", player.getName());
                        playerInfo.put("uuid", player.getUniqueId().toString());
                        playerList.put(playerInfo);
                    }
                    players.put("list", playerList);
                }
            }
            data.put("players", players);
            
            data.put("version", server.getBukkitVersion());
            
            double tps = getTPS(server);
            data.put("tps", tps);
            
            JSONObject memory = new JSONObject();
            MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
            long usedBytes = memoryBean.getHeapMemoryUsage().getUsed();
            long maxBytes = memoryBean.getHeapMemoryUsage().getMax();
            memory.put("used", bytesToMB(usedBytes));
            memory.put("max", bytesToMB(maxBytes));
            data.put("memory", memory);
            
            String motd = server.getMotd();
            if (motd != null) {
                motd = motd.replace("\n", " ").trim();
            }
            data.put("motd", motd != null ? motd : "");
            
            resp.put("data", data);
        } catch (Exception e) {
            if (debugLogger != null) {
                debugLogger.accept("Error getting server status: " + e.getMessage());
            }
            resp.put("success", false);
            resp.put("message", "Failed to get server status");
        }
        
        WebResponseHelper.sendJson(exchange, resp);
    }
    
    private double getTPS(Server server) {
        try {
            java.lang.reflect.Method getTPSMethod = server.getClass().getMethod("getTPS");
            if (getTPSMethod != null) {
                double[] tpsArray = (double[]) getTPSMethod.invoke(server);
                if (tpsArray != null && tpsArray.length > 0) {
                    return Math.round(tpsArray[0] * 100.0) / 100.0;
                }
            }
        } catch (Exception e) {
            if (debugLogger != null) {
                debugLogger.accept("Could not retrieve TPS: " + e.getMessage());
            }
        }
        return -1.0;
    }
    
    private long bytesToMB(long bytes) {
        return bytes / (1024 * 1024);
    }
}

