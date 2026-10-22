package de.sarocesch.sarosragdollfix;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;

// Some mods build one mob out of several entities, Alex's Mobs' bone serpent is a head plus a chain of segment entities. The
// original only ragdolls the entity that died, so the head fell over and the body vanished once its parent was gone.
// Finds the segments of a dying mob so they can be ragdolled as well.
public final class MultipartCompat {
   /** One segment, with its own id so the ragdoll can look the mob up and hide it. */
   public static final class Segment {
      public final Entity entity;

      Segment(Entity entity) {
         this.entity = entity;
      }
   }

   private MultipartCompat() {
   }

   /** Has to run while the dying mob and its segments are still in the world. */
   public static List<Segment> collect(@Nullable Entity head, ClientLevel level) {
      List<Segment> out = new ArrayList<>();
      if (head == null || !FixConfig.RAGDOLL_SEGMENTS.get() || FixConfig.isSegmentType(head.getType())) {
         return out;
      }

      double r = FixConfig.SEGMENT_RADIUS.get();
      AABB box = head.getBoundingBox().inflate(r);
      for (Entity e : level.getEntities((Entity)null, box, o -> o != head && o.isAlive() && FixConfig.isSegmentType(o.getType()))) {
         Boolean mine = belongsTo(e, head);
         if (mine == null || mine) {
            out.add(new Segment(e));
         }
      }

      if (!out.isEmpty()) {
         SarosRagdollFix.LOG.debug("[ragdoll-fix] {} has {} segment entities", head.getType(), out.size());
      }

      return out;
   }

   // Segments in these mods know their parent, but under different names, so ask by shape: a no-argument method returning
   // an Entity. TRUE/FALSE if it gave an answer, null if it can't say (then distance decides).
   @Nullable
   private static Boolean belongsTo(Entity segment, Entity head) {
      for (String name : new String[]{"getParent", "getHead", "getOwnerEntity"}) {
         try {
            Method m = segment.getClass().getMethod(name);
            if (Entity.class.isAssignableFrom(m.getReturnType())) {
               Object parent = m.invoke(segment);
               if (parent instanceof Entity p) {
                  return p == head || p.getId() == head.getId();
               }
            }
         } catch (Throwable ignored) {
         }
      }

      return null;
   }
}
