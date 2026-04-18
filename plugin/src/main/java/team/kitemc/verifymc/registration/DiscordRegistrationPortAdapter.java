package team.kitemc.verifymc.registration;

import team.kitemc.verifymc.integration.DiscordService;

public class DiscordRegistrationPortAdapter implements RegistrationDiscordPort {
    private final DiscordService discordService;

    public DiscordRegistrationPortAdapter(DiscordService discordService) {
        this.discordService = discordService;
    }

    @Override
    public boolean isRequired() {
        return discordService != null && discordService.isRequired();
    }

    @Override
    public boolean isLinked(String username) {
        return discordService != null && discordService.isLinked(username);
    }
}
