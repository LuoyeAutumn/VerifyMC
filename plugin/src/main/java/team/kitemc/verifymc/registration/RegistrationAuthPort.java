package team.kitemc.verifymc.registration;

public interface RegistrationAuthPort {
    boolean isValidPassword(String password);

    void syncApprovedUser(String username);
}
