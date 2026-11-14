package com.loohp.interactionvisualizer.skyhand;

import com.loohp.interactionvisualizer.entityholders.ArmorStand;
import com.loohp.interactionvisualizer.nms.NMSWrapper;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Labels that follow the player around a block (spawner, beacon, conduit) used to be teleported to every viewer
 * every 2 ticks, even when their position did not change (10 packets per second per label and per player).
 * A position is now sent again only when it changed since the last one sent to that player, or after 5 seconds.
 * Spawning, fully updating or removing a label (which puts it back at the block centre on the client) forgets the
 * last position, so the next one is always sent.
 */
public final class SentPositions {

    private static final long RESEND_MS = 5000L;

    // player -> entity id -> {x, y, z, yaw, pitch, time}
    private static final Map<Player, Map<Integer, double[]>> LAST = Collections.synchronizedMap(new WeakHashMap<>());

    private SentPositions() {
    }

    public static void teleportEntity(NMSWrapper nms, Player player, int entityId, Location location) {
        Map<Integer, double[]> sent;
        synchronized (LAST) {
            sent = LAST.computeIfAbsent(player, p -> new ConcurrentHashMap<>());
        }
        double x = location.getX();
        double y = location.getY();
        double z = location.getZ();
        double yaw = location.getYaw();
        double pitch = location.getPitch();
        long now = System.currentTimeMillis();
        double[] last = sent.get(entityId);
        if (last != null && last[0] == x && last[1] == y && last[2] == z && last[3] == yaw && last[4] == pitch
                && now - (long) last[5] < RESEND_MS) {
            return;
        }
        nms.teleportEntity(player, entityId, location);
        sent.put(entityId, new double[]{x, y, z, yaw, pitch, now});
    }

    public static void spawnArmorStand(NMSWrapper nms, Collection<Player> players, ArmorStand entity) {
        nms.spawnArmorStand(players, entity);
        forget(players, entity.getEntityId());
    }

    public static void updateArmorStand(NMSWrapper nms, Collection<Player> players, ArmorStand entity) {
        nms.updateArmorStand(players, entity);
        forget(players, entity.getEntityId());
    }

    public static void removeArmorStand(NMSWrapper nms, Collection<Player> players, ArmorStand entity) {
        nms.removeArmorStand(players, entity);
        forget(players, entity.getEntityId());
    }

    private static void forget(Collection<Player> players, int entityId) {
        for (Player player : players) {
            Map<Integer, double[]> sent = LAST.get(player);
            if (sent != null) {
                sent.remove(entityId);
            }
        }
    }
}
