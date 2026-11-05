package com.playerreinforcements.network;

import com.playerreinforcements.minigame.MinigameSession;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.Test;
import static org.junit.Assert.*;

public final class MinigameMessageTest {
    @Test
    public void outboundFlappySnapshotIsFrozenBeforeTheSimulationAdvances() {
        assertFrozenSnapshot(MinigameSession.FLAPPY, MinigameSession.FLAP);
    }

    @Test
    public void outboundTetrisSnapshotIsFrozenBeforeTheBoardChanges() {
        assertFrozenSnapshot(MinigameSession.TETRIS, MinigameSession.HARD_DROP);
    }

    @Test
    public void everySupportedControlAndGameSelectionRoundTrips() {
        for (int type = -1; type <= 1; type++) {
            ByteBuf buffer = Unpooled.buffer();
            try {
                new MinigameInputMessage(type, MinigameSession.ALL_CONTROLS).toBytes(buffer);
                MinigameInputMessage result = new MinigameInputMessage();
                result.fromBytes(buffer);
                assertEquals(type, result.startType);
                assertEquals(MinigameSession.ALL_CONTROLS, result.controls);
                assertEquals(0, buffer.readableBytes());
            } finally {
                buffer.release();
            }
        }
    }

    @Test
    public void unknownGameSelectionsAreRejected() {
        assertInvalidInput(-2, 0);
        assertInvalidInput(2, 0);
        assertInvalidInput(127, 0);
    }

    @Test
    public void unknownControlBitsAreRejected() {
        assertInvalidInput(-1, 64);
        assertInvalidInput(-1, 128);
        assertInvalidInput(-1, 255);
    }

    @Test(expected = IndexOutOfBoundsException.class)
    public void incompleteControlsCannotDecodeAsAnAction() {
        ByteBuf buffer = Unpooled.buffer();
        try {
            buffer.writeByte(-1);
            new MinigameInputMessage().fromBytes(buffer);
        } finally {
            buffer.release();
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void unknownSnapshotGameTypeIsRejected() {
        ByteBuf buffer = Unpooled.buffer();
        try {
            buffer.writeByte(2);
            new MinigameStateMessage().fromBytes(buffer);
        } finally {
            buffer.release();
        }
    }

    private static void assertFrozenSnapshot(int type, int nextControls) {
        MinigameSession source = new MinigameSession(type, 1234L);
        ByteBuf captured = Unpooled.buffer();
        ByteBuf encoded = Unpooled.buffer();
        ByteBuf repeated = Unpooled.buffer();
        ByteBuf decodedBytes = Unpooled.buffer();
        ByteBuf advanced = Unpooled.buffer();
        try {
            source.write(captured);
            MinigameStateMessage outbound = new MinigameStateMessage(source);
            source.tick(nextControls);
            source.write(advanced);
            assertFalse("The simulation must change after capture", captured.equals(advanced));
            outbound.toBytes(encoded);
            outbound.toBytes(repeated);
            assertEquals(captured, encoded);
            assertEquals("Encoding must not consume the captured snapshot", captured, repeated);

            MinigameStateMessage received = new MinigameStateMessage();
            received.fromBytes(encoded);
            assertEquals(0, encoded.readableBytes());
            assertEquals(type, received.session.type());
            received.toBytes(decodedBytes);
            assertEquals("Every rendering field must survive the round trip", captured, decodedBytes);
        } finally {
            captured.release();
            encoded.release();
            repeated.release();
            decodedBytes.release();
            advanced.release();
        }
    }

    private static void assertInvalidInput(int startType, int controls) {
        ByteBuf buffer = Unpooled.buffer();
        try {
            buffer.writeByte(startType);
            buffer.writeByte(controls);
            try {
                new MinigameInputMessage().fromBytes(buffer);
                fail("Unsupported minigame controls should be rejected");
            } catch (IllegalArgumentException expected) {
                assertEquals("Invalid minigame controls", expected.getMessage());
            }
        } finally {
            buffer.release();
        }
    }
}
