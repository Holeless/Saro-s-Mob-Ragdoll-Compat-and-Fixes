package de.sarocesch.sarosragdollfix;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import javax.annotation.Nullable;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.Entity;

/**
 * Citadel (Alex's Mobs ...) AdvancedEntityModel to a vanilla ModelPart tree. Citadel's boxes use the same space and the
 * same transform order as vanilla parts (rotation point in pixels, Z then Y then X rotation, boxes relative to the
 * rotation point), so this is a direct copy of position, rotation and boxes. Everything is read by reflection, so
 * Citadel is not needed to build this mod and nothing here can crash the game: any failure returns null and the mob
 * keeps the old rigid-box ragdoll.
 */
public final class CitadelModelConverter {
   private static final String ADVANCED_MODEL = "com.github.alexthe666.citadel.client.model.AdvancedEntityModel";

   private CitadelModelConverter() {
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

         // Put the model in its standing pose (the models reset themselves to their default pose in setupAnim).
         try {
            ((EntityModel<Entity>)model).setupAnim(dummy, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F);
         } catch (Throwable t) {
            SarosRagdollFix.LOG.debug("[ragdoll-fix] citadel setupAnim failed, using default pose", t);
         }

         int tw = intField(model, "texWidth", 64);
         int th = intField(model, "texHeight", 32);
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

         Set<Object> isChild = Collections.newSetFromMap(new IdentityHashMap<>());
         for (Object p : parts) {
            for (Object c : children(p)) {
               isChild.add(c);
            }
         }

         MeshDefinition mesh = new MeshDefinition();
         PartDefinition rootDef = mesh.getRoot();
         int[] cubes = new int[1];
         int i = 0;
         for (Object p : parts) {
            if (!isChild.contains(p)) {
               add(rootDef, p, "p" + i++, tw, th, cubes, 0);
            }
         }

         if (cubes[0] == 0) {
            return null;
         }

         return new GeoSyntheticModel(LayerDefinition.create(mesh, tw, th).bakeRoot());
      } catch (Throwable t) {
         SarosRagdollFix.LOG.debug("[ragdoll-fix] could not convert citadel model", t);
         return null;
      }
   }

   private static boolean isAdvanced(Class<?> c) {
      for (Class<?> k = c; k != null; k = k.getSuperclass()) {
         if (k.getName().equals(ADVANCED_MODEL)) {
            return true;
         }
      }

      return false;
   }

   private static void add(PartDefinition parent, Object box, String name, int tw, int th, int[] cubeCount, int depth) throws Exception {
      if (depth > 64 || !bool(box, "showModel", true)) {
         return;
      }

      CubeListBuilder builder = CubeListBuilder.create();
      boolean mirror = bool(box, "mirror", false);
      Object list = field(box, "cubeList");
      if (list instanceof List<?> cubeList) {
         for (Object cube : cubeList) {
            addCube(builder, cube, mirror, tw, th);
            cubeCount[0]++;
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
         add(def, child, name + "_" + i++, tw, th, cubeCount, depth + 1);
      }
   }

   /** One box. Position and size come from posX1..posZ2; texture origin and inflate are recovered from its quads. */
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
