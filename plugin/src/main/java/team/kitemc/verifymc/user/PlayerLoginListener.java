package team.kitemc.verifymc.user;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerLoginEvent;
import team.kitemc.verifymc.platform.PlatformServices;

public class PlayerLoginListener implements Listener {
    private final PlatformServices platform;
    private final UserRepository userRepository;

    public PlayerLoginListener(PlatformServices platform, UserRepository userRepository) {
        this.platform = platform;
        this.userRepository = userRepository;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerLogin(PlayerLoginEvent event) {
        Player player = event.getPlayer();
        String username = player.getName();

        platform.debugLog("PlayerLogin: username=" + username);

        String whitelistMode = platform.getConfigManager().getWhitelistMode();
        boolean pluginMode = "plugin".equalsIgnoreCase(whitelistMode);
        UserRecord user = userRepository.findByUsername(username).orElse(null);

        if (user == null) {
            if (pluginMode) {
                String registerUrl = platform.getPlugin().getConfig().getString("web_register_url", "");
                String message = platform.getMessage("login.not_registered", platform.getConfigManager().getLanguage());
                if (registerUrl != null && !registerUrl.isEmpty()) {
                    message = message.replace("{url}", registerUrl);
                }
                event.disallow(PlayerLoginEvent.Result.KICK_WHITELIST, message);
                platform.debugLog("User " + username + " not registered in plugin mode, kicking.");
            }
            return;
        }

        switch (user.status()) {
            case APPROVED -> platform.debugLog("User " + username + " is approved, allowing login.");
            case PENDING -> {
                event.disallow(
                        PlayerLoginEvent.Result.KICK_OTHER,
                        platform.getMessage("login.pending", platform.getConfigManager().getLanguage())
                );
                platform.debugLog("User " + username + " is pending, kicking.");
            }
            case REJECTED -> {
                event.disallow(
                        PlayerLoginEvent.Result.KICK_OTHER,
                        platform.getMessage("login.rejected", platform.getConfigManager().getLanguage())
                );
                platform.debugLog("User " + username + " is rejected, kicking.");
            }
            case BANNED -> {
                event.disallow(
                        PlayerLoginEvent.Result.KICK_BANNED,
                        platform.getMessage("login.banned", platform.getConfigManager().getLanguage())
                );
                platform.debugLog("User " + username + " is banned, kicking.");
            }
            default -> platform.debugLog("User " + username + " has unknown status: " + user.status().value());
        }
    }
}
