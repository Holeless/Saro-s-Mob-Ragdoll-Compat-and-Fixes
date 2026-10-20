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
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;

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
         if (FixConfig.COPY_DAMAGE_STATE.get()) {
            captureExtras(ragdoll, skeleton, dummy);
         }
      } catch (Throwable t) {
         SarosRagdollFix.LOG.debug("[ragdoll-fix] could not resolve look for {}", type, t);
      }
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

   // Layers that draw nothing on the shared default mob but do on this one, so the original never collected them.
   // Iron golem cracks: the default golem is undamaged so every golem ragdoll came out like new. Only layers that redraw
   // the mob's own model are taken (same cubes, other texture).
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

            if (skeleton.dummy != null) {
               List<RenderType> old = (List<RenderType>)typesUsedBy.invoke(null, layer, skeleton.dummy);
               if (old != null && !old.isEmpty()) {
                  continue;
               }
            }

            if (findModel != null) {
               Object own = findModel.invoke(null, layer);
               if (own != null && own != parent) {
                  continue;
               }
            }

            for (RenderType rt : now) {
               if (rt.format() == DefaultVertexFormat.NEW_ENTITY && !extras.contains(rt)) {
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
