package de.sarocesch.sarosragdollfix;

import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.npc.VillagerData;
import net.minecraft.world.entity.npc.VillagerDataHolder;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;

// Never-ticked copy of a mob that gets drawn with the mob's own renderer. The original mod shares one default copy per
// entity type, so every Naturalist bear had the same variant and every iron golem looked undamaged. This one takes the
// dead mob's synced data, health from before it died, and equipment.
public final class DummyFactory {
   // The physics body already has the mob's facing and a living renderer rotates by (180 - bodyYaw) on top, so yaw 0
   // (what the original uses) comes out backwards. At 180 it cancels.
   private static final float RENDER_YAW = 180.0F;

   private DummyFactory() {
   }

   @Nullable
   public static Entity create(EntityType<?> type, ClientLevel level, @Nullable Entity live, boolean baby) {
      return create(type, level, live, live != null ? live.getId() : -1, baby);
   }

   /** entityId is used to look up what we remembered about the mob while it was alive, also when live is already gone. */
   @Nullable
   public static Entity create(EntityType<?> type, ClientLevel level, @Nullable Entity live, int entityId, boolean baby) {
      try {
         Entity d = type.create(level);
         if (d == null) {
            return null;
         }

         if (live != null) {
            try {
               List<SynchedEntityData.DataValue<?>> values = live.getEntityData().getNonDefaultValues();
               if (values != null) {
                  d.getEntityData().assignValues(values);
               }
            } catch (Throwable t) {
               SarosRagdollFix.LOG.debug("[ragdoll-fix] could not copy entity data for {}", type, t);
            }
         }

         applyVillagerData(d, live, entityId);

         d.setInvisible(false);
         d.setPose(Pose.STANDING);
         d.setCustomName(null);
         d.setCustomNameVisible(false);
         if (baby && d instanceof AgeableMob a) {
            a.setBaby(true);
         }

         if (d instanceof LivingEntity le) {
            // the data copy above also brings the bed position along
            le.clearSleepingPos();
            le.setHealth(healthFor(live, le));
            le.deathTime = 0;
            le.hurtTime = 0;
            le.yBodyRot = RENDER_YAW;
            le.yBodyRotO = RENDER_YAW;
            le.yHeadRot = RENDER_YAW;
            le.yHeadRotO = RENDER_YAW;
            copyEquipment(live, le);
         }

         d.setYRot(RENDER_YAW);
         d.yRotO = RENDER_YAW;
         d.setXRot(0.0F);
         d.xRotO = 0.0F;
         return d;
      } catch (Throwable t) {
         SarosRagdollFix.LOG.warn("[ragdoll-fix] could not create render copy for {}", type, t);
         return null;
      }
   }

   // Villager type / profession / level. Taken from the live mob, but if that is gone or already shows no profession,
   // fall back to what VillagerTracker saw while it was alive. Set explicitly so it doesn't depend on the data copy.
   private static void applyVillagerData(Entity d, @Nullable Entity live, int entityId) {
      if (!(d instanceof VillagerDataHolder target)) {
         return;
      }

      try {
         VillagerData best = null;
         String source = "none";
         if (live instanceof VillagerDataHolder src) {
            best = src.getVillagerData();
            source = "live";
         }

         if (best == null || best.getProfession() == VillagerProfession.NONE) {
            VillagerData tracked = entityId >= 0 ? VillagerTracker.last(entityId) : null;
            if (tracked != null && (best == null || tracked.getProfession() != VillagerProfession.NONE)) {
               best = tracked;
               source = "tracker";
            }
         }

         if (best != null) {
            target.setVillagerData(best);
         }

         SarosRagdollFix.LOG.info(
            "[ragdoll-fix] villager look for entity {}: profession {} level {} (from {})",
            entityId, best == null ? "?" : best.getProfession(), best == null ? 0 : best.getLevel(), source
         );
      } catch (Throwable t) {
         SarosRagdollFix.LOG.debug("[ragdoll-fix] could not set villager data", t);
      }
   }

   // A mob being ragdolled is already at 0 health, so use the fraction it had alive. Unknown = full health.
   private static float healthFor(@Nullable Entity live, LivingEntity copy) {
      float max = copy.getMaxHealth();
      if (!FixConfig.COPY_DAMAGE_STATE.get() || !(live instanceof LivingEntity src)) {
         return max;
      }

      float srcMax = src.getMaxHealth();
      if (srcMax <= 0.0F) {
         return max;
      }

      float fraction = src.getHealth() / srcMax;
      if (fraction <= 0.0F) {
         float last = HealthTracker.lastFraction(src.getId());
         fraction = last > 0.0F ? last : 1.0F;
      }

      return Math.max(0.01F, Math.min(1.0F, fraction)) * max;
   }

   private static void copyEquipment(@Nullable Entity live, LivingEntity copy) {
      if (!FixConfig.COPY_DAMAGE_STATE.get() || !(live instanceof LivingEntity src)) {
         return;
      }

      try {
         for (EquipmentSlot slot : EquipmentSlot.values()) {
            ItemStack stack = src.getItemBySlot(slot);
            if (!stack.isEmpty()) {
               copy.setItemSlot(slot, stack.copy());
            }
         }
      } catch (Throwable t) {
         SarosRagdollFix.LOG.debug("[ragdoll-fix] could not copy equipment", t);
      }
   }
}
