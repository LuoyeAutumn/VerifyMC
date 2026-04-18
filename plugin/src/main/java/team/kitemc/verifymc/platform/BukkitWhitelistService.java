package team.kitemc.verifymc.platform;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

public class BukkitWhitelistService implements WhitelistService {
    private final Plugin plugin;

    public BukkitWhitelistService(Plugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void add(String username) {
        FoliaCompat.runTaskGlobal(
                plugin,
                () -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "whitelist add " + username)
        );
    }

    @Override
    public void remove(String username) {
        FoliaCompat.runTaskGlobal(
                plugin,
                () -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "whitelist remove " + username)
        );
    }
}
