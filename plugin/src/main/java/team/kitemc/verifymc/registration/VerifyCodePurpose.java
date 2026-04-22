package team.kitemc.verifymc.registration;

public enum VerifyCodePurpose {
    REGISTER("register"),
    FORGOT_PASSWORD("forgot_password");

    private final String key;

    VerifyCodePurpose(String key) {
        this.key = key;
    }

    public String key() {
        return key;
    }
}
