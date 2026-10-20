package de.sarocesch.sarosragdollfix;

import com.jme3.bullet.objects.PhysicsRigidBody;
import com.jme3.math.Quaternion;
import com.jme3.math.Vector3f;
import de.sarocesch.sarosmobragdoll.ragdoll.physics.BoneRagdollPhysics;

/** Puts a freshly built (standing) ragdoll on its back, head towards a given direction. No Minecraft classes in here. */
public final class SleepPose {
   private SleepPose() {
   }

   /**
    * Moves every body rigidly (so the joints stay happy) and clears the velocities.
    *
    * @param standingFeet where the ragdoll was built, feet position
    * @param bodyYaw      yaw it was built with, degrees
    * @param head         unit vector, horizontal direction the head should point to
    * @param lyingFeet    where the feet end up
    * @param faceDown     lie on the front instead of the back
    */
   public static void layDown(BoneRagdollPhysics physics, Vector3f standingFeet, float bodyYaw, Vector3f head, Vector3f lyingFeet, boolean faceDown) {
      int n = physics.size();
      if (n == 0) {
         return;
      }

      // Tipping 90 degrees towards 'head' sends the front to the opposite side of the head direction, so turn the front
      // that way first (towards the head for face down).
      Vector3f front = faceDown ? head : head.negate();
      float turn = (float)Math.atan2(front.x, front.z) + (float)Math.toRadians(bodyYaw);
      Quaternion q = new Quaternion().fromAngleNormalAxis((float)(Math.PI / 2.0), new Vector3f(head.z, 0.0F, -head.x))
         .mult(new Quaternion().fromAngleNormalAxis(turn, Vector3f.UNIT_Y));

      Vector3f p = new Vector3f();
      Vector3f moved = new Vector3f();
      Vector3f zero = new Vector3f();
      Quaternion rot = new Quaternion();
      for (int i = 0; i < n; i++) {
         PhysicsRigidBody body = physics.getBody(i);
         body.getPhysicsLocation(p);
         p.subtractLocal(standingFeet);
         q.mult(p, moved);
         moved.addLocal(lyingFeet);
         body.setPhysicsLocation(moved);
         body.getPhysicsRotation(rot);
         body.setPhysicsRotation(q.mult(rot));
         body.setLinearVelocity(zero);
         body.setAngularVelocity(zero);
      }
   }
}
