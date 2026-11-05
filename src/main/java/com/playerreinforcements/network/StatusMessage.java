package com.playerreinforcements.network;

import com.playerreinforcements.PlayerReinforcements;
import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

public final class StatusMessage implements IMessage {
    private static final int MAX_TEAMMATES = 1024;
    public boolean waiting;
    public int remainingTicks;
    public boolean groupWipe;
    public boolean showTeammateDimensions;
    public boolean deathMinigamesEnabled;
    public boolean minigameReducesRespawnTimer;
    public List<Teammate> teammates = Collections.emptyList();
    public String error = "";

    public StatusMessage() {
    }

    public StatusMessage(boolean waiting, int remainingTicks, boolean groupWipe, List<Teammate> teammates, String error) {
        this(waiting, remainingTicks, groupWipe, teammates, error, false);
    }

    public StatusMessage(boolean waiting, int remainingTicks, boolean groupWipe, List<Teammate> teammates, String error,
                         boolean showTeammateDimensions) {
        this(waiting, remainingTicks, groupWipe, teammates, error, showTeammateDimensions, false, false);
    }

    public StatusMessage(boolean waiting, int remainingTicks, boolean groupWipe, List<Teammate> teammates, String error,
                         boolean showTeammateDimensions, boolean deathMinigamesEnabled, boolean minigameReducesRespawnTimer) {
        this.waiting = waiting;
        this.remainingTicks = Math.max(0, remainingTicks);
        this.groupWipe = groupWipe;
        this.showTeammateDimensions = showTeammateDimensions;
        this.deathMinigamesEnabled = deathMinigamesEnabled;
        this.minigameReducesRespawnTimer = minigameReducesRespawnTimer;
        this.teammates = Collections.unmodifiableList(new ArrayList<>(teammates));
        this.error = error == null ? "" : error;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        waiting = buf.readBoolean();
        remainingTicks = Math.max(0, buf.readInt());
        groupWipe = buf.readBoolean();
        showTeammateDimensions = buf.readBoolean();
        deathMinigamesEnabled = buf.readBoolean();
        minigameReducesRespawnTimer = buf.readBoolean();
        int count = buf.readInt();
        if (count < 0 || count > MAX_TEAMMATES) {
            throw new IllegalArgumentException("Invalid reinforcement teammate count");
        }
        List<Teammate> decoded = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            UUID id = new UUID(buf.readLong(), buf.readLong());
            String name = ByteBufUtils.readUTF8String(buf);
            if (name.length() > 64) {
                throw new IllegalArgumentException("Invalid reinforcement teammate name");
            }
            decoded.add(new Teammate(id, name, showTeammateDimensions ? buf.readInt() : 0));
        }
        teammates = Collections.unmodifiableList(decoded);
        error = ByteBufUtils.readUTF8String(buf);
        if (error.length() > 512) {
            throw new IllegalArgumentException("Invalid reinforcement status text");
        }
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeBoolean(waiting);
        buf.writeInt(remainingTicks);
        buf.writeBoolean(groupWipe);
        buf.writeBoolean(showTeammateDimensions);
        buf.writeBoolean(deathMinigamesEnabled);
        buf.writeBoolean(minigameReducesRespawnTimer);
        int count = Math.min(MAX_TEAMMATES, teammates.size());
        buf.writeInt(count);
        for (int i = 0; i < count; i++) {
            Teammate teammate = teammates.get(i);
            buf.writeLong(teammate.id.getMostSignificantBits());
            buf.writeLong(teammate.id.getLeastSignificantBits());
            ByteBufUtils.writeUTF8String(buf, teammate.name);
            if (showTeammateDimensions) {
                buf.writeInt(teammate.dimension);
            }
        }
        ByteBufUtils.writeUTF8String(buf, error);
    }

    public static final class Teammate {
        public final UUID id;
        public final String name;
        public final int dimension;

        public Teammate(UUID id, String name, int dimension) {
            this.id = id;
            this.name = name;
            this.dimension = dimension;
        }
    }

    public static final class Handler implements IMessageHandler<StatusMessage, IMessage> {
        @Override
        public IMessage onMessage(StatusMessage message, MessageContext ctx) {
            PlayerReinforcements.PROXY.receiveStatus(message);
            return null;
        }
    }
}
