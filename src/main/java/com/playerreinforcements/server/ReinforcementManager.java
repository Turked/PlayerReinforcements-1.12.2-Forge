package com.playerreinforcements.server;

import com.playerreinforcements.PlayerReinforcements;
import com.playerreinforcements.config.ReinforcementsConfig;
import com.playerreinforcements.network.StatusMessage;
import com.playerreinforcements.network.MinigameStateMessage;
import com.playerreinforcements.network.MinigameScoresMessage;
import com.playerreinforcements.minigame.MinigameSession;
import com.mojang.authlib.properties.Property;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.play.server.SPacketUpdateHealth;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** All world access and respawn decisions run on the server thread. */
public final class ReinforcementManager {
    public static final ReinforcementManager INSTANCE = new ReinforcementManager();
    private MinecraftServer server;
    private ReinforcementsData saved;
    private final TestDummyManager dummies = TestDummyManager.INSTANCE;
    private final Set<UUID> reconnectingDeadPlayers = new HashSet<>();
    private final Map<UUID, MinigamePlayer> minigames = new HashMap<>();

    private ReinforcementManager() { }

    public void start(MinecraftServer server) {
        this.server = server;
        this.saved = null;
        minigames.clear();
        reconnectingDeadPlayers.clear();
        dummies.start(server);
        data().rememberExistingPlayers(server.getPlayerList().getAvailablePlayerDat());
        NBTTagCompound host = server.getWorld(0).getWorldInfo().getPlayerNBTTagCompound();
        if (host != null && host.hasUniqueId("UUID")) data().markJoined(host.getUniqueId("UUID"));
    }

    public void stop() {
        dummies.stop();
        minigames.clear();
        reconnectingDeadPlayers.clear();
        server = null;
        saved = null;
    }

    private ReinforcementsData data() {
        if (saved == null) saved = ReinforcementsData.get(server.getWorld(0));
        return saved;
    }

    private long now() { return server.getWorld(0).getTotalWorldTime(); }

    public boolean isWaiting(UUID id) {
        return server != null && server.getWorld(0) != null && data().queue.isWaiting(id);
    }

    private boolean member(EntityPlayerMP player) {
        return !(player instanceof FakePlayer) && !dummies.isDummy(player) && !player.isSpectator();
    }

    private boolean living(EntityPlayerMP player) {
        return (member(player) || dummies.isDummy(player)) && player.isEntityAlive()
                && !isWaiting(player.getUniqueID()) && !player.queuedEndExit;
    }

    private boolean rememberDeath(EntityPlayerMP player) {
        boolean changed = data().queue.markDead(player.getUniqueID(), now());
        if (changed) data().markDirty();
        return changed;
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.player instanceof EntityPlayerMP) || server == null) return;
        EntityPlayerMP player = (EntityPlayerMP) event.player;
        minigames.remove(player.getUniqueID());
        boolean firstJoin = !(player instanceof FakePlayer) && !dummies.isDummy(player)
                && data().markJoined(player.getUniqueID());
        if (isWaiting(player.getUniqueID())) {
            // A reconnect must not revive someone or restart the persisted cooldown.
            player.setHealth(0.0F);
            reconnectingDeadPlayers.add(player.getUniqueID());
            return;
        }
        if (firstJoin && living(player)) {
            joinLivingGroup(player);
        }
        sendStatus(player);
        broadcastWaiting();
    }

    private void joinLivingGroup(EntityPlayerMP player) {
        boolean hasTeammate = false;
        for (EntityPlayerMP candidate : server.getPlayerList().getPlayers()) {
            if (candidate == player || !member(candidate) || !living(candidate)) continue;
            hasTeammate = true;
            Optional<Vec3d> location = RespawnLocation.near(player, candidate);
            if (location.isPresent()) {
                place(player, candidate, location.get());
                return;
            }
        }
        if (hasTeammate) {
            PlayerReinforcements.LOGGER.warn("Could not find a safe initial group spawn for {}.", player.getName());
        }
    }

    @SubscribeEvent
    public void onTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || server == null || server.getWorld(0) == null) return;
        dummies.tick();
        // Recreating a player replaces their entry in the server's player list.
        List<EntityPlayerMP> players = new ArrayList<>(server.getPlayerList().getPlayers());
        boolean wipe = players.stream().noneMatch(this::living) && dummies.members().stream().noneMatch(this::living);
        List<EntityPlayerMP> waiting = Collections.emptyList();
        EntityPlayerMP firstPersonalRespawn = null;
        boolean changed = false;
        boolean refresh = now() % 20 == 0;
        for (EntityPlayerMP player : players) {
            if (!member(player)) continue;
            UUID id = player.getUniqueID();
            boolean alreadyWaiting = isWaiting(id);
            boolean reconnecting = reconnectingDeadPlayers.remove(id);
            changed |= reconnecting;
            // Observe accepted deaths after every LivingDeathEvent subscriber has run.
            // Check persisted waits too, since a spawnpoint may have become usable again.
            if (player.getHealth() <= 0.0F && !player.queuedEndExit
                    && (!alreadyWaiting || reconnecting || refresh || wipe)) {
                if (hasPersonalSpawn(player)) {
                    EntityPlayerMP fresh = respawnNormally(player);
                    if (firstPersonalRespawn == null) firstPersonalRespawn = fresh;
                    changed = true;
                    continue;
                }
                changed |= rememberDeath(player);
            }
            if (isWaiting(id)) {
                if (waiting.isEmpty()) waiting = new ArrayList<>();
                waiting.add(player);
            }
        }
        reconnectingDeadPlayers.clear();
        if (wipe) {
            // A wipe bypasses cooldowns for players who need the world-spawn fallback.
            if (!waiting.isEmpty()) {
                respawnGroup(waiting);
            } else if (firstPersonalRespawn != null) {
                dummies.resetAfterWipe(firstPersonalRespawn);
            }
        }
        tickMinigames();
        if (changed || refresh) broadcastWaiting();
    }

    public void sendStatus(EntityPlayerMP player) {
        sendStatus(player, "");
    }

    private void sendStatus(EntityPlayerMP player, String error) {
        sendStatus(player, error, null);
    }

    private void sendStatus(EntityPlayerMP player, String error, List<StatusMessage.Teammate> sharedRoster) {
        if (server == null || player.connection == null || !player.connection.netManager.isChannelOpen()
                || reconnectingDeadPlayers.contains(player.getUniqueID())) return;
        if (!isWaiting(player.getUniqueID())) {
            PlayerReinforcements.NETWORK.sendTo(new StatusMessage(false, 0, false, Collections.emptyList(), error,
                    ReinforcementsConfig.showTeammateDimensions(), ReinforcementsConfig.enableDeathMinigames(),
                    ReinforcementsConfig.minigameReducesRespawnTimer()), player);
            return;
        }
        List<StatusMessage.Teammate> teammates = sharedRoster == null ? livingRoster() : sharedRoster;
        boolean wipe = teammates.isEmpty();
        long remaining = wipe ? 0 : data().queue.remaining(player.getUniqueID(), now());
        PlayerReinforcements.NETWORK.sendTo(new StatusMessage(true, (int) Math.min(Integer.MAX_VALUE, remaining),
                wipe, teammates, error, ReinforcementsConfig.showTeammateDimensions(),
                ReinforcementsConfig.enableDeathMinigames(), ReinforcementsConfig.minigameReducesRespawnTimer()), player);
    }

    private List<StatusMessage.Teammate> livingRoster() {
        List<StatusMessage.Teammate> roster = new ArrayList<>();
        addLivingTeammates(server.getPlayerList().getPlayers(), roster);
        addLivingTeammates(dummies.members(), roster);
        return roster;
    }

    private void addLivingTeammates(List<EntityPlayerMP> candidates, List<StatusMessage.Teammate> roster) {
        for (EntityPlayerMP candidate : candidates) {
            if (living(candidate)) {
                roster.add(new StatusMessage.Teammate(candidate.getUniqueID(), candidate.getName(), candidate.dimension));
            }
        }
    }

    private void broadcastWaiting() {
        List<StatusMessage.Teammate> roster = null;
        for (EntityPlayerMP player : new ArrayList<>(server.getPlayerList().getPlayers())) {
            if (!isWaiting(player.getUniqueID())) continue;
            if (roster == null) roster = livingRoster();
            sendStatus(player, "", roster);
        }
    }

    public void requestRespawn(EntityPlayerMP player, UUID targetId) {
        if (server == null || server.getPlayerList().getPlayerByUUID(player.getUniqueID()) != player
                || player.connection.player != player || !isWaiting(player.getUniqueID())) return;
        if (hasPersonalSpawn(player)) {
            respawnNormally(player);
            broadcastWaiting();
            return;
        }
        if (data().queue.remaining(player.getUniqueID(), now()) > 0) {
            sendStatus(player);
            return;
        }
        EntityPlayerMP target = targetId == null ? null : server.getPlayerList().getPlayerByUUID(targetId);
        if (target == null && targetId != null) target = dummies.find(targetId);
        if (target == null || target == player || !living(target)) {
            sendStatus(player, "playerreinforcements.error.target");
            return;
        }
        Optional<Vec3d> location = RespawnLocation.near(player, target);
        if (!location.isPresent()) {
            sendStatus(player, "playerreinforcements.error.location");
            return;
        }
        EntityPlayerMP fresh = recreate(player);
        place(fresh, target, location.get());
        finish(fresh);
        broadcastWaiting();
    }

    private boolean hasPersonalSpawn(EntityPlayerMP player) {
        // Resolve the same dimension and spawnpoint as Forge's recreatePlayerEntity.
        WorldServer world = server.getWorld(player.dimension);
        if (world == null) {
            world = server.getWorld(player.getSpawnDimension());
        } else if (!world.provider.canRespawnHere()) {
            world = server.getWorld(world.provider.getRespawnDimension(player));
        }
        if (world == null) world = server.getWorld(0);
        int dimension = world.provider.getDimension();
        BlockPos spawnpoint = player.getBedLocation(dimension);
        return spawnpoint != null
                && EntityPlayer.getBedSpawnLocation(world, spawnpoint, player.isSpawnForced(dimension)) != null;
    }

    private EntityPlayerMP respawnNormally(EntityPlayerMP dead) {
        EntityPlayerMP fresh = recreate(dead);
        finish(fresh);
        return fresh;
    }

    private EntityPlayerMP recreate(EntityPlayerMP dead) {
        // Keep normal Forge clone/respawn hooks for First Aid, Corpse Complex, and other mods.
        EntityPlayerMP fresh = server.getPlayerList().recreatePlayerEntity(dead, dead.dimension, false);
        fresh.connection.player = fresh;
        return fresh;
    }

    private void place(EntityPlayerMP fresh, EntityPlayerMP anchor, Vec3d location) {
        if (fresh.dimension != anchor.dimension) {
            // A placement teleporter preserves dimension-change synchronization without creating portals.
            server.getPlayerList().transferPlayerToDimension(fresh, anchor.dimension,
                    (world, entity, yaw) -> entity.setLocationAndAngles(location.x, location.y, location.z, anchor.rotationYaw, 0.0F));
        }
        fresh.connection.setPlayerLocation(location.x, location.y, location.z, anchor.rotationYaw, 0.0F);
        fresh.motionX = fresh.motionY = fresh.motionZ = 0.0D;
        fresh.fallDistance = 0.0F;
    }

    private void finish(EntityPlayerMP fresh) {
        minigames.remove(fresh.getUniqueID());
        reconnectingDeadPlayers.remove(fresh.getUniqueID());
        if (data().queue.remove(fresh.getUniqueID())) data().markDirty();
        // Send health before status so the client can close the death screen immediately.
        fresh.connection.sendPacket(new SPacketUpdateHealth(fresh.getHealth(), fresh.getFoodStats().getFoodLevel(),
                fresh.getFoodStats().getSaturationLevel()));
        sendStatus(fresh);
    }

    private void respawnGroup(List<EntityPlayerMP> waiting) {
        // One normal world-spawn fallback chooses the shared destination.
        EntityPlayerMP anchor = respawnNormally(waiting.get(0));
        for (int i = 1; i < waiting.size(); i++) {
            EntityPlayerMP dead = waiting.get(i);
            Optional<Vec3d> location = RespawnLocation.near(dead, anchor);
            if (!location.isPresent()) {
                sendStatus(dead, "playerreinforcements.error.location");
                continue;
            }
            EntityPlayerMP fresh = recreate(dead);
            place(fresh, anchor, location.get());
            finish(fresh);
        }
        dummies.resetAfterWipe(anchor);
        broadcastWaiting();
    }

    private boolean canPlayMinigames(EntityPlayerMP player, boolean waiting) {
        return ReinforcementsConfig.enableDeathMinigames() && player != null && isWaiting(player.getUniqueID()) == waiting
                && (waiting || (player.isEntityAlive() && !player.queuedEndExit));
    }

    private boolean isConnectedPlayer(EntityPlayerMP player) {
        return server != null && server.getPlayerList().getPlayerByUUID(player.getUniqueID()) == player
                && player.connection != null && player.connection.player == player;
    }

    public void minigamePanel(EntityPlayerMP player, boolean open, boolean waiting) {
        if (!isConnectedPlayer(player)) return;
        UUID id = player.getUniqueID();
        MinigamePlayer viewer = minigames.get(id);
        if (!open) {
            if (viewer != null && viewer.waiting == waiting) minigames.remove(id);
            return;
        }
        if (!canPlayMinigames(player, waiting) || (viewer != null && viewer.waiting == waiting)) return;
        minigames.put(id, new MinigamePlayer(waiting));
        sendHighScores(player, MinigameSession.FLAPPY, data().highScores.leader(MinigameSession.FLAPPY));
        sendHighScores(player, MinigameSession.TETRIS, data().highScores.leader(MinigameSession.TETRIS));
    }

    /** Only authenticated clients with an open panel can control server-owned games. */
    public void minigameInput(EntityPlayerMP player, int startType, int controls) {
        if (!isConnectedPlayer(player) || startType < -1 || startType > MinigameSession.TETRIS
                || (controls & ~MinigameSession.ALL_CONTROLS) != 0) return;
        MinigamePlayer viewer = minigames.get(player.getUniqueID());
        if (viewer == null || !canPlayMinigames(player, viewer.waiting)) return;
        MinigameRun run = viewer.run;
        if (startType >= 0) {
            // Bound reset requests; a reset never awards or re-awards any points.
            if (run != null && now() - run.startedAt < 10) return;
            run = new MinigameRun(new MinigameSession(startType, player.getServerWorld().rand.nextLong()), now());
            viewer.run = run;
        }
        if (run != null) run.controls |= controls;
    }

    private void tickMinigames() {
        Iterator<Map.Entry<UUID, MinigamePlayer>> iterator = minigames.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, MinigamePlayer> entry = iterator.next();
            EntityPlayerMP player = server.getPlayerList().getPlayerByUUID(entry.getKey());
            MinigamePlayer viewer = entry.getValue();
            if (!canPlayMinigames(player, viewer.waiting)) {
                iterator.remove();
                continue;
            }
            MinigameRun run = viewer.run;
            if (run == null) continue;
            int previous = run.session.score();
            boolean changed = run.session.tick(run.controls);
            run.controls = 0;
            int points = run.session.score() - previous;
            if (points > 0) {
                Iterator<Property> textures = player.getGameProfile().getProperties().get("textures").iterator();
                Property skin = textures.hasNext() ? textures.next() : null;
                if (data().highScores.record(run.session.type(), entry.getKey(), player.getName(), run.session.score(),
                        skin == null ? "" : skin.getValue(), skin == null || !skin.hasSignature() ? "" : skin.getSignature())) {
                    data().markDirty();
                    broadcastHighScores(run.session.type());
                }
            }
            if (points > 0 && viewer.waiting && ReinforcementsConfig.minigameReducesRespawnTimer()
                    && data().queue.reduce(entry.getKey(), (long) points * 200L, now())) {
                data().markDirty();
                sendStatus(player);
            }
            if (changed || !run.published) {
                PlayerReinforcements.NETWORK.sendTo(new MinigameStateMessage(run.session), player);
                run.published = true;
            }
        }
    }

    private void sendHighScores(EntityPlayerMP player, int game, MinigameHighScores.Entry leader) {
        PlayerReinforcements.NETWORK.sendTo(new MinigameScoresMessage(game,
                data().highScores.best(game, player.getUniqueID()),
                leader == null ? null : leader.player, leader == null ? "" : leader.name,
                leader == null ? 0 : leader.score, leader == null ? "" : leader.texture,
                leader == null ? "" : leader.textureSignature), player);
    }

    private void broadcastHighScores(int game) {
        MinigameHighScores.Entry leader = data().highScores.leader(game);
        for (Map.Entry<UUID, MinigamePlayer> entry : minigames.entrySet()) {
            EntityPlayerMP player = server.getPlayerList().getPlayerByUUID(entry.getKey());
            if (canPlayMinigames(player, entry.getValue().waiting)) sendHighScores(player, game, leader);
        }
    }

    private static final class MinigamePlayer {
        private final boolean waiting;
        private MinigameRun run;

        private MinigamePlayer(boolean waiting) {
            this.waiting = waiting;
        }
    }

    private static final class MinigameRun {
        private final MinigameSession session;
        private final long startedAt;
        private int controls;
        private boolean published;

        private MinigameRun(MinigameSession session, long startedAt) {
            this.session = session;
            this.startedAt = startedAt;
        }
    }
}
