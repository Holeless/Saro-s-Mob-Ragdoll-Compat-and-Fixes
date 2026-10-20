package de.sarocesch.sarosragdollfix;

import java.util.List;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.registries.ForgeRegistries;

/** Client config: {@code config/saros_ragdoll_fix-client.toml}. The ragdolls are simulated client-side, so this is too. */
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
   public static final ForgeConfigSpec.BooleanValue OWN_RENDER_DUMMY;
   public static final ForgeConfigSpec.BooleanValue REMOVE_ORIGINAL_FOR_RIGID;

   static {
      ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();

      b.comment(
            "Death launch. The original mod throws every mob 6 blocks/s away from the attacker and 2 blocks/s up, with the",
            "same velocity on every body part, so corpses fly far and stay stiff. These settings replace that.")
         .push("launch");
      FIX_LAUNCH = b.comment("Master switch for this whole section. false = the original mod's behaviour.").define("enabled", true);
      HORIZONTAL_SCALE = b.comment("Multiplier on the horizontal launch speed (original = 1.0).").defineInRange("horizontalScale", 0.35, 0.0, 2.0);
      VERTICAL_SCALE = b.comment("Multiplier on the vertical launch speed (original = 1.0).").defineInRange("verticalScale", 0.5, 0.0, 2.0);
      MAX_LAUNCH_SPEED = b.comment("Hard cap on launch speed in blocks/second.").defineInRange("maxLaunchSpeed", 5.0, 0.0, 50.0);
      SCALE_BY_SIZE = b.comment("Mobs bigger than a player are pushed less (never more).").define("scaleBySize", true);
      LIMB_VELOCITY_FACTOR = b.comment(
            "Fraction of the launch speed given to every part except the body/torso. 1.0 = everything moves as one stiff piece",
            "(original behaviour). Lower = limbs trail behind and the corpse goes limp sooner.")
         .defineInRange("limbVelocityFactor", 0.6, 0.0, 1.0);
      SPIN_ON_KILL = b.comment("Topple rate (rad/s) so the body tips over instead of staying upright. 0 = off.").defineInRange("spinOnKill", 2.5, 0.0, 10.0);
      RANDOM_TOPPLE = b.comment("Tip over in a random direction when almost nothing pushed the body (fall, fire, poison ...).")
         .define("randomToppleWhenNotPushed", true);
      NO_PUSH_THRESHOLD = b.comment("Horizontal speed (after scaling) below which a body counts as not pushed.").defineInRange("notPushedBelow", 0.8, 0.0, 5.0);
      NO_PUSH_SPEED = b.comment("Horizontal speed given to a body that was not pushed.").defineInRange("notPushedSpeed", 0.8, 0.0, 5.0);
      b.pop();

      b.comment("Joint looseness.").push("joints");
      FIX_JOINTS = b.comment("Replace the original mod's fixed joint limit.").define("enabled", true);
      JOINT_LIMIT_DEGREES = b.comment("How far every joint may bend on every axis, in degrees. The original mod uses about 40.")
         .defineInRange("jointLimitDegrees", 75.0, 5.0, 150.0);
      b.pop();

      b.comment(
            "Compatibility for mobs whose model is not a vanilla ModelPart tree (Alex's Mobs/Citadel, Naturalist/GeckoLib ...).",
            "The original mod falls back to one rigid body for these. It never moved or toppled, and the original mob kept",
            "standing there. Here the rigid body topples, is drawn with the mob's own renderer, and the original is removed.")
         .push("compat");
      FORCE_RIGID = b.comment(
            "Mod namespaces (\"alexsmobs\") or single entity ids (\"naturalist:bear\") that always use the rigid real-model path,",
            "even if the original mod could guess a partial skeleton for them.")
         .defineListAllowEmpty("forceRigid", List.of("alexsmobs", "naturalist"), o -> o instanceof String);
      OWN_RENDER_DUMMY = b.comment(
            "Draw each rigid ragdoll from its own copy of the mob that carries the dead mob's variant/colour and is turned the",
            "right way round. false = the original mod's shared default-variant copy, which also faces backwards.")
         .define("perRagdollModel", true);
      REMOVE_ORIGINAL_FOR_RIGID = b.comment(
            "Remove the dying mob from the client world the moment a rigid ragdoll replaces it. Needed because GeckoLib and",
            "Citadel renderers do not always honour the original mod's hide trick, which left a frozen duplicate.")
         .define("removeOriginal", true);
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

   /** True if this entity type matches a namespace or full id in {@code compat.forceRigid}. */
   public static boolean isForcedRigid(EntityType<?> type) {
      ResourceLocation key = ForgeRegistries.ENTITY_TYPES.getKey(type);
      if (key == null) {
         return false;
      }

      for (String entry : FORCE_RIGID.get()) {
         String e = entry.trim();
         if (e.equalsIgnoreCase(key.getNamespace()) || e.equalsIgnoreCase(key.toString())) {
            return true;
         }
      }

      return false;
   }
}
