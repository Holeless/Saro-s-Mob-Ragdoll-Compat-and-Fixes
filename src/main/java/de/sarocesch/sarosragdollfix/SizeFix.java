package de.sarocesch.sarosragdollfix;

import com.jme3.math.Vector3f;
import de.sarocesch.sarosmobragdoll.client.skeleton.Bone;
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
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

// Saro builds one skeleton per entity type from a default mob and reuses it. Mobs whose size depends on the individual
// (Ice and Fire dragons, slimes...) came out at the default size, so a grown dragon ragdolled as a hatchling.
// This rescales the cached skeleton to the size the mob actually had.
public final class SizeFix {
   // sizes are grouped in 4% steps, so a growing mob doesn't get a new skeleton every tick of its life
   private static final double STEP = Math.log(1.04);
   private static final int MAX_VARIANTS = 16;

   private static final Map<MobSkeleton, Map<Long, MobSkeleton>> VARIANTS = Collections.synchronizedMap(new WeakHashMap<>());
   private static Method rendererScale;
   private static boolean reflectionTried;

   private SizeFix() {
   }

   /** The skeleton to use for this mob. Same instance if its size matches the default. */
   public static MobSkeleton fit(MobSkeleton base, @Nullable Entity entity, boolean baby) {
      if (base == null || entity == null || !FixConfig.FIX_ENTITY_SIZE.get()) {
         return base;
      }

      try {
         return base.rigid ? fitRigid(base, entity) : fitModel(base, entity, baby);
      } catch (Throwable t) {
         SarosRagdollFix.LOG.debug("[ragdoll-fix] size fix failed", t);
         return base;
      }
   }

   // Ratio of the renderer's scale() for this mob and for the default one the skeleton was built from.
   // Babies already get Saro's own baby scaling, so they are left alone.
   private static MobSkeleton fitModel(MobSkeleton base, Entity entity, boolean baby) {
      if (baby || base.renderer == null || base.dummy == null) {
         return base;
      }

      float own = scaleOf(base.renderer, entity);
      float def = scaleOf(base.renderer, base.dummy);
      if (own <= 0.0F || def <= 0.0F) {
         return base;
      }

      int bucket = bucket(own / def);
      if (bucket == 0) {
         return base;
      }

      float k = (float)Math.exp(bucket * STEP);
      return variant(base, (long)bucket, () -> scaled(base, k, k, k));
   }

   // A rigid skeleton is one box the size of the default mob, compare it with the real bounding box.
   private static MobSkeleton fitRigid(MobSkeleton base, Entity entity) {
      if (base.bones.isEmpty() || !(entity instanceof LivingEntity)) {
         return base;
      }

      Bone box = base.bones.get(0);
      float rs = box.renderScale == 0.0F ? 1.0F : box.renderScale;
      float w = 2.0F * Math.max(box.halfExtents.x, box.halfExtents.z) * rs;
      float h = 2.0F * box.halfExtents.y * rs;
      if (w <= 0.0F || h <= 0.0F) {
         return base;
      }

      int bw = bucket(entity.getBbWidth() / w);
      int bh = bucket(entity.getBbHeight() / h);
      if (bw == 0 && bh == 0) {
         return base;
      }

      float kw = (float)Math.exp(bw * STEP);
      float kh = (float)Math.exp(bh * STEP);
      return variant(base, ((long)bw << 32) ^ (bh & 0xFFFFFFFFL), () -> scaled(base, kw, kh, kw));
   }

   private static int bucket(float ratio) {
      if (!Float.isFinite(ratio) || ratio <= 0.0F) {
         return 0;
      }

      return (int)Math.round(Math.log(ratio) / STEP);
   }

   private static MobSkeleton variant(MobSkeleton base, long key, java.util.function.Supplier<MobSkeleton> make) {
      synchronized (VARIANTS) {
         Map<Long, MobSkeleton> map = VARIANTS.computeIfAbsent(base, b -> new HashMap<>());
         if (map.size() >= MAX_VARIANTS) {
            map.clear();
         }

         return map.computeIfAbsent(key, k -> make.get());
      }
   }

   // Sizes, positions and pivots are in blocks from the feet, so they scale directly. A model bone's collision box is
   // halfExtents * renderScale, so only renderScale changes there. Rigid boxes have no renderScale, scale the box itself.
   private static MobSkeleton scaled(MobSkeleton base, float kx, float ky, float kz) {
      boolean uniform = !base.rigid;
      List<Bone> bones = new ArrayList<>(base.bones.size());
      for (Bone b : base.bones) {
         Vector3f half = uniform ? b.halfExtents.clone() : new Vector3f(b.halfExtents.x * kx, b.halfExtents.y * ky, b.halfExtents.z * kz);
         Vector3f local = uniform ? b.localBoxCenter.clone() : new Vector3f(b.localBoxCenter.x * kx, b.localBoxCenter.y * ky, b.localBoxCenter.z * kz);
         float volume = kx * ky * kz;
         float mass = Math.max(0.2F, Math.min(40.0F, b.mass * volume));
         bones.add(new Bone(
            b.part,
            b.parentIndex,
            mass,
            half,
            new Vector3f(b.restCenter.x * kx, b.restCenter.y * ky, b.restCenter.z * kz),
            b.restRotation.clone(),
            new Vector3f(b.jointPivot.x * kx, b.jointPivot.y * ky, b.jointPivot.z * kz),
            local,
            uniform ? b.renderScale * kx : b.renderScale
         ));
      }

      MobSkeleton out = new MobSkeleton(base.rigid, bones, base.rootIndex, base.texture, base.dummy, base.renderer, base.layers);
      if (GeoSkeletons.isConverted(base)) {
         GeoSkeletons.markConverted(out);
      }

      return out;
   }

   // Saro's own helper: what the mob's renderer scale() does to the pose stack. Returns -1 if it can't tell.
   private static float scaleOf(EntityRenderer<?> renderer, Entity entity) {
      if (!reflectionTried) {
         reflectionTried = true;
         try {
            Method m = VanillaModelSkeletonProvider.class.getDeclaredMethod("rendererScale", EntityRenderer.class, Entity.class);
            m.setAccessible(true);
            rendererScale = m;
         } catch (Throwable t) {
            SarosRagdollFix.LOG.warn("[ragdoll-fix] could not find rendererScale, size fix is off", t);
         }
      }

      if (rendererScale == null) {
         return -1.0F;
      }

      try {
         return (Float)rendererScale.invoke(null, renderer, entity);
      } catch (Throwable t) {
         return -1.0F;
      }
   }
}
