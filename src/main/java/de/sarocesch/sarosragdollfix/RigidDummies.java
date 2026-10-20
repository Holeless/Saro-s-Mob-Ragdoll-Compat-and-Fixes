package de.sarocesch.sarosragdollfix;

import de.sarocesch.sarosmobragdoll.client.ClientMobRagdoll;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;
import javax.annotation.Nullable;
import net.minecraft.world.entity.Entity;

/** Per-ragdoll render copies of rigid (modded) mobs. Weak keys: entries disappear with their ragdoll. */
public final class RigidDummies {
   private static final Map<ClientMobRagdoll, Entity> MAP = Collections.synchronizedMap(new WeakHashMap<>());

   private RigidDummies() {
   }

   public static void put(ClientMobRagdoll ragdoll, Entity dummy) {
      MAP.put(ragdoll, dummy);
   }

   @Nullable
   public static Entity get(ClientMobRagdoll ragdoll) {
      return MAP.get(ragdoll);
   }
}
