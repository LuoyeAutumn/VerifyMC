package team.kitemc.verifymc.admin;

import com.sun.net.httpserver.HttpHandler;
import java.util.function.BiConsumer;

public class AdminRoutes {
    private final HttpHandler adminVerifyHandler;
    private final HttpHandler adminUserListHandler;
    private final HttpHandler adminUserApproveHandler;
    private final HttpHandler adminUserRejectHandler;
    private final HttpHandler adminUserDeleteHandler;
    private final HttpHandler adminUserBanHandler;
    private final HttpHandler adminUserUnbanHandler;
    private final HttpHandler adminUserPasswordHandler;
    private final HttpHandler adminAuditHandler;
    private final HttpHandler adminSyncHandler;

    public AdminRoutes(
            HttpHandler adminVerifyHandler,
            HttpHandler adminUserListHandler,
            HttpHandler adminUserApproveHandler,
            HttpHandler adminUserRejectHandler,
            HttpHandler adminUserDeleteHandler,
            HttpHandler adminUserBanHandler,
            HttpHandler adminUserUnbanHandler,
            HttpHandler adminUserPasswordHandler,
            HttpHandler adminAuditHandler,
            HttpHandler adminSyncHandler
    ) {
        this.adminVerifyHandler = adminVerifyHandler;
        this.adminUserListHandler = adminUserListHandler;
        this.adminUserApproveHandler = adminUserApproveHandler;
        this.adminUserRejectHandler = adminUserRejectHandler;
        this.adminUserDeleteHandler = adminUserDeleteHandler;
        this.adminUserBanHandler = adminUserBanHandler;
        this.adminUserUnbanHandler = adminUserUnbanHandler;
        this.adminUserPasswordHandler = adminUserPasswordHandler;
        this.adminAuditHandler = adminAuditHandler;
        this.adminSyncHandler = adminSyncHandler;
    }

    public void register(BiConsumer<String, HttpHandler> registerApiRoute) {
        registerApiRoute.accept("/api/admin/verify", adminVerifyHandler);
        registerApiRoute.accept("/api/admin/users", adminUserListHandler);
        registerApiRoute.accept("/api/admin/user/approve", adminUserApproveHandler);
        registerApiRoute.accept("/api/admin/user/reject", adminUserRejectHandler);
        registerApiRoute.accept("/api/admin/user/delete", adminUserDeleteHandler);
        registerApiRoute.accept("/api/admin/user/ban", adminUserBanHandler);
        registerApiRoute.accept("/api/admin/user/unban", adminUserUnbanHandler);
        registerApiRoute.accept("/api/admin/user/password", adminUserPasswordHandler);
        registerApiRoute.accept("/api/admin/audits", adminAuditHandler);
        registerApiRoute.accept("/api/admin/sync", adminSyncHandler);
    }
}
