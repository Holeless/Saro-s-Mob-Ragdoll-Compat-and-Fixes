package de.sarocesch.sarosragdollfix.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import de.sarocesch.sarosmobragdoll.client.ClientMobRagdoll;
import de.sarocesch.sarosmobragdoll.client.skeleton.Bone;
import de.sarocesch.sarosmobragdoll.client.skeleton.MobSkeleton;
import de.sarocesch.sarosmobragdoll.client.skeleton.SkeletonLayer;
import de.sarocesch.sarosragdollfix.RigidDummies;
import de.sarocesch.sarosragdollfix.TintedConsumer;
import de.sarocesch.sarosragdollfix.WoundCompat;
import java.util.List;
import java.util.Set;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = ClientMobRagdoll.class, remap = false)
public abstract class ClientMobRagdollMixin {
   @Shadow
   @Final
   private MobSkeleton skeleton;

   @Shadow
   private static void renderPartCubesOnly(ModelPart part, PoseStack pose, VertexConsumer consumer, int light, float alpha, Set<ModelPart> boneParts) {
      throw new AssertionError();
   }

   /** Rigid ragdolls: draw from their own copy of the mob (right variant, right way round). */
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

   /** Normal ragdolls: use this mob's own texture instead of the one cached per entity type (white rabbit stays white). */
   @Redirect(
      method = "render",
      at = @At(
         value = "FIELD",
         target = "Lde/sarocesch/sarosmobragdoll/client/skeleton/MobSkeleton;texture:Lnet/minecraft/resources/ResourceLocation;",
         opcode = Opcodes.GETFIELD
      )
   )
   private ResourceLocation saros_fix$ownTexture(MobSkeleton skeleton) {
      return RigidDummies.textureFor((ClientMobRagdoll)(Object)this, skeleton);
   }

   /** Overlay layers (villager profession, biome ...): use this mob's own, not the cached default mob's. */
   @Redirect(
      method = "renderLayersFor",
      at = @At(
         value = "FIELD",
         target = "Lde/sarocesch/sarosmobragdoll/client/skeleton/SkeletonLayer;renderType:Lnet/minecraft/client/renderer/RenderType;",
         opcode = Opcodes.GETFIELD
      )
   )
   private RenderType saros_fix$ownLayerType(SkeletonLayer layer) {
      return RigidDummies.layerTypeFor((ClientMobRagdoll)(Object)this, layer.renderType);
   }

   /**
    * After the original's layers for one bone: draw what only THIS mob had. Damage overlays (iron golem cracks) are the same
    * cubes with another texture; wounds / bullet holes are the same cubes with the wound texture of Better Blood Overlay.
    * The pose stack is already at this bone, exactly as for the original's own layers.
    */
   @Inject(method = "renderLayersFor", at = @At("RETURN"))
   private void saros_fix$ownOverlays(int boneIndex, Bone bone, PoseStack pose, MultiBufferSource buffers, int light, float alpha, CallbackInfo ci) {
      ClientMobRagdoll self = (ClientMobRagdoll)(Object)this;
      List<RenderType> extras = RigidDummies.extraTypes(self);
      List<WoundCompat.Wound> wounds = RigidDummies.wounds(self);
      if ((extras == null && wounds == null) || bone.part == null) {
         return;
      }

      if (extras != null) {
         for (RenderType type : extras) {
            pose.pushPose();
            renderPartCubesOnly(bone.part, pose, buffers.getBuffer(type), light, alpha, this.skeleton.boneParts);
            pose.popPose();
         }
      }

      if (wounds != null) {
         for (WoundCompat.Wound w : wounds) {
            VertexConsumer consumer = buffers.getBuffer(w.type);
            if (w.tinted()) {
               consumer = new TintedConsumer(consumer, w.r, w.g, w.b);
            }

            pose.pushPose();
            renderPartCubesOnly(bone.part, pose, consumer, light, alpha * w.alpha, this.skeleton.boneParts);
            pose.popPose();
         }
      }
   }

   /** Frees the wound textures this ragdoll owns. */
   @Inject(method = "remove", at = @At("HEAD"))
   private void saros_fix$release(CallbackInfo ci) {
      RigidDummies.release((ClientMobRagdoll)(Object)this);
   }
}
