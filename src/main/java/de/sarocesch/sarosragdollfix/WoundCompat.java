package de.sarocesch.sarosragdollfix;

import com.mojang.blaze3d.platform.NativeImage;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;

/**
 * Keeps the wounds / bullet holes of Better Blood Overlay on a ragdoll. All by reflection, so the mod is optional and
 * nothing here can crash the game: any failure just means "no wounds on the ragdoll".
 *
 * <p>Why a copy: that mod stores wounds per entity UUID and deletes them (and frees their textures) when the entity leaves
 * the world. The original mob is removed or dies right when the ragdoll starts, so the ragdoll has to own its own frozen
 * copy of the wound textures, taken before that happens. The copy is freed when the ragdoll is removed.
 */
public final class WoundCompat {
   private static final String PKG = "tailorworks.betterbloodoverlay_1_20_1.";

   private static boolean tried;
   private static boolean ok;
   private static Method getWounds;
   private static Method computeAlpha;
   private static Method fadeIn;
   private static Method woundOverlay;
   private static Method overlayBlood;
   private static Method tintR;
   private static Method tintG;
   private static Method tintB;
   private static Field fTexture;
   private static Field fProfile;
   private static Field fReleased;

   private WoundCompat() {
   }

   /** One frozen wound: its own texture, the render type for it, tint and strength at the moment of death. */
   public static final class Wound {
      private final DynamicTexture texture;
      public final RenderType type;
      public final float r;
      public final float g;
      public final float b;
      public final float alpha;

      Wound(DynamicTexture texture, RenderType type, float r, float g, float b, float alpha) {
         this.texture = texture;
         this.type = type;
         this.r = r;
         this.g = g;
         this.b = b;
         this.alpha = alpha;
      }

      public boolean tinted() {
         return this.r != 1.0F || this.g != 1.0F || this.b != 1.0F;
      }

      void release() {
         try {
            this.texture.close();
         } catch (Throwable ignored) {
         }
      }
   }

   private static boolean init() {
      if (tried) {
         return ok;
      }

      tried = true;
      try {
         Class<?> store = Class.forName(PKG + "WoundStore");
         Class<?> clientWound = Class.forName(PKG + "WoundStore$ClientWound");
         Class<?> renderer = Class.forName(PKG + "BloodOverlayRenderer");
         Class<?> renderType = Class.forName(PKG + "BloodRenderType");
         Class<?> profile = Class.forName(PKG + "MobWoundProfiles$MobProfile");
         Class<?> visuals = Class.forName(PKG + "BloodVisuals");

         getWounds = store.getMethod("getWounds", UUID.class);
         computeAlpha = renderer.getDeclaredMethod("computeAlpha", clientWound, long.class);
         computeAlpha.setAccessible(true);
         fadeIn = renderer.getDeclaredMethod("fadeIn", clientWound, long.class);
         fadeIn.setAccessible(true);
         woundOverlay = renderType.getDeclaredMethod("woundOverlay", ResourceLocation.class);
         woundOverlay.setAccessible(true);
         overlayBlood = visuals.getMethod("overlayBlood");
         tintR = profile.getMethod("tintR");
         tintG = profile.getMethod("tintG");
         tintB = profile.getMethod("tintB");
         fTexture = clientWound.getDeclaredField("texture");
         fTexture.setAccessible(true);
         fProfile = clientWound.getDeclaredField("profile");
         fProfile.setAccessible(true);
         fReleased = clientWound.getDeclaredField("released");
         fReleased.setAccessible(true);
         ok = true;
         SarosRagdollFix.LOG.info("[ragdoll-fix] Better Blood Overlay found, wounds will be kept on ragdolls");
      } catch (ClassNotFoundException e) {
         ok = false;
      } catch (Throwable t) {
         ok = false;
         SarosRagdollFix.LOG.warn("[ragdoll-fix] Better Blood Overlay is present but its API did not match, wounds will not be copied", t);
      }

      return ok;
   }

   /** Freeze the entity's current wounds. Call while the entity is still in the world. Null if there are none. */
   @Nullable
   public static List<Wound> capture(Entity live, long gameTime) {
      if (!init()) {
         return null;
      }

      try {
         if (!(Boolean)overlayBlood.invoke(null)) {
            return null;
         }

         List<?> source = (List<?>)getWounds.invoke(null, live.getUUID());
         if (source == null || source.isEmpty()) {
            return null;
         }

         TextureManager textures = Minecraft.getInstance().getTextureManager();
         List<Wound> out = new ArrayList<>();
         for (Object w : new ArrayList<>(source)) {
            try {
               if (fReleased.getBoolean(w)) {
                  continue;
               }

               DynamicTexture tex = (DynamicTexture)fTexture.get(w);
               NativeImage src = tex == null ? null : tex.getPixels();
               if (src == null) {
                  continue;
               }

               float alpha = (Float)computeAlpha.invoke(null, w, gameTime) * (Float)fadeIn.invoke(null, w, gameTime);
               if (alpha < 0.02F) {
                  continue;
               }

               NativeImage copy = new NativeImage(src.format(), src.getWidth(), src.getHeight(), false);
               copy.copyFrom(src);
               DynamicTexture own = new DynamicTexture(copy);
               ResourceLocation location = textures.register("saros_fix_wound", own);
               RenderType type = (RenderType)woundOverlay.invoke(null, location);

               float r = 1.0F;
               float g = 1.0F;
               float b = 1.0F;
               Object profile = fProfile.get(w);
               if (profile != null) {
                  r = (Float)tintR.invoke(profile);
                  g = (Float)tintG.invoke(profile);
                  b = (Float)tintB.invoke(profile);
               }

               out.add(new Wound(own, type, r, g, b, Math.min(1.0F, alpha)));
            } catch (Throwable t) {
               SarosRagdollFix.LOG.debug("[ragdoll-fix] could not copy one wound", t);
            }
         }

         return out.isEmpty() ? null : out;
      } catch (Throwable t) {
         SarosRagdollFix.LOG.debug("[ragdoll-fix] could not read wounds", t);
         return null;
      }
   }

   public static void releaseAll(@Nullable List<Wound> wounds) {
      if (wounds != null) {
         for (Wound w : wounds) {
            w.release();
         }
      }
   }
}
