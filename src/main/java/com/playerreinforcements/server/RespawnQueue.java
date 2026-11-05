package com.playerreinforcements.server;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Server-thread state. Deadlines use the overworld's total world time. */
public final class RespawnQueue {
    public static final long COOLDOWN_TICKS = 2L * 60L * 20L;

    private final Map<UUID, Long> deadlines = new LinkedHashMap<>();

    /** Duplicate death notifications must not restart an existing wait. */
    public boolean markDead(UUID player, long now) {
        Objects.requireNonNull(player, "player");
        requireNonNegative(now);
        if (deadlines.containsKey(player)) {
            return false;
        }
        long deadline = now > Long.MAX_VALUE - COOLDOWN_TICKS
                ? Long.MAX_VALUE : now + COOLDOWN_TICKS;
        deadlines.put(player, deadline);
        return true;
    }

    public boolean isWaiting(UUID player) {
        return deadlines.containsKey(player);
    }

    /** A zero remaining wait means eligible; it does not mean alive. */
    public long remaining(UUID player, long now) {
        requireNonNegative(now);
        Long deadline = deadlines.get(player);
        return deadline == null || now >= deadline ? 0L : deadline - now;
    }

    public boolean remove(UUID player) {
        return deadlines.remove(player) != null;
    }

    /** Reduce an active wait without reviving its owner or extending an expired deadline. */
    public boolean reduce(UUID player, long ticks, long now) {
        Objects.requireNonNull(player, "player");
        requireNonNegative(ticks);
        requireNonNegative(now);
        Long deadline = deadlines.get(player);
        if (deadline == null || deadline <= now || ticks == 0L) {
            return false;
        }
        long remaining = deadline - now;
        // Subtract only a bounded amount: neither addition nor subtraction can overflow.
        deadlines.put(player, deadline - Math.min(ticks, remaining));
        return true;
    }

    /** Copy suitable for saving, without exposing mutable queue state. */
    public Map<UUID, Long> snapshot() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(deadlines));
    }

    public void restore(UUID player, long deadline) {
        Objects.requireNonNull(player, "player");
        requireNonNegative(deadline);
        deadlines.put(player, deadline);
    }

    private static void requireNonNegative(long ticks) {
        if (ticks < 0L) {
            throw new IllegalArgumentException("World ticks must not be negative");
        }
    }
}
