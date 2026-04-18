package team.kitemc.verifymc.registration;

import java.util.function.Consumer;
import org.json.JSONArray;
import org.json.JSONObject;
import team.kitemc.verifymc.platform.WhitelistService;
import team.kitemc.verifymc.questionnaire.QuestionnaireService;
import team.kitemc.verifymc.shared.EmailAddressUtil;
import team.kitemc.verifymc.user.NewUserRecord;
import team.kitemc.verifymc.user.UserRepository;
import team.kitemc.verifymc.user.UserStatus;

public class RegisterUserUseCase {
    private final RegistrationPolicy registrationPolicy;
    private final VerifyCodeService codeService;
    private final UserRepository userRepository;
    private final RegistrationAuthPort registrationAuthPort;
    private final CaptchaService captchaService;
    private final QuestionnaireService questionnaireService;
    private final RegistrationDiscordPort registrationDiscordPort;
    private final WhitelistService whitelistService;
    private final UsernameRuleService usernameRules;
    private final QuestionnaireSubmissionStore questionnaireSubmissionStore;
    private final Consumer<String> debugLogger;
    private final RegistrationOutcomeResolver outcomeResolver = new RegistrationOutcomeResolver();
    private final RegistrationOutcomeMessageKeyMapper messageKeyMapper = new RegistrationOutcomeMessageKeyMapper();

    public RegisterUserUseCase(
            RegistrationPolicy registrationPolicy,
            VerifyCodeService codeService,
            UserRepository userRepository,
            RegistrationAuthPort registrationAuthPort,
            CaptchaService captchaService,
            QuestionnaireService questionnaireService,
            RegistrationDiscordPort registrationDiscordPort,
            WhitelistService whitelistService,
            UsernameRuleService usernameRules,
            QuestionnaireSubmissionStore questionnaireSubmissionStore,
            Consumer<String> debugLogger
    ) {
        this.registrationPolicy = registrationPolicy;
        this.codeService = codeService;
        this.userRepository = userRepository;
        this.registrationAuthPort = registrationAuthPort;
        this.captchaService = captchaService;
        this.questionnaireService = questionnaireService;
        this.registrationDiscordPort = registrationDiscordPort;
        this.whitelistService = whitelistService;
        this.usernameRules = usernameRules;
        this.questionnaireSubmissionStore = questionnaireSubmissionStore;
        this.debugLogger = debugLogger;
    }

    public RegisterUserResult execute(RegisterUserCommand command) {
        RegistrationRequest request = command.request();
        String requestId = command.requestId();

        logRegistrationStage(requestId, "start", null);

        RegistrationValidationResult basicResult = validateBasicInput(request, requestId);
        if (!basicResult.passed()) {
            return RegisterUserResult.validationFailure(basicResult.messageKey(), basicResult.responseFields());
        }

        QuestionnaireValidationOutcome questionnaireOutcome = validateQuestionnaireSubmission(request, requestId);
        if (questionnaireOutcome.error() != null) {
            RegistrationValidationResult error = questionnaireOutcome.error();
            return RegisterUserResult.validationFailure(error.messageKey(), error.responseFields());
        }

        RegistrationValidationResult verificationResult = validateVerificationMethod(request, requestId);
        if (!verificationResult.passed()) {
            return RegisterUserResult.validationFailure(verificationResult.messageKey(), verificationResult.responseFields());
        }

        RegistrationValidationResult discordResult = validateDiscordRequirement(request, requestId);
        if (!discordResult.passed()) {
            return RegisterUserResult.validationFailure(discordResult.messageKey(), discordResult.responseFields());
        }

        return executeRegistration(request, questionnaireOutcome.record(), requestId);
    }

    private RegistrationValidationResult validateBasicInput(RegistrationRequest request, String requestId) {
        logRegistrationStage(requestId, "validate_basic_input", null);

        if (request.normalizedUsername() == null || request.normalizedUsername().trim().isEmpty()) {
            return RegistrationValidationResult.reject("register.invalid_username");
        }
        if (!EmailAddressUtil.isValid(request.email())) {
            return RegistrationValidationResult.reject("register.invalid_email");
        }
        if (!usernameRules.isValid(request.normalizedUsername(), request.platform())) {
            return RegistrationValidationResult.reject(
                    "username.invalid",
                    new JSONObject().put("regex", usernameRules.getUnifiedRegex())
            );
        }
        if (request.password() == null || request.password().trim().isEmpty()) {
            return RegistrationValidationResult.reject("register.password_required");
        }
        if (!registrationAuthPort.isValidPassword(request.password())) {
            return RegistrationValidationResult.reject(
                    "register.invalid_password",
                    new JSONObject().put("regex", registrationPolicy.getAuthmePasswordRegex())
            );
        }
        if (registrationPolicy.isEmailAliasLimitEnabled() && EmailAddressUtil.hasAlias(request.email())) {
            return RegistrationValidationResult.reject("register.alias_not_allowed");
        }
        if (registrationPolicy.isEmailDomainWhitelistEnabled()) {
            String domain = EmailAddressUtil.extractDomain(request.email());
            if (!registrationPolicy.getEmailDomainWhitelist().contains(domain)) {
                return RegistrationValidationResult.reject("register.domain_not_allowed");
            }
        }

        if (registrationPolicy.isUsernameCaseSensitive()) {
            if (userRepository.findByUsernameExact(request.normalizedUsername()).isPresent()) {
                return RegistrationValidationResult.reject("register.username_exists");
            }
        } else {
            var caseInsensitiveUser = userRepository.findByUsernameIgnoreCase(request.normalizedUsername());
            if (caseInsensitiveUser.isPresent()) {
                return RegistrationValidationResult.reject("username.case_conflict");
            }
        }

        if (userRepository.countByEmail(request.email()) >= registrationPolicy.getMaxAccountsPerEmail()) {
            return RegistrationValidationResult.reject("register.email_limit");
        }
        return RegistrationValidationResult.pass();
    }

    private QuestionnaireValidationOutcome validateQuestionnaireSubmission(RegistrationRequest request, String requestId) {
        logRegistrationStage(requestId, "validate_questionnaire_submission", null);
        if (!questionnaireService.isEnabled()) {
            return new QuestionnaireValidationOutcome(null, null);
        }

        JSONObject questionnaire = request.questionnaire();
        if (questionnaire == null) {
            return new QuestionnaireValidationOutcome(null, RegistrationValidationResult.reject("register.questionnaire_required"));
        }

        String questionnaireToken = questionnaire.optString("token", "");
        long submittedAt = questionnaire.optLong("submittedAt", questionnaire.optLong("submitted_at", 0L));
        long expiresAt = questionnaire.optLong("expiresAt", questionnaire.optLong("expires_at", 0L));
        JSONObject answers = questionnaire.optJSONObject("answers");
        if (questionnaireToken.isEmpty() || answers == null) {
            return new QuestionnaireValidationOutcome(null, RegistrationValidationResult.reject("register.questionnaire_required"));
        }

        QuestionnaireSubmissionRecord record = questionnaireSubmissionStore.take(questionnaireToken);
        if (record == null) {
            return new QuestionnaireValidationOutcome(null, RegistrationValidationResult.reject("register.questionnaire_missing"));
        }
        if (record.isExpired() || System.currentTimeMillis() > expiresAt || submittedAt <= 0 || expiresAt <= submittedAt) {
            return new QuestionnaireValidationOutcome(null, RegistrationValidationResult.reject("register.questionnaire_expired"));
        }
        if (!record.answers().similar(answers) || record.submittedAt() != submittedAt || record.expiresAt() != expiresAt) {
            return new QuestionnaireValidationOutcome(null, RegistrationValidationResult.reject("register.questionnaire_invalid"));
        }
        if (!record.passed() && !record.manualReviewRequired()) {
            return new QuestionnaireValidationOutcome(null, RegistrationValidationResult.reject("register.questionnaire_required"));
        }
        return new QuestionnaireValidationOutcome(record, null);
    }

    private RegistrationValidationResult validateVerificationMethod(RegistrationRequest request, String requestId) {
        logRegistrationStage(requestId, "validate_verification_method", null);

        boolean useCaptcha = registrationPolicy.usesCaptchaVerification();
        boolean useEmail = registrationPolicy.usesEmailVerification();
        if (!useCaptcha && !useEmail) {
            return RegistrationValidationResult.pass();
        }
        if (useCaptcha) {
            if (request.captchaToken().isEmpty() || request.captchaAnswer().isEmpty()) {
                return RegistrationValidationResult.reject("captcha.required");
            }
            if (!captchaService.validateCaptcha(request.captchaToken(), request.captchaAnswer())) {
                return RegistrationValidationResult.reject("captcha.invalid");
            }
        }
        if (useEmail && !codeService.checkCode(request.email(), request.code())) {
            return RegistrationValidationResult.reject("verify.wrong_code");
        }
        return RegistrationValidationResult.pass();
    }

    private RegistrationValidationResult validateDiscordRequirement(RegistrationRequest request, String requestId) {
        logRegistrationStage(requestId, "validate_discord_requirement", null);
        if (registrationDiscordPort.isRequired() && !registrationDiscordPort.isLinked(request.normalizedUsername())) {
            return RegistrationValidationResult.reject(
                    "discord.required",
                    new JSONObject().put("discordRequired", true)
            );
        }
        return RegistrationValidationResult.pass();
    }

    private RegisterUserResult executeRegistration(
            RegistrationRequest request,
            QuestionnaireSubmissionRecord submissionRecord,
            String requestId
    ) {
        logRegistrationStage(requestId, "execute_registration", null);

        boolean manualReviewRequired = submissionRecord != null && submissionRecord.manualReviewRequired();
        boolean questionnairePassed = submissionRecord != null && submissionRecord.passed();
        boolean scoringServiceUnavailable = submissionRecord != null && submissionRecord.scoringServiceUnavailable();
        boolean registerAutoApprove = registrationPolicy.isAutoApprove();

        RegistrationOutcome provisionalOutcome = outcomeResolver.resolve(
                true,
                manualReviewRequired,
                questionnairePassed,
                registerAutoApprove,
                scoringServiceUnavailable
        );
        UserStatus status = provisionalOutcome == RegistrationOutcome.SUCCESS_WHITELISTED
                ? UserStatus.APPROVED
                : UserStatus.PENDING;

        NewUserRecord newUser = new NewUserRecord(
                request.normalizedUsername(),
                request.email(),
                status,
                null,
                request.password(),
                false,
                submissionRecord != null ? submissionRecord.score() : null,
                submissionRecord != null ? submissionRecord.passed() : null,
                submissionRecord != null ? buildQuestionnaireReviewSummary(submissionRecord.details()) : null,
                submissionRecord != null ? submissionRecord.submittedAt() : null
        );
        boolean created = userRepository.create(newUser);
        RegistrationOutcome outcome = outcomeResolver.resolve(
                created,
                manualReviewRequired,
                questionnairePassed,
                registerAutoApprove,
                scoringServiceUnavailable
        );

        if (created && outcome == RegistrationOutcome.SUCCESS_WHITELISTED) {
            whitelistService.add(request.normalizedUsername());
            registrationAuthPort.syncApprovedUser(request.normalizedUsername());
        }

        return RegisterUserResult.outcome(created, outcome, messageKeyMapper.toMessageKey(outcome));
    }

    private String buildQuestionnaireReviewSummary(JSONArray details) {
        if (details == null || details.isEmpty()) {
            return null;
        }
        java.util.List<String> parts = new java.util.ArrayList<>();
        for (int i = 0; i < details.length(); i++) {
            JSONObject detail = details.optJSONObject(i);
            if (detail == null || !"text".equalsIgnoreCase(detail.optString("type", ""))) {
                continue;
            }
            int questionId = detail.optInt("questionId", detail.optInt("question_id", -1));
            int score = detail.optInt("score", 0);
            int maxScore = detail.optInt("maxScore", detail.optInt("max_score", 0));
            String reason = detail.optString("reason", "").trim();
            if (reason.isEmpty()) {
                reason = "N/A";
            }
            parts.add("Q" + questionId + "(" + score + "/" + maxScore + "): " + reason);
        }
        return parts.isEmpty() ? null : String.join(" | ", parts);
    }

    private void logRegistrationStage(String requestId, String stage, JSONObject extra) {
        if (debugLogger == null) {
            return;
        }
        JSONObject payload = new JSONObject();
        payload.put("requestId", requestId);
        payload.put("stage", stage);
        if (extra != null) {
            payload.put("extra", extra);
        }
        debugLogger.accept("registration_stage=" + payload);
    }

    private record QuestionnaireValidationOutcome(
            QuestionnaireSubmissionRecord record,
            RegistrationValidationResult error
    ) {
    }
}
