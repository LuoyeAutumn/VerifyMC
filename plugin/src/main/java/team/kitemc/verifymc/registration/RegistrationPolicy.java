package team.kitemc.verifymc.registration;

import java.util.List;

public interface RegistrationPolicy {
    String getAuthmePasswordRegex();

    boolean isEmailAliasLimitEnabled();

    boolean isEmailDomainWhitelistEnabled();

    List<String> getEmailDomainWhitelist();

    boolean isUsernameCaseSensitive();

    long getMaxAccountsPerEmail();

    boolean usesCaptchaVerification();

    boolean usesEmailVerification();

    boolean isAutoApprove();
}
