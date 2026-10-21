package de.sarocesch.sarosragdollfix;

import java.util.List;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.registries.ForgeRegistries;

/** config/saros_ragdoll_fix-client.toml */
public final class FixConfig {
   public static final ForgeConfigSpec SPEC;

   public static final ForgeConfigSpec.BooleanValue FIX_LAUNCH;
   public static final ForgeConfigSpec.DoubleValue HORIZONTAL_SCALE;
   public static final ForgeConfigSpec.DoubleValue VERTICAL_SCALE;
   public static final ForgeConfigSpec.DoubleValue MAX_LAUNCH_SPEED;
   public static final ForgeConfigSpec.BooleanValue SCALE_BY_SIZE;
   public static final ForgeConfigSpec.DoubleValue LIMB_VELOCITY_FACTOR;
   public static final ForgeConfigSpec.DoubleValue SPIN_ON_KILL;
   public static final ForgeConfigSpec.BooleanValue RANDOM_TOPPLE;
   public static final ForgeConfigSpec.DoubleValue NO_PUSH_THRESHOLD;
   public static final ForgeConfigSpec.DoubleValue NO_PUSH_SPEED;

   public static final ForgeConfigSpec.BooleanValue FIX_JOINTS;
   public static final ForgeConfigSpec.DoubleValue JOINT_LIMIT_DEGREES;

   public static final ForgeConfigSpec.ConfigValue<List<? extends String>> FORCE_RIGID;
   public static final ForgeConfigSpec.ConfigValue<List<? extends String>> NO_RAGDOLL;
   public static final ForgeConfigSpec.BooleanValue OWN_RENDER_DUMMY;
   public static final ForgeConfigSpec.BooleanValue REMOVE_ORIGINAL_FOR_RIGID;
   public static final ForgeConfigSpec.BooleanValue CONVERT_GEO;
   public static final ForgeConfigSpec.BooleanValue CONVERT_CITADEL;
   public static final ForgeConfigSpec.ConfigValue<List<? extends String>> GEO_HIDDEN_BONES;

   public static final ForgeConfigSpec.BooleanValue COPY_DAMAGE_STATE;
   public static final ForgeConfigSpec.BooleanValue COPY_WOUNDS;
   public static final ForgeConfigSpec.BooleanValue FIX_MODEL_SCALE;
   public static final ForgeConfigSpec.BooleanValue FIX_ENTITY_SIZE;
   public static final ForgeConfigSpec.BooleanValue DEDUPE_SPAWNS;

   public static final ForgeConfigSpec.BooleanValue LAY_SLEEPERS;
   public static final ForgeConfigSpec.BooleanValue SLEEP_FACE_DOWN;

   static {
      ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();

      b.comment("Death launch. The original throws every mob 6 blocks/s away from the attacker and 2 up, same speed on",
            "every body part, so corpses fly far and stay stiff.")
         .push("launch");
      FIX_LAUNCH = b.comment("false = original launch").define("enabled", true);
      HORIZONTAL_SCALE = b.comment("Multiplier on the horizontal speed (original = 1.0)").defineInRange("horizontalScale", 0.35, 0.0, 2.0);
      VERTICAL_SCALE = b.comment("Multiplier on the vertical speed (original = 1.0)").defineInRange("verticalScale", 0.5, 0.0, 2.0);
      MAX_LAUNCH_SPEED = b.comment("Speed cap, blocks/s").defineInRange("maxLaunchSpeed", 5.0, 0.0, 50.0);
      SCALE_BY_SIZE = b.comment("Mobs bigger than a player get pushed less").define("scaleBySize", true);
      LIMB_VELOCITY_FACTOR = b.comment("Speed of the limbs relative to the torso. 1.0 = everything moves as one stiff piece (original)")
         .defineInRange("limbVelocityFactor", 0.2, 0.0, 1.0);
      SPIN_ON_KILL = b.comment("Topple rate in rad/s so the body tips over instead of standing. 0 = off").defineInRange("spinOnKill", 2.5, 0.0, 10.0);
      RANDOM_TOPPLE = b.comment("Tip over in a random direction when nothing pushed the body (fall damage, fire, poison...)")
         .define("randomToppleWhenNotPushed", true);
      NO_PUSH_THRESHOLD = b.comment("Horizontal speed below which a body counts as not pushed").defineInRange("notPushedBelow", 0.8, 0.0, 5.0);
      NO_PUSH_SPEED = b.comment("Horizontal speed given to a body that wasn't pushed").defineInRange("notPushedSpeed", 0.8, 0.0, 5.0);
      b.pop();

      b.comment("Joint looseness").push("joints");
      FIX_JOINTS = b.comment("Replace the original's fixed joint limit").define("enabled", true);
      JOINT_LIMIT_DEGREES = b.comment("Max bend per axis in degrees. Original is about 40")
         .defineInRange("jointLimitDegrees", 75.0, 5.0, 150.0);
      b.pop();

      b.comment("Mobs whose model isn't a vanilla ModelPart tree (Alex's Mobs, Naturalist...). The original falls back to one",
            "rigid box for these.")
         .push("compat");
      CONVERT_GEO = b.comment("GeckoLib and AzureLib mobs: build a limb skeleton from the .geo.json so they flop like vanilla mobs.",
            "Falls back to the rigid box if the model can't be converted.")
         .define("convertGeoModels", true);
      CONVERT_CITADEL = b.comment("Same for Citadel mobs (Alex's Mobs)").define("convertCitadelModels", true);
      GEO_HIDDEN_BONES = b.comment("Bones (lower case) that GeckoLib/AzureLib mobs only show in some states. They're left out of the",
            "converted skeleton. baby_* bones are only used for babies.")
         .defineListAllowEmpty("geoHiddenBones", List.of("sleep", "sleeping", "angry", "head_angry", "snout_angry"), o -> o instanceof String);
      FORCE_RIGID = b.comment("Namespaces (\"alexsmobs\") or entity ids (\"naturalist:bear\") that always use the rigid path.",
            "A GeckoLib/AzureLib mob that converts fine ignores this.")
         .defineListAllowEmpty("forceRigid", List.of("alexsmobs", "naturalist"), o -> o instanceof String);
      NO_RAGDOLL = b.comment("Namespaces or entity ids that never turn into a ragdoll, the dead mob stays as it is.",
            "Ice and Fire dragons are on this list because their bodies have to stay lootable. Empty the list to ragdoll them too.")
         .defineListAllowEmpty("noRagdoll", List.of("iceandfire:fire_dragon", "iceandfire:ice_dragon", "iceandfire:lightning_dragon"), o -> o instanceof String);
      OWN_RENDER_DUMMY = b.comment("Draw each ragdoll from its own copy of the mob (variant, colour, profession, facing).",
            "false = the original's shared default copy. copyDamageState needs this.")
         .define("perRagdollModel", true);
      REMOVE_ORIGINAL_FOR_RIGID = b.comment("Remove the dying mob from the client world when a rigid or converted ragdoll takes over.",
            "GeckoLib/AzureLib/Citadel renderers don't always respect the original's hide trick.")
         .define("removeOriginal", true);
      b.pop();

      b.comment("Make the ragdoll look like the mob did").push("fidelity");
      COPY_DAMAGE_STATE = b.comment("Copy health from before death (iron golem cracks) and equipment").define("copyDamageState", true);
      COPY_WOUNDS = b.comment("Keep Better Blood Overlay wounds / bullet holes on the ragdoll. Does nothing without that mod")
         .define("copyBloodWounds", true);
      FIX_MODEL_SCALE = b.comment("Fix models that scale themselves when drawn (adult rabbits were 1.67x too big)")
         .define("fixModelScale", true);
      FIX_ENTITY_SIZE = b.comment("Scale the ragdoll to the size this mob actually had. The original builds one per mob type from a",
            "default-sized mob, so grown Ice and Fire dragons ragdolled as hatchlings.")
         .define("fixEntitySize", true);
      DEDUPE_SPAWNS = b.comment("Ignore a second spawn for the same mob (same id, or same type on the same spot in the same tick).",
            "Some GeckoLib/AzureLib mobs showed up twice. Ignored spawns are logged as [ragdoll-fix].")
         .define("dedupeSpawns", true);
      b.pop();

      b.comment("Mobs that die in a bed").push("sleeping");
      LAY_SLEEPERS = b.comment("Mobs that die while sleeping stay lying in the bed instead of standing next to it")
         .define("layDownSleepers", true);
      SLEEP_FACE_DOWN = b.comment("Turn this on if sleepers end up face down").define("sleepFaceDown", false);
      b.pop();

      SPEC = b.build();
   }

   private FixConfig() {
   }

   public static RagdollLaunch.Settings settings() {
      RagdollLaunch.Settings s = new RagdollLaunch.Settings();
      s.horizontalScale = HORIZONTAL_SCALE.get();
      s.verticalScale = VERTICAL_SCALE.get();
      s.maxLaunchSpeed = MAX_LAUNCH_SPEED.get();
      s.scaleBySize = SCALE_BY_SIZE.get();
      s.limbVelocityFactor = LIMB_VELOCITY_FACTOR.get();
      s.spinOnKill = SPIN_ON_KILL.get();
      s.randomTopple = RANDOM_TOPPLE.get();
      s.noPushThreshold = NO_PUSH_THRESHOLD.get();
      s.noPushSpeed = NO_PUSH_SPEED.get();
      s.jointLimitDegrees = JOINT_LIMIT_DEGREES.get();
      return s;
   }

   public static boolean isForcedRigid(EntityType<?> type) {
      return listed(FORCE_RIGID.get(), type);
   }

   public static boolean isNoRagdoll(EntityType<?> type) {
      return listed(NO_RAGDOLL.get(), type);
   }

   private static boolean listed(List<? extends String> list, EntityType<?> type) {
      ResourceLocation key = ForgeRegistries.ENTITY_TYPES.getKey(type);
      if (key == null) {
         return false;
      }

      for (String entry : list) {
         String e = entry.trim();
         if (e.equalsIgnoreCase(key.getNamespace()) || e.equalsIgnoreCase(key.toString())) {
            return true;
         }
      }

      return false;
   }
}
