package de.sarocesch.sarosragdollfix;

import de.sarocesch.sarosmobragdoll.client.skeleton.MobSkeleton;
import de.sarocesch.sarosmobragdoll.client.skeleton.VanillaModelSkeletonProvider;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.WeakHashMap;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;

// Real multi-bone skeletons for GeckoLib / AzureLib / Citadel mobs: the model is converted to a ModelPart tree and Saro's own
// builder runs on it like it would for a vanilla mob.
public final class GeoSkeletons {
   private static final ThreadLocal<EntityModel<?>> OVERRIDE = new ThreadLocal<>();
   private static final Set<MobSkeleton> CONVERTED = Collections.synchronizedSet(Collections.newSetFromMap(new WeakHashMap<>()));
   private static Method modelOf;

   private GeoSkeletons() {
   }

   /** Model to use for the build() call running right now, or null. */
   @Nullable
   public static EntityModel<?> override() {
      return OVERRIDE.get();
   }

   public static boolean isConverted(MobSkeleton skeleton) {
      return CONVERTED.contains(skeleton);
   }

   public static void markConverted(MobSkeleton skeleton) {
      CONVERTED.add(skeleton);
   }

   // Saro's private modelOf
   @Nullable
   public static EntityModel<?> originalModelOf(EntityRenderer<?> renderer) {
      try {
         if (modelOf == null) {
            Method m = VanillaModelSkeletonProvider.class.getDeclaredMethod("modelOf", EntityRenderer.class);
            m.setAccessible(true);
            modelOf = m;
         }

         return (EntityModel<?>)modelOf.invoke(null, renderer);
      } catch (Throwable t) {
         SarosRagdollFix.LOG.warn("[ragdoll-fix] could not call modelOf", t);
         return null;
      }
   }

   /** Null if this isn't a convertible mob. */
   @Nullable
   public static MobSkeleton tryBuild(VanillaModelSkeletonProvider provider, EntityType<?> type, ClientLevel level, boolean baby) {
      try {
         Entity dummy = DummyFactory.create(type, level, null, baby);
         if (dummy == null) {
            return null;
         }

         EntityRenderer<?> renderer = Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(dummy);
         if (renderer == null) {
            return null;
         }

         Set<String> hidden = new HashSet<>();
         for (String s : FixConfig.GEO_HIDDEN_BONES.get()) {
            hidden.add(s.trim().toLowerCase(Locale.ROOT));
         }

         GeoSyntheticModel model = null;
         String source = "";
         if (FixConfig.CONVERT_GEO.get()) {
            ResourceLocation geo = resolveGeo(renderer, dummy, type);
            if (geo != null) {
               model = GeoModelConverter.convert(geo, baby, hidden);
               source = geo.toString();
            }
         }

         if (model == null && FixConfig.CONVERT_CITADEL.get()) {
            model = CitadelModelConverter.convert(renderer, dummy);
            source = "citadel model";
         }

         if (model == null) {
            return null;
         }

         MobSkeleton skeleton;
         try {
            OVERRIDE.set(model);
            skeleton = provider.build(type, level, baby);
         } finally {
            OVERRIDE.remove();
         }

         if (skeleton == null || skeleton.rigid || skeleton.bones.size() < 2) {
            return null;
         }

         CONVERTED.add(skeleton);
         SarosRagdollFix.LOG.info("[ragdoll-fix] {} -> {} bones from {}", type, skeleton.bones.size(), source);
         return skeleton;
      } catch (Throwable t) {
         SarosRagdollFix.LOG.warn("[ragdoll-fix] geo conversion failed for {}", type, t);
         return null;
      }
   }

   private static final String AZ_ENTITY_RENDERER = "mod.azure.azurelib.render.entity.AzEntityRenderer";

   // GeckoLib renderers have getGeoModel(), AzureLib ones keep the model location in their config.
   // Falls back to geo/entity/<name>.geo.json.
   @Nullable
   private static ResourceLocation resolveGeo(EntityRenderer<?> renderer, Entity dummy, EntityType<?> type) {
      boolean geckoLib = hasMethod(renderer, "getGeoModel");
      boolean azureLib = isAzureRenderer(renderer);
      if (!geckoLib && !azureLib) {
         return null;
      }

      ResourceLocation found = geckoLib ? fromGeckoLib(renderer, dummy, type) : fromAzureLib(renderer, dummy, type);
      if (found != null) {
         return found;
      }

      ResourceLocation key = EntityType.getKey(type);
      ResourceLocation guess = new ResourceLocation(key.getNamespace(), "geo/entity/" + key.getPath() + ".geo.json");
      return Minecraft.getInstance().getResourceManager().getResource(guess).isPresent() ? guess : null;
   }

   private static boolean hasMethod(Object o, String name) {
      try {
         o.getClass().getMethod(name);
         return true;
      } catch (NoSuchMethodException e) {
         return false;
      }
   }

   private static boolean isAzureRenderer(Object renderer) {
      for (Class<?> c = renderer.getClass(); c != null; c = c.getSuperclass()) {
         if (AZ_ENTITY_RENDERER.equals(c.getName())) {
            return true;
         }
      }

      return false;
   }

   @Nullable
   private static ResourceLocation fromGeckoLib(EntityRenderer<?> renderer, Entity dummy, EntityType<?> type) {
      try {
         Object model = renderer.getClass().getMethod("getGeoModel").invoke(renderer);
         if (model != null) {
            for (Method m : model.getClass().getMethods()) {
               if (!m.getName().equals("getModelResource")) {
                  continue;
               }

               try {
                  Object r = null;
                  if (m.getParameterCount() == 1 && m.getParameterTypes()[0].isInstance(dummy)) {
                     r = m.invoke(model, dummy);
                  } else if (m.getParameterCount() == 2 && m.getParameterTypes()[0].isInstance(dummy)) {
                     r = m.invoke(model, dummy, renderer);
                  }

                  if (r instanceof ResourceLocation rl) {
                     return rl;
                  }
               } catch (Throwable ignored) {
               }
            }
         }
      } catch (Throwable t) {
         SarosRagdollFix.LOG.debug("[ragdoll-fix] getGeoModel failed for {}", type, t);
      }

      return null;
   }

   // the config sits in a private field of AzEntityRenderer
   @Nullable
   private static ResourceLocation fromAzureLib(EntityRenderer<?> renderer, Entity dummy, EntityType<?> type) {
      try {
         Object config = null;
         for (Class<?> c = renderer.getClass(); c != null && config == null; c = c.getSuperclass()) {
            try {
               Field f = c.getDeclaredField("config");
               f.setAccessible(true);
               config = f.get(renderer);
            } catch (NoSuchFieldException ignored) {
            }
         }

         if (config == null) {
            return null;
         }

         for (Method m : config.getClass().getMethods()) {
            if (!m.getName().equals("modelLocation")) {
               continue;
            }

            try {
               Object r = null;
               if (m.getParameterCount() == 1 && m.getParameterTypes()[0].isInstance(dummy)) {
                  r = m.invoke(config, dummy);
               } else if (m.getParameterCount() == 2 && m.getParameterTypes()[1].isInstance(dummy)) {
                  r = m.invoke(config, dummy, dummy);
               }

               if (r instanceof ResourceLocation rl) {
                  return rl;
               }
            } catch (Throwable ignored) {
            }
         }
      } catch (Throwable t) {
         SarosRagdollFix.LOG.debug("[ragdoll-fix] could not read the AzureLib model location for {}", type, t);
      }

      return null;
   }
}
