package com.playerreinforcements.server;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.INetHandler;
import net.minecraft.network.NetHandlerPlayServer;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.client.CPacketClientStatus;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.network.FMLNetworkEvent;

/** Rejects vanilla respawn packets before they can recreate a waiting player. */
public final class RespawnPacketGuard {
    private static final String HANDLER_NAME = "playerreinforcements_respawn_guard";

    @SubscribeEvent
    public void onConnection(FMLNetworkEvent.ServerConnectionFromClientEvent event) {
        if (!(event.getHandler() instanceof NetHandlerPlayServer)) {
            return;
        }
        NetHandlerPlayServer handler = (NetHandlerPlayServer) event.getHandler();
        MinecraftServer server = handler.player.getServerWorld().getMinecraftServer();
        NetworkManager manager = event.getManager();
        manager.channel().eventLoop().execute(() -> {
            if (manager.channel().pipeline().get(HANDLER_NAME) == null) {
                manager.channel().pipeline().addBefore("packet_handler", HANDLER_NAME,
                        new RespawnGate(manager, server));
            }
        });
    }

    private static final class RespawnGate extends ChannelInboundHandlerAdapter {
        private final NetworkManager manager;
        private final MinecraftServer server;
        private final AtomicBoolean respawnCheckQueued = new AtomicBoolean();

        private RespawnGate(NetworkManager manager, MinecraftServer server) {
            this.manager = manager;
            this.server = server;
        }

        @Override
        public void channelRead(ChannelHandlerContext context, Object message) throws Exception {
            if (!(message instanceof CPacketClientStatus)
                    || ((CPacketClientStatus) message).getStatus()
                    != CPacketClientStatus.State.PERFORM_RESPAWN) {
                context.fireChannelRead(message);
                return;
            }
            // Coalesce repeated button presses without building an unbounded task queue.
            if (respawnCheckQueued.compareAndSet(false, true)) {
                CPacketClientStatus packet = (CPacketClientStatus) message;
                server.addScheduledTask(() -> {
                    try {
                        checkOnServerThread(packet);
                    } finally {
                        respawnCheckQueued.set(false);
                    }
                });
            }
        }

        private void checkOnServerThread(CPacketClientStatus packet) {
            if (!manager.isChannelOpen()) {
                return;
            }
            INetHandler listener = manager.getNetHandler();
            if (!(listener instanceof NetHandlerPlayServer)) {
                return;
            }
            NetHandlerPlayServer handler = (NetHandlerPlayServer) listener;
            EntityPlayerMP player = handler.player;
            if (player.connection != handler
                    || server.getPlayerList().getPlayerByUUID(player.getUniqueID()) != player) {
                return;
            }
            // End credits use the same packet and must retain the vanilla exit path.
            // Check current entity/state here, after any death earlier in this tick.
            if (player.queuedEndExit || ((player.isSpectator() || player.getHealth() > 0.0F)
                    && !ReinforcementManager.INSTANCE.isWaiting(player.getUniqueID()))) {
                handler.processClientStatus(packet);
            }
        }
    }
}
