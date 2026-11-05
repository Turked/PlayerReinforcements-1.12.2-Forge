package com.playerreinforcements.network;

import com.playerreinforcements.PlayerReinforcements;
import com.playerreinforcements.minigame.MinigameSession;
import io.netty.buffer.ByteBuf;
import java.util.UUID;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

/** Sent separately from movement snapshots, only when a record changes or a game starts. */
public final class MinigameScoresMessage implements IMessage {
    public int game;
    public int personalBest;
    public UUID leaderId;
    public String leaderName = "";
    public int leaderBest;
    public String leaderTexture = "";
    public String leaderTextureSignature = "";

    public MinigameScoresMessage() { }

    public MinigameScoresMessage(int game, int personalBest, UUID leaderId, String leaderName, int leaderBest) {
        this(game, personalBest, leaderId, leaderName, leaderBest, "", "");
    }

    public MinigameScoresMessage(int game, int personalBest, UUID leaderId, String leaderName, int leaderBest,
                                String leaderTexture, String leaderTextureSignature) {
        this.game = game;
        this.personalBest = personalBest;
        this.leaderId = leaderId;
        this.leaderName = leaderName;
        this.leaderBest = leaderBest;
        this.leaderTexture = leaderTexture;
        this.leaderTextureSignature = leaderTextureSignature;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        game = buf.readUnsignedByte();
        personalBest = buf.readInt();
        if (buf.readBoolean()) {
            leaderId = new UUID(buf.readLong(), buf.readLong());
            leaderName = ByteBufUtils.readUTF8String(buf);
            leaderBest = buf.readInt();
            leaderTexture = ByteBufUtils.readUTF8String(buf);
            leaderTextureSignature = ByteBufUtils.readUTF8String(buf);
        } else {
            leaderId = null;
            leaderName = "";
            leaderBest = 0;
            leaderTexture = leaderTextureSignature = "";
        }
        if ((game != MinigameSession.FLAPPY && game != MinigameSession.TETRIS) || personalBest < 0
                || leaderBest < personalBest || (leaderId != null && (leaderName.isEmpty()
                    || leaderName.length() > 64 || leaderBest <= 0))
                || leaderTexture.length() > 16384 || leaderTextureSignature.length() > 8192) {
            throw new IllegalArgumentException("Invalid minigame high scores");
        }
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeByte(game);
        buf.writeInt(personalBest);
        buf.writeBoolean(leaderId != null);
        if (leaderId != null) {
            buf.writeLong(leaderId.getMostSignificantBits());
            buf.writeLong(leaderId.getLeastSignificantBits());
            ByteBufUtils.writeUTF8String(buf, leaderName);
            buf.writeInt(leaderBest);
            ByteBufUtils.writeUTF8String(buf, leaderTexture);
            ByteBufUtils.writeUTF8String(buf, leaderTextureSignature);
        }
    }

    public static final class Handler implements IMessageHandler<MinigameScoresMessage, IMessage> {
        @Override
        public IMessage onMessage(MinigameScoresMessage message, MessageContext ctx) {
            PlayerReinforcements.PROXY.receiveScores(message);
            return null;
        }
    }
}
