package com.playerreinforcements.proxy;

import com.playerreinforcements.network.StatusMessage;
import com.playerreinforcements.network.MinigameScoresMessage;
import com.playerreinforcements.network.MinigameStateMessage;

public class CommonProxy {
    public void init() { }

    public void receiveStatus(StatusMessage message) { }

    public void receiveMinigame(MinigameStateMessage message) { }

    public void receiveScores(MinigameScoresMessage message) { }
}
