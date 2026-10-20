package de.sarocesch.sarosragdollfix;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;

/**
 * Two things that stop duplicated corpses:
 * <ul>
 * <li>a spawn guard: the original mod makes one ragdoll per {@code spawn} call and never checks, so a mob that triggers the
 * call twice (some GeckoLib mobs die twice, or send two death packets) got two ragdolls on top of each other;</li>
 * <li>a short removal watch: after the original mob is removed it is checked again for a few seconds and removed again if
 * it comes back (GeckoLib mobs can be re-added by the server during their own death animation).</li>
 * </ul>
 */
public final class RagdollCleanup {
   private static final int SPAWN_MEMORY_TICKS = 600;
   private static final int WATCH_TICKS = 60;

   private static final class Recent {
      final EntityType<?> type;
      final double x;
      final double y;
      final double z;
      final long time;

      Recent(EntityType<?> type, double x, double y, double z, long time) {
         this.type = type;
         this.x = x;
         this.y = y;
         this.z = z;
         this.time = time;
      }
   }

   private static final Map<Integer, Long> SPAWNED = new HashMap<>();
   private static final List<Recent> RECENT = new ArrayList<>();
   private static final Map<Integer, Long> WATCH = new HashMap<>();

   private RagdollCleanup() {
   }

   /** True if this spawn call is a repeat of one already handled. */
   public static boolean isDuplicate(EntityType<?> type, int entityId, double x, double y, double z, long now) {
      if (entityId >= 0) {
         Long t = SPAWNED.get(entityId);
         if (t != null && now - t < SPAWN_MEMORY_TICKS) {
            return true;
         }
      }

      // Same kind of mob, same tick, the exact same spot: two ragdolls would sit inside each other.
      for (Recent r : RECENT) {
         if (r.type == type && Math.abs(now - r.time) <= 1) {
            double dx = r.x - x;
            double dy = r.y - y;
            double dz = r.z - z;
            if (dx * dx + dy * dy + dz * dz < 1.0E-4) {
               return true;
            }
         }
      }

      if (entityId >= 0) {
         SPAWNED.put(entityId, now);
      }

      RECENT.add(new Recent(type, x, y, z, now));
      return false;
   }

   /** Hide and remove the original mob now, and keep checking for a few seconds. */
   public static void discard(@Nullable ClientLevel level, int entityId, @Nullable Entity live) {
      if (level == null || entityId < 0) {
         return;
      }

      if (live != null) {
         live.setInvisible(true);
         level.removeEntity(entityId, Entity.RemovalReason.DISCARDED);
      }

      WATCH.put(entityId, level.getGameTime() + WATCH_TICKS);
   }

   public static void tick(ClientLevel level) {
      long now = level.getGameTime();
      if (!WATCH.isEmpty()) {
         Iterator<Map.Entry<Integer, Long>> it = WATCH.entrySet().iterator();
         while (it.hasNext()) {
            Map.Entry<Integer, Long> e = it.next();
            if (now > e.getValue()) {
               it.remove();
               continue;
            }

            Entity back = level.getEntity(e.getKey());
            if (back != null) {
               SarosRagdollFix.LOG.debug("[ragdoll-fix] original {} came back after removal, removing again", back.getType());
               back.setInvisible(true);
               level.removeEntity(e.getKey(), Entity.RemovalReason.DISCARDED);
            }
         }
      }

      if (now % 100 == 0) {
         SPAWNED.values().removeIf(t -> now - t > SPAWN_MEMORY_TICKS);
         RECENT.removeIf(r -> now - r.time > 5);
      }
   }

   public static void clear() {
      SPAWNED.clear();
      RECENT.clear();
      WATCH.clear();
   }
}
