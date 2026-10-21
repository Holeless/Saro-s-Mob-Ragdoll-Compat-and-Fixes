package de.sarocesch.sarosragdollfix;

import de.sarocesch.sarosmobragdoll.client.skeleton.ModelPartAccess;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nullable;
import net.minecraft.client.model.geom.ModelPart;

// Two wing problems.
//
// 1. Bees lost their wings. The original treats every paper-thin part (wings, legs, antennae) as a decoration that is drawn
//    together with the bone it hangs off. A bee's wings hang off an empty container part that is not a bone, so nothing ever
//    drew them. While a skeleton is being built we track which parts have a drawn bone above them, and a thin part that has
//    none becomes a bone of its own.
//
// 2. Wings kept flapping. Decorations that are not bones are drawn in whatever pose the shared model was last left in, which
//    is the flap of the last living bat, bee or bird that rendered. Before a bone is drawn, everything under it that is not
//    a bone goes back to its rest pose.
public final class WingFix {
   private static final ThreadLocal<Map<ModelPart, Boolean>> COVERED = new ThreadLocal<>();

   private WingFix() {
   }

   /** Call before a skeleton build, and end() in a finally. */
   public static void begin() {
      if (FixConfig.FIX_WINGS.get()) {
         COVERED.set(new IdentityHashMap<>());
      } else {
         COVERED.remove();
      }
   }

   public static void end() {
      COVERED.remove();
   }

   /** Runs at the start of the original's dfs, parents are always visited before their children. */
   public static void enter(ModelPart part, @Nullable ModelPart parent) {
      Map<ModelPart, Boolean> map = COVERED.get();
      if (map == null || part == null) {
         return;
      }

      boolean covered = false;
      if (parent != null) {
         covered = Boolean.TRUE.equals(map.get(parent)) || (hasCubes(parent) && !isThin(parent));
      }

      map.put(part, covered);
   }

   /** A part with cubes and no drawn bone above it: it would never be rendered unless it becomes a bone itself. */
   public static boolean isOrphan(ModelPart part, @Nullable ModelPart parent) {
      Map<ModelPart, Boolean> map = COVERED.get();
      return map != null && parent != null && Boolean.FALSE.equals(map.get(part)) && hasCubes(part);
   }

   private static boolean hasCubes(ModelPart part) {
      List<ModelPart.Cube> cubes = ModelPartAccess.cubes(part);
      return cubes != null && !cubes.isEmpty();
   }

   // same test as the original's isFlat: the thinnest side of the cubes is a pixel or less
   private static boolean isThin(ModelPart part) {
      List<ModelPart.Cube> cubes = ModelPartAccess.cubes(part);
      if (cubes == null || cubes.isEmpty()) {
         return false;
      }

      float minX = Float.MAX_VALUE;
      float minY = Float.MAX_VALUE;
      float minZ = Float.MAX_VALUE;
      float maxX = -Float.MAX_VALUE;
      float maxY = -Float.MAX_VALUE;
      float maxZ = -Float.MAX_VALUE;
      for (ModelPart.Cube c : cubes) {
         minX = Math.min(minX, c.minX);
         minY = Math.min(minY, c.minY);
         minZ = Math.min(minZ, c.minZ);
         maxX = Math.max(maxX, c.maxX);
         maxY = Math.max(maxY, c.maxY);
         maxZ = Math.max(maxZ, c.maxZ);
      }

      return Math.min(maxX - minX, Math.min(maxY - minY, maxZ - minZ)) <= 1.0F;
   }

   /** Puts everything under this part that is not a bone back to its rest pose. */
   public static void restPose(ModelPart part, Set<ModelPart> boneParts) {
      if (!FixConfig.FIX_WINGS.get() || part == null) {
         return;
      }

      restChildren(part, boneParts, 0);
   }

   private static void restChildren(ModelPart part, Set<ModelPart> boneParts, int depth) {
      if (depth > 16) {
         return;
      }

      Map<String, ModelPart> kids = ModelPartAccess.children(part);
      if (kids == null) {
         return;
      }

      for (ModelPart child : kids.values()) {
         if (boneParts != null && boneParts.contains(child)) {
            continue;
         }

         child.resetPose();
         restChildren(child, boneParts, depth + 1);
      }
   }
}
