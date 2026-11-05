package com.playerreinforcements.config;

import net.minecraftforge.common.config.Configuration;
import java.io.File;

/** Server-controlled RLCraft defaults and supported testing/death-screen options. */
public final class ReinforcementsConfig {
    private static boolean showTeammateDimensions;
    private static boolean testDummies = false;
    private static boolean enableDeathMinigames = true;
    private static boolean minigameReducesRespawnTimer;

    private ReinforcementsConfig() { }

    public static void load(File file) {
        Configuration config = new Configuration(file);
        config.load();
        boolean spectate = config.getBoolean("enableSpectate", "general", false, "Not implemented yet. Default: false.");
        boolean stratagems = config.getBoolean("enableStratagems", "general", false, "Not implemented yet. Default: false.");
        boolean timer = config.getBoolean("enableRespawnTimer", "general", true, "Default: true. Other values are not implemented yet.");
        boolean spawnpoint = config.getBoolean("enableRespawningOnSpawnpoint", "general", false, "Not implemented yet. Default: false.");
        boolean dimensions = config.getBoolean("showTeammateDimensions", "general", false,
                "Show teammate dimensions on the death screen. Server-controlled; default: false.");
        boolean dummies = config.getBoolean("testDummies", "general", false,
                "Test dummies: spawn three attackable fake players for solo testing: stationary, follower, and random runner. Default: false. Restart the server after changing.");
        boolean minigames = config.getBoolean("enableDeathMinigames", "general", true,
                "Offer Flappy Cart and Minecraft-themed Tetris beside the death screen while waiting to respawn. Default: true. Restart the server after changing.");
        boolean reduction = config.getBoolean("minigameReducesRespawnTimer", "general", false,
                "Each server-verified minigame point removes 10 seconds from the player's remaining respawn timer. Requires enableDeathMinigames. Default: false. Restart the server after changing.");
        String mode = config.getString("respawnMode", "general", "GROUP_RESPAWN", "Only GROUP_RESPAWN is implemented.");
        int duration = config.get("general", "respawnCooldown", 2, "Default: 2. Other values are not implemented yet.").getInt();
        String unit = config.getString("respawnCooldownUnit", "general", "MINUTES", "Only the default MINUTES with duration 2 is implemented.");
        if (config.hasChanged()) {
            config.save();
        }
        validate(spectate, stratagems, timer, spawnpoint, mode, duration, unit);
        showTeammateDimensions = dimensions;
        testDummies = dummies;
        enableDeathMinigames = minigames;
        minigameReducesRespawnTimer = reduction;
    }

    public static boolean showTeammateDimensions() {
        return showTeammateDimensions;
    }

    public static boolean testDummies() {
        return testDummies;
    }

    public static boolean enableDeathMinigames() {
        return enableDeathMinigames;
    }

    public static boolean minigameReducesRespawnTimer() {
        return minigameReducesRespawnTimer;
    }

    public static void validate(boolean spectate, boolean stratagems, boolean timer, boolean spawnpoint,
                                String mode, int duration, String unit) {
        requireDefault(!spectate, "enableSpectate");
        requireDefault(!stratagems, "enableStratagems");
        requireDefault(timer, "enableRespawnTimer");
        requireDefault(!spawnpoint, "enableRespawningOnSpawnpoint");
        requireDefault("GROUP_RESPAWN".equals(mode), "respawnMode");
        requireDefault(duration == 2, "respawnCooldown");
        requireDefault("MINUTES".equals(unit), "respawnCooldownUnit");
    }

    private static void requireDefault(boolean valid, String setting) {
        if (!valid) {
            throw new IllegalArgumentException("error - not implemented yet: " + setting);
        }
    }
}
