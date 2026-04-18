package team.kitemc.verifymc.registration;

import team.kitemc.verifymc.integration.AuthmeService;

public class AuthmeRegistrationPortAdapter implements RegistrationAuthPort {
    private final AuthmeService authmeService;

    public AuthmeRegistrationPortAdapter(AuthmeService authmeService) {
        this.authmeService = authmeService;
    }

    @Override
    public boolean isValidPassword(String password) {
        return authmeService != null && authmeService.isValidPassword(password);
    }

    @Override
    public void syncApprovedUser(String username) {
        if (authmeService != null && authmeService.isAuthmeEnabled()) {
            authmeService.syncApprovedUserToAuthme(username);
        }
    }
}
