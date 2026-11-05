package com.playerreinforcements.network;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.util.Arrays;
import java.util.Collections;
import java.util.UUID;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import org.junit.Test;
import static org.junit.Assert.*;

public class NetworkMessageTest {
    @Test
    public void respawnRequestPreservesBothUuidHalves() {
        UUID target = new UUID(0x1122334455667788L, 0x8877665544332211L);
        RequestRespawnMessage decoded = roundTrip(new RequestRespawnMessage(target));
        assertEquals(target, decoded.getTarget());
    }

    @Test
    public void absentTargetRemainsAbsent() {
        assertNull(roundTrip(new RequestRespawnMessage(null)).getTarget());
    }

    @Test
    public void enabledDimensionOptionPreservesCooldownRosterDimensionsAndError() {
        UUID first = new UUID(1L, 2L);
        UUID second = new UUID(3L, 4L);
        StatusMessage source = new StatusMessage(true, 137, false, Arrays.asList(
                new StatusMessage.Teammate(first, "First_Player", 0),
                new StatusMessage.Teammate(second, "Second_Player", -1)),
                "playerreinforcements.error.location", true);
        StatusMessage decoded = roundTrip(source);
        assertTrue(decoded.waiting);
        assertEquals(137, decoded.remainingTicks);
        assertFalse(decoded.groupWipe);
        assertTrue(decoded.showTeammateDimensions);
        assertEquals(2, decoded.teammates.size());
        assertEquals(first, decoded.teammates.get(0).id);
        assertEquals("First_Player", decoded.teammates.get(0).name);
        assertEquals(0, decoded.teammates.get(0).dimension);
        assertEquals(second, decoded.teammates.get(1).id);
        assertEquals(-1, decoded.teammates.get(1).dimension);
        assertEquals(source.error, decoded.error);
    }

    @Test
    public void defaultStatusHidesDimensionsAndDecodesThemAsZero() {
        StatusMessage decoded = roundTrip(new StatusMessage(true, 137, false, Arrays.asList(
                new StatusMessage.Teammate(new UUID(1L, 2L), "First_Player", -1),
                new StatusMessage.Teammate(new UUID(3L, 4L), "Second_Player", 111)), ""));
        assertFalse(decoded.showTeammateDimensions);
        assertEquals(0, decoded.teammates.get(0).dimension);
        assertEquals(0, decoded.teammates.get(1).dimension);
    }

    @Test
    public void hiddenDimensionDoesNotAffectPacketBytes() {
        UUID id = new UUID(1L, 2L);
        StatusMessage first = new StatusMessage(true, 137, false, Collections.singletonList(
                new StatusMessage.Teammate(id, "Player", -1)), "", false);
        StatusMessage second = new StatusMessage(true, 137, false, Collections.singletonList(
                new StatusMessage.Teammate(id, "Player", 0x55667788)), "", false);
        ByteBuf firstBytes = Unpooled.buffer();
        ByteBuf secondBytes = Unpooled.buffer();
        ByteBuf visibleBytes = Unpooled.buffer();
        try {
            first.toBytes(firstBytes);
            second.toBytes(secondBytes);
            new StatusMessage(true, 137, false, first.teammates, "", true).toBytes(visibleBytes);
            assertEquals(firstBytes, secondBytes);
            assertEquals(firstBytes.readableBytes() + Integer.BYTES, visibleBytes.readableBytes());
        } finally {
            firstBytes.release();
            secondBytes.release();
            visibleBytes.release();
        }
    }

    @Test
    public void wipeStatusWorksWithEmptyRoster() {
        StatusMessage decoded = roundTrip(new StatusMessage(true, 2400, true,
                Collections.emptyList(), null));
        assertTrue(decoded.groupWipe);
        assertTrue(decoded.teammates.isEmpty());
        assertEquals("", decoded.error);
    }

    @Test
    public void negativeCooldownIsClampedToZero() {
        StatusMessage decoded = roundTrip(new StatusMessage(true, -1, true,
                Collections.emptyList(), ""));
        assertEquals(0, decoded.remainingTicks);
    }

    @Test
    public void minigameOptionsAreServerControlledAndSurviveRoundTrip() {
        StatusMessage decoded = roundTrip(new StatusMessage(true, 500, false, Collections.emptyList(), "", false, true, true));
        assertTrue(decoded.deathMinigamesEnabled);
        assertTrue(decoded.minigameReducesRespawnTimer);
        decoded = roundTrip(new StatusMessage(true, 500, false, Collections.emptyList(), "", false, false, false));
        assertFalse(decoded.deathMinigamesEnabled);
        assertFalse(decoded.minigameReducesRespawnTimer);
    }

    @Test
    public void minigamePacketCarriesOnlyStartAndControls() {
        ByteBuf buf = Unpooled.buffer();
        try {
            new MinigameInputMessage(1, 32).toBytes(buf);
            assertEquals(2, buf.readableBytes());
            MinigameInputMessage decoded = new MinigameInputMessage();
            decoded.fromBytes(buf);
            assertEquals(1, decoded.startType);
            assertEquals(32, decoded.controls);
            assertEquals(0, buf.readableBytes());
        } finally {
            buf.release();
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void minigamePacketRejectsUnrecognizedControls() {
        ByteBuf buf = Unpooled.buffer();
        try {
            buf.writeByte(-1).writeByte(64);
            new MinigameInputMessage().fromBytes(buf);
        } finally {
            buf.release();
        }
    }

    @Test
    public void negativeRosterSizeIsRejectedBeforeAllocation() {
        assertInvalidCount(-1);
    }

    @Test
    public void oversizedRosterIsRejectedBeforeAllocation() {
        assertInvalidCount(1025);
    }

    @Test
    public void oversizedPlayerNameIsRejected() {
        ByteBuf buf = Unpooled.buffer();
        try {
            writeHeader(buf, 1);
            buf.writeLong(1L);
            buf.writeLong(2L);
            ByteBufUtils.writeUTF8String(buf, String.join("", Collections.nCopies(65, "a")));
            assertInvalidStatus(buf);
        } finally {
            buf.release();
        }
    }

    private static RequestRespawnMessage roundTrip(RequestRespawnMessage source) {
        ByteBuf buf = Unpooled.buffer();
        try {
            source.toBytes(buf);
            RequestRespawnMessage decoded = new RequestRespawnMessage();
            decoded.fromBytes(buf);
            assertEquals(0, buf.readableBytes());
            return decoded;
        } finally {
            buf.release();
        }
    }

    private static StatusMessage roundTrip(StatusMessage source) {
        ByteBuf buf = Unpooled.buffer();
        try {
            source.toBytes(buf);
            StatusMessage decoded = new StatusMessage();
            decoded.fromBytes(buf);
            assertEquals(0, buf.readableBytes());
            return decoded;
        } finally {
            buf.release();
        }
    }

    private static void writeHeader(ByteBuf buf, int count) {
        buf.writeBoolean(true);
        buf.writeInt(2400);
        buf.writeBoolean(false);
        buf.writeBoolean(false);
        buf.writeBoolean(false);
        buf.writeBoolean(false);
        buf.writeInt(count);
    }

    private static void assertInvalidCount(int count) {
        ByteBuf buf = Unpooled.buffer();
        try {
            writeHeader(buf, count);
            assertInvalidStatus(buf);
        } finally {
            buf.release();
        }
    }

    private static void assertInvalidStatus(ByteBuf buf) {
        try {
            new StatusMessage().fromBytes(buf);
            fail("Malformed status should be rejected");
        } catch (IllegalArgumentException expected) {
            // Invalid packets fail before the roster can be allocated or displayed.
        }
    }
}
