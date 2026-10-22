package de.sarocesch.sarosragdollfix.mixin;

import com.jme3.bullet.PhysicsSpace;
import com.jme3.bullet.objects.PhysicsRigidBody;
import de.sarocesch.sarosmobragdoll.ragdoll.physics.BasePhysicsWorld;
import de.sarocesch.sarosragdollfix.FixConfig;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import java.util.List;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Bullet puts a body to sleep once it has been still for a couple of seconds, and a sleeping body does not notice that the
// block it rests on was removed. The original never wakes them, so a settled corpse (small ones most of all) hung in the air
// after its floor was broken. Whenever the set of terrain boxes changes, wake everything that can move.
@Mixin(value = BasePhysicsWorld.class, remap = false)
public abstract class BasePhysicsWorldMixin {
   @Shadow
   @Final
   private Long2ObjectMap<List<PhysicsRigidBody>> terrainBodies;

   @Shadow
   public abstract PhysicsSpace getDynamicsWorld();

   @Unique
   private long saros_fix$terrainSig;

   @Unique
   private int saros_fix$terrainCount = -1;

   @Inject(method = "syncTerrain", at = @At("RETURN"))
   private void saros_fix$wakeOnTerrainChange(Long2ObjectMap<float[][]> wanted, CallbackInfo ci) {
      if (!FixConfig.WAKE_ON_TERRAIN_CHANGE.get()) {
         return;
      }

      long sig = 0L;
      for (long key : this.terrainBodies.keySet()) {
         // cheap order independent hash of which blocks have collision
         long h = key * -7046029254386353131L;
         sig += h ^ (h >>> 29);
      }

      int count = this.terrainBodies.size();
      boolean changed = count != this.saros_fix$terrainCount || sig != this.saros_fix$terrainSig;
      boolean first = this.saros_fix$terrainCount < 0;
      this.saros_fix$terrainCount = count;
      this.saros_fix$terrainSig = sig;
      if (!changed || first) {
         return;
      }

      PhysicsSpace space = this.getDynamicsWorld();
      if (space == null) {
         return;
      }

      for (PhysicsRigidBody body : space.getRigidBodyList()) {
         if (body.isDynamic()) {
            body.activate(true);
         }
      }
   }
}
