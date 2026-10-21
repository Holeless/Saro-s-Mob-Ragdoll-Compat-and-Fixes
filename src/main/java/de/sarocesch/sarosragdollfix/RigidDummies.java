package de.sarocesch.sarosragdollfix;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import de.sarocesch.sarosmobragdoll.client.ClientMobRagdoll;
import de.sarocesch.sarosmobragdoll.client.skeleton.MobSkeleton;
import de.sarocesch.sarosmobragdoll.client.skeleton.SkeletonLayer;
import de.sarocesch.sarosmobragdoll.client.skeleton.VanillaModelSkeletonProvider;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.TropicalFish;
import net.minecraft.world.entity.monster.ZombieVillager;
import net.minecraft.world.entity.npc.VillagerData;
import net.minecraft.world.entity.npc.VillagerDataHolder;
import net.minecraft.world.entity.npc.VillagerProfession;

// Per-ragdoll render data. Weak keys, so entries go away with their ragdoll.
public final class RigidDummies {
   private static final Map<ClientMobRagdoll, Entity> MAP = Collections.synchronizedMap(new WeakHashMap<>());
   private static final Map<ClientMobRagdoll, ResourceLocation> TEXTURES = Collections.synchronizedMap(new WeakHashMap<>());
   private static final Map<ClientMobRagdoll, Map<RenderType, RenderType>> LAYER_TYPES = Collections.synchronizedMap(new WeakHashMap<>());
   private static final Map<ClientMobRagdoll, List<RenderType>> EXTRA_TYPES = Collections.synchronizedMap(new WeakHashMap<>());
   private static final Map<ClientMobRagdoll, List<WoundCompat.Wound>> WOUNDS = Collections.synchronizedMap(new WeakHashMap<>());

   // colours that are a tint on a shared texture (sheep wool, tropical fish), see TintProbe
   private static final Map<ClientMobRagdoll, Map<RenderType, float[]>> LAYER_TINTS = Collections.synchronizedMap(new WeakHashMap<>());
   private static final Map<ClientMobRagdoll, float[]> BASE_TINTS = Collections.synchronizedMap(new WeakHashMap<>());

   // villager skins we painted ourselves, freed with the ragdoll
   private static final Map<ClientMobRagdoll, ResourceLocation> OWN_SKINS = Collections.synchronizedMap(new WeakHashMap<>());
   private static boolean hookLogged;

   private static boolean reflectionTried;
   private static Method typesUsedBy;
   private static Method layerObjects;
   private static Method findModel;

   private RigidDummies() {
   }

   public static void put(ClientMobRagdoll ragdoll, Entity dummy) {
      MAP.put(ragdoll, dummy);
   }

   @Nullable
   public static Entity get(ClientMobRagdoll ragdoll) {
      return MAP.get(ragdoll);
   }

   public static ResourceLocation textureFor(ClientMobRagdoll ragdoll, MobSkeleton skeleton) {
      ResourceLocation own = TEXTURES.get(ragdoll);
      return own != null ? own : skeleton.texture;
   }

   public static RenderType layerTypeFor(ClientMobRagdoll ragdoll, RenderType shared) {
      Map<RenderType, RenderType> m = LAYER_TYPES.get(ragdoll);
      if (m == null) {
         return shared;
      }

      RenderType own = m.get(shared);
      return own != null ? own : shared;
   }

   /** Overlays only this mob has (golem cracks), or null. */
   @Nullable
   public static List<RenderType> extraTypes(ClientMobRagdoll ragdoll) {
      return EXTRA_TYPES.get(ragdoll);
   }

   @Nullable
   public static Map<RenderType, float[]> layerTints(ClientMobRagdoll ragdoll) {
      return LAYER_TINTS.get(ragdoll);
   }

   @Nullable
   public static float[] baseTint(ClientMobRagdoll ragdoll) {
      return BASE_TINTS.get(ragdoll);
   }

   @Nullable
   public static List<WoundCompat.Wound> wounds(ClientMobRagdoll ragdoll) {
      return WOUNDS.get(ragdoll);
   }

   public static void putWounds(ClientMobRagdoll ragdoll, List<WoundCompat.Wound> wounds) {
      WOUNDS.put(ragdoll, wounds);
   }

   /** Ragdoll removed, free its wound textures. */
   public static void release(ClientMobRagdoll ragdoll) {
      LAYER_TINTS.remove(ragdoll);
      BASE_TINTS.remove(ragdoll);
      List<WoundCompat.Wound> w = WOUNDS.remove(ragdoll);
      WoundCompat.releaseAll(w);
      ResourceLocation skin = OWN_SKINS.remove(ragdoll);
      if (skin != null) {
         Minecraft.getInstance().getTextureManager().release(skin);
      }
   }

   /** Logged once, so the log shows whether the overlay hook in renderLayersFor ever runs. */
   public static void noteOverlayHook() {
      if (!hookLogged) {
         hookLogged = true;
         SarosRagdollFix.LOG.info("[ragdoll-fix] overlay hook in renderLayersFor is running");
      }
   }

   // Adds to the overlay list instead of replacing it, so villager clothes and golem cracks can both be there.
   private static void mergeExtras(ClientMobRagdoll ragdoll, List<RenderType> add) {
      if (add.isEmpty()) {
         return;
      }

      List<RenderType> list = EXTRA_TYPES.computeIfAbsent(ragdoll, k -> new ArrayList<>());
      for (RenderType rt : add) {
         if (!list.contains(rt)) {
            list.add(rt);
         }
      }
   }

   // Normal ragdolls: ask the mob's renderer which texture and layers this mob uses. The original caches one per type.
   @SuppressWarnings({"unchecked", "rawtypes"})
   public static void captureLook(ClientMobRagdoll ragdoll, MobSkeleton skeleton, EntityType<?> type, ClientLevel level, @Nullable Entity live, @Nullable Entity prebuilt, boolean baby) {
      Entity dummy = prebuilt != null ? prebuilt : DummyFactory.create(type, level, live, baby);
      if (dummy == null) {
         return;
      }

      if (FixConfig.FIX_TINTS.get()) {
         captureBaseTint(ragdoll, dummy);
      }

      // The base texture goes through a hook we know runs, so a villager's profession is painted into it.
      ResourceLocation skin = null;
      if (skeleton.renderer != null) {
         try {
            ResourceLocation tex = ((EntityRenderer)skeleton.renderer).getTextureLocation(dummy);
            if (tex != null) {
               skin = buildVillagerSkin(dummy, tex);
               if (skin != null) {
                  OWN_SKINS.put(ragdoll, skin);
               }

               TEXTURES.put(ragdoll, skin != null ? skin : tex);
            }
         } catch (Throwable t) {
            SarosRagdollFix.LOG.debug("[ragdoll-fix] could not resolve texture for {}", type, t);
         }
      }

      if (skin == null) {
         // no painted skin (not a villager, or painting failed): draw the clothes as an overlay instead
         try {
            addVillagerClothes(ragdoll, dummy);
         } catch (Throwable t) {
            SarosRagdollFix.LOG.debug("[ragdoll-fix] villager clothes failed for {}", type, t);
         }
      }

      if (skeleton.renderer == null) {
         return;
      }

      try {
         captureLayers(ragdoll, skeleton, dummy);
         if (FixConfig.COPY_DAMAGE_STATE.get()) {
            captureExtras(ragdoll, skeleton, dummy);
         }

         if (skin != null) {
            redirectTypeLayers(ragdoll, skeleton, skin);
         }
      } catch (Throwable t) {
         SarosRagdollFix.LOG.debug("[ragdoll-fix] could not resolve look for {}", type, t);
      }
   }

   // The body colour of a tropical fish is a tint the renderer sets on its model, not a texture.
   private static void captureBaseTint(ClientMobRagdoll ragdoll, Entity dummy) {
      if (dummy instanceof TropicalFish fish) {
         float[] c = fish.getBaseColor().getTextureDiffuseColors();
         if (c != null && c.length >= 3) {
            BASE_TINTS.put(ragdoll, new float[]{c[0], c[1], c[2]});
         }
      }
   }

   // Biome, profession and level textures laid over the villager's base texture in vanilla's order, as one new texture.
   // Null if this isn't an adult villager with a profession, or if anything goes wrong.
   @Nullable
   private static ResourceLocation buildVillagerSkin(Entity dummy, ResourceLocation base) {
      if (dummy instanceof ZombieVillager || !(dummy instanceof VillagerDataHolder holder) || !(dummy instanceof LivingEntity living) || living.isBaby()) {
         return null;
      }

      VillagerData data = holder.getVillagerData();
      VillagerProfession profession = data.getProfession();
      if (profession == VillagerProfession.NONE) {
         return null;
      }

      List<ResourceLocation> overlays = new ArrayList<>();
      ResourceLocation typeId = BuiltInRegistries.VILLAGER_TYPE.getKey(data.getType());
      overlays.add(new ResourceLocation(typeId.getNamespace(), "textures/entity/villager/type/" + typeId.getPath() + ".png"));
      ResourceLocation id = BuiltInRegistries.VILLAGER_PROFESSION.getKey(profession);
      overlays.add(new ResourceLocation(id.getNamespace(), "textures/entity/villager/profession/" + id.getPath() + ".png"));
      if (profession != VillagerProfession.NITWIT) {
         String[] badges = {"stone", "iron", "gold", "emerald", "diamond"};
         int lvl = Math.max(1, Math.min(5, data.getLevel()));
         overlays.add(new ResourceLocation("textures/entity/villager/profession_level/" + badges[lvl - 1] + ".png"));
      }

      NativeImage out = readImage(base);
      if (out == null) {
         SarosRagdollFix.LOG.info("[ragdoll-fix] could not read villager base texture {}", base);
         return null;
      }

      try {
         for (ResourceLocation loc : overlays) {
            NativeImage over = readImage(loc);
            if (over == null) {
               continue;
            }

            try {
               if (over.getWidth() == out.getWidth() && over.getHeight() == out.getHeight()) {
                  blend(out, over);
               }
            } finally {
               over.close();
            }
         }

         ResourceLocation skin = Minecraft.getInstance().getTextureManager().register("saros_fix_villager", new DynamicTexture(out));
         SarosRagdollFix.LOG.info("[ragdoll-fix] painted villager skin for {} (level {})", id, data.getLevel());
         return skin;
      } catch (Throwable t) {
         out.close();
         SarosRagdollFix.LOG.warn("[ragdoll-fix] could not paint villager skin", t);
         return null;
      }
   }

   @Nullable
   private static NativeImage readImage(ResourceLocation location) {
      Optional<Resource> res = Minecraft.getInstance().getResourceManager().getResource(location);
      if (res.isEmpty()) {
         return null;
      }

      try (InputStream in = res.get().open()) {
         return NativeImage.read(in);
      } catch (IOException e) {
         return null;
      }
   }

   // normal alpha-over, per channel (the packing order doesn't matter for that)
   private static void blend(NativeImage dst, NativeImage src) {
      for (int y = 0; y < dst.getHeight(); y++) {
         for (int x = 0; x < dst.getWidth(); x++) {
            int s = src.getPixelRGBA(x, y);
            int sa = s >>> 24;
            if (sa == 0) {
               continue;
            }

            if (sa == 255) {
               dst.setPixelRGBA(x, y, s);
               continue;
            }

            int d = dst.getPixelRGBA(x, y);
            int result = 0;
            for (int shift = 0; shift < 24; shift += 8) {
               int c = (((s >> shift) & 255) * sa + ((d >> shift) & 255) * (255 - sa)) / 255;
               result |= c << shift;
            }

            int da = d >>> 24;
            result |= Math.min(255, sa + da * (255 - sa) / 255) << 24;
            dst.setPixelRGBA(x, y, result);
         }
      }
   }

   // The original draws the biome texture as a layer on top of the base. That would cover the painted profession,
   // so those layers draw the painted skin instead.
   private static void redirectTypeLayers(ClientMobRagdoll ragdoll, MobSkeleton skeleton, ResourceLocation skin) {
      Object layers = skeleton.layers;
      if (!(layers instanceof Iterable<?> all)) {
         return;
      }

      RenderType own = RenderType.entityCutoutNoCull(skin);
      Map<RenderType, RenderType> map = LAYER_TYPES.computeIfAbsent(ragdoll, k -> new HashMap<>());
      for (Object o : all) {
         if (o instanceof SkeletonLayer layer && layer.renderType != null && layer.renderType.toString().contains("textures/entity/villager/type/")) {
            map.put(layer.renderType, own);
         }
      }
   }

   // Profession texture and level badge, read straight from the villager's data (same as vanilla's
   // VillagerProfessionLayer). Zombie villagers use other textures, the layer capture handles those.
   private static void addVillagerClothes(ClientMobRagdoll ragdoll, Entity dummy) {
      if (dummy instanceof ZombieVillager || !(dummy instanceof VillagerDataHolder holder) || !(dummy instanceof LivingEntity living) || living.isBaby()) {
         return;
      }

      VillagerData data = holder.getVillagerData();
      VillagerProfession profession = data.getProfession();
      if (profession == VillagerProfession.NONE) {
         return;
      }

      List<RenderType> extras = new ArrayList<>();
      ResourceLocation id = BuiltInRegistries.VILLAGER_PROFESSION.getKey(profession);
      addTexture(extras, new ResourceLocation(id.getNamespace(), "textures/entity/villager/profession/" + id.getPath() + ".png"));
      if (profession != VillagerProfession.NITWIT) {
         String[] badges = {"stone", "iron", "gold", "emerald", "diamond"};
         int level = Math.max(1, Math.min(5, data.getLevel()));
         addTexture(extras, new ResourceLocation("textures/entity/villager/profession_level/" + badges[level - 1] + ".png"));
      }

      mergeExtras(ragdoll, extras);
   }

   private static void addTexture(List<RenderType> out, ResourceLocation texture) {
      if (Minecraft.getInstance().getResourceManager().getResource(texture).isPresent()) {
         RenderType type = RenderType.entityCutoutNoCull(texture);
         if (!out.contains(type)) {
            out.add(type);
         }
      }
   }

   // Layers that draw held items, armor, head items, wings... They use their own models or the item atlas, not the mob's
   // cubes, so drawing their types over the body smears the whole atlas across it (skeleton holding a bow).
   private static final String[] EQUIPMENT_LAYERS = {
      "iteminhand", "customhead", "elytra", "armor", "carried", "arrow", "stinger", "parrot", "cape", "helditem", "heldblock"
   };

   private static boolean isEquipmentLayer(Object layer) {
      String name = layer.getClass().getName().toLowerCase(java.util.Locale.ROOT);
      for (String token : EQUIPMENT_LAYERS) {
         if (name.contains(token)) {
            return true;
         }
      }

      return false;
   }

   private static boolean isAtlas(RenderType type) {
      return type.toString().contains("textures/atlas");
   }

   private static boolean initReflection() {
      if (!reflectionTried) {
         reflectionTried = true;
         try {
            Class<?> probe = Class.forName("de.sarocesch.sarosmobragdoll.client.skeleton.LayerProbe");
            typesUsedBy = probe.getDeclaredMethod("typesUsedBy", Object.class, Entity.class);
            typesUsedBy.setAccessible(true);
            layerObjects = VanillaModelSkeletonProvider.class.getDeclaredMethod("layerObjects", EntityRenderer.class);
            layerObjects.setAccessible(true);
            try {
               findModel = VanillaModelSkeletonProvider.class.getDeclaredMethod("findModel", Object.class);
               findModel.setAccessible(true);
            } catch (Throwable t) {
               findModel = null;
            }
         } catch (Throwable t) {
            typesUsedBy = null;
            SarosRagdollFix.LOG.debug("[ragdoll-fix] could not resolve overlay layers", t);
         }
      }

      return typesUsedBy != null && layerObjects != null;
   }

   // Saro bakes each layer's RenderType from one shared default mob. Ask every layer again for this mob and remember
   // old -> new so the layer can be redirected when drawing.
   @SuppressWarnings("unchecked")
   private static void captureLayers(ClientMobRagdoll ragdoll, MobSkeleton skeleton, Entity dummy) {
      if (skeleton.layers == null || skeleton.layers.isEmpty() || skeleton.dummy == null) {
         return;
      }

      try {
         if (!initReflection()) {
            return;
         }

         Map<RenderType, RenderType> map = new HashMap<>();
         Map<RenderType, float[]> tints = new HashMap<>();
         for (Object layer : (List<Object>)layerObjects.invoke(null, skeleton.renderer)) {
            List<RenderType> old = (List<RenderType>)typesUsedBy.invoke(null, layer, skeleton.dummy);
            List<RenderType> now = (List<RenderType>)typesUsedBy.invoke(null, layer, dummy);
            if (old != null && now != null) {
               for (int k = 0; k < Math.min(old.size(), now.size()); k++) {
                  if (!old.get(k).equals(now.get(k))) {
                     map.put(old.get(k), now.get(k));
                  }
               }
            }

            if (FixConfig.FIX_TINTS.get() && now != null && !now.isEmpty() && !isEquipmentLayer(layer)) {
               tints.putAll(TintProbe.tintsUsedBy(layer, dummy));
            }
         }

         if (!tints.isEmpty()) {
            LAYER_TINTS.put(ragdoll, tints);
         }

         if (!map.isEmpty()) {
            LAYER_TYPES.put(ragdoll, map);
         }
      } catch (Throwable t) {
         typesUsedBy = null;
         SarosRagdollFix.LOG.debug("[ragdoll-fix] could not resolve overlay layers", t);
      }
   }

   // Layer types the shared default mob doesn't have but this one does, so the original never collected them. Iron golem
   // cracks (default golem is undamaged), villager profession and level (default villager has none). Only layers that
   // redraw the mob's own model are taken (same cubes, other texture).
   @SuppressWarnings("unchecked")
   private static void captureExtras(ClientMobRagdoll ragdoll, MobSkeleton skeleton, Entity dummy) {
      if (skeleton.renderer == null) {
         return;
      }

      try {
         if (!initReflection()) {
            return;
         }

         EntityModel<?> parent = GeoSkeletons.originalModelOf(skeleton.renderer);
         List<RenderType> extras = new ArrayList<>();
         for (Object layer : (List<Object>)layerObjects.invoke(null, skeleton.renderer)) {
            List<RenderType> now = (List<RenderType>)typesUsedBy.invoke(null, layer, dummy);
            if (now == null || now.isEmpty()) {
               continue;
            }

            if (isEquipmentLayer(layer)) {
               continue;
            }

            // the default mob may already have some of this layer's types (a villager has its biome one), only the
            // extra ones at the end are ours (profession, level badge)
            int skip = 0;
            if (skeleton.dummy != null) {
               List<RenderType> old = (List<RenderType>)typesUsedBy.invoke(null, layer, skeleton.dummy);
               skip = old == null ? 0 : old.size();
            }

            if (skip >= now.size()) {
               continue;
            }

            if (findModel != null) {
               Object own = findModel.invoke(null, layer);
               if (own != null && own != parent) {
                  continue;
               }
            }

            for (RenderType rt : now.subList(skip, now.size())) {
               if (rt.format() == DefaultVertexFormat.NEW_ENTITY && !isAtlas(rt) && !extras.contains(rt)) {
                  extras.add(rt);
               }
            }
         }

         mergeExtras(ragdoll, extras);
      } catch (Throwable t) {
         SarosRagdollFix.LOG.debug("[ragdoll-fix] could not resolve damage overlays", t);
      }
   }
}
