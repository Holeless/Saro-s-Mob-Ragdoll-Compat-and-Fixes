package de.sarocesch.sarosragdollfix;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/**
 * Remembers the last health fraction each living entity had while it was still alive. When the ragdoll spawns the mob is
 * already at 0 health, so without this a damaged iron golem would be copied as "dead" or (before) as "full health".
 */
public final class HealthTracker {
   private static final Map<Integer, Float> LAST = new HashMap<>();
   private static int ticks;

   private HealthTracker() {
   }

   public static void tick(ClientLevel level) {
      for (Entity e : level.entitiesForRendering()) {
         if (e instanceof LivingEntity le && !le.isDeadOrDying()) {
            float max = le.getMaxHealth();
            if (max > 0.0F) {
               LAST.put(le.getId(), le.getHealth() / max);
            }
         }
      }

      if (++ticks % 200 == 0) {
         LAST.keySet().removeIf(id -> level.getEntity(id) == null);
      }
   }

   /** Health fraction (0..1] before the mob died, or -1 if never seen alive. */
   public static float lastFraction(int entityId) {
      Float f = LAST.get(entityId);
      return f == null ? -1.0F : f;
   }

   public static void clear() {
      LAST.clear();
   }
}
