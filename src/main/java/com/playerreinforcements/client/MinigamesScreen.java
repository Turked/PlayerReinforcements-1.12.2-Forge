package com.playerreinforcements.client;

import com.playerreinforcements.PlayerReinforcements;
import com.playerreinforcements.minigame.MinigameSession;
import com.playerreinforcements.network.MinigameInputMessage;
import com.playerreinforcements.network.MinigameScoresMessage;
import java.io.IOException;
import net.minecraft.client.audio.ISound;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.resources.I18n;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.lwjgl.input.Keyboard;

@SideOnly(Side.CLIENT)
public final class MinigamesScreen extends GuiScreen {
    private static final int BACK = 0;
    private static final int PANEL_GAP = 8;
    private final GuiScreen parent;
    private final MinigameAudio audio = new MinigameAudio();
    private final MinigamePanel panel = new MinigamePanel(new MinigamePanel.Controls() {
        @Override
        public void start(int type) {
            audio.startRequested();
            ClientState.startMinigame(type);
        }

        @Override
        public void input(int controls) {
            if (mc.getConnection() == null) return;
            audio.input(mc, ClientState.getMinigame(), controls);
            PlayerReinforcements.NETWORK.sendToServer(new MinigameInputMessage(-1, controls));
        }

        @Override
        public void tabClicked() {
            audio.click(mc);
        }
    });
    private int titleX;
    private boolean opened;

    MinigamesScreen(GuiScreen parent) {
        this.parent = parent;
    }

    static boolean isAvailable() {
        return ClientState.isServerActive() && ClientState.getStatus().deathMinigamesEnabled
                && !ClientState.getStatus().waiting;
    }

    static String title() {
        String key = "playerreinforcements.gui.show_games";
        return I18n.hasKey(key) ? I18n.format(key) : "Minigames";
    }

    @Override
    public void initGui() {
        int panelWidth = MinigamePanel.widthForScreen(width);
        int controlsWidth = Math.min(98, width - panelWidth - 3 * PANEL_GAP);
        int controlsLeft = (width - panelWidth - PANEL_GAP - controlsWidth) / 2;
        titleX = controlsLeft + controlsWidth / 2;
        panel.setBounds(controlsLeft + controlsWidth + PANEL_GAP, MinigamePanel.VERTICAL_MARGIN,
                panelWidth, MinigamePanel.heightForScreen(height));
        addButton(new GuiButton(BACK, controlsLeft, height / 2, controlsWidth, 20, I18n.format("gui.back")));
        if (!opened) {
            opened = true;
            ClientState.openMinigames(false);
        }
    }

    @Override
    public void updateScreen() {
        if (mc.world == null || (mc.player != null && mc.player.getHealth() <= 0.0F)) {
            mc.displayGuiScreen(null);
            return;
        }
        onMinigameSnapshot(ClientState.getMinigame());
        panel.update();
    }

    void onMinigameSnapshot(MinigameSession session) {
        panel.setSession(session);
        audio.update(mc, session);
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        drawCenteredString(fontRenderer, title(), titleX, height / 2 - 16, 0xFFFFFF);
        super.drawScreen(mouseX, mouseY, partialTicks);
        MinigameSession session = ClientState.getMinigame();
        panel.setSession(session);
        MinigameScoresMessage scores = ClientState.getHighScores(session == null ? MinigameSession.FLAPPY : session.type());
        panel.setHighScores(scores.personalBest, scores.leaderId, scores.leaderName, scores.leaderBest,
                scores.leaderTexture, scores.leaderTextureSignature);
        panel.draw(mc, mouseX, mouseY);
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (keyCode == Keyboard.KEY_ESCAPE) {
            mc.displayGuiScreen(parent);
        } else if (!panel.keyTyped(typedChar, keyCode)) {
            super.keyTyped(typedChar, keyCode);
        }
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        if (!panel.mouseClicked(mouseX, mouseY, mouseButton)) super.mouseClicked(mouseX, mouseY, mouseButton);
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button.id == BACK) mc.displayGuiScreen(parent);
    }

    boolean replacesVanillaMusic(ISound sound) {
        return audio.replacesVanillaMusic(mc, sound);
    }

    @Override
    public void onGuiClosed() {
        audio.stop(mc);
        ClientState.closeMinigames(false);
        opened = false;
        super.onGuiClosed();
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}
