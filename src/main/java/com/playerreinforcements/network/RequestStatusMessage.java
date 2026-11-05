package com.playerreinforcements.network;

import com.playerreinforcements.server.ReinforcementManager;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

public final class RequestStatusMessage implements IMessage {
    @Override
    public void fromBytes(ByteBuf buf) {
    }

    @Override
    public void toBytes(ByteBuf buf) {
    }

    public static final class Handler implements IMessageHandler<RequestStatusMessage, IMessage> {
        @Override
        public IMessage onMessage(RequestStatusMessage message, MessageContext ctx) {
            EntityPlayerMP sender = ctx.getServerHandler().player;
            sender.getServerWorld().addScheduledTask(() -> ReinforcementManager.INSTANCE.sendStatus(sender));
            return null;
        }
    }
}
