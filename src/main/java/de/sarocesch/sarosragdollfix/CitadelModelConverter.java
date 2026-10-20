package de.sarocesch.sarosragdollfix;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import javax.annotation.Nullable;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.Entity;

// Turns a Citadel AdvancedEntityModel (Alex's Mobs etc.) into a vanilla ModelPart tree.
// Same coordinate space and transform order as vanilla, so pivots, rotations and cubes copy straight over.
// Citadel is only touched through reflection, so it isn't a build dependency.
// Any failure returns null and the mob falls back to the rigid box.
public final class CitadelModelConverter {
   private static final String ADVANCED_MODEL = "com.github.alexthe666.citadel.client.model.AdvancedEntityModel";

   private CitadelModelConverter() {
   }

   // Checks the superclass chain by name, no Citadel on the classpath needed.
   private static boolean isAdvanced(Class<?> cls) {
      for (Class<?> c = cls; c != null; c = c.getSuperclass()) {
         if (ADVANCED_MODEL.equals(c.getName())) {
            return true;
         }
      }

      return false;
   }

   @Nullable
   @SuppressWarnings("unchecked")
   public static GeoSyntheticModel convert(EntityRenderer<?> renderer, Entity dummy) {
      try {
         if (!(renderer instanceof LivingEntityRenderer<?, ?> living)) {
            return null;
         }

         EntityModel<?> model = living.getModel();
         if (model == null || !isAdvanced(model.getClass())) {
            return null;
         }

         Iterable<?> all = (Iterable<?>)model.getClass().getMethod("getAllParts").invoke(model);
         List<Object> parts = new ArrayList<>();
         for (Object p : all) {
            if (p != null) {
               parts.add(p);
            }
         }

         if (parts.isEmpty()) {
            return null;
         }

         // The renderer shares this model, so it may still be in the last mob's pose.
         // Reset first so a setupAnim that throws can't leave stale values behind.
         resetPose(parts);
         try {
            ((EntityModel<Entity>)model).setupAnim(dummy, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F);
         } catch (Throwable t) {
            SarosRagdollFix.LOG.warn("[ragdoll-fix] citadel setupAnim failed for {}, using default pose", dummy.getType(), t);
            resetPose(parts); // might have died halfway through
         }

         int tw = intField(model, "texWidth", 64);
         int th = intField(model, "texHeight", 32);

         Set<Object> isChild = Collections.newSetFromMap(new IdentityHashMap<>());
         for (Object p : parts) {
            for (Object c : children(p)) {
               isChild.add(c);
            }
         }

         int[] shown = new int[1];
         int[] total = new int[1];
         MeshDefinition mesh = buildMesh(parts, isChild, tw, th, true, shown);
         buildMesh(parts, isChild, tw, th, false, total);

         // The ragdoll is a standing mob, so nearly everything should be visible. If most cubes are
         // flagged hidden the model was caught in a weird state, so ignore showModel instead of losing limbs.
         if (total[0] > 0 && shown[0] < total[0] * 0.6) {
            SarosRagdollFix.LOG.warn("[ragdoll-fix] citadel model {} had {} of {} cubes hidden, ignoring showModel",
               model.getClass().getSimpleName(), total[0] - shown[0], total[0]);
            mesh = buildMesh(parts, isChild, tw, th, false, shown);
         }

         if (shown[0] == 0) {
            return null;
         }

         return new GeoSyntheticModel(LayerDefinition.create(mesh, tw, th).bakeRoot());
      } catch (Throwable t) {
         SarosRagdollFix.LOG.debug("[ragdoll-fix] could not convert citadel model", t);
         return null;
      }
   }

   private static MeshDefinition buildMesh(List<Object> parts, Set<Object> isChild, int tw, int th, boolean respectHidden, int[] cubeCount) throws Exception {
      cubeCount[0] = 0;
      MeshDefinition mesh = new MeshDefinition();
      PartDefinition rootDef = mesh.getRoot();
      int i = 0;
      for (Object p : parts) {
         if (!isChild.contains(p)) {
            add(rootDef, p, "p" + i++, tw, th, cubeCount, 0, respectHidden);
         }
      }

      return mesh;
   }

   // Citadel's own reset back to the default pose
   private static void resetPose(List<Object> parts) {
      for (Object p : parts) {
         try {
            p.getClass().getMethod("resetToDefaultPose").invoke(p);
         } catch (Throwable ignored) {
         }
      }
   }

   private static void add(PartDefinition parent, Object box, String name, int tw, int th, int[] cubeCount, int depth, boolean respectHidden) throws Exception {
      if (depth > 64) {
         return;
      }

      // Hidden parts stay in the tree (pivot + children), they just get no cubes.
      // Skipping them entirely dropped every limb underneath.
      boolean hidden = respectHidden && !bool(box, "showModel", true);

      CubeListBuilder builder = CubeListBuilder.create();
      if (!hidden) {
         boolean mirror = bool(box, "mirror", false);
         Object list = field(box, "cubeList");
         if (list instanceof List<?> cubeList) {
            for (Object cube : cubeList) {
               addCube(builder, cube, mirror, tw, th);
               cubeCount[0]++;
            }
         }
      }

      PartDefinition def = parent.addOrReplaceChild(
         name,
         builder,
         PartPose.offsetAndRotation(
            num(box, "rotationPointX"), num(box, "rotationPointY"), num(box, "rotationPointZ"),
            num(box, "rotateAngleX"), num(box, "rotateAngleY"), num(box, "rotateAngleZ")
         )
      );

      int i = 0;
      for (Object child : children(box)) {
         add(def, child, name + "_" + i++, tw, th, cubeCount, depth + 1, respectHidden);
      }
   }

   // Bounds come from posX1..posZ2. UV origin and inflate are recovered from the quads.
   private static void addCube(CubeListBuilder builder, Object cube, boolean mirror, int tw, int th) throws Exception {
      float x1 = num(cube, "posX1");
      float y1 = num(cube, "posY1");
      float z1 = num(cube, "posZ1");
      float x2 = num(cube, "posX2");
      float y2 = num(cube, "posY2");
      float z2 = num(cube, "posZ2");

      float minU = Float.MAX_VALUE;
      float minV = Float.MAX_VALUE;
      float minX = Float.MAX_VALUE;
      Object[] quads = (Object[])field(cube, "quads");
      if (quads != null) {
         for (Object q : quads) {
            Object[] verts = (Object[])field(q, "vertexPositions");
            if (verts == null) {
               continue;
            }

            for (Object v : verts) {
               org.joml.Vector3f pos = (org.joml.Vector3f)field(v, "position");
               minX = Math.min(minX, pos.x);
               minU = Math.min(minU, num(v, "textureU"));
               minV = Math.min(minV, num(v, "textureV"));
            }
         }
      }

      int u = minU == Float.MAX_VALUE ? 0 : Math.round(minU * tw);
      int v = minV == Float.MAX_VALUE ? 0 : Math.round(minV * th);
      float delta = minX == Float.MAX_VALUE ? 0.0F : Math.round((Math.min(x1, x2) - minX) * 1000.0F) / 1000.0F;
      if (delta < 0.0F || delta > 8.0F) {
         delta = 0.0F;
      }

      builder.texOffs(u, v).mirror(mirror).addBox(x1, y1, z1, x2 - x1, y2 - y1, z2 - z1, new CubeDeformation(delta));
   }

   private static List<Object> children(Object box) {
      List<Object> out = new ArrayList<>();
      try {
         Object c = field(box, "childModels");
         if (c instanceof Iterable<?> it) {
            for (Object o : it) {
               if (o != null) {
                  out.add(o);
               }
            }
         }
      } catch (Throwable ignored) {
      }

      return out;
   }

   @Nullable
   private static Object field(Object o, String name) throws Exception {
      Field f = o.getClass().getField(name);
      f.setAccessible(true);
      return f.get(o);
   }

   private static float num(Object o, String name) throws Exception {
      Object v = field(o, name);
      return v instanceof Number n ? n.floatValue() : 0.0F;
   }

   private static int intField(Object o, String name, int fallback) {
      try {
         Object v = field(o, name);
         if (v instanceof Number n && n.intValue() > 0) {
            return n.intValue();
         }
      } catch (Throwable ignored) {
      }

      return fallback;
   }

   private static boolean bool(Object o, String name, boolean fallback) {
      try {
         Object v = field(o, name);
         return v instanceof Boolean b ? b : fallback;
      } catch (Throwable t) {
         return fallback;
      }
   }
}
