package team.kitemc.verifymc.review;

import team.kitemc.verifymc.integration.AuthmeService;
import team.kitemc.verifymc.platform.WhitelistService;

public class ReviewApprovalPortAdapter implements ReviewApprovalPort {
    private final WhitelistService whitelistService;
    private final AuthmeService authmeService;

    public ReviewApprovalPortAdapter(WhitelistService whitelistService, AuthmeService authmeService) {
        this.whitelistService = whitelistService;
        this.authmeService = authmeService;
    }

    @Override
    public void provisionApprovedUser(String username) {
        whitelistService.add(username);
        if (authmeService != null && authmeService.isAuthmeEnabled()) {
            authmeService.syncApprovedUserToAuthme(username);
        }
    }
}
