package de.sarocesch.sarosragdollfix;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

// Last health fraction seen while alive. The mob is already at 0 when the ragdoll spawns.
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

   /** 0..1, or -1 if we never saw it alive. */
   public static float lastFraction(int entityId) {
      Float f = LAST.get(entityId);
      return f == null ? -1.0F : f;
   }

   public static void clear() {
      LAST.clear();
   }
}
