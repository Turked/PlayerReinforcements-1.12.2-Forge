package com.playerreinforcements.server;

import com.playerreinforcements.minigame.MinigameSession;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

/** Personal records and the world leader for each minigame, including offline players. */
public final class MinigameHighScores {
    private final Map<UUID, Entry> flappy = new LinkedHashMap<>();
    private final Map<UUID, Entry> tetris = new LinkedHashMap<>();

    public boolean record(int game, UUID player, String name, int score) {
        return record(game, player, name, score, "", "");
    }

    public boolean record(int game, UUID player, String name, int score, String texture, String signature) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(name, "name");
        if (name.isEmpty() || name.length() > 64 || score < 0 || texture.length() > 16384 || signature.length() > 8192) {
            throw new IllegalArgumentException("Invalid minigame record");
        }
        Map<UUID, Entry> records = records(game);
        Entry previous = records.get(player);
        int best = Math.max(score, previous == null ? 0 : previous.score);
        if (texture.isEmpty() && previous != null) {
            texture = previous.texture;
            signature = previous.textureSignature;
        }
        if (best == 0 || (previous != null && previous.score == best && previous.name.equals(name)
                && previous.texture.equals(texture) && previous.textureSignature.equals(signature))) return false;
        records.put(player, new Entry(player, name, best, texture, signature));
        return true;
    }

    public int best(int game, UUID player) {
        Entry record = records(game).get(player);
        return record == null ? 0 : record.score;
    }

    public Entry leader(int game) {
        Entry leader = null;
        for (Entry entry : records(game).values()) {
            // Keep the earlier record holder on a tie, including after save/reload.
            if (leader == null || entry.score > leader.score) leader = entry;
        }
        return leader;
    }

    public void readFromNBT(NBTTagList entries) {
        flappy.clear();
        tetris.clear();
        for (int i = 0; i < entries.tagCount(); i++) {
            NBTTagCompound entry = entries.getCompoundTagAt(i);
            int game = entry.getInteger("Game");
            String name = entry.getString("Name");
            int score = entry.getInteger("Score");
            if ((game != MinigameSession.FLAPPY && game != MinigameSession.TETRIS)
                    || !entry.hasUniqueId("Player") || name.isEmpty() || name.length() > 64 || score <= 0) continue;
            String texture = entry.getString("Texture");
            String signature = entry.getString("TextureSignature");
            if (texture.length() > 16384 || signature.length() > 8192) {
                texture = signature = "";
            }
            record(game, entry.getUniqueId("Player"), name, score, texture, signature);
        }
    }

    public NBTTagList writeToNBT() {
        NBTTagList entries = new NBTTagList();
        writeRecords(MinigameSession.FLAPPY, flappy, entries);
        writeRecords(MinigameSession.TETRIS, tetris, entries);
        return entries;
    }

    private static void writeRecords(int game, Map<UUID, Entry> records, NBTTagList entries) {
        for (Entry record : records.values()) {
            NBTTagCompound tag = new NBTTagCompound();
            tag.setInteger("Game", game);
            tag.setUniqueId("Player", record.player);
            tag.setString("Name", record.name);
            tag.setInteger("Score", record.score);
            tag.setString("Texture", record.texture);
            tag.setString("TextureSignature", record.textureSignature);
            entries.appendTag(tag);
        }
    }

    private Map<UUID, Entry> records(int game) {
        if (game == MinigameSession.FLAPPY) return flappy;
        if (game == MinigameSession.TETRIS) return tetris;
        throw new IllegalArgumentException("Unknown minigame");
    }

    public static final class Entry {
        public final UUID player;
        public final String name;
        public final int score;
        public final String texture;
        public final String textureSignature;

        private Entry(UUID player, String name, int score, String texture, String signature) {
            this.player = player;
            this.name = name;
            this.score = score;
            this.texture = texture;
            this.textureSignature = signature;
        }
    }
}
