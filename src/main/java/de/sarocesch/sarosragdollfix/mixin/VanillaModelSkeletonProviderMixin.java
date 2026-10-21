package de.sarocesch.sarosragdollfix.mixin;

import de.sarocesch.sarosmobragdoll.client.skeleton.VanillaModelSkeletonProvider;
import de.sarocesch.sarosragdollfix.GeoSkeletons;
import de.sarocesch.sarosragdollfix.ModelScaleFix;
import java.util.List;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.EntityRenderer;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = VanillaModelSkeletonProvider.class, remap = false)
public abstract class VanillaModelSkeletonProviderMixin {
   private static final String BUILD =
      "build(Lnet/minecraft/world/entity/EntityType;Lnet/minecraft/client/multiplayer/ClientLevel;Z)Lde/sarocesch/sarosmobragdoll/client/skeleton/MobSkeleton;";

   @Shadow
   protected abstract void dfs(ModelPart part, String name, ModelPart parent, Matrix4f prefix, float scale, int depth, List<?> out);

   // while a GeckoLib/AzureLib/Citadel mob is being converted, hand the builder the converted model
   @Redirect(
      method = BUILD,
      at = @At(
         value = "INVOKE",
         target = "Lde/sarocesch/sarosmobragdoll/client/skeleton/VanillaModelSkeletonProvider;modelOf(Lnet/minecraft/client/renderer/entity/EntityRenderer;)Lnet/minecraft/client/model/EntityModel;"
      )
   )
   private EntityModel<?> saros_fix$modelOf(EntityRenderer<?> renderer) {
      EntityModel<?> forced = GeoSkeletons.override();
      return forced != null ? forced : GeoSkeletons.originalModelOf(renderer);
   }

   // adult rabbit etc: apply the model's own scale so bodies and drawn parts have the real size (see ModelScaleFix)
   @Redirect(
      method = BUILD,
      at = @At(
         value = "INVOKE",
         target = "Lde/sarocesch/sarosmobragdoll/client/skeleton/VanillaModelSkeletonProvider;dfs(Lnet/minecraft/client/model/geom/ModelPart;Ljava/lang/String;Lnet/minecraft/client/model/geom/ModelPart;Lorg/joml/Matrix4f;FILjava/util/List;)V"
      )
   )
   private void saros_fix$scaledDfs(
      VanillaModelSkeletonProvider self, ModelPart part, String name, ModelPart parent, Matrix4f prefix, float scale, int depth, List<?> out
   ) {
      if (ModelScaleFix.active()) {
         this.dfs(part, name, parent, ModelScaleFix.adjust(prefix), ModelScaleFix.adjust(scale), depth, out);
      } else {
         this.dfs(part, name, parent, prefix, scale, depth, out);
      }
   }
}
