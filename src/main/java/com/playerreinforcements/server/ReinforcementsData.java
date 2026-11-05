package com.playerreinforcements.server;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.world.storage.WorldSavedData;
import net.minecraft.world.WorldServer;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Global overworld storage, shared by players in every dimension. */
public final class ReinforcementsData extends WorldSavedData {
    private static final String NAME = "playerreinforcements_waiting";
    public RespawnQueue queue = new RespawnQueue();
    public final MinigameHighScores highScores = new MinigameHighScores();
    private final Set<UUID> joinedPlayers = new LinkedHashSet<>();

    public ReinforcementsData() { super(NAME); }
    public ReinforcementsData(String name) { super(name); }

    public static ReinforcementsData get(WorldServer overworld) {
        ReinforcementsData data = (ReinforcementsData) overworld.getMapStorage().getOrLoadData(ReinforcementsData.class, NAME);
        if (data == null) {
            data = new ReinforcementsData();
            overworld.getMapStorage().setData(NAME, data);
        }
        return data;
    }

    /** Returns true only for the first recorded login in this world. */
    public boolean markJoined(UUID player) {
        if (!joinedPlayers.add(Objects.requireNonNull(player, "player"))) return false;
        markDirty();
        return true;
    }

    /** Seed old worlds from vanilla saves before any new login can create a player file. */
    public void rememberExistingPlayers(String[] savedPlayers) {
        for (String player : savedPlayers) {
            try {
                markJoined(UUID.fromString(player));
            } catch (IllegalArgumentException ignored) {
                // Vanilla's directory listing can also include backup files and legacy names.
            }
        }
    }

    @Override
    public void readFromNBT(NBTTagCompound tag) {
        queue = new RespawnQueue();
        joinedPlayers.clear();
        NBTTagList joined = tag.getTagList("JoinedPlayers", 10);
        for (int i = 0; i < joined.tagCount(); i++) {
            NBTTagCompound entry = joined.getCompoundTagAt(i);
            if (entry.hasUniqueId("Player")) joinedPlayers.add(entry.getUniqueId("Player"));
        }
        highScores.readFromNBT(tag.getTagList("MinigameHighScores", 10));
        NBTTagList entries = tag.getTagList("Waiting", 10);
        for (int i = 0; i < entries.tagCount(); i++) {
            NBTTagCompound entry = entries.getCompoundTagAt(i);
            if (entry.hasUniqueId("Player")) {
                queue.restore(entry.getUniqueId("Player"), entry.getLong("ReadyAt"));
            }
        }
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound tag) {
        NBTTagList joined = new NBTTagList();
        for (UUID player : joinedPlayers) {
            NBTTagCompound entry = new NBTTagCompound();
            entry.setUniqueId("Player", player);
            joined.appendTag(entry);
        }
        tag.setTag("JoinedPlayers", joined);
        tag.setTag("MinigameHighScores", highScores.writeToNBT());
        NBTTagList entries = new NBTTagList();
        for (Map.Entry<UUID, Long> pending : queue.snapshot().entrySet()) {
            NBTTagCompound entry = new NBTTagCompound();
            entry.setUniqueId("Player", pending.getKey());
            entry.setLong("ReadyAt", pending.getValue());
            entries.appendTag(entry);
        }
        tag.setTag("Waiting", entries);
        return tag;
    }
}
