package com.playerreinforcements.server;

import com.playerreinforcements.minigame.MinigameSession;
import java.util.UUID;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import org.junit.Test;
import static org.junit.Assert.*;

public final class MinigameHighScoresTest {
    private final UUID first = new UUID(1, 2);
    private final UUID second = new UUID(3, 4);

    @Test
    public void recordsOnlyImproveAndTheGamesHaveSeparateLeaders() {
        MinigameHighScores scores = new MinigameHighScores();
        assertFalse(scores.record(MinigameSession.FLAPPY, first, "First", 0));
        assertTrue(scores.record(MinigameSession.FLAPPY, first, "First", 8));
        assertFalse(scores.record(MinigameSession.FLAPPY, first, "First", 3));
        scores.record(MinigameSession.FLAPPY, second, "Second", 8);
        assertEquals(first, scores.leader(MinigameSession.FLAPPY).player);
        scores.record(MinigameSession.FLAPPY, second, "Second", 9);
        assertEquals(second, scores.leader(MinigameSession.FLAPPY).player);
        scores.record(MinigameSession.TETRIS, first, "First", 40);
        assertEquals(8, scores.best(MinigameSession.FLAPPY, first));
        assertEquals(40, scores.best(MinigameSession.TETRIS, first));
        assertEquals(first, scores.leader(MinigameSession.TETRIS).player);
    }

    @Test
    public void worldSaveReloadKeepsPersonalRecordsAndTheOfflineLeader() {
        ReinforcementsData original = new ReinforcementsData();
        original.highScores.record(MinigameSession.TETRIS, first, "First", 30);
        original.highScores.record(MinigameSession.TETRIS, second, "Second", 80, "cachedSkin", "signedSkin");
        original.highScores.record(MinigameSession.FLAPPY, first, "First", 12);
        ReinforcementsData loaded = new ReinforcementsData();
        loaded.readFromNBT(original.writeToNBT(new NBTTagCompound()));
        assertEquals(30, loaded.highScores.best(MinigameSession.TETRIS, first));
        assertEquals(80, loaded.highScores.leader(MinigameSession.TETRIS).score);
        assertEquals(second, loaded.highScores.leader(MinigameSession.TETRIS).player);
        assertEquals("Second", loaded.highScores.leader(MinigameSession.TETRIS).name);
        assertEquals("cachedSkin", loaded.highScores.leader(MinigameSession.TETRIS).texture);
        assertEquals("signedSkin", loaded.highScores.leader(MinigameSession.TETRIS).textureSignature);
        assertEquals(12, loaded.highScores.best(MinigameSession.FLAPPY, first));
    }

    @Test
    public void renamedPlayersKeepTheirBestAndInvalidSavedEntriesAreIgnored() {
        MinigameHighScores scores = new MinigameHighScores();
        scores.record(MinigameSession.TETRIS, first, "OldName", 40);
        assertTrue(scores.record(MinigameSession.TETRIS, first, "NewName", 10));
        assertEquals(40, scores.best(MinigameSession.TETRIS, first));
        assertEquals("NewName", scores.leader(MinigameSession.TETRIS).name);
        NBTTagList saved = scores.writeToNBT();
        NBTTagCompound invalid = new NBTTagCompound();
        invalid.setInteger("Game", 7);
        invalid.setUniqueId("Player", second);
        invalid.setString("Name", "Invalid");
        invalid.setInteger("Score", 1000);
        saved.appendTag(invalid);
        scores.readFromNBT(saved);
        assertEquals(first, scores.leader(MinigameSession.TETRIS).player);
        scores.readFromNBT(new NBTTagList());
        assertNull(scores.leader(MinigameSession.TETRIS));
    }
}
