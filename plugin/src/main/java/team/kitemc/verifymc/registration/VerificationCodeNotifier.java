package team.kitemc.verifymc.registration;

public interface VerificationCodeNotifier {
    boolean sendVerificationCode(String email, String code, String language);
}
