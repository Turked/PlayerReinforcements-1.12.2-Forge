package com.playerreinforcements.server;

import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityCreature;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.EnumPacketDirection;
import net.minecraft.network.NetHandlerPlayServer;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.Packet;
import net.minecraft.pathfinding.Path;
import net.minecraft.pathfinding.PathNavigateGround;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.management.PlayerInteractionManager;
import net.minecraft.stats.StatBase;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameType;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.util.ITeleporter;
import net.minecraftforge.fml.relauncher.ReflectionHelper;

/** A survival player entity driven by the server, without a login or network socket. */
public final class TestDummyPlayer extends EntityPlayerMP {
    private static final int PATH_UPDATE_TICKS = 10;

    enum Behavior {
        STILL("Dummy_Still"), FOLLOW("Dummy_Follow"), RUNNER("Dummy_Runner");

        final String playerName;

        Behavior(String playerName) {
            this.playerName = playerName;
        }
    }

    private final Behavior behavior;
    private EntityCreature pathfinder;
    private PathNavigateGround navigator;
    private UUID owner;
    private Path path;
    private BlockPos destination;
    private int nextDestination;
    private int nextPath;
    private boolean sleepWithPlayers;

    TestDummyPlayer(MinecraftServer server, WorldServer world, Behavior behavior, UUID owner) {
        super(server, world, new GameProfile(UUID.nameUUIDFromBytes(
                ("playerreinforcements:test_dummy:" + behavior.name()).getBytes(StandardCharsets.UTF_8)),
                behavior.playerName), new PlayerInteractionManager(world));
        this.behavior = behavior;
        this.owner = owner;
        new DummyConnection(server, this);
        interactionManager.setGameType(GameType.SURVIVAL);
        // The world actor is ready immediately and has no client loading screen to protect.
        ReflectionHelper.setPrivateValue(EntityPlayerMP.class, this, 0,
                "respawnInvulnerabilityTicks", "field_147101_bU");
        // Advancement rewards can send Forge packets directly to a client's channel.
        getAdvancements().dispose();
        stepHeight = 0.6F;
        bindNavigation(world);
    }

    private void bindNavigation(WorldServer world) {
        pathfinder = new EntityCreature(world) { };
        pathfinder.getEntityAttribute(SharedMonsterAttributes.FOLLOW_RANGE).setBaseValue(16.0D);
        navigator = new PathNavigateGround(pathfinder, world);
        navigator.setCanSwim(true);
        path = null;
        destination = null;
        nextPath = nextDestination = 0;
    }

    void setOwner(UUID owner) {
        this.owner = owner;
    }

    boolean setSleepWithPlayers(boolean sleeping) {
        if (sleepWithPlayers == sleeping) return false;
        sleepWithPlayers = sleeping;
        return true;
    }

    @Override
    public boolean isPlayerSleeping() {
        return sleepWithPlayers;
    }

    @Override
    public boolean isPlayerFullyAsleep() {
        // Real players still have to finish their normal sleep timer.
        return sleepWithPlayers;
    }

    @Override
    public void onUpdate() {
        // Real players receive these two updates from the world and their connection.
        // This entity has no connection tick, so run both exactly once here.
        super.onUpdate();
        if (!sleepWithPlayers) super.onUpdateEntity();
    }

    @Override
    public Entity changeDimension(int dimension, ITeleporter teleporter) {
        Entity result = super.changeDimension(dimension, teleporter);
        if (result == this) {
            // A connected client normally clears this by acknowledging its teleport.
            clearInvulnerableDimensionChange();
            // Native player transfer adds chunk subscriptions for a connected client.
            // This actor needs only its world entity, not client chunk subscriptions.
            getServerWorld().getPlayerChunkMap().removePlayer(this);
            if (pathfinder.world != world) bindNavigation(getServerWorld());
        }
        return result;
    }

    @Override
    protected void updateEntityActionState() {
        super.updateEntityActionState();
        moveForward = moveStrafing = 0.0F;
        isJumping = false;
        if (!isEntityAlive() || behavior == Behavior.STILL) {
            motionX = motionZ = 0.0D;
            setSprinting(false);
            return;
        }
        if (pathfinder.world != world) bindNavigation(getServerWorld());
        pathfinder.setPosition(posX, posY, posZ);
        pathfinder.onGround = onGround;
        if (nextPath > 0) nextPath--;
        if (behavior == Behavior.FOLLOW) {
            EntityPlayerMP target = mcServer.getPlayerList().getPlayerByUUID(owner);
            if (target == null || target.dimension != dimension || !target.isEntityAlive()
                    || target.isSpectator() || getDistanceSq(target) < 6.25D) {
                path = null;
                setSprinting(false);
                return;
            }
            destination = target.getPosition();
        } else {
            nextDestination--;
            // Picking a new destination must not bypass the failed-path retry interval.
            if (nextPath == 0 && (destination == null || nextDestination <= 0
                    || collidedHorizontally || destination.distanceSq(posX, posY, posZ) < 4.0D)) {
                chooseRandomDestination();
            }
        }
        if (nextPath == 0) {
            nextPath = PATH_UPDATE_TICKS;
            BlockPos feet = getPosition();
            // Vanilla pathfinding builds a ChunkCache with search range + 8 blocks.
            // Check that whole area first, because ChunkCache otherwise loads terrain.
            path = destination != null && world.isAreaLoaded(feet.add(-24, 0, -24), feet.add(24, 0, 24))
                    ? navigator.getPathToPos(destination) : null;
        }
        if (path == null || path.isFinished()) {
            if (behavior == Behavior.RUNNER) nextDestination = 0;
            setSprinting(false);
            return;
        }
        Vec3d waypoint = path.getPosition(pathfinder);
        while (waypoint.squareDistanceTo(posX, posY, posZ) < 0.36D) {
            path.incrementPathIndex();
            if (path.isFinished()) return;
            waypoint = path.getPosition(pathfinder);
        }
        // Follow actual path nodes with ordinary player collision, gravity and jumping.
        double dx = waypoint.x - posX;
        double dz = waypoint.z - posZ;
        rotationYaw = (float)(MathHelper.atan2(dz, dx) * 180.0D / Math.PI) - 90.0F;
        rotationYawHead = rotationYaw;
        moveForward = 1.0F;
        isJumping = (onGround && waypoint.y > posY + 0.4D) || isInWater();
        setSprinting(behavior == Behavior.RUNNER);
    }

    private void chooseRandomDestination() {
        nextDestination = 60 + rand.nextInt(80);
        destination = null;
        for (int attempt = 0; attempt < 8; attempt++) {
            double angle = rand.nextDouble() * Math.PI * 2.0D;
            double distance = 10.0D + rand.nextDouble() * 14.0D;
            BlockPos candidate = new BlockPos(posX + Math.cos(angle) * distance, posY,
                    posZ + Math.sin(angle) * distance);
            // A testing actor must not generate terrain as it wanders away.
            if (world.isBlockLoaded(candidate) && world.getWorldBorder().contains(candidate)) {
                destination = candidate;
                return;
            }
        }
    }

    @Override
    public void travel(float strafe, float vertical, float forward) {
        if (behavior == Behavior.STILL) {
            motionX = motionZ = 0.0D;
            super.travel(0.0F, 0.0F, 0.0F);
            motionX = motionZ = 0.0D;
        } else {
            super.travel(strafe, vertical, forward);
        }
    }

    @Override
    public void knockBack(Entity source, float strength, double xRatio, double zRatio) {
        if (behavior != Behavior.STILL) super.knockBack(source, strength, xRatio, zRatio);
    }

    @Override
    public void addVelocity(double x, double y, double z) {
        if (behavior != Behavior.STILL) super.addVelocity(x, y, z);
    }

    @Override
    public void applyEntityCollision(Entity entity) {
        if (behavior != Behavior.STILL) super.applyEntityCollision(entity);
    }

    @Override
    public void addStat(StatBase stat, int amount) {
        // Test actors must not earn normal player statistics.
    }

    @Override
    public void unlockRecipes(List<IRecipe> recipes) {
        // Forge's recipe-book synchronization requires a connected client.
    }

    private static final class DummyConnection extends NetHandlerPlayServer {
        DummyConnection(MinecraftServer server, EntityPlayerMP player) {
            super(server, disconnectedNetwork(), player);
        }

        private static NetworkManager disconnectedNetwork() {
            NetworkManager manager = new NetworkManager(EnumPacketDirection.SERVERBOUND);
            // Forge mod packets inspect channel attributes before checking for a dispatcher.
            // A closed in-memory channel supplies those attributes without a network connection.
            new EmbeddedChannel(manager).close();
            return manager;
        }

        @Override
        public void sendPacket(Packet<?> packet) {
            // Other players receive this actor's updates from the world's entity tracker.
        }
    }
}
