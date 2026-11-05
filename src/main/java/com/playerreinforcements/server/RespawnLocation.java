package com.playerreinforcements.server;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.WorldServer;
import net.minecraft.block.material.Material;
import java.util.Optional;

public final class RespawnLocation {
    private RespawnLocation() { }

    /** Prefer supported ground nearby; retain the vanilla anchor if it is in water or in midair. */
    public static Optional<Vec3d> near(EntityPlayerMP player, EntityPlayerMP anchor) {
        WorldServer world = anchor.getServerWorld();
        BlockPos origin = new BlockPos(anchor.posX, anchor.posY, anchor.posZ);
        int[] heights = {0, 1, -1, 2, -2};
        for (int radius = 0; radius <= 4; radius++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != radius) continue;
                    for (int dy : heights) {
                        BlockPos feet = origin.add(dx, dy, dz);
                        BlockPos floor = feet.down();
                        if (world.getBlockState(floor).isSideSolid(world, floor, EnumFacing.UP)
                                && clear(world, player, feet.getX() + 0.5, feet.getY(), feet.getZ() + 0.5)) {
                            return Optional.of(new Vec3d(feet.getX() + 0.5, feet.getY(), feet.getZ() + 0.5));
                        }
                    }
                }
            }
        }
        if (clear(world, player, anchor.posX, anchor.posY, anchor.posZ)) {
            return Optional.of(new Vec3d(anchor.posX, anchor.posY, anchor.posZ));
        }
        return Optional.empty();
    }

    private static boolean clear(WorldServer world, EntityPlayerMP player, double x, double y, double z) {
        BlockPos feet = new BlockPos(x, y, z);
        AxisAlignedBB box = standingBox(player.width, player.height, x, y, z);
        if (y < 1 || box.maxY >= world.getHeight() || !world.getWorldBorder().contains(feet)) return false;
        if (!world.getCollisionBoxes(player, box).isEmpty()) return false;
        for (BlockPos block : BlockPos.getAllInBox(new BlockPos(box.minX, box.minY, box.minZ),
                new BlockPos(box.maxX, box.maxY, box.maxZ))) {
            Material material = world.getBlockState(block).getMaterial();
            if (material == Material.LAVA || material == Material.FIRE) return false;
        }
        return true;
    }

    static AxisAlignedBB standingBox(float width, float height, double x, double y, double z) {
        // The dead entity can still be crouching or gliding; the clone respawns standing.
        double halfWidth = Math.max(0.6F, width) / 2.0;
        double standingHeight = Math.max(1.8F, height);
        return new AxisAlignedBB(x - halfWidth, y, z - halfWidth, x + halfWidth, y + standingHeight, z + halfWidth);
    }
}
