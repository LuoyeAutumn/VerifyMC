package team.kitemc.verifymc.user;

public class ListUsersUseCase {
    private final UserRepository userRepository;

    public ListUsersUseCase(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public UserPage<UserSummary> execute(UserQuery query) {
        return userRepository.findUsers(query);
    }
}
