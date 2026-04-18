package team.kitemc.verifymc.admin;

import team.kitemc.verifymc.audit.AuditService;
import team.kitemc.verifymc.platform.WebAuthHelper;

public interface AuthenticatedRequestContext {
    WebAuthHelper getWebAuthHelper();

    AdminAccessManager getAdminAccessManager();

    AuditService getAuditService();

    String getMessage(String key, String language);
}
