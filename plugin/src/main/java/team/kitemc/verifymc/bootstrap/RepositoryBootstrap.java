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
                validateUsernameCaseConfiguration(platform, userRepository);
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
        validateUsernameCaseConfiguration(platform, userRepository);
        AuditService auditService = createFileAuditService(dataDir);
        log.info("[VerifyMC] Using file storage.");
        return new Result(userRepository, auditService);
    }

    protected UserRepository createMysqlUserRepository(JavaPlugin plugin, PlatformServices platform) throws SQLException {
        return new MysqlUserDao(
                platform.getConfigManager().getMysqlProperties(),
                platform.getI18nManager().getResourceBundle(),
                plugin,
                platform.getConfigManager().isUsernameCaseSensitive()
        );
    }

    protected AuditService createMysqlAuditService(JavaPlugin plugin, PlatformServices platform) throws SQLException {
        return new AuditService(new MysqlAuditDao(platform.getConfigManager().getMysqlProperties(), plugin));
    }

    protected UserRepository createFileUserRepository(JavaPlugin plugin, File dataDir) {
        return new FileUserDao(
                new File(dataDir, "users.json"),
                plugin,
                plugin.getConfig().getBoolean("username_case_sensitive", false)
        );
    }

    protected AuditService createFileAuditService(File dataDir) {
        return new AuditService(new FileAuditDao(new File(dataDir, "audits.json")));
    }

    private void validateUsernameCaseConfiguration(PlatformServices platform, UserRepository userRepository) {
        if (!platform.getConfigManager().isUsernameCaseSensitive()) {
            return;
        }

        java.util.List<java.util.List<String>> conflicts = userRepository.findUsernameCaseConflictGroups();
        if (conflicts.isEmpty()) {
            return;
        }

        try {
            userRepository.close();
        } catch (Exception ignored) {
        }

        String detail = conflicts.stream()
                .map(group -> String.join(", ", group))
                .collect(java.util.stream.Collectors.joining(" | "));
        throw new IllegalStateException(
                "username_case_sensitive=true 时检测到仅大小写不同的历史用户名冲突，请先手动清理后再启动: " + detail
        );
    }

    public record Result(
            UserRepository userRepository,
            AuditService auditService
    ) {
    }
}
