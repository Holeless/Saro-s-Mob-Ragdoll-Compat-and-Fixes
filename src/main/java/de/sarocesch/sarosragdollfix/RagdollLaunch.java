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

// Physics side of the fix. No Minecraft classes here so test-harness can run it headless.
//
// The original gives every body part the same launch velocity, so the joints never bend and the corpse flies off
// like a plank. A body with little push also just stands on its legs for seconds. This replaces that launch.
public final class RagdollLaunch {
   private RagdollLaunch() {
   }

   public static final class Settings {
      public double horizontalScale = 0.35;
      public double verticalScale = 0.5;
      /** blocks/s */
      public double maxLaunchSpeed = 5.0;
      public boolean scaleBySize = true;
      /** limb speed relative to the torso */
      public double limbVelocityFactor = 0.2;
      /** rad/s */
      public double spinOnKill = 2.5;
      public boolean randomTopple = true;
      public double noPushThreshold = 0.8;
      public double noPushSpeed = 0.8;
      /** the original is about 40 (0.7 rad) */
      public double jointLimitDegrees = 75.0;
   }

   private static final float MIN_SPIN_FRACTION = 0.8F;

   /** incoming is the velocity the original would have used. */
   public static void apply(BoneRagdollPhysics physics, Vector3f incoming, double width, double height, Settings s, Random rnd) {
      int n = physics.size();
      if (n == 0) {
         return;
      }

      double f = 1.0;
      if (s.scaleBySize) {
         // 0.65 is the volume of a player-sized box
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

   // Root gets the full velocity, the rest a fraction so limbs trail. The whole body is also spun around its centre of
   // mass, perpendicular to the push, so it tips over the way it was hit. Every part gets v + w x r, so the joints go along with it.
   public static void setLaunch(BoneRagdollPhysics physics, Vector3f velocity, int rootIndex, float limbFactor, float spin, float width, float height) {
      int n = physics.size();
      Vector3f limb = velocity.mult(Math.max(0.0F, Math.min(1.0F, limbFactor)));
      Vector3f omega = new Vector3f();
      float hSpeed = (float)Math.sqrt((double)(velocity.x * velocity.x + velocity.z * velocity.z));
      if (spin > 0.0F) {
         // something balanced on its legs can stay up for seconds, so never go below 80% spin, and with no push
         // at all it still falls over (towards +X)
         float strength = spin * Math.max(MIN_SPIN_FRACTION, Math.min(1.0F, hSpeed / 2.0F));
         if (n == 1) {
            // one rigid box has no joints to buckle, it has to be spun over its base edge
            strength = Math.max(strength, tippingSpin(width, height));
         }
         if (hSpeed > 0.05F) {
            // up x forward, so the top of the body moves in the push direction
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

   // Spin a tall box needs to go over its base edge, x2.5 for what the contact eats. Without it an elephant just keeps
   // standing. Wide low mobs are as good as lying already.
   static float tippingSpin(float width, float height) {
      if (width <= 0.0F || height <= 0.0F || height < 0.8F * width) {
         return 0.0F;
      }

      double rise = Math.sqrt((double)(height * height + width * width) * 0.25) - (double)height * 0.5;
      double need = Math.sqrt(24.0 * 9.81 * rise / (double)(width * width + height * height));
      return (float)Math.min(2.5 * need, 6.0);
   }

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
