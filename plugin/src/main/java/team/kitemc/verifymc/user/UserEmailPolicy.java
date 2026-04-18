package team.kitemc.verifymc.user;

import java.util.List;

public interface UserEmailPolicy {
    boolean requiresVerificationForEmailChange();

    boolean isEmailAliasLimitEnabled();

    boolean isEmailDomainWhitelistEnabled();

    List<String> getEmailDomainWhitelist();

    long getMaxAccountsPerEmail();
}
