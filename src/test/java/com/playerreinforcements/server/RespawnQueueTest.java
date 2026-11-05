package com.playerreinforcements.server;

import java.util.Map;
import java.util.UUID;
import org.junit.Test;

import static org.junit.Assert.*;

public final class RespawnQueueTest {
    private final UUID first = new UUID(0L, 1L);
    private final UUID second = new UUID(0L, 2L);

    @Test
    public void eligibilityBeginsExactlyAtDeadlineButPlayerStaysDead() {
        RespawnQueue queue = new RespawnQueue();
        queue.markDead(first, 100L);

        assertEquals(2400L, queue.remaining(first, 100L));
        assertEquals(1L, queue.remaining(first, 2499L));
        assertEquals(0L, queue.remaining(first, 2500L));
        assertEquals(0L, queue.remaining(first, 3000L));
        assertTrue(queue.isWaiting(first));
    }

    @Test
    public void duplicateDeathCannotExtendCooldown() {
        RespawnQueue queue = new RespawnQueue();
        assertTrue(queue.markDead(first, 100L));
        assertFalse(queue.markDead(first, 1000L));
        assertEquals(1500L, queue.remaining(first, 1000L));
    }

    @Test
    public void savedDeadlinesSurviveReloadWithoutRestartingOfflineWaits() {
        RespawnQueue original = new RespawnQueue();
        original.markDead(first, 100L);
        original.markDead(second, 1100L);
        RespawnQueue reloaded = new RespawnQueue();
        for (Map.Entry<UUID, Long> entry : original.snapshot().entrySet()) {
            reloaded.restore(entry.getKey(), entry.getValue());
        }

        assertEquals(500L, reloaded.remaining(first, 2000L));
        assertEquals(1500L, reloaded.remaining(second, 2000L));
        assertTrue(reloaded.isWaiting(first));
        assertEquals(0L, reloaded.remaining(first, 4000L));
        assertTrue(reloaded.isWaiting(first));
    }

    @Test
    public void onlyCompletedRespawnAllowsNewDeathToStartNewCooldown() {
        RespawnQueue queue = new RespawnQueue();
        queue.markDead(first, 100L);
        assertTrue(queue.remove(first));
        assertFalse(queue.remove(first));
        assertFalse(queue.isWaiting(first));
        assertEquals(0L, queue.remaining(first, 1000L));
        assertTrue(queue.markDead(first, 1000L));
        assertEquals(2400L, queue.remaining(first, 1000L));
    }

    @Test
    public void snapshotIsDetachedFromLaterRespawns() {
        RespawnQueue queue = new RespawnQueue();
        queue.markDead(first, 100L);
        Map<UUID, Long> saved = queue.snapshot();
        queue.remove(first);
        assertEquals(Long.valueOf(2500L), saved.get(first));
    }

    @Test
    public void onePointRemovesTenSecondsAndPreservesWaitingState() {
        RespawnQueue queue = new RespawnQueue();
        queue.markDead(first, 100L);
        assertTrue(queue.reduce(first, 200L, 200L));
        assertEquals(2100L, queue.remaining(first, 200L));
        assertTrue(queue.isWaiting(first));
        assertFalse(queue.markDead(first, 200L));
        assertEquals(2100L, queue.remaining(first, 200L));
    }

    @Test
    public void rewardClampsAtNowAndCannotExtendAnExpiredDeadline() {
        RespawnQueue queue = new RespawnQueue();
        queue.markDead(first, 100L);
        assertTrue(queue.reduce(first, Long.MAX_VALUE, 200L));
        assertEquals(Long.valueOf(200L), queue.snapshot().get(first));
        assertEquals(0L, queue.remaining(first, 200L));
        assertFalse(queue.reduce(first, 200L, 300L));
        assertEquals(Long.valueOf(200L), queue.snapshot().get(first));
        assertTrue(queue.isWaiting(first));
    }

    @Test
    public void zeroRewardAndAbsentPlayerDoNotChangeQueue() {
        RespawnQueue queue = new RespawnQueue();
        queue.markDead(first, 100L);
        assertFalse(queue.reduce(first, 0L, 200L));
        assertFalse(queue.reduce(second, 200L, 200L));
        assertEquals(Long.valueOf(2500L), queue.snapshot().get(first));
        assertFalse(queue.isWaiting(second));
    }

    @Test
    public void reducedDeadlineSurvivesSavingAndNearMaximumWorldTime() {
        RespawnQueue original = new RespawnQueue();
        long now = Long.MAX_VALUE - 100L;
        original.markDead(first, now);
        assertTrue(original.reduce(first, 40L, now));
        RespawnQueue reloaded = new RespawnQueue();
        for (Map.Entry<UUID, Long> entry : original.snapshot().entrySet()) {
            reloaded.restore(entry.getKey(), entry.getValue());
        }
        assertEquals(60L, reloaded.remaining(first, now));
        assertTrue(reloaded.isWaiting(first));
    }

    @Test(expected = IllegalArgumentException.class)
    public void negativeRewardIsRejected() {
        new RespawnQueue().reduce(first, -1L, 0L);
    }

    @Test(expected = IllegalArgumentException.class)
    public void negativeRewardTimeIsRejected() {
        new RespawnQueue().reduce(first, 0L, -1L);
    }

    @Test(expected = UnsupportedOperationException.class)
    public void callerCannotMutateSavedSnapshot() {
        RespawnQueue queue = new RespawnQueue();
        queue.markDead(first, 100L);
        queue.snapshot().clear();
    }
}
