package com.playerreinforcements.server;

import com.playerreinforcements.PlayerReinforcements;
import com.playerreinforcements.config.ReinforcementsConfig;
import com.playerreinforcements.network.StatusMessage;
import com.playerreinforcements.network.MinigameStateMessage;
import com.playerreinforcements.network.MinigameScoresMessage;
import com.playerreinforcements.minigame.MinigameSession;
import com.mojang.authlib.properties.Property;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.play.server.SPacketUpdateHealth;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.Vec3d;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** All world access and respawn decisions run on the server thread. */
public final class ReinforcementManager {
    public static final ReinforcementManager INSTANCE = new ReinforcementManager();
    private MinecraftServer server;
    private ReinforcementsData saved;
    private final TestDummyManager dummies = TestDummyManager.INSTANCE;
    private final Map<UUID, MinigameRun> minigames = new HashMap<>();

    private ReinforcementManager() { }

    public void start(MinecraftServer server) {
        this.server = server;
        this.saved = null;
        minigames.clear();
        dummies.start(server);
        data().rememberExistingPlayers(server.getPlayerList().getAvailablePlayerDat());
        NBTTagCompound host = server.getWorld(0).getWorldInfo().getPlayerNBTTagCompound();
        if (host != null && host.hasUniqueId("UUID")) data().markJoined(host.getUniqueId("UUID"));
    }

    public void stop() {
        dummies.stop();
        minigames.clear();
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
        } else if (member(player) && player.getHealth() <= 0.0F) {
            rememberDeath(player);
        } else if (firstJoin && living(player)) {
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
        List<EntityPlayerMP> waiting = Collections.emptyList();
        boolean hasLivingPlayer = false;
        boolean changed = false;
        for (EntityPlayerMP player : server.getPlayerList().getPlayers()) {
            if (!member(player)) continue;
            UUID id = player.getUniqueID();
            // Observe accepted deaths after every LivingDeathEvent subscriber has run.
            // A canceled death must not persist a wait for a player who stayed alive.
            if (player.getHealth() <= 0.0F && !isWaiting(id)) {
                changed |= rememberDeath(player);
            }
            if (isWaiting(id)) {
                if (waiting.isEmpty()) waiting = new ArrayList<>();
                waiting.add(player);
            } else if (player.isEntityAlive() && !player.queuedEndExit) {
                hasLivingPlayer = true;
            }
        }
        if (!waiting.isEmpty() && !hasLivingPlayer && dummies.members().stream().noneMatch(this::living)) {
            // A wipe always bypasses individual cooldowns, including a solo player's death.
            respawnGroup(waiting);
        }
        tickMinigames();
        if (changed || now() % 20 == 0) broadcastWaiting();
    }

    public void sendStatus(EntityPlayerMP player) {
        sendStatus(player, "");
    }

    private void sendStatus(EntityPlayerMP player, String error) {
        sendStatus(player, error, null);
    }

    private void sendStatus(EntityPlayerMP player, String error, List<StatusMessage.Teammate> sharedRoster) {
        if (server == null || player.connection == null || !player.connection.netManager.isChannelOpen()) return;
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
        data().queue.remove(fresh.getUniqueID());
        data().markDirty();
        // Send health before status so the client can close the death screen immediately.
        fresh.connection.sendPacket(new SPacketUpdateHealth(fresh.getHealth(), fresh.getFoodStats().getFoodLevel(),
                fresh.getFoodStats().getSaturationLevel()));
        sendStatus(fresh);
    }

    private void respawnGroup(List<EntityPlayerMP> waiting) {
        // One vanilla/RLCraft respawn chooses the destination, including its normal bed/waystone rules.
        EntityPlayerMP anchor = recreate(waiting.get(0));
        finish(anchor);
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

    /** Only authenticated, waiting players can control a server-owned game. */
    public void minigameInput(EntityPlayerMP player, int startType, int controls) {
        if (server == null || !ReinforcementsConfig.enableDeathMinigames()
                || server.getPlayerList().getPlayerByUUID(player.getUniqueID()) != player
                || player.connection == null || player.connection.player != player
                || !isWaiting(player.getUniqueID()) || startType < -1 || startType > 1
                || (controls & ~63) != 0) return;
        MinigameRun run = minigames.get(player.getUniqueID());
        if (startType >= 0) {
            // Bound reset requests; a reset never awards or re-awards any points.
            if (run != null && now() - run.startedAt < 10) return;
            run = new MinigameRun(new MinigameSession(startType, player.getServerWorld().rand.nextLong()), now());
            minigames.put(player.getUniqueID(), run);
            sendHighScores(player, startType);
        }
        if (run != null) run.controls |= controls;
    }

    private void tickMinigames() {
        Iterator<Map.Entry<UUID, MinigameRun>> iterator = minigames.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, MinigameRun> entry = iterator.next();
            EntityPlayerMP player = server.getPlayerList().getPlayerByUUID(entry.getKey());
            if (!ReinforcementsConfig.enableDeathMinigames() || player == null || !isWaiting(entry.getKey())) {
                iterator.remove();
                continue;
            }
            MinigameRun run = entry.getValue();
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
            if (points > 0 && ReinforcementsConfig.minigameReducesRespawnTimer()
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

    private void sendHighScores(EntityPlayerMP player, int game) {
        MinigameHighScores.Entry leader = data().highScores.leader(game);
        PlayerReinforcements.NETWORK.sendTo(new MinigameScoresMessage(game,
                data().highScores.best(game, player.getUniqueID()),
                leader == null ? null : leader.player, leader == null ? "" : leader.name,
                leader == null ? 0 : leader.score, leader == null ? "" : leader.texture,
                leader == null ? "" : leader.textureSignature), player);
    }

    private void broadcastHighScores(int game) {
        for (Map.Entry<UUID, MinigameRun> entry : minigames.entrySet()) {
            if (entry.getValue().session.type() != game) continue;
            EntityPlayerMP player = server.getPlayerList().getPlayerByUUID(entry.getKey());
            if (player != null && isWaiting(entry.getKey())) sendHighScores(player, game);
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
