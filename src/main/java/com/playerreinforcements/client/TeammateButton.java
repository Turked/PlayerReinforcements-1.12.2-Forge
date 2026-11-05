package com.playerreinforcements.client;

import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.client.network.NetworkPlayerInfo;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/** The same skin face and hat layers used by the vanilla player list. */
@SideOnly(Side.CLIENT)
final class TeammateButton extends GuiButton {
    final UUID playerId;
    private final int listTop;
    private final int listBottom;
    private final String dimension;

    TeammateButton(int id, int x, int width, String label, UUID playerId, int listTop, int listBottom, String dimension) {
        super(id, x, listTop, width, 20, label);
        this.playerId = playerId;
        this.listTop = listTop;
        this.listBottom = listBottom;
        this.dimension = dimension;
    }

    @Override
    public boolean mousePressed(Minecraft mc, int mouseX, int mouseY) {
        return mouseY >= listTop && mouseY < listBottom && super.mousePressed(mc, mouseX, mouseY);
    }

    @Override
    public void drawButton(Minecraft mc, int mouseX, int mouseY, float partialTicks) {
        String label = displayString;
        displayString = "";
        super.drawButton(mc, mouseX, mouseY >= listTop && mouseY < listBottom ? mouseY : -1, partialTicks);
        displayString = label;
        if (!visible) return;
        NetHandlerPlayClient connection = mc.getConnection();
        NetworkPlayerInfo info = connection == null ? null : connection.getPlayerInfo(playerId);
        ResourceLocation skin = info == null ? DefaultPlayerSkin.getDefaultSkin(playerId) : info.getLocationSkin();
        mc.getTextureManager().bindTexture(skin);
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        GlStateManager.enableBlend();
        Gui.drawScaledCustomSizeModalRect(x + 4, y + 4, 8, 8, 8, 8, 12, 12, 64, 64);
        Gui.drawScaledCustomSizeModalRect(x + 4, y + 4, 40, 8, 8, 8, 12, 12, 64, 64);
        GlStateManager.disableBlend();
        int color = !enabled ? 0xA0A0A0 : hovered ? 0xFFFFA0 : 0xE0E0E0;
        drawString(mc.fontRenderer, label, x + 22, y + (height - 8) / 2, color);
        if (!dimension.isEmpty()) {
            drawCenteredString(mc.fontRenderer, dimension, x + width / 2, y + 22, 0xBBBBBB);
        }
    }
}
