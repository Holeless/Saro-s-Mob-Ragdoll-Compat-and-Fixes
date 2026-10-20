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
import net.minecraft.world.item.ItemStack;

/**
 * Builds the never-ticked copy of a mob that the original mod draws with the mob's own renderer for rigid ragdolls, and
 * that this addon asks "what does THIS mob look like" for normal ragdolls.
 *
 * <p>The original shares one default copy per entity type, so every dead Naturalist bear looked the same whatever its
 * variant, and every iron golem was undamaged. This copy takes the dead mob's synced data (variant, colour, baby ...), its
 * health from before it died (cracks) and its equipment, while that mob is still on the client.
 */
public final class DummyFactory {
   /**
    * Yaw given to the copy. The physics body already carries the mob's facing, and a living renderer rotates by
    * (180 - bodyYaw) on top, so a copy at yaw 0 (the original mod's choice) is drawn turned around by 180 degrees. At
    * yaw 180 the renderer's own rotation cancels out.
    */
   private static final float RENDER_YAW = 180.0F;

   private DummyFactory() {
   }

   @Nullable
   public static Entity create(EntityType<?> type, ClientLevel level, @Nullable Entity live, boolean baby) {
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

         d.setInvisible(false);
         d.setPose(Pose.STANDING);
         d.setCustomName(null);
         d.setCustomNameVisible(false);
         if (baby && d instanceof AgeableMob a) {
            a.setBaby(true);
         }

         if (d instanceof LivingEntity le) {
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

   /**
    * Health for the copy. A mob that is being ragdolled is already at 0, so use the fraction it had while alive (damaged
    * iron golems fall over still cracked). A mob we never saw alive, or the shared default copy, is at full health.
    */
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
