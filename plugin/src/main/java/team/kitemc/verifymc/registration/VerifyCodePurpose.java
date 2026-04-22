package team.kitemc.verifymc.registration;

public enum VerifyCodePurpose {
    REGISTER("register", "Verification for registration"),
    FORGOT_PASSWORD("forgot_password", "Verification for password reset"),
    SMS_REGISTER("sms_register", "SMS verification for registration"),
    SMS_FORGOT_PASSWORD("sms_forgot_password", "SMS verification for password reset");

    private final String key;
    private final String description;

    VerifyCodePurpose(String key, String description) {
        this.key = key;
        this.description = description;
    }

    public String key() {
        return key;
    }

    public String description() {
        return description;
    }
}
