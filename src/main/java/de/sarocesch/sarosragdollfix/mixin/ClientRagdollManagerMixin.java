package de.sarocesch.sarosragdollfix.mixin;

import com.jme3.math.Vector3f;
import de.sarocesch.sarosmobragdoll.client.ClientMobRagdoll;
import de.sarocesch.sarosmobragdoll.client.ClientRagdollManager;
import de.sarocesch.sarosmobragdoll.client.skeleton.MobSkeleton;
import de.sarocesch.sarosmobragdoll.client.skeleton.VanillaModelSkeletonProvider;
import de.sarocesch.sarosmobragdoll.ragdoll.physics.BoneRagdollPhysics;
import de.sarocesch.sarosragdollfix.DummyFactory;
import de.sarocesch.sarosragdollfix.FixConfig;
import de.sarocesch.sarosragdollfix.GeoSkeletons;
import de.sarocesch.sarosragdollfix.ModelScaleFix;
import de.sarocesch.sarosragdollfix.RagdollCleanup;
import de.sarocesch.sarosragdollfix.RagdollLaunch;
import de.sarocesch.sarosragdollfix.RigidDummies;
import de.sarocesch.sarosragdollfix.SarosRagdollFix;
import de.sarocesch.sarosragdollfix.WoundCompat;
import java.util.List;
import java.util.Random;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = ClientRagdollManager.class, remap = false)
public abstract class ClientRagdollManagerMixin {
   @Unique
   private static final Random SAROS_FIX_RANDOM = new Random();

   @Shadow
   @Final
   private List<ClientMobRagdoll> ragdolls;

   @Unique
   private int saros_fix$sizeBefore;

   @Unique
   private EntityType<?> saros_fix$type;

   /** Wounds frozen at the start of spawn(), while the mob is still in the world. */
   @Unique
   private List<WoundCompat.Wound> saros_fix$wounds;

   @Inject(method = "spawn", at = @At("HEAD"), cancellable = true)
   private void saros_fix$spawnHead(
      EntityType<?> type, int entityId, double x, double y, double z, float bodyYaw, float vx, float vy, float vz, boolean baby, int lifeTicks, CallbackInfo ci
   ) {
      this.saros_fix$wounds = null;
      this.saros_fix$type = type;
      this.saros_fix$sizeBefore = this.ragdolls.size();

      ClientLevel level = Minecraft.getInstance().level;
      if (level == null) {
         return;
      }

      if (FixConfig.DEDUPE_SPAWNS.get() && RagdollCleanup.isDuplicate(type, entityId, x, y, z, level.getGameTime())) {
         SarosRagdollFix.LOG.info("[ragdoll-fix] ignored a duplicate ragdoll spawn for {} (entity id {})", type, entityId);
         ci.cancel();
         return;
      }

      // Must happen here: once the original is removed (below, or by the game) Better Blood Overlay deletes its wounds.
      if (FixConfig.COPY_WOUNDS.get() && entityId >= 0) {
         Entity live = level.getEntity(entityId);
         if (live != null) {
            this.saros_fix$wounds = WoundCompat.capture(live, level.getGameTime());
         }
      }
   }

   @Redirect(
      method = "spawn",
      at = @At(value = "INVOKE", target = "Lde/sarocesch/sarosmobragdoll/ragdoll/physics/BoneRagdollPhysics;setUniformVelocity(Lcom/jme3/math/Vector3f;)V")
   )
   private void saros_fix$launch(BoneRagdollPhysics physics, Vector3f velocity) {
      EntityType<?> type = this.saros_fix$type;
      if (FixConfig.FIX_LAUNCH.get() && type != null) {
         RagdollLaunch.apply(physics, velocity, (double)type.getWidth(), (double)type.getHeight(), FixConfig.settings(), SAROS_FIX_RANDOM);
      } else {
         physics.setUniformVelocity(velocity);
      }
   }

   @Redirect(
      method = "resolveSkeleton",
      at = @At(
         value = "INVOKE",
         target = "Lde/sarocesch/sarosmobragdoll/client/skeleton/VanillaModelSkeletonProvider;build(Lnet/minecraft/world/entity/EntityType;Lnet/minecraft/client/multiplayer/ClientLevel;Z)Lde/sarocesch/sarosmobragdoll/client/skeleton/MobSkeleton;"
      )
   )
   private MobSkeleton saros_fix$skipGuessedSkeleton(VanillaModelSkeletonProvider provider, EntityType<?> type, ClientLevel level, boolean baby) {
      ModelScaleFix.begin(type, baby);
      try {
         if (FixConfig.CONVERT_GEO.get() || FixConfig.CONVERT_CITADEL.get()) {
            // GeckoLib mobs: convert the .geo.json so they get limbs. Null = not convertible, use the old behaviour.
            MobSkeleton converted = GeoSkeletons.tryBuild(provider, type, level, baby);
            if (converted != null) {
               return converted;
            }
         }

         return FixConfig.isForcedRigid(type) ? null : provider.build(type, level, baby);
      } finally {
         ModelScaleFix.end();
      }
   }

   /**
    * After a ragdoll was added. Rigid: give it its own render copy and remove the original entity. Normal (ModelPart):
    * remember this mob's own texture, overlays and wounds so the variant, damage and bullet holes are kept.
    */
   @Inject(method = "spawn", at = @At("RETURN"))
   private void saros_fix$spawnTail(
      EntityType<?> type, int entityId, double x, double y, double z, float bodyYaw, float vx, float vy, float vz, boolean baby, int lifeTicks, CallbackInfo ci
   ) {
      List<WoundCompat.Wound> wounds = this.saros_fix$wounds;
      this.saros_fix$wounds = null;

      if (this.ragdolls.size() <= this.saros_fix$sizeBefore) {
         WoundCompat.releaseAll(wounds);
         return;
      }

      ClientMobRagdoll rag = this.ragdolls.get(this.ragdolls.size() - 1);
      MobSkeleton skeleton = ((ClientMobRagdollAccessor)(Object)rag).saros_fix$getSkeleton();
      if (skeleton == null || skeleton.rigid) {
         WoundCompat.releaseAll(wounds);
      } else if (wounds != null && !wounds.isEmpty()) {
         RigidDummies.putWounds(rag, wounds);
      }

      if (skeleton == null) {
         return;
      }

      ClientLevel level = Minecraft.getInstance().level;
      Entity live = level != null && entityId >= 0 ? level.getEntity(entityId) : null;

      if (!skeleton.rigid) {
         if (level != null && FixConfig.OWN_RENDER_DUMMY.get()) {
            RigidDummies.captureLook(rag, skeleton, type, level, live, baby);
         }

         // Converted GeckoLib mobs: their renderer ignores the original mod's hide trick, so remove the original.
         if (GeoSkeletons.isConverted(skeleton) && FixConfig.REMOVE_ORIGINAL_FOR_RIGID.get()) {
            RagdollCleanup.discard(level, entityId, live);
         }
         return;
      }

      if (level != null && FixConfig.OWN_RENDER_DUMMY.get()) {
         Entity dummy = DummyFactory.create(type, level, live, baby);
         if (dummy != null) {
            RigidDummies.put(rag, dummy);
         }
      }

      if (FixConfig.REMOVE_ORIGINAL_FOR_RIGID.get()) {
         RagdollCleanup.discard(level, entityId, live);
      }
   }
}
