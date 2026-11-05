package com.playerreinforcements.network;

import com.playerreinforcements.PlayerReinforcements;
import com.playerreinforcements.minigame.MinigameSession;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

/** Server simulation snapshot; never registered as a server-bound packet. */
public final class MinigameStateMessage implements IMessage {
    public MinigameSession session;
    private byte[] snapshot;

    public MinigameStateMessage() { }

    public MinigameStateMessage(MinigameSession session) {
        // Capture on the server thread before the outbound network encoder runs.
        ByteBuf buffer = Unpooled.buffer();
        try {
            session.write(buffer);
            snapshot = new byte[buffer.readableBytes()];
            buffer.readBytes(snapshot);
        } finally {
            buffer.release();
        }
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        session = MinigameSession.read(buf);
    }

    @Override
    public void toBytes(ByteBuf buf) {
        if (snapshot != null) buf.writeBytes(snapshot);
        else session.write(buf);
    }

    public static final class Handler implements IMessageHandler<MinigameStateMessage, IMessage> {
        @Override
        public IMessage onMessage(MinigameStateMessage message, MessageContext ctx) {
            PlayerReinforcements.PROXY.receiveMinigame(message);
            return null;
        }
    }
}
