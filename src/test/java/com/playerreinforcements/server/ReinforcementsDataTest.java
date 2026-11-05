package com.playerreinforcements.server;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.UUID;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import org.junit.Test;

import static org.junit.Assert.*;

public final class ReinforcementsDataTest {
    @Test
    public void firstJoinIsRecordedOnceAndSurvivesCompressedReload() throws Exception {
        UUID first = new UUID(11L, 12L);
        UUID newcomer = new UUID(13L, 14L);
        ReinforcementsData original = new ReinforcementsData();
        assertTrue(original.markJoined(first));
        assertTrue(original.isDirty());
        original.setDirty(false);
        assertFalse(original.markJoined(first));
        assertFalse(original.isDirty());

        ByteArrayOutputStream savedBytes = new ByteArrayOutputStream();
        CompressedStreamTools.writeCompressed(original.writeToNBT(new NBTTagCompound()), savedBytes);
        ReinforcementsData reloaded = new ReinforcementsData();
        reloaded.readFromNBT(CompressedStreamTools.readCompressed(
                new ByteArrayInputStream(savedBytes.toByteArray())));

        assertFalse(reloaded.markJoined(first));
        assertTrue(reloaded.markJoined(newcomer));
    }

    @Test
    public void oldWorldPlayerFilesPreventReturningPlayersFromBeingFirstJoins() {
        UUID returning = new UUID(21L, 22L);
        UUID newcomer = new UUID(23L, 24L);
        ReinforcementsData migrated = new ReinforcementsData();
        migrated.readFromNBT(new NBTTagCompound());
        migrated.rememberExistingPlayers(new String[] {returning.toString(), "old-player-name",
                returning + ".dat_old", "unrelated-file"});

        assertFalse(migrated.markJoined(returning));
        assertTrue(migrated.markJoined(newcomer));
        assertEquals(2, migrated.writeToNBT(new NBTTagCompound()).getTagList("JoinedPlayers", 10).tagCount());
    }

    @Test
    public void reloadReplacesJoinedPlayersAndIgnoresMissingUuid() {
        UUID old = new UUID(31L, 32L);
        UUID saved = new UUID(33L, 34L);
        ReinforcementsData data = new ReinforcementsData();
        data.markJoined(old);
        NBTTagList joined = new NBTTagList();
        NBTTagCompound valid = new NBTTagCompound();
        valid.setUniqueId("Player", saved);
        joined.appendTag(valid);
        joined.appendTag(new NBTTagCompound());
        NBTTagCompound tag = new NBTTagCompound();
        tag.setTag("JoinedPlayers", joined);
        data.readFromNBT(tag);

        assertFalse(data.markJoined(saved));
        assertTrue(data.markJoined(old));
        data.readFromNBT(new NBTTagCompound());
        assertTrue(data.markJoined(saved));
    }

    @Test
    public void compressedSaveReloadPreservesOfflinePlayersAndOriginalDeadlines() throws Exception {
        UUID first = new UUID(0L, 1L);
        UUID second = new UUID(-1L, Long.MIN_VALUE);
        ReinforcementsData original = new ReinforcementsData();
        original.queue.markDead(first, 100L);
        original.queue.markDead(second, 1100L);

        ByteArrayOutputStream savedBytes = new ByteArrayOutputStream();
        CompressedStreamTools.writeCompressed(original.writeToNBT(new NBTTagCompound()), savedBytes);
        ReinforcementsData reloaded = new ReinforcementsData();
        reloaded.readFromNBT(CompressedStreamTools.readCompressed(
                new ByteArrayInputStream(savedBytes.toByteArray())));

        assertEquals(original.queue.snapshot(), reloaded.queue.snapshot());
        assertEquals(500L, reloaded.queue.remaining(first, 2000L));
        assertEquals(1500L, reloaded.queue.remaining(second, 2000L));
        assertTrue(reloaded.queue.isWaiting(first));
        assertTrue(reloaded.queue.isWaiting(second));
        assertEquals(0L, reloaded.queue.remaining(second, 4000L));
        assertTrue(reloaded.queue.isWaiting(second));
        original.queue.remove(first);
        assertTrue(reloaded.queue.isWaiting(first));
    }

    @Test
    public void reloadReplacesPriorStateAndIgnoresEntriesWithoutPlayerUuid() {
        UUID stalePlayer = new UUID(0L, 1L);
        UUID savedPlayer = new UUID(0L, 2L);
        ReinforcementsData data = new ReinforcementsData();
        data.queue.markDead(stalePlayer, 100L);
        NBTTagList entries = new NBTTagList();
        NBTTagCompound valid = new NBTTagCompound();
        valid.setUniqueId("Player", savedPlayer);
        valid.setLong("ReadyAt", 3500L);
        entries.appendTag(valid);
        NBTTagCompound unrelated = new NBTTagCompound();
        unrelated.setLong("ReadyAt", 99999L);
        entries.appendTag(unrelated);
        NBTTagCompound saved = new NBTTagCompound();
        saved.setTag("Waiting", entries);

        data.readFromNBT(saved);

        assertFalse(data.queue.isWaiting(stalePlayer));
        assertEquals(1, data.queue.snapshot().size());
        assertEquals(1500L, data.queue.remaining(savedPlayer, 2000L));
        data.readFromNBT(new NBTTagCompound());
        assertTrue(data.queue.snapshot().isEmpty());
    }
}
