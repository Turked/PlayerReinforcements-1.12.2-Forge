package com.playerreinforcements.client;

import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiGameOver;
import net.minecraft.client.gui.GuiIngameMenu;
import net.minecraftforge.client.event.GuiOpenEvent;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.client.event.sound.PlaySoundEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.common.network.FMLNetworkEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public final class ClientEvents {
    private static final int OPEN_TO_LAN = 7;
    private static final int MENU_BUTTON_GAP = 4;
    private final DeathScreenTutorialToasts tutorialToasts = new DeathScreenTutorialToasts();

    @SubscribeEvent
    public void onGuiOpen(GuiOpenEvent event) {
        if (event.getGui() instanceof GuiGameOver && ClientState.isServerActive()
                && ClientState.getStatus().waiting) {
            event.setGui(new ReinforcementDeathScreen((GuiGameOver) event.getGui()));
        }
    }

    @SubscribeEvent
    public void onMenuInit(GuiScreenEvent.InitGuiEvent.Post event) {
        if (!(event.getGui() instanceof GuiIngameMenu) || !MinigamesScreen.isAvailable()) return;
        List<GuiButton> buttons = event.getButtonList();
        for (int i = 0; i < buttons.size(); i++) {
            GuiButton lan = buttons.get(i);
            if (lan.id != OPEN_TO_LAN || !lan.visible) continue;
            int rowWidth = lan.getButtonWidth();
            int buttonWidth = (rowWidth - MENU_BUTTON_GAP) / 2;
            lan.setWidth(buttonWidth);
            buttons.add(i + 1, new MinigamesButton(lan.x + buttonWidth + MENU_BUTTON_GAP, lan.y,
                    rowWidth - buttonWidth - MENU_BUTTON_GAP, lan.height));
            return;
        }
    }

    @SubscribeEvent
    public void onMenuAction(GuiScreenEvent.ActionPerformedEvent.Pre event) {
        if (!(event.getGui() instanceof GuiIngameMenu) || !(event.getButton() instanceof MinigamesButton)) return;
        event.setCanceled(true);
        if (!MinigamesScreen.isAvailable()) return;
        Minecraft mc = Minecraft.getMinecraft();
        event.getButton().playPressSound(mc.getSoundHandler());
        mc.displayGuiScreen(new MinigamesScreen(event.getGui()));
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            ClientState.tick();
        }
    }

    @SubscribeEvent
    public void onRenderTick(TickEvent.RenderTickEvent event) {
        if (event.phase == TickEvent.Phase.START) {
            tutorialToasts.beforeRender(Minecraft.getMinecraft());
        }
    }

    @SubscribeEvent
    public void onPlaySound(PlaySoundEvent event) {
        Minecraft mc = Minecraft.getMinecraft();
        if ((mc.currentScreen instanceof ReinforcementDeathScreen
                && ((ReinforcementDeathScreen) mc.currentScreen).replacesVanillaMusic(event.getSound()))
                || (mc.currentScreen instanceof MinigamesScreen
                && ((MinigamesScreen) mc.currentScreen).replacesVanillaMusic(event.getSound()))) {
            event.setResultSound(null);
        }
    }

    @SubscribeEvent
    public void onDisconnected(FMLNetworkEvent.ClientDisconnectionFromServerEvent event) {
        Minecraft.getMinecraft().addScheduledTask(ClientState::reset);
    }

    @SubscribeEvent
    public void onConnected(FMLNetworkEvent.ClientConnectedToServerEvent event) {
        Minecraft.getMinecraft().addScheduledTask(ClientState::reset);
    }

    private static final class MinigamesButton extends GuiButton {
        private MinigamesButton(int x, int y, int width, int height) {
            super(-1, x, y, width, height, MinigamesScreen.title());
        }
    }
}
