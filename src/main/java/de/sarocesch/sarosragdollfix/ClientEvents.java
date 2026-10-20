package de.sarocesch.sarosragdollfix;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

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
         SleepTracker.clear();
         VillagerTracker.clear();
         return;
      }

      HealthTracker.tick(level);
      RagdollCleanup.tick(level);
      SleepTracker.tick(level);
      VillagerTracker.tick(level);
   }
}
