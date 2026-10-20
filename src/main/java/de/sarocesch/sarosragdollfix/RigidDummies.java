package de.sarocesch.sarosragdollfix;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import de.sarocesch.sarosmobragdoll.client.ClientMobRagdoll;
import de.sarocesch.sarosmobragdoll.client.skeleton.MobSkeleton;
import de.sarocesch.sarosmobragdoll.client.skeleton.VanillaModelSkeletonProvider;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
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
   public static List<WoundCompat.Wound> wounds(ClientMobRagdoll ragdoll) {
      return WOUNDS.get(ragdoll);
   }

   public static void putWounds(ClientMobRagdoll ragdoll, List<WoundCompat.Wound> wounds) {
      WOUNDS.put(ragdoll, wounds);
   }

   /** Ragdoll removed, free its wound textures. */
   public static void release(ClientMobRagdoll ragdoll) {
      List<WoundCompat.Wound> w = WOUNDS.remove(ragdoll);
      WoundCompat.releaseAll(w);
   }

   // Normal ragdolls: ask the mob's renderer which texture and layers this mob uses. The original caches one per type.
   @SuppressWarnings({"unchecked", "rawtypes"})
   public static void captureLook(ClientMobRagdoll ragdoll, MobSkeleton skeleton, EntityType<?> type, ClientLevel level, @Nullable Entity live, @Nullable Entity prebuilt, boolean baby) {
      if (skeleton.renderer == null) {
         return;
      }

      try {
         Entity dummy = prebuilt != null ? prebuilt : DummyFactory.create(type, level, live, baby);
         if (dummy == null) {
            return;
         }

         ResourceLocation tex = ((EntityRenderer)skeleton.renderer).getTextureLocation(dummy);
         if (tex != null) {
            TEXTURES.put(ragdoll, tex);
         }

         captureLayers(ragdoll, skeleton, dummy);
         if (FixConfig.COPY_DAMAGE_STATE.get()) {
            captureExtras(ragdoll, skeleton, dummy);
         }

         addVillagerClothes(ragdoll, dummy);
      } catch (Throwable t) {
         SarosRagdollFix.LOG.debug("[ragdoll-fix] could not resolve look for {}", type, t);
      }
   }

   // Villager clothes (profession texture and level badge) read straight from the villager's data, same textures and
   // order as vanilla's VillagerProfessionLayer. Doesn't depend on what Saro's layer probe found.
   private static void addVillagerClothes(ClientMobRagdoll ragdoll, Entity dummy) {
      if (!(dummy instanceof VillagerDataHolder holder) || !(dummy instanceof LivingEntity living) || living.isBaby()) {
         return;
      }

      VillagerData data = holder.getVillagerData();
      VillagerProfession profession = data.getProfession();
      if (profession == VillagerProfession.NONE) {
         return;
      }

      List<RenderType> old = EXTRA_TYPES.get(ragdoll);
      List<RenderType> extras = old == null ? new ArrayList<>() : new ArrayList<>(old);
      ResourceLocation id = BuiltInRegistries.VILLAGER_PROFESSION.getKey(profession);
      addTexture(extras, new ResourceLocation(id.getNamespace(), "textures/entity/villager/profession/" + id.getPath() + ".png"));
      if (profession != VillagerProfession.NITWIT) {
         String[] badges = {"stone", "iron", "gold", "emerald", "diamond"};
         int level = Math.max(1, Math.min(5, data.getLevel()));
         addTexture(extras, new ResourceLocation("textures/entity/villager/profession_level/" + badges[level - 1] + ".png"));
      }

      if (!extras.isEmpty()) {
         EXTRA_TYPES.put(ragdoll, extras);
      }
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

         if (!extras.isEmpty()) {
            EXTRA_TYPES.put(ragdoll, extras);
         }
      } catch (Throwable t) {
         SarosRagdollFix.LOG.debug("[ragdoll-fix] could not resolve damage overlays", t);
      }
   }
}
