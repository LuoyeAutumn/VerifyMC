package team.kitemc.verifymc.admin;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import team.kitemc.verifymc.platform.PlatformServices;
import team.kitemc.verifymc.registration.UsernameRuleService;
import team.kitemc.verifymc.review.ApproveUserUseCase;
import team.kitemc.verifymc.review.RejectUserUseCase;
import team.kitemc.verifymc.review.ReviewUserCommand;
import team.kitemc.verifymc.review.ReviewUserResult;
import team.kitemc.verifymc.user.AdminUserCommand;
import team.kitemc.verifymc.user.AdminUserResult;
import team.kitemc.verifymc.user.BanUserUseCase;
import team.kitemc.verifymc.user.DeleteUserUseCase;
import team.kitemc.verifymc.user.ListUsersUseCase;
import team.kitemc.verifymc.user.ResetUserPasswordCommand;
import team.kitemc.verifymc.user.ResetUserPasswordUseCase;
import team.kitemc.verifymc.user.UnbanUserUseCase;
import team.kitemc.verifymc.user.UserPage;
import team.kitemc.verifymc.user.UserQuery;
import team.kitemc.verifymc.user.UserRecord;
import team.kitemc.verifymc.user.UserRepository;
import team.kitemc.verifymc.user.UserStatus;
import team.kitemc.verifymc.user.UserSummary;

public class VmcCommandExecutor implements CommandExecutor, TabCompleter {
    private static final String BASE_PERMISSION = "verifymc.use";
    private static final java.util.Map<String, AdminAction> SUBCOMMAND_ACTIONS = java.util.Map.of(
            "reload", AdminAction.RELOAD,
            "approve", AdminAction.APPROVE,
            "reject", AdminAction.REJECT,
            "delete", AdminAction.DELETE,
            "ban", AdminAction.BAN,
            "unban", AdminAction.UNBAN,
            "password", AdminAction.PASSWORD,
            "list", AdminAction.LIST,
            "info", AdminAction.INFO
    );
    private static final List<String> SUBCOMMANDS = Arrays.asList(
            "reload", "approve", "reject", "delete", "ban", "unban", "password", "list", "info", "version"
    );

    private final PlatformServices platform;
    private final AdminAccessManager adminAccessManager;
    private final UsernameRuleService usernameRuleService;
    private final UserRepository userRepository;
    private final ApproveUserUseCase approveUserUseCase;
    private final RejectUserUseCase rejectUserUseCase;
    private final DeleteUserUseCase deleteUserUseCase;
    private final BanUserUseCase banUserUseCase;
    private final UnbanUserUseCase unbanUserUseCase;
    private final ResetUserPasswordUseCase resetUserPasswordUseCase;
    private final ListUsersUseCase listUsersUseCase;

    public VmcCommandExecutor(
            PlatformServices platform,
            AdminAccessManager adminAccessManager,
            UsernameRuleService usernameRuleService,
            UserRepository userRepository,
            ApproveUserUseCase approveUserUseCase,
            RejectUserUseCase rejectUserUseCase,
            DeleteUserUseCase deleteUserUseCase,
            BanUserUseCase banUserUseCase,
            UnbanUserUseCase unbanUserUseCase,
            ResetUserPasswordUseCase resetUserPasswordUseCase,
            ListUsersUseCase listUsersUseCase
    ) {
        this.platform = platform;
        this.adminAccessManager = adminAccessManager;
        this.usernameRuleService = usernameRuleService;
        this.userRepository = userRepository;
        this.approveUserUseCase = approveUserUseCase;
        this.rejectUserUseCase = rejectUserUseCase;
        this.deleteUserUseCase = deleteUserUseCase;
        this.banUserUseCase = banUserUseCase;
        this.unbanUserUseCase = unbanUserUseCase;
        this.resetUserPasswordUseCase = resetUserPasswordUseCase;
        this.listUsersUseCase = listUsersUseCase;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            List<String> availableSubcommands = getAvailableSubcommands(sender);
            if (availableSubcommands.isEmpty()) {
                sendNoPermission(sender);
                return true;
            }

            sendInfo(sender, message("command.usage", "{subcommands}", String.join("|", availableSubcommands)));
            return true;
        }

        String sub = args[0].toLowerCase();
        if (!SUBCOMMANDS.contains(sub)) {
            sendError(sender, message("command.unknown_subcommand", "{subcommand}", sub));
            return true;
        }
        if (!hasPermissionForSubcommand(sender, sub)) {
            sendNoPermission(sender);
            return true;
        }

        switch (sub) {
            case "reload" -> handleReload(sender);
            case "approve" -> handleApprove(sender, args);
            case "reject" -> handleReject(sender, args);
            case "delete" -> handleDelete(sender, args);
            case "ban" -> handleBan(sender, args);
            case "unban" -> handleUnban(sender, args);
            case "password" -> handlePassword(sender, args);
            case "list" -> handleList(sender, args);
            case "info" -> handleInfo(sender, args);
            case "version" -> handleVersion(sender);
            default -> {
            }
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return SUBCOMMANDS.stream()
                    .filter(sub -> hasPermissionForSubcommand(sender, sub))
                    .filter(sub -> sub.startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }
        if (args.length == 2) {
            String sub = args[0].toLowerCase();
            if (!hasPermissionForSubcommand(sender, sub)) {
                return List.of();
            }
            if (List.of("approve", "reject", "delete", "ban", "unban", "password", "info").contains(sub)) {
                return userRepository.suggestUsernames(args[1], 20);
            }
            if ("list".equals(sub)) {
                return Arrays.asList("all", "pending", "approved", "rejected", "banned");
            }
        }
        return List.of();
    }

    private void handleReload(CommandSender sender) {
        platform.getConfigManager().reloadConfig();
        platform.getI18nManager().clearCache();
        platform.getI18nManager().init(platform.getConfigManager().getLanguage());
        sendSuccess(sender, message("command.reload_success"));
    }

    private void handleApprove(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sendInfo(sender, message("command.approve_usage"));
            return;
        }
        String target = args[1];
        if (!usernameRuleService.canOperateAdminTarget(target, userRepository)) {
            sendError(sender, message("command.invalid_username", "{username}", target));
            return;
        }
        ReviewUserResult result = approveUserUseCase.execute(new ReviewUserCommand(sender.getName(), target, ""));
        sendResult(sender, result.success(), message(result.messageKey()));
    }

    private void handleReject(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sendInfo(sender, message("command.reject_usage"));
            return;
        }
        String target = args[1];
        String reason = args.length > 2 ? String.join(" ", Arrays.copyOfRange(args, 2, args.length)) : "";
        ReviewUserResult result = rejectUserUseCase.execute(new ReviewUserCommand(sender.getName(), target, reason));
        sendResult(sender, result.success(), message(result.messageKey()));
    }

    private void handleDelete(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sendInfo(sender, message("command.delete_usage"));
            return;
        }
        String target = args[1];
        if (!usernameRuleService.canOperateAdminTarget(target, userRepository)) {
            sendError(sender, message("command.invalid_username", "{username}", target));
            return;
        }
        AdminUserResult result = deleteUserUseCase.execute(new AdminUserCommand(sender.getName(), target, ""));
        sendResult(sender, result.success(), message(result.messageKey()));
    }

    private void handleBan(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sendInfo(sender, message("command.ban_usage"));
            return;
        }
        String target = args[1];
        String reason = args.length > 2 ? String.join(" ", Arrays.copyOfRange(args, 2, args.length)) : "";
        AdminUserResult result = banUserUseCase.execute(new AdminUserCommand(sender.getName(), target, reason));
        sendResult(sender, result.success(), message(result.messageKey()));
    }

    private void handleUnban(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sendInfo(sender, message("command.unban_usage"));
            return;
        }
        String target = args[1];
        AdminUserResult result = unbanUserUseCase.execute(new AdminUserCommand(sender.getName(), target, ""));
        sendResult(sender, result.success(), message(result.messageKey()));
    }

    private void handlePassword(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sendInfo(sender, message("command.password_usage"));
            return;
        }
        String target = args[1];
        String password = String.join(" ", Arrays.copyOfRange(args, 2, args.length));
        AdminUserResult result = resetUserPasswordUseCase.execute(
                new ResetUserPasswordCommand(sender.getName(), target, password)
        );
        sendResult(sender, result.success(), message(result.messageKey()));
    }

    private void handleList(CommandSender sender, String[] args) {
        String statusFilter = args.length > 1 ? args[1].toLowerCase() : "all";
        List<UserSummary> users;
        if ("all".equals(statusFilter)) {
            UserPage<UserSummary> page = listUsersUseCase.execute(new UserQuery(1, Integer.MAX_VALUE, "", null));
            users = page.items();
        } else {
            UserStatus status = UserStatus.fromValue(statusFilter).orElse(null);
            users = status == null ? List.of() : userRepository.findUsersByStatus(status);
        }

        sendInfo(sender, message("command.list_title", "{status}", statusFilter));
        if (users.isEmpty()) {
            sendSecondary(sender, message("command.list_empty"));
            return;
        }

        for (UserSummary user : users) {
            sender.sendMessage("§6[VerifyMC] §7  " + user.username() + " §f- §e" + user.status().value());
        }
    }

    private void handleInfo(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sendInfo(sender, message("command.info_usage"));
            return;
        }
        String target = args[1];
        UserRecord user = userRepository.findByUsername(target).orElse(null);
        if (user == null) {
            sendError(sender, message("command.info_not_found", "{username}", target));
            return;
        }

        sendInfo(sender, message("command.info_title"));
        sendSecondary(sender, message("command.info_username", "{username}", user.username()));
        sendSecondary(sender, message("command.info_email", "{email}", user.email() == null ? "?" : user.email()));
        sendSecondary(sender, message("command.info_status", "{status}", user.status().value()));
    }

    private void handleVersion(CommandSender sender) {
        sendInfo(sender, message("command.version", "{version}", platform.getPlugin().getDescription().getVersion()));
    }

    private List<String> getAvailableSubcommands(CommandSender sender) {
        return SUBCOMMANDS.stream()
                .filter(subcommand -> hasPermissionForSubcommand(sender, subcommand))
                .collect(Collectors.toList());
    }

    private boolean hasPermissionForSubcommand(CommandSender sender, String subcommand) {
        AdminAction action = SUBCOMMAND_ACTIONS.get(subcommand);
        if (action == null) {
            return sender.hasPermission(BASE_PERMISSION) || adminAccessManager.hasAnyAdminAccess(sender);
        }
        return adminAccessManager.canAccess(sender, action);
    }

    private void sendNoPermission(CommandSender sender) {
        sendError(sender, message("command.no_permission"));
    }

    private String message(String key, String... replacements) {
        String resolved = platform.getMessage(key, platform.getConfigManager().getLanguage());
        for (int i = 0; i + 1 < replacements.length; i += 2) {
            resolved = resolved.replace(replacements[i], replacements[i + 1]);
        }
        return resolved;
    }

    private void sendResult(CommandSender sender, boolean success, String message) {
        if (success) {
            sendSuccess(sender, message);
        } else {
            sendError(sender, message);
        }
    }

    private void sendInfo(CommandSender sender, String message) {
        sender.sendMessage("§6[VerifyMC] §f" + message);
    }

    private void sendSuccess(CommandSender sender, String message) {
        sender.sendMessage("§6[VerifyMC] §a" + message);
    }

    private void sendError(CommandSender sender, String message) {
        sender.sendMessage("§6[VerifyMC] §c" + message);
    }

    private void sendSecondary(CommandSender sender, String message) {
        sender.sendMessage("§6[VerifyMC] §7" + message);
    }
}
