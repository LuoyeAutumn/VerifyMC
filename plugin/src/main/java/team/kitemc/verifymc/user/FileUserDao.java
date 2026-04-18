package team.kitemc.verifymc.user;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import team.kitemc.verifymc.shared.PasswordUtil;

public class FileUserDao implements UserRepository {
    private final File file;
    private final Map<String, UserRecord> users = new ConcurrentHashMap<>();
    private final Gson gson = new Gson();
    private final boolean debug;
    private final org.bukkit.plugin.Plugin plugin;
    private volatile boolean dirty = false;
    private volatile boolean running = true;

    public FileUserDao(File dataFile, org.bukkit.plugin.Plugin plugin) {
        this.plugin = plugin;
        this.debug = plugin.getConfig().getBoolean("debug", false);

        try {
            File dataFolder = plugin.getDataFolder();
            String canonicalPath = dataFile.getCanonicalPath();
            String expectedPath = dataFolder.getCanonicalPath();
            if (!canonicalPath.startsWith(expectedPath)) {
                throw new SecurityException("File path must be within plugin data folder: " + canonicalPath);
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to validate file path", e);
        }

        this.file = dataFile;
        load();
        startFlushThread();
    }

    private void startFlushThread() {
        Thread flushThread = new Thread(() -> {
            while (running) {
                try {
                    Thread.sleep(5000);
                    if (dirty) {
                        save();
                    }
                } catch (InterruptedException e) {
                    break;
                }
            }
        });
        flushThread.setDaemon(true);
        flushThread.setName("FileUserDao-Flush");
        flushThread.start();
    }

    private void saveLater() {
        dirty = true;
    }

    private void debugLog(String msg) {
        if (debug && plugin != null) {
            plugin.getLogger().info("[DEBUG] FileUserDao: " + msg);
        }
    }

    public synchronized void load() {
        debugLog("Loading users from: " + file.getAbsolutePath());
        if (!file.exists()) {
            debugLog("File does not exist, creating new user database");
            return;
        }

        try (Reader reader = new FileReader(file)) {
            Map<String, Map<String, Object>> loaded =
                    gson.fromJson(reader, new TypeToken<Map<String, Map<String, Object>>>() {
                    }.getType());
            if (loaded == null) {
                return;
            }

            boolean hasUpgraded = false;
            for (Map.Entry<String, Map<String, Object>> entry : loaded.entrySet()) {
                Map<String, Object> rawUser = entry.getValue();
                if (rawUser == null) {
                    continue;
                }
                UserRecord userRecord = mapUser(rawUser);
                users.put(normalizeKey(userRecord.username()), userRecord);
                hasUpgraded = hasUpgraded || needsStorageUpgrade(rawUser);
            }

            if (hasUpgraded) {
                save();
            }
            debugLog("Loaded " + users.size() + " users from database");
        } catch (Exception e) {
            debugLog("Error loading users: " + e.getMessage());
        }
    }

    @Override
    public synchronized void save() {
        debugLog("Saving " + users.size() + " users to: " + file.getAbsolutePath());
        File tempFile = new File(file.getAbsolutePath() + ".tmp");

        try (Writer writer = new FileWriter(tempFile)) {
            gson.toJson(toStorageMap(), writer);
            writer.flush();

            if (!tempFile.renameTo(file)) {
                debugLog("Atomic rename failed, falling back to copy");
                try (InputStream in = new FileInputStream(tempFile);
                     OutputStream out = new FileOutputStream(file)) {
                    byte[] buffer = new byte[8192];
                    int bytesRead;
                    while ((bytesRead = in.read(buffer)) != -1) {
                        out.write(buffer, 0, bytesRead);
                    }
                }
                if (!tempFile.delete()) {
                    debugLog("Warning: failed to delete temporary file: " + tempFile.getAbsolutePath());
                }
            }
            dirty = false;
        } catch (Exception e) {
            debugLog("Error saving users: " + e.getMessage());
            if (tempFile.exists() && !tempFile.delete()) {
                debugLog("Warning: failed to delete temporary file after error: " + tempFile.getAbsolutePath());
            }
        }
    }

    @Override
    public boolean create(NewUserRecord user) {
        debugLog("create called: username=" + user.username());
        String key = normalizeKey(user.username());
        if (users.containsKey(key)) {
            return false;
        }

        UserRecord record = new UserRecord(
                user.username(),
                user.email(),
                user.status(),
                user.statusBeforeBan(),
                user.passwordEncoded() ? user.password() : PasswordUtil.hash(user.password()),
                System.currentTimeMillis(),
                null,
                user.questionnaireScore(),
                user.questionnairePassed(),
                user.questionnaireReviewSummary(),
                user.questionnaireScoredAt()
        );
        users.put(key, record);
        saveLater();
        return true;
    }

    @Override
    public Optional<UserRecord> findByUsername(String username) {
        if (username == null || username.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(users.get(normalizeKey(username)));
    }

    @Override
    public Optional<UserRecord> findByUsernameExact(String username) {
        if (username == null || username.isBlank()) {
            return Optional.empty();
        }
        return users.values().stream()
                .filter(user -> username.equals(user.username()))
                .findFirst();
    }

    @Override
    public Optional<UserRecord> findByEmail(String email) {
        if (email == null || email.isBlank()) {
            return Optional.empty();
        }
        return users.values().stream()
                .filter(user -> email.equalsIgnoreCase(user.email()))
                .findFirst();
    }

    @Override
    public Optional<UserRecord> findByDiscordId(String discordId) {
        if (discordId == null || discordId.isBlank()) {
            return Optional.empty();
        }
        return users.values().stream()
                .filter(user -> discordId.equals(user.discordId()))
                .findFirst();
    }

    @Override
    public List<UserRecord> findAll() {
        return new ArrayList<>(users.values());
    }

    @Override
    public List<UserSummary> findUsersByStatus(UserStatus status) {
        return users.values().stream()
                .filter(user -> user.status() == status)
                .sorted(Comparator.comparingLong(UserRecord::regTime).reversed())
                .map(UserRecord::toSummary)
                .toList();
    }

    @Override
    public UserPage<UserSummary> findUsers(UserQuery query) {
        List<UserSummary> filtered = users.values().stream()
                .filter(user -> query.status() == null || user.status() == query.status())
                .filter(user -> matchesSearch(user, query.search()))
                .sorted(Comparator.comparingLong(UserRecord::regTime).reversed())
                .map(UserRecord::toSummary)
                .collect(Collectors.toList());

        int fromIndex = Math.min(query.offset(), filtered.size());
        int toIndex = Math.min(fromIndex + query.pageSize(), filtered.size());
        return UserPage.of(filtered.subList(fromIndex, toIndex), query.page(), query.pageSize(), filtered.size());
    }

    @Override
    public List<String> suggestUsernames(String prefix, int limit) {
        String normalizedPrefix = prefix == null ? "" : prefix.toLowerCase(Locale.ROOT);
        return users.values().stream()
                .map(UserRecord::username)
                .filter(username -> normalizedPrefix.isEmpty()
                        || username.toLowerCase(Locale.ROOT).startsWith(normalizedPrefix))
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .limit(Math.max(1, limit))
                .toList();
    }

    @Override
    public long countByEmail(String email) {
        if (email == null || email.isBlank()) {
            return 0L;
        }
        return users.values().stream()
                .filter(user -> email.equalsIgnoreCase(user.email()))
                .count();
    }

    @Override
    public boolean updateStatus(String username, UserStatus status, String operator) {
        return updateUser(username, user -> new UserRecord(
                user.username(),
                user.email(),
                status,
                user.statusBeforeBan(),
                user.passwordHash(),
                user.regTime(),
                user.discordId(),
                user.questionnaireScore(),
                user.questionnairePassed(),
                user.questionnaireReviewSummary(),
                user.questionnaireScoredAt()
        ));
    }

    @Override
    public boolean updateStatusForBan(String username, String operator) {
        return updateUser(username, user -> new UserRecord(
                user.username(),
                user.email(),
                UserStatus.BANNED,
                user.status() == UserStatus.BANNED ? user.statusBeforeBan() : user.status(),
                user.passwordHash(),
                user.regTime(),
                user.discordId(),
                user.questionnaireScore(),
                user.questionnairePassed(),
                user.questionnaireReviewSummary(),
                user.questionnaireScoredAt()
        ));
    }

    @Override
    public boolean restoreStatusFromBan(String username, UserStatus fallbackStatus, String operator) {
        UserStatus restoredStatus = fallbackStatus == null ? UserStatus.APPROVED : fallbackStatus;
        return updateUser(username, user -> new UserRecord(
                user.username(),
                user.email(),
                user.statusBeforeBan() == null ? restoredStatus : user.statusBeforeBan(),
                null,
                user.passwordHash(),
                user.regTime(),
                user.discordId(),
                user.questionnaireScore(),
                user.questionnairePassed(),
                user.questionnaireReviewSummary(),
                user.questionnaireScoredAt()
        ));
    }

    @Override
    public boolean updatePassword(String username, String plainPassword) {
        return updateStoredPassword(username, PasswordUtil.hash(plainPassword));
    }

    @Override
    public boolean updateStoredPassword(String username, String storedPassword) {
        return updateUser(username, user -> new UserRecord(
                user.username(),
                user.email(),
                user.status(),
                user.statusBeforeBan(),
                storedPassword,
                user.regTime(),
                user.discordId(),
                user.questionnaireScore(),
                user.questionnairePassed(),
                user.questionnaireReviewSummary(),
                user.questionnaireScoredAt()
        ));
    }

    @Override
    public boolean updateEmail(String username, String email) {
        return updateUser(username, user -> new UserRecord(
                user.username(),
                email,
                user.status(),
                user.statusBeforeBan(),
                user.passwordHash(),
                user.regTime(),
                user.discordId(),
                user.questionnaireScore(),
                user.questionnairePassed(),
                user.questionnaireReviewSummary(),
                user.questionnaireScoredAt()
        ));
    }

    @Override
    public boolean updateDiscordId(String username, String discordId) {
        return updateUser(username, user -> new UserRecord(
                user.username(),
                user.email(),
                user.status(),
                user.statusBeforeBan(),
                user.passwordHash(),
                user.regTime(),
                discordId,
                user.questionnaireScore(),
                user.questionnairePassed(),
                user.questionnaireReviewSummary(),
                user.questionnaireScoredAt()
        ));
    }

    @Override
    public boolean delete(String username) {
        boolean removed = users.remove(normalizeKey(username)) != null;
        if (removed) {
            saveLater();
        }
        return removed;
    }

    @Override
    public void close() {
        running = false;
        if (dirty) {
            save();
        }
        debugLog("FileUserDao closed");
    }

    private boolean updateUser(String username, java.util.function.Function<UserRecord, UserRecord> mapper) {
        String key = normalizeKey(username);
        UserRecord existing = users.get(key);
        if (existing == null) {
            return false;
        }
        users.put(key, mapper.apply(existing));
        saveLater();
        return true;
    }

    private boolean matchesSearch(UserRecord user, String rawSearch) {
        if (rawSearch == null || rawSearch.isBlank()) {
            return true;
        }
        String search = rawSearch.toLowerCase(Locale.ROOT);
        String username = user.username() == null ? "" : user.username().toLowerCase(Locale.ROOT);
        String email = user.email() == null ? "" : user.email().toLowerCase(Locale.ROOT);
        return username.contains(search) || email.contains(search);
    }

    private Map<String, Map<String, Object>> toStorageMap() {
        Map<String, Map<String, Object>> storage = new LinkedHashMap<>();
        users.forEach((key, value) -> storage.put(key, toStorageUser(value)));
        return storage;
    }

    private Map<String, Object> toStorageUser(UserRecord user) {
        Map<String, Object> storage = new LinkedHashMap<>();
        storage.put("username", user.username());
        storage.put("email", user.email());
        storage.put("status", user.status().value());
        storage.put("statusBeforeBan", user.statusBeforeBan() == null ? null : user.statusBeforeBan().value());
        storage.put("password", user.passwordHash());
        storage.put("regTime", user.regTime());
        storage.put("discordId", user.discordId());
        storage.put("questionnaireScore", user.questionnaireScore());
        storage.put("questionnairePassed", user.questionnairePassed());
        storage.put("questionnaireReviewSummary", user.questionnaireReviewSummary());
        storage.put("questionnaireScoredAt", user.questionnaireScoredAt());
        return storage;
    }

    private UserRecord mapUser(Map<String, Object> rawUser) {
        String username = asString(rawUser.get("username"));
        String email = asString(rawUser.get("email"));
        UserStatus status = UserStatus.fromValue(asString(rawUser.get("status"))).orElse(UserStatus.PENDING);
        UserStatus statusBeforeBan = UserStatus.fromValue(firstNonBlank(
                rawUser.get("statusBeforeBan"),
                rawUser.get("status_before_ban")
        )).orElse(null);
        String passwordHash = asString(rawUser.get("password"));
        long regTime = asLong(rawUser.get("regTime"), System.currentTimeMillis());
        String discordId = firstNonBlank(rawUser.get("discordId"), rawUser.get("discord_id"));
        Integer questionnaireScore = asInteger(firstNonNull(rawUser.get("questionnaireScore"), rawUser.get("questionnaire_score")));
        Boolean questionnairePassed = asBoolean(firstNonNull(rawUser.get("questionnairePassed"), rawUser.get("questionnaire_passed")));
        String questionnaireReviewSummary = firstNonBlank(
                rawUser.get("questionnaireReviewSummary"),
                rawUser.get("questionnaire_review_summary")
        );
        Long questionnaireScoredAt = asNullableLong(firstNonNull(
                rawUser.get("questionnaireScoredAt"),
                rawUser.get("questionnaire_scored_at")
        ));

        return new UserRecord(
                username,
                email,
                status,
                statusBeforeBan,
                passwordHash,
                regTime,
                discordId,
                questionnaireScore,
                questionnairePassed,
                questionnaireReviewSummary,
                questionnaireScoredAt
        );
    }

    private boolean needsStorageUpgrade(Map<String, Object> rawUser) {
        return !rawUser.containsKey("discordId")
                || !rawUser.containsKey("statusBeforeBan")
                || !rawUser.containsKey("questionnaireScore")
                || !rawUser.containsKey("questionnairePassed")
                || !rawUser.containsKey("questionnaireReviewSummary")
                || !rawUser.containsKey("questionnaireScoredAt");
    }

    private String normalizeKey(String username) {
        return username == null ? "" : username.toLowerCase(Locale.ROOT);
    }

    private Object firstNonNull(Object primary, Object fallback) {
        return primary != null ? primary : fallback;
    }

    private String firstNonBlank(Object primary, Object fallback) {
        String first = asString(primary);
        if (first != null && !first.isBlank()) {
            return first;
        }
        return asString(fallback);
    }

    private String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private long asLong(Object value, long defaultValue) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value == null) {
            return defaultValue;
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException ignored) {
            return defaultValue;
        }
    }

    private Long asNullableLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value == null) {
            return null;
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private Integer asInteger(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value == null) {
            return null;
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private Boolean asBoolean(Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value == null) {
            return null;
        }
        return Boolean.parseBoolean(String.valueOf(value));
    }
}
