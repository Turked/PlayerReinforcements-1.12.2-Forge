package com.playerreinforcements.client;

import com.playerreinforcements.network.StatusMessage;
import com.playerreinforcements.network.MinigameStateMessage;
import com.playerreinforcements.network.MinigameScoresMessage;
import com.playerreinforcements.minigame.MinigameSession;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGameOver;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public final class ClientState {
    private static StatusMessage status = new StatusMessage();
    private static boolean serverActive;
    private static long clientTicks;
    private static long receivedTick;
    private static int revision;
    private static MinigameSession minigame;
    private static final MinigameScoresMessage[] highScores = {
            new MinigameScoresMessage(), new MinigameScoresMessage()
    };

    private ClientState() {
    }

    public static void receive(StatusMessage message) {
        Minecraft.getMinecraft().addScheduledTask(() -> {
            if (!message.waiting || !status.waiting || !message.deathMinigamesEnabled) minigame = null;
            status = message;
            serverActive = true;
            receivedTick = clientTicks;
            revision++;
            Minecraft mc = Minecraft.getMinecraft();
            if (message.waiting && mc.currentScreen instanceof GuiGameOver) {
                mc.displayGuiScreen(new ReinforcementDeathScreen((GuiGameOver) mc.currentScreen));
            }
        });
    }

    public static void receiveMinigame(MinigameStateMessage message) {
        Minecraft.getMinecraft().addScheduledTask(() -> {
            if (status.waiting && status.deathMinigamesEnabled) {
                minigame = message.session;
                Minecraft mc = Minecraft.getMinecraft();
                if (mc.currentScreen instanceof ReinforcementDeathScreen) {
                    ((ReinforcementDeathScreen) mc.currentScreen).onMinigameSnapshot(minigame);
                }
            }
        });
    }

    public static MinigameSession getMinigame() {
        return minigame;
    }

    public static void receiveScores(MinigameScoresMessage message) {
        Minecraft.getMinecraft().addScheduledTask(() -> highScores[message.game] = message);
    }

    public static MinigameScoresMessage getHighScores(int game) {
        return highScores[game];
    }

    public static void tick() {
        clientTicks++;
        Minecraft mc = Minecraft.getMinecraft();
        if (serverActive && !status.waiting && mc.player != null && mc.player.getHealth() > 0.0F
                && (mc.currentScreen instanceof ReinforcementDeathScreen
                    || mc.currentScreen instanceof ReinforcementDeathScreen.LeaveConfirmation)) {
            mc.displayGuiScreen(null);
        }
    }

    public static void reset() {
        status = new StatusMessage();
        serverActive = false;
        clientTicks = 0;
        receivedTick = 0;
        revision++;
        minigame = null;
        highScores[0] = new MinigameScoresMessage();
        highScores[1] = new MinigameScoresMessage();
    }

    public static boolean isServerActive() {
        return serverActive;
    }

    public static StatusMessage getStatus() {
        return status;
    }

    public static int getRemainingTicks() {
        return (int) Math.max(0L, status.remainingTicks - (clientTicks - receivedTick));
    }

    public static int getRevision() {
        return revision;
    }
}
