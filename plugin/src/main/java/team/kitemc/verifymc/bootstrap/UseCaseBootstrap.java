package team.kitemc.verifymc.bootstrap;

import team.kitemc.verifymc.registration.AuthmeRegistrationPortAdapter;
import team.kitemc.verifymc.registration.ConfigRegistrationPolicy;
import team.kitemc.verifymc.registration.DiscordRegistrationPortAdapter;
import team.kitemc.verifymc.registration.InMemoryQuestionnaireSubmissionStore;
import team.kitemc.verifymc.registration.MailVerificationCodeNotifier;
import team.kitemc.verifymc.registration.RegisterUserUseCase;
import team.kitemc.verifymc.registration.RegistrationAuthPort;
import team.kitemc.verifymc.registration.RegistrationDiscordPort;
import team.kitemc.verifymc.registration.RegistrationPolicy;
import team.kitemc.verifymc.review.ApproveUserUseCase;
import team.kitemc.verifymc.review.MailReviewNotificationPortAdapter;
import team.kitemc.verifymc.review.RejectUserUseCase;
import team.kitemc.verifymc.review.ReviewApprovalPort;
import team.kitemc.verifymc.review.ReviewApprovalPortAdapter;
import team.kitemc.verifymc.review.ReviewEventPublisher;
import team.kitemc.verifymc.review.ReviewLanguageProvider;
import team.kitemc.verifymc.review.ReviewNotificationPort;
import team.kitemc.verifymc.review.WebSocketReviewEventPublisher;
import team.kitemc.verifymc.user.BanUserUseCase;
import team.kitemc.verifymc.user.ConfigUserEmailPolicy;
import team.kitemc.verifymc.user.DeleteUserUseCase;
import team.kitemc.verifymc.user.ForgotPasswordResetUseCase;
import team.kitemc.verifymc.user.ListUsersUseCase;
import team.kitemc.verifymc.user.ResetUserPasswordUseCase;
import team.kitemc.verifymc.user.SendForgotPasswordCodeUseCase;
import team.kitemc.verifymc.user.SendUserPasswordCodeUseCase;
import team.kitemc.verifymc.user.UnbanUserUseCase;
import team.kitemc.verifymc.user.UpdateEmailUseCase;
import team.kitemc.verifymc.user.UserAccessSyncPort;
import team.kitemc.verifymc.user.UserAccessSyncPortAdapter;
import team.kitemc.verifymc.user.UserEmailPolicy;
import team.kitemc.verifymc.user.UserRepository;

public class UseCaseBootstrap {
    public Result bootstrap(
            PlatformBootstrap.Result platformModule,
            RepositoryBootstrap.Result repositoryModule,
            IntegrationBootstrap.Result integrationModule
    ) {
        RegistrationPolicy registrationPolicy = new ConfigRegistrationPolicy(platformModule.platform().getConfigManager());
        RegistrationAuthPort registrationAuthPort = new AuthmeRegistrationPortAdapter(integrationModule.authmeService());
        RegistrationDiscordPort registrationDiscordPort = new DiscordRegistrationPortAdapter(integrationModule.discordService());
        ReviewNotificationPort reviewNotificationPort = new MailReviewNotificationPortAdapter(integrationModule.mailService());
        ReviewApprovalPort reviewApprovalPort = new ReviewApprovalPortAdapter(
                integrationModule.whitelistService(),
                integrationModule.authmeService()
        );
        ReviewEventPublisher reviewEventPublisher = new WebSocketReviewEventPublisher(integrationModule.wsServer());
        ReviewLanguageProvider reviewLanguageProvider = platformModule.platform().getConfigManager()::getLanguage;
        UserAccessSyncPort userAccessSyncPort = new UserAccessSyncPortAdapter(
                integrationModule.whitelistService(),
                integrationModule.authmeService()
        );
        UserEmailPolicy userEmailPolicy = new ConfigUserEmailPolicy(platformModule.platform().getConfigManager());
        UserRepository userRepository = repositoryModule.userRepository();
        MailVerificationCodeNotifier mailVerificationCodeNotifier = new MailVerificationCodeNotifier(integrationModule.mailService());

        RegisterUserUseCase registerUserUseCase = new RegisterUserUseCase(
                registrationPolicy,
                integrationModule.verifyCodeService(),
                userRepository,
                registrationAuthPort,
                integrationModule.captchaService(),
                integrationModule.questionnaireService(),
                registrationDiscordPort,
                integrationModule.whitelistService(),
                platformModule.usernameRuleService(),
                new InMemoryQuestionnaireSubmissionStore(integrationModule.questionnaireSubmissionStore()),
                platformModule.platform()::debugLog
        );
        ApproveUserUseCase approveUserUseCase = new ApproveUserUseCase(
                userRepository,
                repositoryModule.auditService(),
                reviewNotificationPort,
                reviewApprovalPort,
                reviewEventPublisher,
                reviewLanguageProvider
        );
        RejectUserUseCase rejectUserUseCase = new RejectUserUseCase(
                userRepository,
                repositoryModule.auditService(),
                reviewNotificationPort,
                reviewEventPublisher,
                reviewLanguageProvider
        );
        DeleteUserUseCase deleteUserUseCase = new DeleteUserUseCase(
                userRepository,
                repositoryModule.auditService(),
                userAccessSyncPort
        );
        BanUserUseCase banUserUseCase = new BanUserUseCase(
                userRepository,
                repositoryModule.auditService(),
                userAccessSyncPort
        );
        UnbanUserUseCase unbanUserUseCase = new UnbanUserUseCase(
                userRepository,
                repositoryModule.auditService(),
                userAccessSyncPort
        );
        ResetUserPasswordUseCase resetUserPasswordUseCase = new ResetUserPasswordUseCase(
                userRepository,
                repositoryModule.auditService(),
                userAccessSyncPort
        );
        UpdateEmailUseCase updateEmailUseCase = new UpdateEmailUseCase(
                userEmailPolicy,
                userRepository,
                repositoryModule.auditService()
        );
        ListUsersUseCase listUsersUseCase = new ListUsersUseCase(userRepository);
        SendUserPasswordCodeUseCase sendUserPasswordCodeUseCase = new SendUserPasswordCodeUseCase(
                userRepository,
                integrationModule.verifyCodeService(),
                mailVerificationCodeNotifier
        );
        SendForgotPasswordCodeUseCase sendForgotPasswordCodeUseCase = new SendForgotPasswordCodeUseCase(
                userRepository,
                integrationModule.verifyCodeService(),
                mailVerificationCodeNotifier
        );
        ForgotPasswordResetUseCase forgotPasswordResetUseCase = new ForgotPasswordResetUseCase(
                userRepository,
                integrationModule.verifyCodeService(),
                new team.kitemc.verifymc.user.ConfigUserPasswordPolicy(platformModule.platform().getConfigManager()),
                userAccessSyncPort,
                repositoryModule.auditService()
        );

        return new Result(
                registerUserUseCase,
                approveUserUseCase,
                rejectUserUseCase,
                deleteUserUseCase,
                banUserUseCase,
                unbanUserUseCase,
                resetUserPasswordUseCase,
                updateEmailUseCase,
                listUsersUseCase,
                sendUserPasswordCodeUseCase,
                sendForgotPasswordCodeUseCase,
                forgotPasswordResetUseCase
        );
    }

    public record Result(
            RegisterUserUseCase registerUserUseCase,
            ApproveUserUseCase approveUserUseCase,
            RejectUserUseCase rejectUserUseCase,
            DeleteUserUseCase deleteUserUseCase,
            BanUserUseCase banUserUseCase,
            UnbanUserUseCase unbanUserUseCase,
            ResetUserPasswordUseCase resetUserPasswordUseCase,
            UpdateEmailUseCase updateEmailUseCase,
            ListUsersUseCase listUsersUseCase,
            SendUserPasswordCodeUseCase sendUserPasswordCodeUseCase,
            SendForgotPasswordCodeUseCase sendForgotPasswordCodeUseCase,
            ForgotPasswordResetUseCase forgotPasswordResetUseCase
    ) {
    }
}
