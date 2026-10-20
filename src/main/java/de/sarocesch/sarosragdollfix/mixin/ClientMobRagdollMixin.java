package de.sarocesch.sarosragdollfix.mixin;

import de.sarocesch.sarosmobragdoll.client.ClientMobRagdoll;
import de.sarocesch.sarosmobragdoll.client.skeleton.MobSkeleton;
import de.sarocesch.sarosragdollfix.RigidDummies;
import net.minecraft.world.entity.Entity;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Draws a rigid ragdoll from its own copy of the mob (right variant, right way round) instead of the shared one. */
@Mixin(value = ClientMobRagdoll.class, remap = false)
public abstract class ClientMobRagdollMixin {
   @Redirect(
      method = "renderRigid",
      at = @At(
         value = "FIELD",
         target = "Lde/sarocesch/sarosmobragdoll/client/skeleton/MobSkeleton;dummy:Lnet/minecraft/world/entity/Entity;",
         opcode = Opcodes.GETFIELD
      )
   )
   private Entity saros_fix$ownDummy(MobSkeleton skeleton) {
      Entity own = RigidDummies.get((ClientMobRagdoll)(Object)this);
      return own != null ? own : skeleton.dummy;
   }
}
