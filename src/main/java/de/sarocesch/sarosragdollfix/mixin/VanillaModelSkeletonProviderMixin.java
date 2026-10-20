package de.sarocesch.sarosragdollfix.mixin;

import de.sarocesch.sarosmobragdoll.client.skeleton.VanillaModelSkeletonProvider;
import de.sarocesch.sarosragdollfix.GeoSkeletons;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.entity.EntityRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = VanillaModelSkeletonProvider.class, remap = false)
public abstract class VanillaModelSkeletonProviderMixin {
   /** While a GeckoLib mob is being converted, feed Saro's builder the converted vanilla-style model instead of nothing. */
   @Redirect(
      method = "build(Lnet/minecraft/world/entity/EntityType;Lnet/minecraft/client/multiplayer/ClientLevel;Z)Lde/sarocesch/sarosmobragdoll/client/skeleton/MobSkeleton;",
      at = @At(
         value = "INVOKE",
         target = "Lde/sarocesch/sarosmobragdoll/client/skeleton/VanillaModelSkeletonProvider;modelOf(Lnet/minecraft/client/renderer/entity/EntityRenderer;)Lnet/minecraft/client/model/EntityModel;"
      )
   )
   private EntityModel<?> saros_fix$modelOf(EntityRenderer<?> renderer) {
      EntityModel<?> forced = GeoSkeletons.override();
      return forced != null ? forced : GeoSkeletons.originalModelOf(renderer);
   }
}
