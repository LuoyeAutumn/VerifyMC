package team.kitemc.verifymc.review;

import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import javax.net.ssl.SSLContext;
import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.DefaultSSLWebSocketServerFactory;
import org.java_websocket.server.WebSocketServer;
import team.kitemc.verifymc.platform.WebAuthHelper;

public class ReviewWebSocketServer extends WebSocketServer {
    private static final int MAX_CLIENTS = 50;
    private static final int CLOSE_CODE_POLICY_VIOLATION = 1008;

    private final Set<WebSocket> clients = Collections.synchronizedSet(new HashSet<>());
    private final boolean debug;
    private final org.bukkit.plugin.Plugin plugin;
    private final WebAuthHelper authHelper;

    public ReviewWebSocketServer(int port, org.bukkit.plugin.Plugin plugin, boolean debug, WebAuthHelper authHelper) {
        super(new InetSocketAddress(port));
        this.plugin = plugin;
        this.debug = debug;
        this.authHelper = authHelper;
    }

    @Deprecated
    public ReviewWebSocketServer(int port, org.bukkit.plugin.Plugin plugin) {
        this(port, plugin, plugin != null && plugin.getConfig().getBoolean("debug", false), null);
    }

    @Deprecated
    public ReviewWebSocketServer(int port) {
        this(port, null, false, null);
    }

    private void debugLog(String msg) {
        if (debug && plugin != null) {
            plugin.getLogger().info("[DEBUG] ReviewWebSocketServer: " + msg);
        }
    }

    @Override
    public void onOpen(WebSocket conn, ClientHandshake handshake) {
        debugLog("WebSocket connection opened: " + conn.getRemoteSocketAddress());

        if (clients.size() >= MAX_CLIENTS) {
            debugLog("Connection rejected: max clients limit reached (" + MAX_CLIENTS + ")");
            conn.close(CLOSE_CODE_POLICY_VIOLATION, "Max clients limit reached");
            return;
        }

        if (authHelper != null) {
            String token = extractToken(handshake);
            if (token == null) {
                debugLog("Connection rejected: no token provided");
                conn.close(CLOSE_CODE_POLICY_VIOLATION, "Authentication required");
                return;
            }

            if (!authHelper.validateToken(token)) {
                debugLog("Connection rejected: invalid or expired token");
                conn.close(CLOSE_CODE_POLICY_VIOLATION, "Invalid or expired token");
                return;
            }

            debugLog("Connection authenticated successfully");
        }

        clients.add(conn);
        debugLog("Total clients connected: " + clients.size());
    }

    private String extractToken(ClientHandshake handshake) {
        String authHeader = handshake.getFieldValue("Authorization");
        if (authHeader != null && !authHeader.isEmpty()) {
            if (authHeader.startsWith("Bearer ")) {
                return authHeader.substring(7);
            }
            return authHeader;
        }

        String resourceDescriptor = handshake.getResourceDescriptor();
        if (resourceDescriptor != null && resourceDescriptor.contains("?")) {
            try {
                String query = resourceDescriptor.substring(resourceDescriptor.indexOf('?') + 1);
                String[] params = query.split("&");
                for (String param : params) {
                    String[] pair = param.split("=", 2);
                    if (pair.length == 2 && "token".equals(pair[0])) {
                        return URLDecoder.decode(pair[1], StandardCharsets.UTF_8.name());
                    }
                }
            } catch (Exception e) {
                debugLog("Error parsing query parameters: " + e.getMessage());
            }
        }

        return null;
    }

    @Override
    public void onClose(WebSocket conn, int code, String reason, boolean remote) {
        debugLog("WebSocket connection closed: " + conn.getRemoteSocketAddress() + ", code: " + code + ", reason: " + reason);
        clients.remove(conn);
        debugLog("Remaining clients: " + clients.size());
    }

    @Override
    public void onMessage(WebSocket conn, String message) {
        debugLog("Received message from " + conn.getRemoteSocketAddress() + ": " + message);
    }

    @Override
    public void onError(WebSocket conn, Exception ex) {
        debugLog("WebSocket error: " + ex.getMessage());
        if (conn != null) {
            debugLog("Error on connection: " + conn.getRemoteSocketAddress());
        }
    }

    @Override
    public void onStart() {
        debugLog("WebSocket server started on port: " + getPort());
    }

    public void enableSsl(SSLContext sslContext) {
        setWebSocketFactory(new DefaultSSLWebSocketServerFactory(sslContext));
        debugLog("WebSocket SSL enabled");
    }

    public void broadcastMessage(String message) {
        debugLog("Broadcasting message to " + clients.size() + " clients: " + message);
        synchronized (clients) {
            int sentCount = 0;
            for (WebSocket ws : clients) {
                if (ws.isOpen()) {
                    ws.send(message);
                    sentCount++;
                }
            }
            debugLog("Message sent to " + sentCount + " clients");
        }
    }
}
