package com.playerreinforcements.network;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.util.UUID;
import org.junit.Test;
import static org.junit.Assert.*;

public final class MinigameScoresMessageTest {
    @Test
    public void personalAndOfflineLeaderScoresRoundTrip() {
        UUID leader = new UUID(0x1122334455667788L, -887766554433L);
        MinigameScoresMessage result = roundTrip(new MinigameScoresMessage(1, 40, leader, "RecordHolder", 80,
                "savedTexture", "textureSignature"));
        assertEquals(1, result.game);
        assertEquals(40, result.personalBest);
        assertEquals(leader, result.leaderId);
        assertEquals("RecordHolder", result.leaderName);
        assertEquals(80, result.leaderBest);
        assertEquals("savedTexture", result.leaderTexture);
        assertEquals("textureSignature", result.leaderTextureSignature);
    }

    @Test
    public void newWorldHasNoLeader() {
        MinigameScoresMessage result = roundTrip(new MinigameScoresMessage(0, 0, null, "", 0));
        assertNull(result.leaderId);
        assertEquals(0, result.personalBest);
        assertEquals(0, result.leaderBest);
    }

    @Test(expected = IllegalArgumentException.class)
    public void personalRecordCannotExceedTheWorldRecord() {
        roundTrip(new MinigameScoresMessage(0, 50, new UUID(1, 2), "Leader", 20));
    }

    @Test(expected = IllegalArgumentException.class)
    public void unknownGameIsRejected() {
        roundTrip(new MinigameScoresMessage(2, 0, null, "", 0));
    }

    private static MinigameScoresMessage roundTrip(MinigameScoresMessage original) {
        ByteBuf buffer = Unpooled.buffer();
        try {
            original.toBytes(buffer);
            MinigameScoresMessage received = new MinigameScoresMessage();
            received.fromBytes(buffer);
            assertEquals(0, buffer.readableBytes());
            return received;
        } finally {
            buffer.release();
        }
    }
}
