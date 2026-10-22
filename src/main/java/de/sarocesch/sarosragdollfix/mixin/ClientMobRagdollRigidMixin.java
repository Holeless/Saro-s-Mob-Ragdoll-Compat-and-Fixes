package de.sarocesch.sarosragdollfix.mixin;

import de.sarocesch.sarosmobragdoll.client.ClientMobRagdoll;
import de.sarocesch.sarosragdollfix.FixConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

// Separate from ClientMobRagdollMixin because this one targets a vanilla method called from inside the original, so it has
// to be remapped (the other mixin only touches the original's own members).
@Mixin(ClientMobRagdoll.class)
public abstract class ClientMobRagdollRigidMixin {
   // GeckoLib / AzureLib work out the animation time of an entity as tickCount + partialTick. The render copy never ticks, so
   // that is 0 + a number between 0 and 1 that changes every frame, and wings and tails keep twitching on a dead body. The
   // original hands its real partial tick to the renderer, give it 0 instead. Positions don't use this value.
   // require = 0: if the target can't be found the ragdoll still works, it just keeps moving.
   @ModifyArg(
      method = "renderRigid",
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/renderer/entity/EntityRenderer;render(Lnet/minecraft/world/entity/Entity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V"
      ),
      index = 2,
      require = 0
   )
   private float saros_fix$freezeAnimation(float partialTick) {
      return FixConfig.FREEZE_RIGID_ANIMATION.get() ? 0.0F : partialTick;
   }
}
