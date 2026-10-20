package de.sarocesch.sarosragdollfix;

import de.sarocesch.sarosmobragdoll.client.ClientMobRagdoll;
import de.sarocesch.sarosmobragdoll.client.skeleton.MobSkeleton;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;
import javax.annotation.Nullable;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;

/** Per-ragdoll render data. Weak keys: entries disappear with their ragdoll. */
public final class RigidDummies {
   private static final Map<ClientMobRagdoll, Entity> MAP = Collections.synchronizedMap(new WeakHashMap<>());
   private static final Map<ClientMobRagdoll, ResourceLocation> TEXTURES = Collections.synchronizedMap(new WeakHashMap<>());

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

   /**
    * For normal (ModelPart) ragdolls: ask the mob's own renderer which texture THIS mob (with its variant) uses.
    * The original mod caches one texture per entity type, so every rabbit came out brown.
    */
   @SuppressWarnings({"unchecked", "rawtypes"})
   public static void captureTexture(ClientMobRagdoll ragdoll, MobSkeleton skeleton, EntityType<?> type, ClientLevel level, @Nullable Entity live, boolean baby) {
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
      } catch (Throwable t) {
         SarosRagdollFix.LOG.debug("[ragdoll-fix] could not resolve texture for {}", type, t);
      }
   }
}
