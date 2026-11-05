package com.playerreinforcements.client;

import com.playerreinforcements.minigame.MinigameSession;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.network.NetworkPlayerInfo;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.resources.I18n;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.client.config.GuiUtils;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;

/** A separate in-screen window that leaves the respawn list and timer visible. */
@SideOnly(Side.CLIENT)
public final class MinigamePanel {
    public interface Controls {
        void start(int type);
        void input(int controls);
        default void tabClicked() { }
    }

    private static final Block[] TETRIS_BLOCKS = {
            Blocks.DIAMOND_ORE, Blocks.GOLD_ORE, Blocks.STONE, Blocks.EMERALD_ORE,
            Blocks.REDSTONE_ORE, Blocks.LAPIS_ORE, Blocks.IRON_ORE
    };
    private static final ItemStack CART = new ItemStack(Items.MINECART);
    private static final long SNAPSHOT_INTERVAL_NS = 50_000_000L;
    private static final long FLAP_PREVIEW_LIMIT_NS = 125_000_000L;
    private static final long TAB_PRESS_NS = 140_000_000L;
    private static final int PLAYFIELD_TOP = 54;
    private final Controls controls;
    private final GameTab flappyTab = new GameTab(MinigameSession.FLAPPY);
    private final GameTab tetrisTab = new GameTab(MinigameSession.TETRIS);
    private MinigameSession session;
    private long snapshotReceivedAt;
    private long pendingFlapAt;
    private float pendingFlapY;
    private int left;
    private int top;
    private int width;
    private int height;
    private int leftHeldTicks;
    private int rightHeldTicks;
    private boolean focused = true;
    private boolean timerReduction;
    private int personalBest;
    private UUID leaderId;
    private String leaderName = "";
    private String leaderTexture = "";
    private String leaderTextureSignature = "";
    private int leaderBest;
    private NetworkPlayerInfo cachedLeaderSkin;

    public MinigamePanel(Controls controls) {
        this.controls = controls;
    }

    public void setBounds(int left, int top, int width, int height) {
        this.left = left;
        this.top = top;
        this.width = width;
        this.height = height;
        int tabSpace = Math.max(0, width - 14);
        flappyTab.setBounds(left + 6, top + 4, tabSpace / 2);
        tetrisTab.setBounds(flappyTab.x + flappyTab.width + 2, top + 4, tabSpace - flappyTab.width);
    }

    public void setSession(MinigameSession session) {
        if (this.session == session) return;
        if (session == null || session.type() != MinigameSession.FLAPPY || session.gameOver()
                || session.cartVelocity() == MinigameSession.FLAP_VELOCITY
                || (!session.flappyStarted() && this.session != null && this.session.flappyStarted())) {
            pendingFlapAt = 0;
        }
        snapshotReceivedAt = System.nanoTime();
        this.session = session;
    }

    public void setTimerReduction(boolean enabled) { timerReduction = enabled; }

    public void setHighScores(int personalBest, UUID leaderId, String leaderName, int leaderBest) {
        setHighScores(personalBest, leaderId, leaderName, leaderBest, "", "");
    }

    public void setHighScores(int personalBest, UUID leaderId, String leaderName, int leaderBest,
                              String texture, String signature) {
        this.personalBest = Math.max(0, personalBest);
        this.leaderBest = Math.max(0, leaderBest);
        String name = leaderName == null ? "" : leaderName;
        String skinTexture = texture == null ? "" : texture;
        String skinSignature = signature == null ? "" : signature;
        if (!Objects.equals(this.leaderId, leaderId) || !this.leaderName.equals(name)
                || !leaderTexture.equals(skinTexture) || !leaderTextureSignature.equals(skinSignature)) {
            this.leaderId = leaderId;
            this.leaderName = name;
            this.leaderTexture = skinTexture;
            this.leaderTextureSignature = skinSignature;
            cachedLeaderSkin = null;
            if (leaderId != null) {
                GameProfile profile = new GameProfile(leaderId, name);
                if (!skinTexture.isEmpty()) {
                    Property property = skinSignature.isEmpty() ? new Property("textures", skinTexture)
                            : new Property("textures", skinTexture, skinSignature);
                    profile.getProperties().put("textures", property);
                }
                cachedLeaderSkin = new NetworkPlayerInfo(profile);
            }
        }
    }

    public void update() {
        if (!focused || session == null || session.gameOver() || session.type() != MinigameSession.TETRIS) {
            leftHeldTicks = rightHeldTicks = 0;
            return;
        }
        int input = 0;
        leftHeldTicks = keyDown(Keyboard.KEY_LEFT, Keyboard.KEY_A) ? leftHeldTicks + 1 : 0;
        rightHeldTicks = keyDown(Keyboard.KEY_RIGHT, Keyboard.KEY_D) ? rightHeldTicks + 1 : 0;
        if (leftHeldTicks >= 5) {
            input |= MinigameSession.LEFT;
            leftHeldTicks = 3;
        }
        if (rightHeldTicks >= 5) {
            input |= MinigameSession.RIGHT;
            rightHeldTicks = 3;
        }
        if (keyDown(Keyboard.KEY_DOWN, Keyboard.KEY_S)) input |= MinigameSession.SOFT_DROP;
        if (input != 0) controls.input(input);
    }

    private static boolean keyDown(int arrow, int wasd) {
        return Keyboard.isKeyDown(arrow) || Keyboard.isKeyDown(wasd);
    }

    public boolean mouseClicked(int x, int y, int button) {
        focused = x >= left && x < left + width && y >= top && y < top + height;
        if (!focused || button != 0) return focused;
        Minecraft mc = Minecraft.getMinecraft();
        if (flappyTab.mousePressed(mc, x, y)) {
            pressTab(flappyTab);
        } else if (tetrisTab.mousePressed(mc, x, y)) {
            pressTab(tetrisTab);
        } else if (y >= top + height - 22 && y < top + height - 5) {
            controls.start(session == null ? MinigameSession.FLAPPY : session.type());
        } else if (session != null && !session.gameOver() && session.type() == MinigameSession.FLAPPY
                && y >= top + PLAYFIELD_TOP && y < top + height - 47) {
            flap();
        }
        return true;
    }

    private void pressTab(GameTab tab) {
        tab.pressedAt = System.nanoTime();
        controls.tabClicked();
        controls.start(tab.id);
    }

    public boolean keyTyped(char typedChar, int keyCode) {
        if (!focused || session == null) return false;
        if (keyCode == Keyboard.KEY_R) {
            controls.start(session.type());
            return true;
        }
        if (keyCode == Keyboard.KEY_SPACE || keyCode == Keyboard.KEY_UP || keyCode == Keyboard.KEY_W) {
            if (!session.gameOver() && !Keyboard.isRepeatEvent()) {
                if (session.type() == MinigameSession.FLAPPY) flap();
                else controls.input(MinigameSession.ROTATE);
            }
            return true;
        }
        if (session.type() != MinigameSession.TETRIS) return false;
        int input;
        switch (keyCode) {
            case Keyboard.KEY_A:
            case Keyboard.KEY_LEFT: input = MinigameSession.LEFT; break;
            case Keyboard.KEY_D:
            case Keyboard.KEY_RIGHT: input = MinigameSession.RIGHT; break;
            case Keyboard.KEY_S:
            case Keyboard.KEY_DOWN: input = MinigameSession.SOFT_DROP; break;
            case Keyboard.KEY_RETURN: input = MinigameSession.HARD_DROP; break;
            default: return false;
        }
        if (!session.gameOver() && !Keyboard.isRepeatEvent()) controls.input(input);
        return true;
    }

    private void flap() {
        long now = System.nanoTime();
        // Animate the first few pixels immediately. The server still owns movement and collisions.
        pendingFlapY = visibleCartY(now);
        pendingFlapAt = now;
        controls.input(MinigameSession.FLAP);
    }

    public void draw(Minecraft mc, int mouseX, int mouseY) {
        FontRenderer font = mc.fontRenderer;
        Gui.drawRect(left, top, left + width, top + height, 0xFF171A1D);
        Gui.drawRect(left + 1, top + 1, left + width - 1, top + height - 1, 0xF033383D);
        flappyTab.drawTab(mc, mouseX, mouseY, text("flappy", "Flappy Cart"),
                session != null && session.type() == MinigameSession.FLAPPY);
        tetrisTab.drawTab(mc, mouseX, mouseY, text("tetris", "Tetris"),
                session != null && session.type() == MinigameSession.TETRIS);
        List<String> scoreTooltip = drawScores(mc, mouseX, mouseY);

        int boardTop = top + PLAYFIELD_TOP;
        int boardHeight = height - PLAYFIELD_TOP - 48;
        if (session == null) {
            Gui.drawRect(left + 6, boardTop, left + width - 6, boardTop + boardHeight, 0xFF18242C);
            centerTrimmed(font, text("choose_game", "Choose a game above"), boardTop + boardHeight / 2 - 4, 0xDDDDDD);
        } else {
            if (session.type() == MinigameSession.FLAPPY) drawFlappy(mc, boardTop, boardHeight);
            else drawTetris(mc, boardTop, boardHeight);
            if (session.gameOver()) {
                int bannerTop = boardTop + boardHeight / 2 - 12;
                Gui.drawRect(left + 6, bannerTop, left + width - 6, bannerTop + 25, 0xDC181818);
                center(font, text("game_over", "Game over"), bannerTop + 8, 0xFFFF77);
            } else if (session.type() == MinigameSession.FLAPPY && !session.flappyStarted()
                    && !flapPreviewActive(System.nanoTime())) {
                int hintTop = boardTop + boardHeight / 2 + 14;
                Gui.drawRect(left + 6, hintTop, left + width - 6, hintTop + 17, 0xBB181818);
                centerTrimmed(font, text("flappy_ready", "Flap to start"), hintTop + 5, 0xFFFF77);
            }
        }
        boolean tetris = session != null && session.type() == MinigameSession.TETRIS;
        String keys = tetris ? text("tetris_keys", "WASD / Space rotates")
                : text("flappy_keys", "Space / W / click to flap");
        centerTrimmed(font, keys, top + height - 43, 0xDDDDDD);
        if (timerReduction) {
            centerTrimmed(font, text("reward", "1 point = 10s less wait"), top + height - 32, 0xAADD99);
        }
        boolean hover = mouseX >= left + 6 && mouseX < left + width - 6
                && mouseY >= top + height - 22 && mouseY < top + height - 5;
        Gui.drawRect(left + 6, top + height - 22, left + width - 6, top + height - 5,
                hover ? 0xFF72818B : 0xFF555D64);
        center(font, session == null ? text("start", "Start game") : text("restart", "Restart (R)"), top + height - 17, 0xFFFFFF);
        if (scoreTooltip != null) {
            ScaledResolution screen = new ScaledResolution(mc);
            GuiUtils.drawHoveringText(scoreTooltip, mouseX, mouseY, screen.getScaledWidth(),
                    screen.getScaledHeight(), -1, font);
        }
    }

    private List<String> drawScores(Minecraft mc, int mouseX, int mouseY) {
        FontRenderer font = mc.fontRenderer;
        int contentLeft = left + 6;
        int contentRight = left + width - 6;
        int currentScore = session == null ? 0 : session.score();
        String highLabel = text("high_score", "HighScore");
        String scoreLabel = text("score_label", "Score");
        int centerX = Math.min(left + width / 2, contentRight - font.getStringWidth(highLabel)
                - 6 - (font.getStringWidth(scoreLabel) + 1) / 2);
        font.drawString(text("top", "Top"), contentLeft, top + 29, 0xBBBBBB);
        font.drawString(scoreLabel, centerX - font.getStringWidth(scoreLabel) / 2, top + 29, 0xBBBBBB);
        font.drawString(highLabel, contentRight - font.getStringWidth(highLabel), top + 29, 0xBBBBBB);

        int leaderScoreX = contentLeft;
        if (leaderId != null) {
            drawLeaderFace(mc, contentLeft, top + 41);
            leaderScoreX += 12;
        }
        font.drawString(compactScore(leaderBest), leaderScoreX, top + 41, 0xFFE5BD68);
        String currentValue = compactScore(currentScore);
        font.drawString(currentValue, centerX - font.getStringWidth(currentValue) / 2, top + 41, 0xFFFFFF);
        String highValue = compactScore(personalBest);
        font.drawString(highValue, contentRight - font.getStringWidth(highValue), top + 41, 0xFFE5BD68);

        if (mouseY < top + 28 || mouseY >= top + 51 || mouseX < contentLeft || mouseX >= contentRight) return null;
        int scoreArea = (width - 12) / 3;
        if (mouseX < contentLeft + scoreArea) {
            return Arrays.asList(leaderName.isEmpty() ? text("top", "Top") : leaderName, Integer.toString(leaderBest));
        }
        if (mouseX >= contentRight - scoreArea) {
            return Arrays.asList(highLabel, Integer.toString(personalBest));
        }
        return Arrays.asList(text("score_label", "Score"), Integer.toString(currentScore));
    }

    private void drawLeaderFace(Minecraft mc, int x, int y) {
        NetworkPlayerInfo player = mc.getConnection() == null ? null : mc.getConnection().getPlayerInfo(leaderId);
        if (player == null) player = cachedLeaderSkin;
        ResourceLocation skin = player.getLocationSkin();
        mc.getTextureManager().bindTexture(skin);
        GlStateManager.color(1, 1, 1, 1);
        GlStateManager.enableBlend();
        Gui.drawScaledCustomSizeModalRect(x, y, 8, 8, 8, 8, 8, 8, 64, 64);
        Gui.drawScaledCustomSizeModalRect(x, y, 40, 8, 8, 8, 8, 8, 64, 64);
        GlStateManager.disableBlend();
    }

    private static String compactScore(int score) {
        if (score < 10_000) return Integer.toString(score);
        if (score < 1_000_000) return String.format(Locale.ROOT, "%.1fK", score / 1_000.0F).replace(".0K", "K");
        if (score < 1_000_000_000) return String.format(Locale.ROOT, "%.1fM", score / 1_000_000.0F).replace(".0M", "M");
        return String.format(Locale.ROOT, "%.1fB", score / 1_000_000_000.0F).replace(".0B", "B");
    }

    private static final class GameTab extends GuiButton {
        private boolean selected;
        private long pressedAt;

        private GameTab(int type) {
            super(type, 0, 0, 0, 20, "");
        }

        private void setBounds(int x, int y, int width) {
            this.x = x;
            this.y = y;
            this.width = width;
        }

        @Override
        protected int getHoverState(boolean mouseOver) {
            return super.getHoverState(mouseOver || selected);
        }

        private void drawTab(Minecraft mc, int mouseX, int mouseY, String label, boolean selected) {
            this.selected = selected;
            this.displayString = mc.fontRenderer.trimStringToWidth(label, Math.max(0, width - 6));
            this.packedFGColour = selected ? 0xFFFFA0 : 0;
            long elapsed = System.nanoTime() - pressedAt;
            boolean animating = pressedAt != 0 && elapsed < TAB_PRESS_NS;
            if (animating) {
                float remaining = 1.0F - (float) elapsed / TAB_PRESS_NS;
                float squeeze = remaining * remaining;
                float centerX = x + width / 2.0F;
                float centerY = y + height / 2.0F;
                GlStateManager.pushMatrix();
                GlStateManager.translate(centerX, centerY, 0);
                GlStateManager.scale(1.0F - 0.06F * squeeze, 1.0F - 0.22F * squeeze, 1);
                GlStateManager.translate(-centerX, -centerY, 0);
            }
            drawButton(mc, mouseX, mouseY, 0);
            if (animating) GlStateManager.popMatrix();
        }
    }

    private void drawFlappy(Minecraft mc, int boardTop, int boardHeight) {
        int boardLeft = left + 6;
        int boardWidth = width - 12;
        Gui.drawRect(boardLeft, boardTop, boardLeft + boardWidth, boardTop + boardHeight, 0xFF77B5CF);
        long now = System.nanoTime();
        float progress = session.flappyStarted() && !session.gameOver() ? frameFraction(now - snapshotReceivedAt) : 0;
        float scaleX = (float) boardWidth / MinigameSession.FLAPPY_WIDTH;
        float scaleY = (float) boardHeight / MinigameSession.FLAPPY_HEIGHT;
        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder buffer = tessellator.getBuffer();
        GlStateManager.disableTexture2D();
        GlStateManager.pushMatrix();
        GlStateManager.translate(boardLeft, boardTop, 0);
        GlStateManager.scale(scaleX, scaleY, 1);
        buffer.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR);
        for (int i = 0; i < MinigameSession.PILLAR_COUNT; i++) {
            float x = session.pillarX(i) - MinigameSession.PILLAR_SPEED * progress;
            int gap = session.gapY(i);
            int gapBottom = gap + MinigameSession.GAP_HEIGHT;
            float right = x + MinigameSession.PILLAR_WIDTH;
            pillarRect(buffer, x, 0, right, gap, 0x73543A);
            pillarRect(buffer, x, gapBottom, right, MinigameSession.FLAPPY_HEIGHT, 0x73543A);
            pillarRect(buffer, x, gap - 5, right, gap, 0x64A34D);
            pillarRect(buffer, x, gapBottom, right, gapBottom + 5, 0x64A34D);
            for (int y = 12; y < MinigameSession.FLAPPY_HEIGHT; y += 22) {
                if (y < gap - 8 || y > gapBottom + 5) {
                    pillarRect(buffer, x + 3, y, x + 8, y + 3, 0x8F6B4A);
                }
            }
        }
        tessellator.draw();
        GlStateManager.enableTexture2D();

        // Vanilla's 16px sprite has one transparent pixel on each edge. Place its
        // opaque 14px bounds over the simulation's collision rectangle.
        float spriteSize = MinigameSession.CART_WIDTH * (16.0F / 14);
        float transparentMargin = spriteSize / 16;
        float cartLeft = MinigameSession.CART_X - transparentMargin;
        float cartTop = visibleCartY(now) - transparentMargin;
        float visibleTop = Math.max(0, cartTop);
        float visibleBottom = Math.min(MinigameSession.FLAPPY_HEIGHT, cartTop + spriteSize);
        TextureAtlasSprite cartSprite = mc.getRenderItem().getItemModelMesher().getItemModel(CART).getParticleTexture();
        beginSprites(mc, buffer);
        sprite(buffer, cartSprite, cartLeft, visibleTop, cartLeft + spriteSize, visibleBottom,
                (visibleTop - cartTop) / spriteSize, (visibleBottom - cartTop) / spriteSize);
        finishSprites(tessellator);
        GlStateManager.popMatrix();
    }

    private float visibleCartY(long now) {
        float y = session.cartY();
        if (session.gameOver()) return y;
        if (flapPreviewActive(now)) {
            y = pendingFlapY + MinigameSession.FLAP_VELOCITY * frameFraction(now - pendingFlapAt);
        } else if (session.flappyStarted()) {
            y += session.cartVelocity() * frameFraction(now - snapshotReceivedAt);
        }
        return Math.max(0, Math.min(MinigameSession.FLAPPY_HEIGHT - MinigameSession.CART_HEIGHT, y));
    }

    private boolean flapPreviewActive(long now) {
        return pendingFlapAt != 0 && now - pendingFlapAt < FLAP_PREVIEW_LIMIT_NS;
    }

    private static float frameFraction(long elapsed) {
        return Math.min(1.0F, (float) elapsed / SNAPSHOT_INTERVAL_NS);
    }

    private static float lerp(float before, float after, float progress) {
        return before + (after - before) * progress;
    }

    private static void pillarRect(BufferBuilder buffer, float x1, float y1, float x2, float y2, int color) {
        x1 = Math.max(0, x1);
        x2 = Math.min(MinigameSession.FLAPPY_WIDTH, x2);
        if (x1 >= x2) return;
        int red = (color >> 16) & 255;
        int green = (color >> 8) & 255;
        int blue = color & 255;
        buffer.pos(x1, y2, 0).color(red, green, blue, 255).endVertex();
        buffer.pos(x2, y2, 0).color(red, green, blue, 255).endVertex();
        buffer.pos(x2, y1, 0).color(red, green, blue, 255).endVertex();
        buffer.pos(x1, y1, 0).color(red, green, blue, 255).endVertex();
    }

    private void drawTetris(Minecraft mc, int boardTop, int boardHeight) {
        int columns = MinigameSession.COLUMNS;
        int rows = MinigameSession.ROWS;
        int cell = Math.max(1, Math.min((width - 20) / columns, boardHeight / rows));
        int boardLeft = left + (width - cell * columns) / 2;
        int boardRight = boardLeft + cell * columns;
        int boardBottom = boardTop + cell * rows;
        Gui.drawRect(boardLeft - 1, boardTop - 1, boardRight + 1, boardBottom + 1, 0xFF929292);
        Gui.drawRect(boardLeft, boardTop, boardRight, boardBottom, 0xFF202125);
        TextureAtlasSprite[] textures = new TextureAtlasSprite[TETRIS_BLOCKS.length];
        for (int i = 0; i < TETRIS_BLOCKS.length; i++) {
            textures[i] = mc.getBlockRendererDispatcher().getBlockModelShapes().getTexture(TETRIS_BLOCKS[i].getDefaultState());
        }
        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder buffer = tessellator.getBuffer();
        beginSprites(mc, buffer);
        for (int row = 0; row < rows; row++) {
            for (int column = 0; column < columns; column++) {
                int value = session.cell(column, row);
                if (value > 0 && value <= textures.length) {
                    tile(buffer, textures[value - 1], boardLeft + column * cell, boardTop + row * cell, cell);
                }
            }
        }
        if (!session.gameOver()) {
            for (int index = 0; index < 4; index++) {
                int x = session.pieceX() + session.blockX(index);
                int y = session.pieceY() + session.blockY(index);
                if (x >= 0 && x < columns && y >= 0 && y < rows) {
                    tile(buffer, textures[session.piece()], boardLeft + x * cell, boardTop + y * cell, cell);
                }
            }
        }
        finishSprites(tessellator);
    }

    private static void beginSprites(Minecraft mc, BufferBuilder buffer) {
        mc.getTextureManager().bindTexture(TextureMap.LOCATION_BLOCKS_TEXTURE);
        GlStateManager.color(1, 1, 1, 1);
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        buffer.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX);
    }

    private static void finishSprites(Tessellator tessellator) {
        tessellator.draw();
        GlStateManager.disableBlend();
    }

    private static void tile(BufferBuilder buffer, TextureAtlasSprite texture, int x, int y, int size) {
        sprite(buffer, texture, x, y, x + size, y + size, 0, 1);
    }

    private static void sprite(BufferBuilder buffer, TextureAtlasSprite texture,
                               float left, float top, float right, float bottom, float v1, float v2) {
        float u1 = texture.getMinU();
        float u2 = texture.getMaxU();
        float topV = lerp(texture.getMinV(), texture.getMaxV(), v1);
        float bottomV = lerp(texture.getMinV(), texture.getMaxV(), v2);
        buffer.pos(left, bottom, 0).tex(u1, bottomV).endVertex();
        buffer.pos(right, bottom, 0).tex(u2, bottomV).endVertex();
        buffer.pos(right, top, 0).tex(u2, topV).endVertex();
        buffer.pos(left, top, 0).tex(u1, topV).endVertex();
    }

    private void center(FontRenderer font, String label, int y, int color) {
        font.drawString(label, left + (width - font.getStringWidth(label)) / 2, y, color);
    }

    private void centerTrimmed(FontRenderer font, String label, int y, int color) {
        center(font, font.trimStringToWidth(label, width - 10), y, color);
    }

    private static String text(String key, String english, Object... args) {
        String translationKey = "playerreinforcements.minigame." + key;
        return I18n.hasKey(translationKey)
                ? I18n.format(translationKey, args)
                : String.format(Locale.ROOT, english, args);
    }
}
