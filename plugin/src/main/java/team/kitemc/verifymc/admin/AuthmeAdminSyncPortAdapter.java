package team.kitemc.verifymc.admin;

import team.kitemc.verifymc.integration.AuthmeService;

public class AuthmeAdminSyncPortAdapter implements AdminAuthmeSyncPort {
    private final AuthmeService authmeService;

    public AuthmeAdminSyncPortAdapter(AuthmeService authmeService) {
        this.authmeService = authmeService;
    }

    @Override
    public boolean isAvailable() {
        return authmeService != null;
    }

    @Override
    public boolean isEnabled() {
        return authmeService != null && authmeService.isAuthmeEnabled();
    }

    @Override
    public void syncApprovedUsers() {
        if (authmeService != null) {
            authmeService.syncApprovedUsers();
        }
    }
}
