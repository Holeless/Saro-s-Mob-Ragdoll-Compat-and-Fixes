package de.sarocesch.sarosragdollfix;

import de.sarocesch.sarosmobragdoll.client.ClientMobRagdoll;
import de.sarocesch.sarosmobragdoll.client.skeleton.MobSkeleton;
import de.sarocesch.sarosmobragdoll.client.skeleton.VanillaModelSkeletonProvider;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import javax.annotation.Nullable;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;

/** Per-ragdoll render data. Weak keys: entries disappear with their ragdoll. */
public final class RigidDummies {
   private static final Map<ClientMobRagdoll, Entity> MAP = Collections.synchronizedMap(new WeakHashMap<>());
   private static final Map<ClientMobRagdoll, ResourceLocation> TEXTURES = Collections.synchronizedMap(new WeakHashMap<>());
   private static final Map<ClientMobRagdoll, Map<RenderType, RenderType>> LAYER_TYPES = Collections.synchronizedMap(new WeakHashMap<>());

   private static boolean reflectionTried;
   private static Method typesUsedBy;
   private static Method layerObjects;

   private RigidDummies() {
   }

   public static void put(ClientMobRagdoll ragdoll, Entity dummy) {
      MAP.put(ragdoll, dummy);
   }

   @Nullable
   public static Entity get(ClientMobRagdoll ragdoll) {
      return MAP.get(ragdoll);
   }

   /** Texture of this ragdoll's own mob (right rabbit colour etc.), or the shared one if we have none. */
   public static ResourceLocation textureFor(ClientMobRagdoll ragdoll, MobSkeleton skeleton) {
      ResourceLocation own = TEXTURES.get(ragdoll);
      return own != null ? own : skeleton.texture;
   }

   /** Render type for an overlay layer (villager profession, biome ...) of this ragdoll's own mob. */
   public static RenderType layerTypeFor(ClientMobRagdoll ragdoll, RenderType shared) {
      Map<RenderType, RenderType> m = LAYER_TYPES.get(ragdoll);
      if (m == null) {
         return shared;
      }

      RenderType own = m.get(shared);
      return own != null ? own : shared;
   }

   /**
    * For normal (ModelPart) ragdolls: ask the mob's own renderer which texture and which overlay layers THIS mob (with its
    * variant, villager profession ...) uses. The original mod caches one of each per entity type.
    */
   @SuppressWarnings({"unchecked", "rawtypes"})
   public static void captureLook(ClientMobRagdoll ragdoll, MobSkeleton skeleton, EntityType<?> type, ClientLevel level, @Nullable Entity live, boolean baby) {
      if (skeleton.renderer == null) {
         return;
      }

      try {
         Entity dummy = DummyFactory.create(type, level, live, baby);
         if (dummy == null) {
            return;
         }

         ResourceLocation tex = ((EntityRenderer)skeleton.renderer).getTextureLocation(dummy);
         if (tex != null) {
            TEXTURES.put(ragdoll, tex);
         }

         captureLayers(ragdoll, skeleton, dummy);
      } catch (Throwable t) {
         SarosRagdollFix.LOG.debug("[ragdoll-fix] could not resolve look for {}", type, t);
      }
   }

   /**
    * Saro bakes each overlay layer's RenderType from one shared default mob. Ask every layer again for this mob and
    * remember old to new, so the layer can be redirected at render time.
    */
   @SuppressWarnings("unchecked")
   private static void captureLayers(ClientMobRagdoll ragdoll, MobSkeleton skeleton, Entity dummy) {
      if (skeleton.layers == null || skeleton.layers.isEmpty() || skeleton.dummy == null) {
         return;
      }

      try {
         if (!reflectionTried) {
            reflectionTried = true;
            Class<?> probe = Class.forName("de.sarocesch.sarosmobragdoll.client.skeleton.LayerProbe");
            typesUsedBy = probe.getDeclaredMethod("typesUsedBy", Object.class, Entity.class);
            typesUsedBy.setAccessible(true);
            layerObjects = VanillaModelSkeletonProvider.class.getDeclaredMethod("layerObjects", EntityRenderer.class);
            layerObjects.setAccessible(true);
         }

         if (typesUsedBy == null || layerObjects == null) {
            return;
         }

         Map<RenderType, RenderType> map = new HashMap<>();
         for (Object layer : (List<Object>)layerObjects.invoke(null, skeleton.renderer)) {
            List<RenderType> old = (List<RenderType>)typesUsedBy.invoke(null, layer, skeleton.dummy);
            List<RenderType> now = (List<RenderType>)typesUsedBy.invoke(null, layer, dummy);
            if (old != null && now != null && !old.isEmpty() && !now.isEmpty() && !old.get(0).equals(now.get(0))) {
               map.put(old.get(0), now.get(0));
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
}
