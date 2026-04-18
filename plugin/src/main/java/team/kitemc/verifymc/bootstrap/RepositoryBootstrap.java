package team.kitemc.verifymc.bootstrap;

import java.io.File;
import java.sql.SQLException;
import java.util.logging.Logger;
import org.bukkit.plugin.java.JavaPlugin;
import team.kitemc.verifymc.audit.AuditService;
import team.kitemc.verifymc.audit.FileAuditDao;
import team.kitemc.verifymc.audit.MysqlAuditDao;
import team.kitemc.verifymc.platform.PlatformServices;
import team.kitemc.verifymc.user.FileUserDao;
import team.kitemc.verifymc.user.MysqlUserDao;
import team.kitemc.verifymc.user.UserRepository;

public class RepositoryBootstrap {
    public Result bootstrap(JavaPlugin plugin, PlatformServices platform, Logger log) {
        if ("mysql".equalsIgnoreCase(platform.getConfigManager().getStorageType())) {
            try {
                UserRepository userRepository = createMysqlUserRepository(plugin, platform);
                AuditService auditService = createMysqlAuditService(plugin, platform);
                log.info("[VerifyMC] Using MySQL storage.");
                return new Result(userRepository, auditService);
            } catch (SQLException e) {
                log.severe("[VerifyMC] Database initialization failed: " + e.getMessage());
                throw new IllegalStateException("Failed to initialize MySQL storage", e);
            }
        }

        File dataDir = plugin.getDataFolder();
        UserRepository userRepository = createFileUserRepository(plugin, dataDir);
        AuditService auditService = createFileAuditService(dataDir);
        log.info("[VerifyMC] Using file storage.");
        return new Result(userRepository, auditService);
    }

    protected UserRepository createMysqlUserRepository(JavaPlugin plugin, PlatformServices platform) throws SQLException {
        return new MysqlUserDao(
                platform.getConfigManager().getMysqlProperties(),
                platform.getI18nManager().getResourceBundle(),
                plugin
        );
    }

    protected AuditService createMysqlAuditService(JavaPlugin plugin, PlatformServices platform) throws SQLException {
        return new AuditService(new MysqlAuditDao(platform.getConfigManager().getMysqlProperties(), plugin));
    }

    protected UserRepository createFileUserRepository(JavaPlugin plugin, File dataDir) {
        return new FileUserDao(new File(dataDir, "users.json"), plugin);
    }

    protected AuditService createFileAuditService(File dataDir) {
        return new AuditService(new FileAuditDao(new File(dataDir, "audits.json")));
    }

    public record Result(
            UserRepository userRepository,
            AuditService auditService
    ) {
    }
}
