package team.kitemc.verifymc.registration;

public record RegisterUserCommand(
        RegistrationRequest request,
        String requestId
) {
}
