package team.kitemc.verifymc.platform;

import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsParameters;
import com.sun.net.httpserver.HttpsServer;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.Executors;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;

public class WebServer {
    private final PlatformServices platform;
    private final SSLContext sslContext;
    private final ApiRouter router;
    private HttpServer server;

    public WebServer(
            PlatformServices platform,
            ApiRouter router,
            SSLContext sslContext
    ) {
        this.platform = platform;
        this.sslContext = sslContext;
        this.router = router;
    }

    public void start() {
        int port = platform.getConfigManager().getWebPort();
        boolean sslEnabled = platform.getConfigManager().isSslEnabled();
        try {
            server = sslEnabled ? createHttpsServer(port) : HttpServer.create(new InetSocketAddress(port), 0);
            server.setExecutor(Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors() * 2));
            router.registerRoutes(server);
            server.start();
            String protocol = sslEnabled ? "HTTPS" : "HTTP";
            platform.getPlugin().getLogger().info("[VerifyMC] " + protocol + " web server started on port " + port);
        } catch (IOException e) {
            platform.getPlugin().getLogger().severe("[VerifyMC] Failed to start web server: " + e.getMessage());
        }
    }

    private HttpsServer createHttpsServer(int port) throws IOException {
        if (sslContext == null) {
            throw new IllegalStateException("SSL is enabled but no SSLContext was provided");
        }

        HttpsServer httpsServer = HttpsServer.create(new InetSocketAddress(port), 0);
        httpsServer.setHttpsConfigurator(new HttpsConfigurator(sslContext) {
            @Override
            public void configure(HttpsParameters params) {
                SSLParameters sslParameters = getSSLContext().getDefaultSSLParameters();
                params.setSSLParameters(sslParameters);
            }
        });
        return httpsServer;
    }

    public void stop() {
        if (server != null) {
            server.stop(0);
            platform.getPlugin().getLogger().info("[VerifyMC] Web server stopped.");
        }
    }

    public HttpServer getServer() {
        return server;
    }
}
