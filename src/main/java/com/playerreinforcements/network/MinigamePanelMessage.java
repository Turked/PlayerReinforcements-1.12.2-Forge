package com.playerreinforcements.network;

import com.playerreinforcements.server.ReinforcementManager;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

/** Opening a panel requests the existing records without starting a game. */
public final class MinigamePanelMessage implements IMessage {
    private static final int OPEN = 1;
    private static final int WAITING = 2;
    boolean open;
    boolean waiting;

    public MinigamePanelMessage() { }

    public MinigamePanelMessage(boolean open, boolean waiting) {
        this.open = open;
        this.waiting = waiting;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        int flags = buf.readUnsignedByte();
        if ((flags & ~(OPEN | WAITING)) != 0) {
            throw new IllegalArgumentException("Invalid minigame panel state");
        }
        open = (flags & OPEN) != 0;
        waiting = (flags & WAITING) != 0;
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeByte((open ? OPEN : 0) | (waiting ? WAITING : 0));
    }

    public static final class Handler implements IMessageHandler<MinigamePanelMessage, IMessage> {
        @Override
        public IMessage onMessage(MinigamePanelMessage message, MessageContext ctx) {
            EntityPlayerMP sender = ctx.getServerHandler().player;
            sender.getServerWorld().addScheduledTask(() ->
                    ReinforcementManager.INSTANCE.minigamePanel(sender, message.open, message.waiting));
            return null;
        }
    }
}
