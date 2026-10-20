package de.sarocesch.sarosragdollfix;

import java.util.HashMap;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;

// When a mob dies in bed, vanilla calls stopSleeping() and moves it next to the bed. By the time the ragdoll spawns the
// client entity is already standing there, so we remember where it was lying while it was still asleep.
public final class SleepTracker {
   private static final int MAX_AGE = 20;
   private static final Map<Integer, Spot> SPOTS = new HashMap<>();

   private SleepTracker() {
   }

   public static final class Spot {
      public final double x;
      public final double y;
      public final double z;
      // horizontal direction the head points (the bed's facing)
      public final int dirX;
      public final int dirZ;
      public final float eyeHeight;
      final long time;

      Spot(double x, double y, double z, Direction dir, float eyeHeight, long time) {
         this.x = x;
         this.y = y;
         this.z = z;
         this.dirX = dir.getStepX();
         this.dirZ = dir.getStepZ();
         this.eyeHeight = eyeHeight;
         this.time = time;
      }
   }

   public static void tick(ClientLevel level) {
      long now = level.getGameTime();
      for (Entity e : level.entitiesForRendering()) {
         if (e instanceof LivingEntity le && le.isSleeping()) {
            remember(le, now);
         }
      }

      if (now % 100 == 0) {
         SPOTS.values().removeIf(s -> now - s.time > MAX_AGE);
      }
   }

   private static void remember(LivingEntity le, long now) {
      Direction dir = le.getBedOrientation();
      if (dir != null) {
         SPOTS.put(le.getId(), new Spot(le.getX(), le.getY(), le.getZ(), dir, le.getEyeHeight(Pose.STANDING), now));
      }
   }

   /** Where this mob was sleeping just before it died, or null if it wasn't in a bed. Forgets the entry. */
   @Nullable
   public static Spot take(@Nullable Entity live, int entityId, long now) {
      if (live instanceof LivingEntity le && le.isSleeping()) {
         remember(le, now);
      }

      Spot s = SPOTS.remove(entityId);
      return s != null && now - s.time <= MAX_AGE ? s : null;
   }

   public static void clear() {
      SPOTS.clear();
   }
}
