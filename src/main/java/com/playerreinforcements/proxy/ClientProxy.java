package com.playerreinforcements.proxy;

import com.playerreinforcements.client.ClientEvents;
import com.playerreinforcements.client.ClientState;
import com.playerreinforcements.network.StatusMessage;
import com.playerreinforcements.network.MinigameScoresMessage;
import com.playerreinforcements.network.MinigameStateMessage;
import net.minecraftforge.common.MinecraftForge;

public final class ClientProxy extends CommonProxy {
    @Override
    public void init() {
        MinecraftForge.EVENT_BUS.register(new ClientEvents());
    }

    @Override
    public void receiveStatus(StatusMessage message) {
        ClientState.receive(message);
    }

    @Override
    public void receiveMinigame(MinigameStateMessage message) {
        ClientState.receiveMinigame(message);
    }

    @Override
    public void receiveScores(MinigameScoresMessage message) {
        ClientState.receiveScores(message);
    }
}
