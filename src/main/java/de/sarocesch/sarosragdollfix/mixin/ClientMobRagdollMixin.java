package de.sarocesch.sarosragdollfix.mixin;

import de.sarocesch.sarosmobragdoll.client.ClientMobRagdoll;
import de.sarocesch.sarosmobragdoll.client.skeleton.MobSkeleton;
import de.sarocesch.sarosmobragdoll.client.skeleton.SkeletonLayer;
import de.sarocesch.sarosragdollfix.RigidDummies;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = ClientMobRagdoll.class, remap = false)
public abstract class ClientMobRagdollMixin {
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
}
