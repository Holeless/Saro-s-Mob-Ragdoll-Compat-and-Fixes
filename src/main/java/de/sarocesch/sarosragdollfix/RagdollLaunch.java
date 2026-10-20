package de.sarocesch.sarosragdollfix;

import com.jme3.bullet.joints.New6Dof;
import com.jme3.bullet.joints.PhysicsJoint;
import com.jme3.bullet.joints.motors.MotorParam;
import com.jme3.bullet.joints.motors.RotationMotor;
import com.jme3.bullet.objects.PhysicsRigidBody;
import com.jme3.math.Vector3f;
import de.sarocesch.sarosmobragdoll.ragdoll.physics.BoneRagdollPhysics;
import java.util.List;
import java.util.Random;

/**
 * The physics side of the fix. Pure Java + jME/Bullet + Saro's {@link BoneRagdollPhysics}; no Minecraft classes, so it
 * can be unit-tested headless.
 *
 * <p>Saro's Mob Ragdoll gives every body part the SAME launch velocity when a mob dies (about 6 blocks/s away from the
 * attacker plus 2 up). The joints never flex, so the corpse flies upright like a plank ("stiff"), and it flies far
 * ("fling"). A corpse with little push also just stands on its legs for seconds. This class replaces that launch.
 */
public final class RagdollLaunch {
   private RagdollLaunch() {
   }

   /** Plain values so the physics can be tested without Forge's config system. */
   public static final class Settings {
      /** Multiplier on the horizontal launch speed the original mod computed. */
      public double horizontalScale = 0.35;
      /** Multiplier on the vertical launch speed the original mod computed. */
      public double verticalScale = 0.5;
      /** Hard cap on launch speed, blocks/second. */
      public double maxLaunchSpeed = 5.0;
      /** Big mobs are pushed less than a player-sized mob, never more. */
      public boolean scaleBySize = true;
      /** Fraction of the launch speed given to every part except the heaviest one (the torso/body). */
      public double limbVelocityFactor = 0.6;
      /** Topple rate in rad/s about the body's centre of mass. 0 disables. */
      public double spinOnKill = 2.5;
      /** Tip over in a random direction when almost nothing pushed the body. */
      public boolean randomTopple = true;
      /** Horizontal speed (after scaling) below which a body counts as "not pushed". */
      public double noPushThreshold = 0.8;
      /** Horizontal speed given to a body that was not pushed. */
      public double noPushSpeed = 0.8;
      /** Joint bend limit in degrees on every axis. The original mod hardcodes 0.7 rad (about 40). */
      public double jointLimitDegrees = 75.0;
   }

   private static final float MIN_SPIN_FRACTION = 0.8F;

   /**
    * @param incoming the velocity the original mod would have used, blocks/second
    * @param width    entity type bounding box width
    * @param height   entity type bounding box height
    */
   public static void apply(BoneRagdollPhysics physics, Vector3f incoming, double width, double height, Settings s, Random rnd) {
      int n = physics.size();
      if (n == 0) {
         return;
      }

      double f = 1.0;
      if (s.scaleBySize) {
         // 0.65 = volume of a player-sized bounding box.
         double volume = width * width * height;
         f = Math.max(0.3, Math.min(1.0, Math.cbrt(0.65 / Math.max(volume, 0.01))));
      }

      double hx = incoming.x * s.horizontalScale * f;
      double hz = incoming.z * s.horizontalScale * f;
      double vy = incoming.y * s.verticalScale * f;
      if (s.randomTopple && Math.sqrt(hx * hx + hz * hz) < s.noPushThreshold) {
         double a = rnd.nextDouble() * (Math.PI * 2.0);
         double push = s.noPushSpeed * f;
         hx += Math.cos(a) * push;
         hz += Math.sin(a) * push;
      }

      double len = Math.sqrt(hx * hx + vy * vy + hz * hz);
      double max = Math.max(0.0, s.maxLaunchSpeed);
      if (len > max && len > 1.0E-6) {
         double k = max / len;
         hx *= k;
         vy *= k;
         hz *= k;
      }

      int root = 0;
      float heaviest = -1.0F;
      for (int i = 0; i < n; i++) {
         float m = physics.getBody(i).getMass();
         if (m > heaviest) {
            heaviest = m;
            root = i;
         }
      }

      setLaunch(physics, new Vector3f((float)hx, (float)vy, (float)hz), root, (float)s.limbVelocityFactor, (float)s.spinOnKill, (float)width, (float)height);
   }

   /**
    * The root gets the full velocity, other parts a fraction (so limbs trail behind), and the whole body is spun about
    * its centre of mass around the horizontal axis perpendicular to the push, so it tips over the way it was hit. The
    * spin is a consistent rotation of all parts (v + w x r) so the joints do not fight it.
    */
   public static void setLaunch(BoneRagdollPhysics physics, Vector3f velocity, int rootIndex, float limbFactor, float spin, float width, float height) {
      int n = physics.size();
      Vector3f limb = velocity.mult(Math.max(0.0F, Math.min(1.0F, limbFactor)));
      Vector3f omega = new Vector3f();
      float hSpeed = (float)Math.sqrt((double)(velocity.x * velocity.x + velocity.z * velocity.z));
      if (spin > 0.0F) {
         // A body balanced on its legs (or a box on its base) can stand upright for seconds if nothing tips it, so the
         // spin never drops below 80% strength and with no horizontal push at all it still topples (towards +X).
         float strength = spin * Math.max(MIN_SPIN_FRACTION, Math.min(1.0F, hSpeed / 2.0F));
         if (n == 1) {
            // A single rigid box (modded mobs) has no joints to buckle, so it needs enough spin to tip over its base.
            strength = Math.max(strength, tippingSpin(width, height));
         }
         if (hSpeed > 0.05F) {
            // up x forward = (fz, 0, -fx): the top of the body moves in the push direction.
            omega.set(velocity.z / hSpeed * strength, 0.0F, -velocity.x / hSpeed * strength);
         } else {
            omega.set(0.0F, 0.0F, -strength);
         }
      }

      Vector3f com = new Vector3f();
      float totalMass = 0.0F;
      Vector3f p = new Vector3f();
      for (int i = 0; i < n; i++) {
         PhysicsRigidBody body = physics.getBody(i);
         float m = Math.max(body.getMass(), 0.001F);
         body.getPhysicsLocation(p);
         com.addLocal(p.x * m, p.y * m, p.z * m);
         totalMass += m;
      }

      if (totalMass > 0.0F) {
         com.multLocal(1.0F / totalMass);
      }

      Vector3f r = new Vector3f();
      Vector3f v = new Vector3f();
      for (int i = 0; i < n; i++) {
         PhysicsRigidBody body = physics.getBody(i);
         body.getPhysicsLocation(p);
         p.subtract(com, r);
         omega.cross(r, v);
         v.addLocal(i == rootIndex ? velocity : limb);
         body.setLinearVelocity(v);
         body.setAngularVelocity(omega);
      }
   }

   /**
    * Spin (rad/s about the centre of mass) a tall box needs to tip over its base edge, with a safety factor of 2.5 for
    * the energy lost in the contact. A big upright mob (elephant, giraffe) otherwise keeps standing after a hit. Wide, low
    * mobs are already as good as lying down, so they get nothing extra.
    */
   static float tippingSpin(float width, float height) {
      if (width <= 0.0F || height <= 0.0F || height < 0.8F * width) {
         return 0.0F;
      }

      double rise = Math.sqrt((double)(height * height + width * width) * 0.25) - (double)height * 0.5;
      double need = Math.sqrt(24.0 * 9.81 * rise / (double)(width * width + height * height));
      return (float)Math.min(2.5 * need, 6.0);
   }

   /** Replaces the original mod's fixed +-0.7 rad limit on every joint axis. */
   public static void applyJointLimits(List<PhysicsJoint> joints, double degrees) {
      float limit = (float)Math.toRadians(Math.max(5.0, Math.min(150.0, degrees)));
      for (PhysicsJoint j : joints) {
         if (j instanceof New6Dof dof) {
            for (int axis = 0; axis < 3; axis++) {
               RotationMotor m = dof.getRotationMotor(axis);
               m.set(MotorParam.LowerLimit, -limit);
               m.set(MotorParam.UpperLimit, limit);
            }
         }
      }
   }
}
