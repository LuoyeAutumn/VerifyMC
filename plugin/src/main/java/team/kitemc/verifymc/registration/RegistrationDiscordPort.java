package team.kitemc.verifymc.registration;

public interface RegistrationDiscordPort {
    boolean isRequired();

    boolean isLinked(String username);
}
