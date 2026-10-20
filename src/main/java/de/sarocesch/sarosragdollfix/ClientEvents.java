package de.sarocesch.sarosragdollfix;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Per-tick bookkeeping: remember mob health while alive, and keep removed originals removed. */
@Mod.EventBusSubscriber(modid = SarosRagdollFix.MODID, value = Dist.CLIENT)
public final class ClientEvents {
   private ClientEvents() {
   }

   @SubscribeEvent
   public static void onClientTick(TickEvent.ClientTickEvent event) {
      if (event.phase != TickEvent.Phase.END) {
         return;
      }

      ClientLevel level = Minecraft.getInstance().level;
      if (level == null) {
         HealthTracker.clear();
         RagdollCleanup.clear();
         return;
      }

      HealthTracker.tick(level);
      RagdollCleanup.tick(level);
   }
}
