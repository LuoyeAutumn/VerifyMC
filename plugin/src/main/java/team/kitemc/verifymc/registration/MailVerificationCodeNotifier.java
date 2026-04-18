package team.kitemc.verifymc.registration;

import team.kitemc.verifymc.integration.MailService;

public class MailVerificationCodeNotifier implements VerificationCodeNotifier {
    private final MailService mailService;

    public MailVerificationCodeNotifier(MailService mailService) {
        this.mailService = mailService;
    }

    @Override
    public boolean sendVerificationCode(String email, String code, String language) {
        return mailService != null && mailService.sendVerificationCode(email, code, language);
    }
}
