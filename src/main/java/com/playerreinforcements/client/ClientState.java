package com.playerreinforcements.client;

import com.playerreinforcements.PlayerReinforcements;
import com.playerreinforcements.network.MinigameInputMessage;
import com.playerreinforcements.network.MinigamePanelMessage;
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
    private static boolean minigamesOpen;
    private static boolean minigamesWhileWaiting;
    private static boolean minigameRequested;
    private static MinigameSession minigame;
    private static final MinigameScoresMessage[] highScores = {
            new MinigameScoresMessage(), new MinigameScoresMessage()
    };

    private ClientState() {
    }

    public static void receive(StatusMessage message) {
        Minecraft.getMinecraft().addScheduledTask(() -> {
            if (minigamesOpen && (!message.deathMinigamesEnabled || message.waiting != minigamesWhileWaiting)) {
                clearMinigames();
            }
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
            if (!minigamesOpen || !minigameRequested) return;
            minigame = message.session;
            Minecraft mc = Minecraft.getMinecraft();
            if (minigamesWhileWaiting && mc.currentScreen instanceof ReinforcementDeathScreen) {
                ((ReinforcementDeathScreen) mc.currentScreen).onMinigameSnapshot(minigame);
            } else if (!minigamesWhileWaiting && mc.currentScreen instanceof MinigamesScreen) {
                ((MinigamesScreen) mc.currentScreen).onMinigameSnapshot(minigame);
            }
        });
    }

    static void openMinigames(boolean waiting) {
        if (Minecraft.getMinecraft().getConnection() == null || (minigamesOpen && minigamesWhileWaiting == waiting)) {
            return;
        }
        clearMinigames();
        minigamesOpen = true;
        minigamesWhileWaiting = waiting;
        PlayerReinforcements.NETWORK.sendToServer(new MinigamePanelMessage(true, waiting));
    }

    static void closeMinigames(boolean waiting) {
        if (!minigamesOpen || minigamesWhileWaiting != waiting) return;
        clearMinigames();
        if (Minecraft.getMinecraft().getConnection() != null) {
            PlayerReinforcements.NETWORK.sendToServer(new MinigamePanelMessage(false, waiting));
        }
    }

    static void startMinigame(int type) {
        if (!minigamesOpen || Minecraft.getMinecraft().getConnection() == null) return;
        minigameRequested = true;
        PlayerReinforcements.NETWORK.sendToServer(new MinigameInputMessage(type, 0));
    }

    private static void clearMinigames() {
        minigamesOpen = false;
        minigameRequested = false;
        minigame = null;
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
        clearMinigames();
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
