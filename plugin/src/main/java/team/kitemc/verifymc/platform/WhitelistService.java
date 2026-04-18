package team.kitemc.verifymc.platform;

public interface WhitelistService {
    void add(String username);

    void remove(String username);
}
