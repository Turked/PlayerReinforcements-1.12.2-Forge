package com.playerreinforcements.client;

import com.playerreinforcements.PlayerReinforcements;
import com.playerreinforcements.minigame.MinigameSession;
import com.playerreinforcements.network.RequestRespawnMessage;
import com.playerreinforcements.network.RequestStatusMessage;
import com.playerreinforcements.network.StatusMessage;
import com.playerreinforcements.network.MinigameInputMessage;
import com.playerreinforcements.network.MinigameScoresMessage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiGameOver;
import net.minecraft.client.gui.GuiLabel;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiYesNo;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.audio.ISound;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.resources.I18n;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.math.MathHelper;
import net.minecraftforge.fml.relauncher.ReflectionHelper;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public final class ReinforcementDeathScreen extends GuiScreen {
    private static final int LEAVE = 1;
    private static final int MINIGAMES = 2;
    private static final int FIRST_TEAMMATE = 100;
    private static final int SCROLLBAR_WIDTH = 9;
    private static final int SCROLLBAR_GAP = 4;
    private int ticksOpen;
    private int lastRevision = -1;
    private int rowHeight;
    private int scrollOffset;
    private int dragOffset = -1;
    private int firstVisible;
    private int endVisible;
    private int guiScale;
    private int panelLeft;
    private int panelWidth;
    private int titleY;
    private int listTop;
    private int listBottom;
    private int actionY;
    private boolean requestPending;
    private boolean gamesOpen;
    private boolean dispatchingClick;
    private boolean rebuildAfterClick;
    private StatusMessage layoutStatus;
    private List<String> deathLines = Collections.emptyList();
    private List<String> errorLines = Collections.emptyList();
    private long countdownSeconds = -1;
    private String countdownText;
    private final List<TeammateButton> roster = new ArrayList<>();
    private final ITextComponent deathMessage;
    private final MinigameAudio minigameAudio = new MinigameAudio();
    private final MinigamePanel minigame = new MinigamePanel(new MinigamePanel.Controls() {
        @Override
        public void start(int type) {
            if (mc.getConnection() != null) {
                minigameAudio.startRequested();
                PlayerReinforcements.NETWORK.sendToServer(new MinigameInputMessage(type, 0));
            }
        }

        @Override
        public void input(int controls) {
            if (mc.getConnection() != null) {
                minigameAudio.input(mc, ClientState.getMinigame(), controls);
                PlayerReinforcements.NETWORK.sendToServer(new MinigameInputMessage(-1, controls));
            }
        }

        @Override
        public void tabClicked() {
            minigameAudio.click(mc);
        }
    });

    public ReinforcementDeathScreen() {
        this.deathMessage = null;
    }

    ReinforcementDeathScreen(GuiGameOver vanilla) {
        this.deathMessage = ReflectionHelper.getPrivateValue(GuiGameOver.class, vanilla,
                "causeOfDeath", "field_184871_f");
    }

    private boolean showsGames() {
        return gamesOpen && ClientState.getStatus().deathMinigamesEnabled;
    }

    @Override
    public void initGui() {
        guiScale = new ScaledResolution(mc).getScaleFactor();
        dragOffset = -1;
        rebuildButtons();
    }

    private void rebuildButtons() {
        if (dispatchingClick) {
            // GuiScreen is still testing this click against the original button positions.
            rebuildAfterClick = true;
            return;
        }
        StatusMessage status = ClientState.getStatus();
        UUID topPlayer = roster.isEmpty() ? null : roster.get(scrollOffset / rowHeight).playerId;
        int offsetWithinRow = roster.isEmpty() ? 0 : scrollOffset % rowHeight;
        buttonList.clear();
        roster.clear();
        if (showsGames()) {
            int available = Math.min(508, width - 16);
            int gameWidth = Math.min(220, (available - 8) / 2);
            panelWidth = available - 8 - gameWidth;
            panelLeft = (width - available) / 2;
            minigame.setBounds(panelLeft + panelWidth + 8, 12, gameWidth, height - 24);
        } else {
            panelWidth = Math.max(100, Math.min(280, width - 32));
            panelLeft = (width - panelWidth) / 2;
        }
        titleY = Math.max(12, Math.min(30, height / 10));
        listTop = titleY + 88;
        int leaveY = height - 28;
        actionY = leaveY;
        listBottom = actionY - (status.error.isEmpty() ? 8 : 32);
        rowHeight = status.showTeammateDimensions ? 32 : 24;
        countdownSeconds = -1;
        deathLines = deathMessage == null ? Collections.emptyList()
                : fontRenderer.listFormattedStringToWidth(deathMessage.getFormattedText(), panelWidth);
        errorLines = status.error.isEmpty() ? Collections.emptyList()
                : fontRenderer.listFormattedStringToWidth(errorText(status.error), panelWidth);

        if (status.deathMinigamesEnabled) {
            int gameButtonWidth = (panelWidth - 6) / 2;
            addButton(new GuiButton(MINIGAMES, panelLeft, leaveY, gameButtonWidth, 20,
                    gamesOpen ? text("hide_games", "Hide games") : text("show_games", "Minigames")));
            addButton(new GuiButton(LEAVE, panelLeft + gameButtonWidth + 6, leaveY,
                    panelWidth - gameButtonWidth - 6, 20, text("leave", "Leave Game")));
        } else {
            addButton(new GuiButton(LEAVE, panelLeft, leaveY, panelWidth, 20, text("leave", "Leave Game")));
        }
        if (!status.groupWipe) {
            int rowWidth = panelWidth;
            if (status.teammates.size() * rowHeight - 4 > listBottom - listTop) {
                rowWidth -= SCROLLBAR_WIDTH + SCROLLBAR_GAP;
            }
            for (int index = 0; index < status.teammates.size(); index++) {
                StatusMessage.Teammate teammate = status.teammates.get(index);
                String label = fontRenderer.trimStringToWidth(teammate.name, rowWidth - 28);
                String dimension = status.showTeammateDimensions
                        ? fontRenderer.trimStringToWidth(dimensionName(teammate.dimension), rowWidth - 4) : "";
                roster.add(new TeammateButton(FIRST_TEAMMATE + index, panelLeft, rowWidth, label,
                        teammate.id, listTop, listBottom, dimension));
                if (teammate.id.equals(topPlayer)) {
                    scrollOffset = index * rowHeight + Math.min(offsetWithinRow, rowHeight - 1);
                }
            }
        }
        layoutStatus = status;
        lastRevision = ClientState.getRevision();
        scrollOffset = MathHelper.clamp(scrollOffset, 0, maxScroll());
        layoutVisibleRows();
    }

    private boolean rosterLayoutChanged(StatusMessage status) {
        if (layoutStatus == null || layoutStatus.groupWipe != status.groupWipe
                || layoutStatus.deathMinigamesEnabled != status.deathMinigamesEnabled
                || layoutStatus.showTeammateDimensions != status.showTeammateDimensions
                || !layoutStatus.error.equals(status.error)
                || layoutStatus.teammates.size() != status.teammates.size()) return true;
        for (int i = 0; i < status.teammates.size(); i++) {
            StatusMessage.Teammate before = layoutStatus.teammates.get(i);
            StatusMessage.Teammate after = status.teammates.get(i);
            if (!before.id.equals(after.id) || !before.name.equals(after.name)
                    || before.dimension != after.dimension) return true;
        }
        return false;
    }

    private void layoutVisibleRows() {
        buttonList.removeIf(button -> button instanceof TeammateButton);
        firstVisible = Math.min(roster.size(), scrollOffset / rowHeight);
        int viewport = Math.max(0, listBottom - listTop);
        endVisible = Math.min(roster.size(), (scrollOffset + viewport + rowHeight - 1) / rowHeight);
        for (int i = firstVisible; i < endVisible; i++) {
            TeammateButton row = roster.get(i);
            row.y = listTop + i * rowHeight - scrollOffset;
            addButton(row);
        }
        updateButtonStates();
    }

    private void updateButtonStates() {
        StatusMessage status = ClientState.getStatus();
        boolean canRespawn = status.waiting && ClientState.getRemainingTicks() == 0
                && !requestPending;
        for (GuiButton button : buttonList) {
            if (button.id == LEAVE) {
                button.enabled = ticksOpen >= 20;
            } else if (button.id == MINIGAMES) {
                button.enabled = status.waiting && !status.groupWipe;
            } else if (button instanceof TeammateButton) {
                button.enabled = canRespawn;
            }
        }
    }

    @Override
    public void updateScreen() {
        ticksOpen++;
        if (ClientState.getRevision() != lastRevision) {
            requestPending = false;
            if (rosterLayoutChanged(ClientState.getStatus())) rebuildButtons();
            lastRevision = ClientState.getRevision();
        }
        updateButtonStates();
        if (showsGames()) {
            onMinigameSnapshot(ClientState.getMinigame());
            minigame.setTimerReduction(ClientState.getStatus().minigameReducesRespawnTimer);
            minigame.update();
        } else {
            minigameAudio.stop(mc);
        }
        if (mc.getConnection() != null && (ticksOpen == 1 || ticksOpen % 100 == 0)) {
            PlayerReinforcements.NETWORK.sendToServer(new RequestStatusMessage());
        }
    }

    void onMinigameSnapshot(MinigameSession session) {
        if (!showsGames()) return;
        minigame.setSession(session);
        minigameAudio.update(mc, session);
    }

    boolean replacesVanillaMusic(ISound sound) {
        return minigameAudio.replacesVanillaMusic(mc, sound);
    }

    @Override
    public void onGuiClosed() {
        minigameAudio.stop(mc);
        super.onGuiClosed();
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (!button.enabled) {
            return;
        }
        if (button.id == MINIGAMES) {
            gamesOpen = !gamesOpen;
            if (!gamesOpen) minigameAudio.stop(mc);
            rebuildButtons();
        } else if (button.id == LEAVE) {
            GuiYesNo confirmation = new LeaveConfirmation(this);
            mc.displayGuiScreen(confirmation);
            confirmation.setButtonDelay(20);
        } else if (button instanceof TeammateButton) {
            if (mc.getConnection() != null) {
                requestPending = true;
                PlayerReinforcements.NETWORK.sendToServer(new RequestRespawnMessage(((TeammateButton) button).playerId));
                updateButtonStates();
            }
        }
    }

    private int maxScroll() {
        if (roster.isEmpty() || listBottom <= listTop) return 0;
        return Math.max(0, roster.size() * rowHeight - 4 - (listBottom - listTop));
    }

    private void scrollBy(int pixels) {
        int offset = MathHelper.clamp(scrollOffset + pixels, 0, maxScroll());
        if (offset != scrollOffset) {
            scrollOffset = offset;
            layoutVisibleRows();
        }
    }

    private int trackLeft() {
        return panelLeft + panelWidth - SCROLLBAR_WIDTH;
    }

    private int thumbHeight() {
        int viewport = listBottom - listTop;
        return Math.min(viewport, Math.max(14, viewport * viewport / (viewport + maxScroll())));
    }

    private int thumbTop() {
        return listTop + scrollOffset * (listBottom - listTop - thumbHeight()) / maxScroll();
    }

    private void dragScrollbar(int mouseY) {
        if (maxScroll() == 0) return;
        int travel = listBottom - listTop - thumbHeight();
        if (travel <= 0) return;
        int y = MathHelper.clamp(mouseY - listTop - dragOffset, 0, travel);
        scrollBy(Math.round((float) y * maxScroll() / travel) - scrollOffset);
    }

    @Override
    public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        int wheel = Mouse.getEventDWheel();
        if (wheel != 0) {
            int mouseY = height - Mouse.getEventY() * height / mc.displayHeight - 1;
            int mouseX = Mouse.getEventX() * width / mc.displayWidth;
            scrollWithWheel(mouseX, mouseY, wheel);
        }
    }

    private void scrollWithWheel(int mouseX, int mouseY, int wheel) {
        if (wheel != 0 && mouseX >= panelLeft && mouseX < panelLeft + panelWidth
                && mouseY >= listTop && mouseY < listBottom) {
            int steps = Math.max(1, Math.abs(wheel / 120));
            scrollBy((wheel > 0 ? -steps : steps) * rowHeight);
        }
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) {
        if (showsGames() && minigame.keyTyped(typedChar, keyCode)) return;
        switch (keyCode) {
            case Keyboard.KEY_UP: scrollBy(-rowHeight); break;
            case Keyboard.KEY_DOWN: scrollBy(rowHeight); break;
            case Keyboard.KEY_PRIOR: scrollBy(listTop - listBottom); break;
            case Keyboard.KEY_NEXT: scrollBy(listBottom - listTop); break;
            case Keyboard.KEY_HOME: scrollBy(-scrollOffset); break;
            case Keyboard.KEY_END: scrollBy(maxScroll() - scrollOffset); break;
            default: break;
        }
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        if (showsGames() && minigame.mouseClicked(mouseX, mouseY, mouseButton)) return;
        if (mouseButton == 0 && maxScroll() > 0 && mouseX >= trackLeft() && mouseX < panelLeft + panelWidth
                && mouseY >= listTop && mouseY < listBottom) {
            int localY = mouseY - thumbTop();
            int thumb = thumbHeight();
            if (localY >= 0 && localY < thumb) {
                dragOffset = localY;
            } else {
                dragOffset = thumb / 2;
                dragScrollbar(mouseY);
            }
            return;
        }
        dispatchingClick = true;
        try {
            super.mouseClicked(mouseX, mouseY, mouseButton);
        } finally {
            dispatchingClick = false;
            if (rebuildAfterClick) {
                rebuildAfterClick = false;
                rebuildButtons();
            }
        }
    }

    @Override
    protected void mouseClickMove(int mouseX, int mouseY, int button, long elapsed) {
        if (button == 0 && dragOffset >= 0) dragScrollbar(mouseY);
    }

    @Override
    protected void mouseReleased(int mouseX, int mouseY, int button) {
        if (button == 0) dragOffset = -1;
        super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public void confirmClicked(boolean result, int id) {
        if (result) {
            if (mc.world != null) {
                mc.world.sendQuittingDisconnectingPacket();
            }
            mc.loadWorld((WorldClient) null);
            ClientState.reset();
            mc.displayGuiScreen(new GuiMainMenu());
        } else {
            mc.displayGuiScreen(this);
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawGradientRect(0, 0, width, height, 1615855616, -1602211792);
        GlStateManager.pushMatrix();
        int center = panelLeft + panelWidth / 2;
        String title = I18n.format("deathScreen.title");
        float titleScale = Math.min(2.0F, (panelWidth - 8.0F) / Math.max(1, fontRenderer.getStringWidth(title)));
        GlStateManager.scale(titleScale, titleScale, titleScale);
        drawCenteredString(fontRenderer, title, (int) (center / titleScale), (int) (titleY / titleScale), 0xFFFFFF);
        GlStateManager.popMatrix();
        for (int i = 0; i < Math.min(2, deathLines.size()); i++) {
            drawCenteredString(fontRenderer, deathLines.get(i), center, titleY + 23 + i * 10, 0xFFFFFF);
        }

        StatusMessage status = ClientState.getStatus();
        int remainingTicks = ClientState.getRemainingTicks();
        String cooldown;
        int color = 0xFFFF55;
        if (!status.waiting) {
            cooldown = text("synchronizing", "Waiting for server...");
            color = 0xCCCCCC;
        } else if (requestPending || (status.groupWipe && remainingTicks == 0)) {
            cooldown = text("requesting", "Respawning...");
        } else if (remainingTicks > 0) {
            long seconds = (remainingTicks + 19L) / 20;
            if (seconds != countdownSeconds) {
                countdownSeconds = seconds;
                countdownText = text("cooldown", "Respawn in %s",
                        String.format(Locale.ROOT, "%d:%02d", seconds / 60, seconds % 60));
            }
            cooldown = countdownText;
        } else {
            cooldown = text("ready", "Ready to respawn");
            color = 0x55FF55;
        }
        GlStateManager.pushMatrix();
        float timerScale = Math.min(1.5F, (panelWidth - 4.0F) / Math.max(1, fontRenderer.getStringWidth(cooldown)));
        GlStateManager.translate(center, titleY + 45, 0);
        GlStateManager.scale(timerScale, timerScale, 1);
        drawCenteredString(fontRenderer, cooldown, 0, 0, color);
        GlStateManager.popMatrix();

        if (roster.isEmpty()) {
            if (status.waiting) {
                drawCenteredString(fontRenderer, text("no_teammates", "No living teammates"), center, listTop + 5, 0xFFFFFF);
                drawCenteredString(fontRenderer, text("group_wait", "Respawning together"), center, listTop + 20, 0xCCCCCC);
            }
        } else {
            String heading = text("teammates", "Choose player to spawn on");
            drawCenteredString(fontRenderer, fontRenderer.trimStringToWidth(heading, panelWidth),
                    center, listTop - 15, 0xFFFFFF);
            drawRoster(mouseX, mouseY, partialTicks);
        }
        for (int line = 0; line < Math.min(2, errorLines.size()); line++) {
            drawCenteredString(fontRenderer, errorLines.get(line), center, actionY - 24 + line * 10, 0xFF5555);
        }
        for (GuiButton button : buttonList) {
            if (!(button instanceof TeammateButton)) button.drawButton(mc, mouseX, mouseY, partialTicks);
        }
        for (GuiLabel label : labelList) label.drawLabel(mc, mouseX, mouseY);
        if (showsGames()) {
            MinigameSession session = ClientState.getMinigame();
            minigame.setSession(session);
            MinigameScoresMessage scores = ClientState.getHighScores(session == null ? MinigameSession.FLAPPY : session.type());
            minigame.setHighScores(scores.personalBest, scores.leaderId, scores.leaderName, scores.leaderBest,
                    scores.leaderTexture, scores.leaderTextureSignature);
            minigame.setTimerReduction(status.minigameReducesRespawnTimer);
            minigame.draw(mc, mouseX, mouseY);
        }
    }

    private void drawRoster(int mouseX, int mouseY, float partialTicks) {
        int viewport = listBottom - listTop;
        if (viewport <= 0) return;
        if (maxScroll() > 0) {
            int left = trackLeft();
            int right = panelLeft + panelWidth;
            int top = thumbTop();
            int bottom = top + thumbHeight();
            boolean highlighted = dragOffset >= 0 || (mouseX >= left && mouseX < right && mouseY >= top && mouseY < bottom);
            drawRect(left, listTop, right, listBottom, 0xA0191212);
            drawRect(left + 1, top, right - 1, bottom, highlighted ? 0xFFD6C8C3 : 0xFFAC9E99);
            drawRect(left + 1, top, right - 1, top + 1, 0xFFEEE2DB);
            drawRect(left + 1, bottom - 1, right - 1, bottom, 0xFF756762);
        }
        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        GL11.glScissor(panelLeft * guiScale, mc.displayHeight - listBottom * guiScale,
                panelWidth * guiScale, viewport * guiScale);
        try {
            for (int i = firstVisible; i < endVisible; i++) {
                roster.get(i).drawButton(mc, mouseX, mouseY, partialTicks);
            }
        } finally {
            GL11.glDisable(GL11.GL_SCISSOR_TEST);
        }
    }

    private static String errorText(String key) {
        if (I18n.hasKey(key)) return I18n.format(key);
        return "playerreinforcements.error.location".equals(key)
                ? "There is no safe space near that player." : "That player is no longer available.";
    }

    private static String dimensionName(int dimension) {
        switch (dimension) {
            case 0:
                return text("dimension.overworld", "Overworld");
            case -1:
                return text("dimension.nether", "The Nether");
            case 1:
                return text("dimension.end", "The End");
            default:
                return text("dimension.other", "Dimension %s", dimension);
        }
    }

    private static String text(String key, String fallback, Object... args) {
        String translation = "playerreinforcements.gui." + key;
        return I18n.hasKey(translation) ? I18n.format(translation, args) : String.format(Locale.ROOT, fallback, args);
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    static final class LeaveConfirmation extends GuiYesNo {
        LeaveConfirmation(ReinforcementDeathScreen screen) {
            super(screen, I18n.format("deathScreen.quit.confirm"), "", text("leave", "Leave Game"),
                    I18n.format("gui.cancel"), 0);
        }

        @Override
        public boolean doesGuiPauseGame() {
            return false;
        }
    }
}
