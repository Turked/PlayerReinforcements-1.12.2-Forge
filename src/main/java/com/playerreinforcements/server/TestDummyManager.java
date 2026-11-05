package com.playerreinforcements.server;

import com.playerreinforcements.PlayerReinforcements;
import com.playerreinforcements.config.ReinforcementsConfig;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import net.minecraft.block.material.Material;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.play.server.SPacketPlayerListItem;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.DimensionManager;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/** Owns three temporary, server-wide actors; none is a logged-in client or saved player. */
public final class TestDummyManager {
    public static final TestDummyManager INSTANCE = new TestDummyManager();
    private final List<TestDummyPlayer> dummies = new ArrayList<>();
    private MinecraftServer server;
    private UUID owner;
    private boolean spawned;

    private TestDummyManager() { }

    public void start(MinecraftServer server) {
        stop();
        this.server = server;
    }

    public void stop() {
        removeDummies();
        server = null;
        owner = null;
        spawned = false;
    }

    public boolean isDummy(Entity player) {
        return player instanceof TestDummyPlayer;
    }

    public List<EntityPlayerMP> members() {
        return Collections.unmodifiableList(new ArrayList<EntityPlayerMP>(dummies));
    }

    public EntityPlayerMP find(UUID id) {
        for (TestDummyPlayer dummy : dummies) {
            if (dummy.getUniqueID().equals(id)) return dummy;
        }
        return null;
    }

    public void tick() {
        if (server == null) return;
        List<EntityPlayerMP> players = server.getPlayerList().getPlayers().stream()
                .filter(p -> !isDummy(p) && !(p instanceof FakePlayer))
                .collect(Collectors.toList());
        if (!ReinforcementsConfig.testDummies() || players.isEmpty()) {
            removeDummies();
            spawned = false;
            owner = null;
            return;
        }
        EntityPlayerMP anchor = players.stream().filter(p -> p.getUniqueID().equals(owner))
                .findFirst().orElse(players.get(0));
        owner = anchor.getUniqueID();
        if (!spawned && anchor.isEntityAlive() && !anchor.isSpectator()) spawn(anchor);
        List<WorldServer> changedSleepWorlds = null;
        for (TestDummyPlayer dummy : dummies) {
            dummy.setOwner(owner);
            WorldServer world = dummy.getServerWorld();
            if (DimensionManager.getWorld(dummy.dimension) != world) {
                if (!dummy.isDead) dummy.setDead();
                continue;
            }
            if (dummy.setSleepWithPlayers(allRealPlayersSleeping(players, world))) {
                if (changedSleepWorlds == null) changedSleepWorlds = new ArrayList<>();
                if (!changedSleepWorlds.contains(world)) changedSleepWorlds.add(world);
            }
        }
        // Native spawn, removal and real sleep changes already update the world flag.
        if (changedSleepWorlds != null) {
            for (WorldServer world : changedSleepWorlds) world.updateAllPlayersSleepingFlag();
        }
    }

    private static boolean allRealPlayersSleeping(List<EntityPlayerMP> players, WorldServer world) {
        boolean hasPlayer = false;
        for (EntityPlayerMP player : players) {
            if (player.world != world || player.isSpectator()) continue;
            if (!player.isPlayerSleeping()) return false;
            hasPlayer = true;
        }
        return hasPlayer;
    }

    /** A complete group wipe recreates the actors beside the group's normal respawn anchor. */
    public void resetAfterWipe(EntityPlayerMP anchor) {
        if (server == null || !ReinforcementsConfig.testDummies()) return;
        removeDummies();
        owner = anchor.getUniqueID();
        spawn(anchor);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onJoinWorld(EntityJoinWorldEvent event) {
        if (server == null || event.getWorld().isRemote || !(event.getEntity() instanceof EntityPlayerMP)
                || isDummy(event.getEntity()) || event.isCanceled() || dummies.isEmpty()) return;
        EntityPlayerMP player = (EntityPlayerMP)event.getEntity();
        if (player.connection != null) {
            // The profile must arrive before the tracker sends SPacketSpawnPlayer.
            player.connection.sendPacket(new SPacketPlayerListItem(SPacketPlayerListItem.Action.ADD_PLAYER,
                    members()));
        }
    }

    private void spawn(EntityPlayerMP anchor) {
        spawned = true;
        for (TestDummyPlayer.Behavior behavior : TestDummyPlayer.Behavior.values()) {
            TestDummyPlayer dummy = new TestDummyPlayer(server, anchor.getServerWorld(), behavior, owner);
            Vec3d location = safeLocation(dummy, anchor, behavior.ordinal());
            dummy.setLocationAndAngles(location.x, location.y, location.z, anchor.rotationYaw, 0.0F);
            // Vanilla clients need tab-list profiles to construct visible player entities.
            server.getPlayerList().sendPacketToAllPlayers(
                    new SPacketPlayerListItem(SPacketPlayerListItem.Action.ADD_PLAYER, dummy));
            if (anchor.getServerWorld().spawnEntity(dummy)) {
                dummies.add(dummy);
            } else {
                server.getPlayerList().sendPacketToAllPlayers(
                        new SPacketPlayerListItem(SPacketPlayerListItem.Action.REMOVE_PLAYER, dummy));
                dummy.getAdvancements().dispose();
                PlayerReinforcements.LOGGER.warn("Could not spawn test dummy {}: a world-spawn hook rejected it.",
                        dummy.getName());
            }
        }
    }

    private Vec3d safeLocation(TestDummyPlayer dummy, EntityPlayerMP anchor, int index) {
        WorldServer world = anchor.getServerWorld();
        BlockPos origin = anchor.getPosition();
        int[] heights = {0, 1, -1, 2, -2, 3, -3};
        for (int radius = 2; radius <= 6; radius++) {
            // Rotate each actor's first search direction so the three spawn apart.
            for (int step = 0; step < 8 * radius; step++) {
                double angle = ((double)step / (8 * radius) + (double)index / 3) * Math.PI * 2;
                int dx = (int)Math.round(Math.cos(angle) * radius);
                int dz = (int)Math.round(Math.sin(angle) * radius);
                for (int dy : heights) {
                    BlockPos feet = origin.add(dx, dy, dz);
                    if (!world.isBlockLoaded(feet) || feet.getY() < 1 || feet.getY() + 2 >= world.getHeight()
                            || !world.getWorldBorder().contains(feet)) continue;
                    BlockPos floor = feet.down();
                    if (!world.getBlockState(floor).isSideSolid(world, floor, EnumFacing.UP)) continue;
                    double x = feet.getX() + 0.5D;
                    double z = feet.getZ() + 0.5D;
                    AxisAlignedBB box = RespawnLocation.standingBox(dummy.width, dummy.height, x, feet.getY(), z);
                    if (!world.getCollisionBoxes(dummy, box).isEmpty() || !world.checkNoEntityCollision(box, dummy)) continue;
                    Material ground = world.getBlockState(floor).getMaterial();
                    Material body = world.getBlockState(feet).getMaterial();
                    if (ground == Material.LAVA || ground == Material.FIRE || body == Material.LAVA
                            || body == Material.FIRE) continue;
                    return new Vec3d(x, feet.getY(), z);
                }
            }
        }
        return RespawnLocation.near(dummy, anchor).orElse(anchor.getPositionVector());
    }

    private void removeDummies() {
        if (server != null && !dummies.isEmpty()) {
            for (TestDummyPlayer dummy : dummies) {
                dummy.getServerWorld().getPlayerChunkMap().removePlayer(dummy);
                dummy.getServerWorld().removeEntityDangerously(dummy);
                dummy.getAdvancements().dispose();
            }
            server.getPlayerList().sendPacketToAllPlayers(
                    new SPacketPlayerListItem(SPacketPlayerListItem.Action.REMOVE_PLAYER, members()));
        }
        dummies.clear();
    }
}
