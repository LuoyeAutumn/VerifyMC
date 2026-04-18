package team.kitemc.verifymc.user;

public record ResetUserPasswordCommand(String operator, String username, String password) {
}
