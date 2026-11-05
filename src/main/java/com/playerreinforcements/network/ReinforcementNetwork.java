package com.playerreinforcements.network;

import com.playerreinforcements.PlayerReinforcements;
import net.minecraftforge.fml.relauncher.Side;

public final class ReinforcementNetwork {
    private ReinforcementNetwork() {
    }

    public static void register() {
        PlayerReinforcements.NETWORK.registerMessage(StatusMessage.Handler.class, StatusMessage.class, 0, Side.CLIENT);
        PlayerReinforcements.NETWORK.registerMessage(RequestRespawnMessage.Handler.class, RequestRespawnMessage.class, 1, Side.SERVER);
        PlayerReinforcements.NETWORK.registerMessage(RequestStatusMessage.Handler.class, RequestStatusMessage.class, 2, Side.SERVER);
        PlayerReinforcements.NETWORK.registerMessage(MinigameInputMessage.Handler.class, MinigameInputMessage.class, 3, Side.SERVER);
        PlayerReinforcements.NETWORK.registerMessage(MinigameStateMessage.Handler.class, MinigameStateMessage.class, 4, Side.CLIENT);
        PlayerReinforcements.NETWORK.registerMessage(MinigameScoresMessage.Handler.class, MinigameScoresMessage.class, 5, Side.CLIENT);
    }
}
