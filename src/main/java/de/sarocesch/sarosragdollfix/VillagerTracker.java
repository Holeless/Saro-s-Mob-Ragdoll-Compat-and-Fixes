package de.sarocesch.sarosragdollfix;

import java.util.HashMap;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.VillagerData;
import net.minecraft.world.entity.npc.VillagerDataHolder;

// Last villager data (type, profession, level) seen while the villager was alive. The ragdoll spawns when the mob is
// already dying, and by then the live entity can be gone or its data reset, which gave profession villagers a plain
// ragdoll. Same idea as HealthTracker.
public final class VillagerTracker {
   private static final int KEEP_TICKS = 400;
   private static final Map<Integer, Entry> LAST = new HashMap<>();
   private static int ticks;

   private record Entry(VillagerData data, long seen) {
   }

   private VillagerTracker() {
   }

   public static void tick(ClientLevel level) {
      long now = level.getGameTime();
      for (Entity e : level.entitiesForRendering()) {
         if (e instanceof VillagerDataHolder holder && e instanceof LivingEntity le && !le.isDeadOrDying()) {
            VillagerData data = holder.getVillagerData();
            if (data != null) {
               LAST.put(le.getId(), new Entry(data, now));
            }
         }
      }

      if (++ticks % 200 == 0) {
         LAST.values().removeIf(en -> now - en.seen > KEEP_TICKS);
      }
   }

   @Nullable
   public static VillagerData last(int entityId) {
      Entry e = LAST.get(entityId);
      return e == null ? null : e.data;
   }

   public static void clear() {
      LAST.clear();
   }
}
