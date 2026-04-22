package team.kitemc.verifymc.db;

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

public class FileAuditDao implements AuditDao {
    private final File file;
    private final List<AuditRecord> audits = new ArrayList<>();
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private long nextId = 1L;

    public FileAuditDao(File file) {
        this.file = file;
        load();
    }

    private synchronized void load() {
        audits.clear();
        nextId = 1L;

        if (!file.exists()) {
            return;
        }

        try (Reader reader = new FileReader(file)) {
            List<AuditRecord> loaded = gson.fromJson(reader, new TypeToken<List<AuditRecord>>() {}.getType());
            if (loaded != null) {
                audits.addAll(loaded);
                nextId = audits.stream()
                        .map(AuditRecord::id)
                        .filter(id -> id != null && id > 0)
                        .max(Long::compareTo)
                        .orElse(0L) + 1L;
            }
        } catch (Exception ignored) {
            audits.clear();
            nextId = 1L;
        }
    }

    @Override
    public synchronized void save() {
        File parent = file.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }

        try (Writer writer = new FileWriter(file)) {
            gson.toJson(audits, writer);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to save audit entries", e);
        }
    }

    @Override
    public synchronized void addAudit(AuditRecord audit) {
        AuditRecord persisted = new AuditRecord(
                audit.id() != null ? audit.id() : nextId++,
                audit.eventType(),
                audit.operator(),
                audit.target(),
                audit.detail(),
                audit.occurredAt()
        );
        audits.add(persisted);
        save();
    }

    @Override
    public synchronized AuditPage query(AuditQuery query) {
        String keyword = query.keyword().toLowerCase(Locale.ROOT);
        List<AuditRecord> filtered = audits.stream()
                .filter(audit -> query.eventType() == null || audit.eventType() == query.eventType())
                .filter(audit -> keyword.isBlank() || matchesKeyword(audit, keyword))
                .sorted(Comparator
                        .comparingLong(AuditRecord::occurredAt).reversed()
                        .thenComparing(audit -> audit.id() == null ? 0L : audit.id(), Comparator.reverseOrder()))
                .toList();

        int startIndex = Math.min((query.page() - 1) * query.size(), filtered.size());
        int endIndex = Math.min(startIndex + query.size(), filtered.size());
        List<AuditRecord> pageItems = filtered.subList(startIndex, endIndex);
        return new AuditPage(pageItems, query.page(), query.size(), filtered.size());
    }

    private boolean matchesKeyword(AuditRecord audit, String keyword) {
        return audit.operator().toLowerCase(Locale.ROOT).contains(keyword)
                || audit.target().toLowerCase(Locale.ROOT).contains(keyword)
                || audit.detail().toLowerCase(Locale.ROOT).contains(keyword);
    }
}
