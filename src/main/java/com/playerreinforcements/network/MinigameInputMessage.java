package com.playerreinforcements.network;

import com.playerreinforcements.server.ReinforcementManager;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

/** Carries controls only. Clients cannot submit scores or timer reductions. */
public final class MinigameInputMessage implements IMessage {
    public int startType = -1;
    public int controls;

    public MinigameInputMessage() { }

    public MinigameInputMessage(int startType, int controls) {
        this.startType = startType;
        this.controls = controls;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        startType = buf.readByte();
        controls = buf.readUnsignedByte();
        if (startType < -1 || startType > 1 || (controls & ~63) != 0) {
            throw new IllegalArgumentException("Invalid minigame controls");
        }
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeByte(startType);
        buf.writeByte(controls);
    }

    public static final class Handler implements IMessageHandler<MinigameInputMessage, IMessage> {
        @Override
        public IMessage onMessage(MinigameInputMessage message, MessageContext ctx) {
            EntityPlayerMP sender = ctx.getServerHandler().player;
            sender.getServerWorld().addScheduledTask(() ->
                    ReinforcementManager.INSTANCE.minigameInput(sender, message.startType, message.controls));
            return null;
        }
    }
}
