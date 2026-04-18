package team.kitemc.verifymc.admin;

import java.util.function.BiFunction;
import team.kitemc.verifymc.audit.AuditService;
import team.kitemc.verifymc.platform.WebAuthHelper;

public class SimpleAuthenticatedRequestContext implements AuthenticatedRequestContext {
    private final WebAuthHelper webAuthHelper;
    private final AdminAccessManager adminAccessManager;
    private final AuditService auditService;
    private final BiFunction<String, String, String> messageResolver;

    public SimpleAuthenticatedRequestContext(
            WebAuthHelper webAuthHelper,
            AdminAccessManager adminAccessManager,
            AuditService auditService,
            BiFunction<String, String, String> messageResolver
    ) {
        this.webAuthHelper = webAuthHelper;
        this.adminAccessManager = adminAccessManager;
        this.auditService = auditService;
        this.messageResolver = messageResolver;
    }

    @Override
    public WebAuthHelper getWebAuthHelper() {
        return webAuthHelper;
    }

    @Override
    public AdminAccessManager getAdminAccessManager() {
        return adminAccessManager;
    }

    @Override
    public AuditService getAuditService() {
        return auditService;
    }

    @Override
    public String getMessage(String key, String language) {
        return messageResolver.apply(key, language);
    }
}
