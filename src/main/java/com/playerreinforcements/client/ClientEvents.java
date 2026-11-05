package com.playerreinforcements.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGameOver;
import net.minecraftforge.client.event.GuiOpenEvent;
import net.minecraftforge.client.event.sound.PlaySoundEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.common.network.FMLNetworkEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public final class ClientEvents {
    private final DeathScreenTutorialToasts tutorialToasts = new DeathScreenTutorialToasts();

    @SubscribeEvent
    public void onGuiOpen(GuiOpenEvent event) {
        if (event.getGui() instanceof GuiGameOver && ClientState.isServerActive()) {
            event.setGui(new ReinforcementDeathScreen((GuiGameOver) event.getGui()));
        }
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
        if (mc.currentScreen instanceof ReinforcementDeathScreen
                && ((ReinforcementDeathScreen) mc.currentScreen).replacesVanillaMusic(event.getSound())) {
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
}
