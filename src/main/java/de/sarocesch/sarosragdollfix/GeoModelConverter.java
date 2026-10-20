package de.sarocesch.sarosragdollfix;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.Reader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.model.geom.builders.PartPose;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;

/**
 * Bedrock/GeckoLib geometry (.geo.json) to a vanilla ModelPart tree.
 *
 * <p>Both formats use pixels (1/16 block) and the same box-UV layout, and both put the front at -Z with +X on the same
 * side. Only Y differs: Bedrock is Y-up with the feet at 0, the vanilla model space is Y-down with the feet at 24. So a
 * point (x, y, z) becomes (x, 24 - y, z), and relative to a parent pivot (dx, -dy, dz). Rotations are in the same ZYX
 * order and carry over unchanged (the Y mirror and GeckoLib's own sign flips cancel out), as in Blockbench's exporter.
 */
public final class GeoModelConverter {
   private GeoModelConverter() {
   }

   @Nullable
   public static GeoSyntheticModel convert(ResourceLocation location, boolean baby, Set<String> hiddenBones) {
      try {
         Optional<Resource> res = Minecraft.getInstance().getResourceManager().getResource(location);
         if (res.isEmpty()) {
            return null;
         }

         JsonObject json;
         try (Reader reader = res.get().openAsReader()) {
            json = JsonParser.parseReader(reader).getAsJsonObject();
         }

         JsonArray geos = json.has("minecraft:geometry") ? json.getAsJsonArray("minecraft:geometry") : null;
         if (geos == null || geos.size() == 0) {
            return null;
         }

         JsonObject geo = geos.get(0).getAsJsonObject();
         JsonObject desc = geo.has("description") ? geo.getAsJsonObject("description") : new JsonObject();
         int tw = desc.has("texture_width") ? desc.get("texture_width").getAsInt() : 64;
         int th = desc.has("texture_height") ? desc.get("texture_height").getAsInt() : 64;
         JsonArray bones = geo.has("bones") ? geo.getAsJsonArray("bones") : null;
         if (bones == null || bones.size() == 0) {
            return null;
         }

         Map<String, JsonObject> byName = new LinkedHashMap<>();
         for (JsonElement e : bones) {
            JsonObject b = e.getAsJsonObject();
            byName.put(b.get("name").getAsString(), b);
         }

         Map<String, List<JsonObject>> children = new HashMap<>();
         List<JsonObject> roots = new ArrayList<>();
         for (JsonObject b : byName.values()) {
            String parent = b.has("parent") ? b.get("parent").getAsString() : null;
            if (parent == null || !byName.containsKey(parent) || parent.equals(b.get("name").getAsString())) {
               roots.add(b);
            } else {
               children.computeIfAbsent(parent, k -> new ArrayList<>()).add(b);
            }
         }

         MeshDefinition mesh = new MeshDefinition();
         PartDefinition rootDef = mesh.getRoot();
         int[] cubeCount = new int[1];
         for (JsonObject r : roots) {
            addBone(rootDef, r, null, children, hiddenBones, baby, cubeCount, 0);
         }

         if (cubeCount[0] == 0) {
            return null;
         }

         ModelPart root = LayerDefinition.create(mesh, tw, th).bakeRoot();
         return new GeoSyntheticModel(root);
      } catch (Throwable t) {
         SarosRagdollFix.LOG.debug("[ragdoll-fix] could not convert geo model {}", location, t);
         return null;
      }
   }

   private static boolean isHidden(JsonObject bone, String name, boolean baby, Set<String> hidden) {
      if (bone.has("neverRender") && bone.get("neverRender").getAsBoolean()) {
         return true;
      }

      String l = name.toLowerCase(Locale.ROOT);
      if (l.startsWith("baby_")) {
         return !baby;
      }

      return hidden.contains(l);
   }

   private static void addBone(
      PartDefinition parent, JsonObject b, @Nullable float[] parentPivot, Map<String, List<JsonObject>> children, Set<String> hidden,
      boolean baby, int[] cubeCount, int depth
   ) {
      String name = b.get("name").getAsString();
      if (depth > 64 || isHidden(b, name, baby, hidden)) {
         return;
      }

      float[] p = vec(b, "pivot");
      float[] rot = vec(b, "rotation");
      float ox = parentPivot == null ? p[0] : p[0] - parentPivot[0];
      float oy = parentPivot == null ? 24.0F - p[1] : -(p[1] - parentPivot[1]);
      float oz = parentPivot == null ? p[2] : p[2] - parentPivot[2];

      boolean boneMirror = b.has("mirror") && b.get("mirror").getAsBoolean();
      float boneInflate = b.has("inflate") ? b.get("inflate").getAsFloat() : 0.0F;

      CubeListBuilder cubes = CubeListBuilder.create();
      List<JsonObject> rotated = new ArrayList<>();
      if (b.has("cubes")) {
         for (JsonElement ce : b.getAsJsonArray("cubes")) {
            JsonObject c = ce.getAsJsonObject();
            float[] cr = vec(c, "rotation");
            if (cr[0] != 0.0F || cr[1] != 0.0F || cr[2] != 0.0F) {
               rotated.add(c);
            } else {
               addCube(cubes, c, p, boneMirror, boneInflate);
               cubeCount[0]++;
            }
         }
      }

      PartDefinition def = parent.addOrReplaceChild(
         name, cubes, PartPose.offsetAndRotation(ox, oy, oz, rad(rot[0]), rad(rot[1]), rad(rot[2]))
      );

      int i = 0;
      for (JsonObject c : rotated) {
         float[] cp = c.has("pivot") ? vec(c, "pivot") : p;
         float[] cr = vec(c, "rotation");
         CubeListBuilder one = CubeListBuilder.create();
         addCube(one, c, cp, boneMirror, boneInflate);
         cubeCount[0]++;
         def.addOrReplaceChild(
            name + "__cube" + i++, one,
            PartPose.offsetAndRotation(cp[0] - p[0], -(cp[1] - p[1]), cp[2] - p[2], rad(cr[0]), rad(cr[1]), rad(cr[2]))
         );
      }

      List<JsonObject> kids = children.get(name);
      if (kids != null) {
         for (JsonObject k : kids) {
            addBone(def, k, p, children, hidden, baby, cubeCount, depth + 1);
         }
      }
   }

   /** One box, positioned relative to {@code pv} (the pivot of the part that owns it). */
   private static void addCube(CubeListBuilder builder, JsonObject c, float[] pv, boolean boneMirror, float boneInflate) {
      float[] o = vec(c, "origin");
      float[] s = vec(c, "size");
      float inflate = c.has("inflate") ? c.get("inflate").getAsFloat() : boneInflate;
      boolean mirror = c.has("mirror") ? c.get("mirror").getAsBoolean() : boneMirror;
      int u = 0;
      int v = 0;
      if (c.has("uv")) {
         JsonElement uv = c.get("uv");
         if (uv.isJsonArray() && uv.getAsJsonArray().size() >= 2) {
            u = Math.round(uv.getAsJsonArray().get(0).getAsFloat());
            v = Math.round(uv.getAsJsonArray().get(1).getAsFloat());
         } else if (uv.isJsonObject() && uv.getAsJsonObject().has("north")) {
            // Per-face UV: approximate with the box layout derived from the front (north) face.
            JsonObject north = uv.getAsJsonObject().getAsJsonObject("north");
            if (north.has("uv")) {
               JsonArray a = north.getAsJsonArray("uv");
               u = Math.round(a.get(0).getAsFloat() - s[2]);
               v = Math.round(a.get(1).getAsFloat() - s[2]);
            }
         }
      }

      builder.texOffs(u, v).mirror(mirror).addBox(
         o[0] - pv[0], pv[1] - o[1] - s[1], o[2] - pv[2], s[0], s[1], s[2], new CubeDeformation(inflate)
      );
   }

   private static float[] vec(JsonObject o, String key) {
      float[] r = new float[3];
      if (o.has(key) && o.get(key).isJsonArray()) {
         JsonArray a = o.getAsJsonArray(key);
         for (int i = 0; i < 3 && i < a.size(); i++) {
            r[i] = a.get(i).getAsFloat();
         }
      }

      return r;
   }

   private static float rad(float deg) {
      return (float)Math.toRadians(deg);
   }
}
