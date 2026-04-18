package team.kitemc.verifymc.audit;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.Reader;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public class FileAuditRepository implements AuditRepository {
    private final File file;
    private final List<AuditEntry> entries = new ArrayList<>();
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private long nextId = 1L;

    public FileAuditRepository(File file) {
        this.file = file;
        load();
    }

    private synchronized void load() {
        entries.clear();
        nextId = 1L;

        if (!file.exists()) {
            return;
        }

        try (Reader reader = new FileReader(file)) {
            List<AuditEntry> loaded = gson.fromJson(reader, new TypeToken<List<AuditEntry>>() {}.getType());
            if (loaded != null) {
                entries.addAll(loaded);
                nextId = entries.stream()
                        .map(AuditEntry::id)
                        .filter(id -> id != null && id > 0)
                        .max(Long::compareTo)
                        .orElse(0L) + 1L;
            }
        } catch (Exception ignored) {
            entries.clear();
            nextId = 1L;
        }
    }

    private synchronized void save() {
        File parent = file.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }

        try (Writer writer = new FileWriter(file)) {
            gson.toJson(entries, writer);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to save audit entries", e);
        }
    }

    @Override
    public synchronized void append(AuditEntry entry) {
        AuditEntry persisted = new AuditEntry(
                entry.id() != null ? entry.id() : nextId++,
                entry.eventType(),
                entry.operator(),
                entry.target(),
                entry.detail(),
                entry.occurredAt()
        );
        entries.add(persisted);
        save();
    }

    @Override
    public synchronized AuditPage query(AuditQuery query) {
        String keyword = query.keyword().toLowerCase(Locale.ROOT);
        List<AuditEntry> filtered = entries.stream()
                .filter(entry -> query.eventType() == null || entry.eventType() == query.eventType())
                .filter(entry -> keyword.isBlank() || matchesKeyword(entry, keyword))
                .sorted(Comparator
                        .comparingLong(AuditEntry::occurredAt).reversed()
                        .thenComparing(entry -> entry.id() == null ? 0L : entry.id(), Comparator.reverseOrder()))
                .toList();

        int startIndex = Math.min((query.page() - 1) * query.size(), filtered.size());
        int endIndex = Math.min(startIndex + query.size(), filtered.size());
        List<AuditEntry> pageItems = filtered.subList(startIndex, endIndex);
        return new AuditPage(pageItems, query.page(), query.size(), filtered.size());
    }

    private boolean matchesKeyword(AuditEntry entry, String keyword) {
        return entry.operator().toLowerCase(Locale.ROOT).contains(keyword)
                || entry.target().toLowerCase(Locale.ROOT).contains(keyword)
                || entry.detail().toLowerCase(Locale.ROOT).contains(keyword);
    }
}
