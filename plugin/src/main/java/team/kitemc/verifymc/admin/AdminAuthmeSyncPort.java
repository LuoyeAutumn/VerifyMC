package team.kitemc.verifymc.admin;

public interface AdminAuthmeSyncPort {
    boolean isAvailable();

    boolean isEnabled();

    void syncApprovedUsers();
}
