package team.kitemc.verifymc.service;

import team.kitemc.verifymc.db.AuditDao;
import team.kitemc.verifymc.db.AuditEventType;
import team.kitemc.verifymc.db.AuditPage;
import team.kitemc.verifymc.db.AuditQuery;
import team.kitemc.verifymc.db.AuditRecord;

import java.util.List;

public class AuditService implements AutoCloseable {
    private final AuditDao auditDao;

    public AuditService(AuditDao auditDao) {
        this.auditDao = auditDao;
    }

    public void record(AuditEventType eventType, String operator, String target, String detail) {
        auditDao.addAudit(new AuditRecord(eventType, operator, target, detail, System.currentTimeMillis()));
    }

    public void recordApproval(String operator, String target) {
        record(AuditEventType.APPROVE, operator, target, "");
    }

    public void recordRejection(String operator, String target, String reason) {
        record(AuditEventType.REJECT, operator, target, reason);
    }

    public void recordBan(String operator, String target, String reason) {
        record(AuditEventType.BAN, operator, target, reason);
    }

    public void recordUnban(String operator, String target) {
        record(AuditEventType.UNBAN, operator, target, "");
    }

    public void recordDeletion(String operator, String target) {
        record(AuditEventType.DELETE, operator, target, "");
    }

    public void recordPasswordChange(String operator, String target, String detail) {
        record(AuditEventType.PASSWORD_CHANGE, operator, target, detail);
    }

    public void recordPasswordMigration(String target, String detail) {
        record(AuditEventType.PASSWORD_MIGRATION, "system", target, detail);
    }

    public void recordEmailUpdate(String username, String newEmail) {
        record(AuditEventType.EMAIL_UPDATE, username, username, "Email updated to: " + newEmail);
    }

    public void recordAdminAccessDenied(String username, String path) {
        record(
                AuditEventType.ADMIN_ACCESS_DENIED,
                username,
                path,
                "Non-admin user attempted to access admin endpoint"
        );
    }

    public AuditPage query(AuditQuery query) {
        return auditDao.query(query);
    }

    public List<String> getAvailableActions() {
        return AuditEventType.availableKeys();
    }

    @Override
    public void close() {
        auditDao.close();
    }
}
