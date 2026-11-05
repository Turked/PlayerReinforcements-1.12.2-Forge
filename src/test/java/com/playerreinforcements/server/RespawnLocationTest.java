package com.playerreinforcements.server;

import net.minecraft.util.math.AxisAlignedBB;
import org.junit.Test;
import static org.junit.Assert.*;

public class RespawnLocationTest {
    @Test
    public void aCrouchedOrGlidingDeathMustNotFitUnderAStandingPlayersCeiling() {
        AxisAlignedBB ceiling = new AxisAlignedBB(-1.0, 65.7, -1.0, 1.0, 66.0, 1.0);
        for (float deadHeight : new float[] {0.2F, 0.6F, 1.65F}) {
            AxisAlignedBB respawn = RespawnLocation.standingBox(0.6F, deadHeight, 0, 64, 0);
            assertTrue("Respawn needs standing headroom even if the dead player was smaller", respawn.intersects(ceiling));
            assertEquals(1.8, respawn.maxY - respawn.minY, 0.00001);
        }
    }

    @Test
    public void largerPlayerDimensionsStillRequireTheirFullSpace() {
        AxisAlignedBB respawn = RespawnLocation.standingBox(1.0F, 2.4F, 10, 64, -10);
        assertEquals(1.0, respawn.maxX - respawn.minX, 0.00001);
        assertEquals(2.4, respawn.maxY - respawn.minY, 0.00001);
    }
}
