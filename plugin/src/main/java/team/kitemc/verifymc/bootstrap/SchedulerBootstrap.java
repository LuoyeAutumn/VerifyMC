package team.kitemc.verifymc.bootstrap;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;
import org.bukkit.plugin.Plugin;
import team.kitemc.verifymc.integration.AuthmeService;
import team.kitemc.verifymc.platform.ConfigManager;
import team.kitemc.verifymc.platform.FoliaCompat;
import team.kitemc.verifymc.platform.WebAuthHelper;

public class SchedulerBootstrap {
    public SchedulerModule bootstrap(
            Plugin plugin,
            ConfigManager configManager,
            AuthmeService authmeService,
            WebAuthHelper webAuthHelper,
            Logger log
    ) {
        startTokenCleanup(webAuthHelper);

        List<Object> tasks = new ArrayList<>();
        if (authmeService.isAuthmeEnabled()) {
            int syncInterval = configManager.getAuthmeSyncInterval();
            if (syncInterval > 0) {
                Object task = scheduleAuthmeSync(plugin, authmeService, syncInterval);
                if (task != null) {
                    tasks.add(task);
                }
                log.info("[VerifyMC] AuthMe periodic sync scheduled every " + syncInterval + " seconds.");
            }
        }
        return new SchedulerModule(plugin, tasks);
    }

    protected void startTokenCleanup(WebAuthHelper webAuthHelper) {
        webAuthHelper.startTokenCleanupTask();
    }

    protected Object scheduleAuthmeSync(Plugin plugin, AuthmeService authmeService, int syncIntervalSeconds) {
        return FoliaCompat.runTaskTimerAsync(
                plugin,
                authmeService::syncApprovedUsers,
                syncIntervalSeconds * 20L,
                syncIntervalSeconds * 20L
        );
    }

    public static final class SchedulerModule {
        private final Plugin plugin;
        private final List<Object> tasks;
        private boolean shutdown;

        private SchedulerModule(Plugin plugin, List<Object> tasks) {
            this.plugin = plugin;
            this.tasks = new ArrayList<>(tasks);
        }

        public synchronized void shutdown() {
            if (shutdown) {
                return;
            }
            shutdown = true;
            FoliaCompat.cancelTasks(plugin, tasks);
        }

        public int taskCount() {
            return tasks.size();
        }
    }
}
