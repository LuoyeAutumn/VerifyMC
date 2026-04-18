package team.kitemc.verifymc.user;

public interface UserAccessSyncPort {
    void removeUserAccess(String username);

    void grantApprovedAccess(String username);

    void syncPasswordChange(String username, String password);
}
