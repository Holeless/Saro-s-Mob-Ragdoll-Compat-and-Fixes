package de.sarocesch.sarosragdollfix.mixin;

import com.jme3.bullet.joints.PhysicsJoint;
import com.jme3.math.Vector3f;
import de.sarocesch.sarosmobragdoll.client.skeleton.Bone;
import de.sarocesch.sarosmobragdoll.ragdoll.physics.BoneRagdollPhysics;
import de.sarocesch.sarosragdollfix.FixConfig;
import de.sarocesch.sarosragdollfix.RagdollLaunch;
import java.util.List;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// the original hardcodes +-0.7 rad (about 40 degrees) on every axis of every joint
@Mixin(value = BoneRagdollPhysics.class, remap = false)
public abstract class BoneRagdollPhysicsMixin {
   @Shadow
   @Final
   private List<PhysicsJoint> joints;

   @Inject(method = "build", at = @At("TAIL"))
   private void saros_fix$looserJoints(List<Bone> bones, int rootIndex, Vector3f spawnPos, float bodyYaw, CallbackInfo ci) {
      if (FixConfig.FIX_JOINTS.get()) {
         RagdollLaunch.applyJointLimits(this.joints, FixConfig.JOINT_LIMIT_DEGREES.get());
      }
   }
}
