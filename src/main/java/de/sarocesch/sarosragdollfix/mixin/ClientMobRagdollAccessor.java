package de.sarocesch.sarosragdollfix.mixin;

import de.sarocesch.sarosmobragdoll.client.ClientMobRagdoll;
import de.sarocesch.sarosmobragdoll.client.skeleton.MobSkeleton;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = ClientMobRagdoll.class, remap = false)
public interface ClientMobRagdollAccessor {
   @Accessor("skeleton")
   MobSkeleton saros_fix$getSkeleton();
}
