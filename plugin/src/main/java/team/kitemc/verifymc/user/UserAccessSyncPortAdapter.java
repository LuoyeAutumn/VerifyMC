package team.kitemc.verifymc.user;

import team.kitemc.verifymc.integration.AuthmeService;
import team.kitemc.verifymc.platform.WhitelistService;

public class UserAccessSyncPortAdapter implements UserAccessSyncPort {
    private final WhitelistService whitelistService;
    private final AuthmeService authmeService;

    public UserAccessSyncPortAdapter(WhitelistService whitelistService, AuthmeService authmeService) {
        this.whitelistService = whitelistService;
        this.authmeService = authmeService;
    }

    @Override
    public void removeUserAccess(String username) {
        whitelistService.remove(username);
        if (authmeService != null && authmeService.isAuthmeEnabled()) {
            authmeService.removeUserFromAuthme(username);
        }
    }

    @Override
    public void grantApprovedAccess(String username) {
        whitelistService.add(username);
        if (authmeService != null && authmeService.isAuthmeEnabled()) {
            authmeService.syncApprovedUserToAuthme(username);
        }
    }

    @Override
    public void syncPasswordChange(String username, String password) {
        if (authmeService != null && authmeService.isAuthmeEnabled()) {
            authmeService.syncUserPasswordToAuthme(username, password);
        }
    }
}
