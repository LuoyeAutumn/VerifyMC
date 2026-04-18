package team.kitemc.verifymc.integration;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import team.kitemc.verifymc.platform.FoliaCompat;
import team.kitemc.verifymc.shared.PasswordUtil;
import team.kitemc.verifymc.user.NewUserRecord;
import team.kitemc.verifymc.user.UserRecord;
import team.kitemc.verifymc.user.UserRepository;
import team.kitemc.verifymc.user.UserStatus;

public class AuthmeService {
    private final Plugin plugin;
    private final boolean debug;
    private UserRepository userRepository;

    public AuthmeService(Plugin plugin) {
        this.plugin = plugin;
        this.debug = plugin.getConfig().getBoolean("debug", false);
    }

    public void setUserRepository(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public boolean isAuthmeEnabled() {
        return plugin.getConfig().getBoolean("authme.enabled", false);
    }

    public String getAuthmePassword(String username) {
        if (!isAuthmeEnabled() || username == null || username.isEmpty()) {
            return null;
        }
        String sql = "SELECT " + passwordColumn() + " FROM " + tableName() + " WHERE " + nameColumn() + " = ?";
        try (Connection conn = getAuthmeConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getString(1);
                }
            }
        } catch (Exception e) {
            debugLog("Failed to get AuthMe password for " + username + ": " + e.getMessage());
        }
        return null;
    }

    public boolean hasAuthmeUser(String username) {
        if (!isAuthmeEnabled() || username == null || username.isEmpty()) {
            return false;
        }
        String sql = "SELECT 1 FROM " + tableName() + " WHERE " + nameColumn() + " = ?";
        try (Connection conn = getAuthmeConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (Exception e) {
            debugLog("Failed to check AuthMe user " + username + ": " + e.getMessage());
        }
        return false;
    }

    public boolean isValidPassword(String password) {
        if (password == null || password.trim().isEmpty()) {
            return false;
        }
        String regex = plugin.getConfig().getString("authme.password_regex", "^[a-zA-Z0-9_]{8,26}$");
        return Pattern.matches(regex, password);
    }

    public boolean syncApprovedUserToAuthme(String username) {
        if (!isAuthmeEnabled() || userRepository == null || username == null || username.isBlank()) {
            return false;
        }

        UserRecord localUser = userRepository.findByUsername(username).orElse(null);
        return syncApprovedLocalUser(localUser, null, null);
    }

    public boolean removeUserFromAuthme(String username) {
        if (!isAuthmeEnabled()) {
            debugLog("AuthMe not enabled, skipping unregistration");
            return false;
        }

        return deleteAuthmeUser(username);
    }

    public boolean syncUserPasswordToAuthme(String username, String newPassword) {
        if (!isAuthmeEnabled()) {
            debugLog("AuthMe not enabled, skipping password change");
            return false;
        }

        return updateAuthmePassword(username, newPassword);
    }

    public String encodePasswordForStorage(String plainOrEncodedPassword) {
        return buildStoredPassword(plainOrEncodedPassword);
    }

    public void syncApprovedUsers() {
        if (!isAuthmeEnabled() || userRepository == null) {
            return;
        }
        try {
            List<UserRecord> localUsers = userRepository.findAll();
            Map<String, UserRecord> localByLowerName = new HashMap<>();
            for (UserRecord u : localUsers) {
                String username = u.username();
                if (username != null) {
                    localByLowerName.put(username.toLowerCase(), u);
                }
            }

            Map<String, AuthmeProfile> authmeProfilesByName = listAuthmeProfiles();
            Map<String, String> authmeByLowerName = new HashMap<>();
            for (Map.Entry<String, AuthmeProfile> entry : authmeProfilesByName.entrySet()) {
                authmeByLowerName.put(entry.getKey().toLowerCase(), entry.getKey());
            }

            for (UserRecord local : localUsers) {
                syncApprovedLocalUser(local, authmeProfilesByName, authmeByLowerName);
            }

            authmeProfilesByName = listAuthmeProfiles();
            for (Map.Entry<String, AuthmeProfile> entry : authmeProfilesByName.entrySet()) {
                String authName = entry.getKey();
                AuthmeProfile profile = entry.getValue();
                String authPassword = profile != null ? profile.password : null;
                String authEmail = profile != null ? profile.email : null;
                UserRecord local = localByLowerName.get(authName.toLowerCase());
                if (local == null) {
                    if (authPassword != null && !authPassword.trim().isEmpty()) {
                        String localEmail = authEmail != null ? authEmail : "";
                        userRepository.create(new NewUserRecord(
                                authName,
                                localEmail,
                                UserStatus.APPROVED,
                                null,
                                authPassword,
                                true,
                                null,
                                null,
                                null,
                                null
                        ));
                        FoliaCompat.runTaskGlobal(plugin, () ->
                                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "whitelist add " + authName));
                    }
                    continue;
                }
                if (local.status() != UserStatus.APPROVED) {
                    continue;
                }

                if (authPassword != null && !authPassword.trim().isEmpty()) {
                    String localPassword = local.passwordHash();
                    if (localPassword == null || localPassword.trim().isEmpty() || !authPassword.equals(localPassword)) {
                        userRepository.updateStoredPassword(authName, authPassword);
                    }
                }

                if (authEmail != null && !authEmail.trim().isEmpty()) {
                    String localEmail = local.email();
                    if (localEmail == null || localEmail.trim().isEmpty() || !authEmail.equalsIgnoreCase(localEmail)) {
                        userRepository.updateEmail(authName, authEmail);
                    }
                }
            }
            userRepository.save();
        } catch (Exception e) {
            debugLog("Failed syncApprovedUsers: " + e.getMessage());
        }
    }

    private boolean syncApprovedLocalUser(UserRecord localUser,
                                          Map<String, AuthmeProfile> authmeProfilesByName,
                                          Map<String, String> authmeByLowerName) {
        if (localUser == null) {
            return false;
        }

        String username = localUser.username();
        String password = localUser.passwordHash();
        String email = localUser.email();
        if (username == null || localUser.status() != UserStatus.APPROVED) {
            return false;
        }

        String authName = authmeByLowerName != null
                ? authmeByLowerName.get(username.toLowerCase())
                : findAuthmeUsername(username);
        if (authName == null) {
            if (password == null || password.trim().isEmpty()) {
                return false;
            }

            boolean created = upsertAuthmeUser(username, password);
            if (created && email != null && !email.trim().isEmpty()) {
                updateAuthmeEmail(username, email);
            }
            return created;
        }

        boolean changed = false;
        AuthmeProfile profile = authmeProfilesByName != null
                ? authmeProfilesByName.get(authName)
                : getAuthmeProfile(authName);
        String authPassword = profile != null ? profile.password : null;
        String authEmail = profile != null ? profile.email : null;

        if (password != null && !password.trim().isEmpty() && !password.equals(authPassword)) {
            changed = updateAuthmePassword(authName, password) || changed;
        }

        if (email != null && !email.trim().isEmpty() && (authEmail == null || authEmail.trim().isEmpty() || !email.equalsIgnoreCase(authEmail))) {
            changed = updateAuthmeEmail(authName, email) || changed;
        }

        return changed;
    }

    private String findAuthmeUsername(String username) {
        if (username == null || username.isBlank()) {
            return null;
        }

        String sql = "SELECT " + nameColumn() + " FROM " + tableName() + " WHERE LOWER(" + nameColumn() + ") = LOWER(?)";
        try (Connection conn = getAuthmeConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getString(1);
                }
            }
        } catch (Exception e) {
            debugLog("Failed to find AuthMe user " + username + ": " + e.getMessage());
        }
        return null;
    }

    private AuthmeProfile getAuthmeProfile(String username) {
        if (username == null || username.isBlank()) {
            return null;
        }

        String sql = "SELECT " + passwordColumn() + ", " + column("mySQLColumnEmail", "email") +
                " FROM " + tableName() + " WHERE " + nameColumn() + " = ?";
        try (Connection conn = getAuthmeConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new AuthmeProfile(rs.getString(1), rs.getString(2));
                }
            }
        } catch (Exception e) {
            debugLog("Failed to get AuthMe profile for " + username + ": " + e.getMessage());
        }
        return null;
    }

    private Connection getAuthmeConnection() throws Exception {
        String type = plugin.getConfig().getString("authme.database.type", "sqlite").toLowerCase();
        if ("sqlite".equals(type)) {
            Class.forName("org.sqlite.JDBC");
            String path = plugin.getConfig().getString("authme.database.sqlite.path", "plugins/AuthMe/authme.db");
            return DriverManager.getConnection("jdbc:sqlite:" + path);
        }

        Class.forName("com.mysql.cj.jdbc.Driver");
        String host = plugin.getConfig().getString("authme.database.mysql.host", "127.0.0.1");
        int port = plugin.getConfig().getInt("authme.database.mysql.port", 3306);
        String database = plugin.getConfig().getString("authme.database.mysql.database", "authme");
        String user = plugin.getConfig().getString("authme.database.mysql.user", "root");
        String password = plugin.getConfig().getString("authme.database.mysql.password", "");
        boolean useSSL = plugin.getConfig().getBoolean("authme.database.mysql.useSSL", true);
        boolean allowPublicKeyRetrieval = plugin.getConfig().getBoolean("authme.database.mysql.allowPublicKeyRetrieval", false);
        String url = "jdbc:mysql://" + host + ":" + port + "/" + database +
                "?useSSL=" + useSSL +
                "&allowPublicKeyRetrieval=" + allowPublicKeyRetrieval +
                "&characterEncoding=utf8" +
                "&connectTimeout=5000" +
                "&socketTimeout=10000";
        return DriverManager.getConnection(url, user, password);
    }

    private static final java.util.regex.Pattern SAFE_SQL_IDENTIFIER = java.util.regex.Pattern.compile("^[a-zA-Z0-9_]{1,64}$");

    private String tableName() {
        String name = plugin.getConfig().getString("authme.database.table", "authme");
        if (!SAFE_SQL_IDENTIFIER.matcher(name).matches()) {
            debugLog("Unsafe table name in config: " + name + ", falling back to 'authme'");
            return "authme";
        }
        return name;
    }

    private String column(String key, String def) {
        String col = plugin.getConfig().getString("authme.database.columns." + key, def);
        if (col != null && !col.isEmpty() && !SAFE_SQL_IDENTIFIER.matcher(col).matches()) {
            debugLog("Unsafe column name in config: " + col + ", falling back to '" + def + "'");
            return def;
        }
        return col;
    }

    private String nameColumn() {
        return column("mySQLColumnName", "username");
    }

    private String passwordColumn() {
        return column("mySQLColumnPassword", "password");
    }

    private String saltColumn() {
        return column("mySQLColumnSalt", "");
    }

    private boolean hasSaltColumn() {
        String saltCol = saltColumn();
        return saltCol != null && !saltCol.trim().isEmpty();
    }

    private static final class AuthmeProfile {
        private final String password;
        private final String email;

        private AuthmeProfile(String password, String email) {
            this.password = password;
            this.email = email;
        }
    }

    private Map<String, AuthmeProfile> listAuthmeProfiles() throws Exception {
        Map<String, AuthmeProfile> result = new HashMap<>();
        String sql = "SELECT " + nameColumn() + ", " + passwordColumn() + ", " + column("mySQLColumnEmail", "email") + " FROM " + tableName();
        try (Connection conn = getAuthmeConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                String username = rs.getString(1);
                if (username != null) {
                    result.put(username, new AuthmeProfile(rs.getString(2), rs.getString(3)));
                }
            }
        }
        return result;
    }

    private boolean upsertAuthmeUser(String username, String password) {
        String nameCol = nameColumn();
        String passCol = passwordColumn();
        String realNameCol = column("mySQLRealName", "realname");
        String regDateCol = column("mySQLColumnRegisterDate", "regdate");
        String lastLoginCol = column("mySQLColumnLastLogin", "lastlogin");
        String ipCol = column("mySQLColumnIp", "ip");
        String regIpCol = column("mySQLColumnRegisterIp", "regip");
        String loggedCol = column("mySQLColumnLogged", "isLogged");
        String hasSessionCol = column("mySQLColumnHasSession", "hasSession");
        String xCol = column("mySQLlastlocX", "x");
        String yCol = column("mySQLlastlocY", "y");
        String zCol = column("mySQLlastlocZ", "z");
        String worldCol = column("mySQLlastlocWorld", "world");
        String yawCol = column("mySQLlastlocYaw", "yaw");
        String pitchCol = column("mySQLlastlocPitch", "pitch");
        String emailCol = column("mySQLColumnEmail", "email");
        String saltCol = saltColumn();

        String selectSql = "SELECT " + nameCol + " FROM " + tableName() + " WHERE " + nameCol + " = ?";

        StringBuilder updateSql = new StringBuilder("UPDATE " + tableName() + " SET " + passCol + " = ?, "
            + realNameCol + " = ?, " + regDateCol + " = ?, " + lastLoginCol + " = ?, "
            + ipCol + " = ?, " + regIpCol + " = ?, " + loggedCol + " = 0, " + hasSessionCol + " = 0");
        if (hasSaltColumn()) {
            updateSql.append(", ").append(saltCol).append(" = ?");
        }
        updateSql.append(" WHERE ").append(nameCol).append(" = ?");

        StringBuilder insertColumns = new StringBuilder(nameCol + ", " + realNameCol + ", " + passCol + ", "
            + regDateCol + ", " + lastLoginCol + ", " + ipCol + ", " + regIpCol + ", " + loggedCol
            + ", " + hasSessionCol + ", " + xCol + ", " + yCol + ", " + zCol + ", " + worldCol
            + ", " + yawCol + ", " + pitchCol + ", " + emailCol);
        StringBuilder insertValues = new StringBuilder("?, ?, ?, ?, ?, ?, ?, 0, 0, 0, 0, 0, ?, 0, 0, ?");
        if (hasSaltColumn()) {
            insertColumns.append(", ").append(saltCol);
            insertValues.append(", ?");
        }
        String insertSql = "INSERT INTO " + tableName() + " (" + insertColumns + ") VALUES (" + insertValues + ")";

        long now = System.currentTimeMillis() / 1000;
        String loopback = "127.0.0.1";
        String storedPassword = buildStoredPassword(password);

        try (Connection conn = getAuthmeConnection();
             PreparedStatement select = conn.prepareStatement(selectSql)) {
            select.setString(1, username);
            boolean exists;
            try (ResultSet rs = select.executeQuery()) {
                exists = rs.next();
            }

            if (exists) {
                try (PreparedStatement update = conn.prepareStatement(updateSql.toString())) {
                    int idx = 1;
                    update.setString(idx++, storedPassword);
                    update.setString(idx++, username);
                    update.setLong(idx++, now);
                    update.setLong(idx++, now);
                    update.setString(idx++, loopback);
                    update.setString(idx++, loopback);
                    if (hasSaltColumn()) {
                        update.setString(idx++, "");
                    }
                    update.setString(idx, username);
                    return update.executeUpdate() > 0;
                }
            } else {
                try (PreparedStatement insert = conn.prepareStatement(insertSql)) {
                    int idx = 1;
                    insert.setString(idx++, username);
                    insert.setString(idx++, username);
                    insert.setString(idx++, storedPassword);
                    insert.setLong(idx++, now);
                    insert.setLong(idx++, now);
                    insert.setString(idx++, loopback);
                    insert.setString(idx++, loopback);
                    insert.setString(idx++, "world");
                    insert.setString(idx++, "");
                    if (hasSaltColumn()) {
                        insert.setString(idx++, "");
                    }
                    return insert.executeUpdate() > 0;
                }
            }
        } catch (Exception e) {
            debugLog("Failed to upsert AuthMe user " + username + ": " + e.getMessage());
            return false;
        }
    }

    private boolean deleteAuthmeUser(String username) {
        String sql = "DELETE FROM " + tableName() + " WHERE " + nameColumn() + " = ?";
        try (Connection conn = getAuthmeConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, username);
            ps.executeUpdate();
            return true;
        } catch (Exception e) {
            debugLog("Failed to delete AuthMe user " + username + ": " + e.getMessage());
            return false;
        }
    }

    private boolean updateAuthmeEmail(String username, String email) {
        String sql = "UPDATE " + tableName() + " SET " + column("mySQLColumnEmail", "email") + " = ? WHERE " + nameColumn() + " = ?";
        try (Connection conn = getAuthmeConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, email);
            ps.setString(2, username);
            return ps.executeUpdate() > 0;
        } catch (Exception e) {
            debugLog("Failed to update AuthMe email " + username + ": " + e.getMessage());
            return false;
        }
    }

    private boolean updateAuthmePassword(String username, String newPassword) {
        String passCol = passwordColumn();
        String nameCol = nameColumn();
        String sql;
        if (hasSaltColumn()) {
            sql = "UPDATE " + tableName() + " SET " + passCol + " = ?, " + saltColumn() + " = ? WHERE " + nameCol + " = ?";
        } else {
            sql = "UPDATE " + tableName() + " SET " + passCol + " = ? WHERE " + nameCol + " = ?";
        }
        try (Connection conn = getAuthmeConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, buildStoredPassword(newPassword));
            if (hasSaltColumn()) {
                ps.setString(2, "");
                ps.setString(3, username);
            } else {
                ps.setString(2, username);
            }
            return ps.executeUpdate() > 0;
        } catch (Exception e) {
            debugLog("Failed to update AuthMe password " + username + ": " + e.getMessage());
            return false;
        }
    }

    private String buildStoredPassword(String plainOrStoredPassword) {
        if (plainOrStoredPassword == null) {
            return null;
        }
        if (PasswordUtil.isHashed(plainOrStoredPassword) || PasswordUtil.isUnsaltedSha256(plainOrStoredPassword)) {
            return plainOrStoredPassword;
        }
        return PasswordUtil.hash(plainOrStoredPassword);
    }

    private void debugLog(String msg) {
        if (debug) {
            plugin.getLogger().info("[DEBUG] AuthmeService: " + msg);
        }
    }
}

