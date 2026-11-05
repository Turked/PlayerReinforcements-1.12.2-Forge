package com.playerreinforcements.network;

import com.playerreinforcements.server.ReinforcementManager;
import io.netty.buffer.ByteBuf;
import java.util.UUID;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

public final class RequestRespawnMessage implements IMessage {
    private UUID target;

    public RequestRespawnMessage() {
    }

    public RequestRespawnMessage(UUID target) {
        this.target = target;
    }

    public UUID getTarget() {
        return target;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        target = buf.readBoolean() ? new UUID(buf.readLong(), buf.readLong()) : null;
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeBoolean(target != null);
        if (target != null) {
            buf.writeLong(target.getMostSignificantBits());
            buf.writeLong(target.getLeastSignificantBits());
        }
    }

    public static final class Handler implements IMessageHandler<RequestRespawnMessage, IMessage> {
        @Override
        public IMessage onMessage(RequestRespawnMessage message, MessageContext ctx) {
            EntityPlayerMP sender = ctx.getServerHandler().player;
            sender.getServerWorld().addScheduledTask(() -> ReinforcementManager.INSTANCE.requestRespawn(sender, message.target));
            return null;
        }
    }
}
