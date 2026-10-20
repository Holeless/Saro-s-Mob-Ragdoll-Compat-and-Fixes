package de.sarocesch.sarosmobragdoll.ragdoll.physics;
import com.jme3.bullet.PhysicsSpace;
import com.jme3.bullet.collision.PhysicsCollisionObject;
import com.jme3.bullet.joints.PhysicsJoint;
import com.jme3.math.Vector3f;
public class BasePhysicsWorld {
  public PhysicsSpace space;
  public BasePhysicsWorld() {
    space = new PhysicsSpace(new Vector3f(-3.0E7F,-2048F,-3.0E7F), new Vector3f(3.0E7F,2048F,3.0E7F), PhysicsSpace.BroadphaseType.DBVT);
    space.setForceUpdateAllAabbs(false);
    space.setGravity(new Vector3f(0,-9.81F,0));
    space.getSolverInfo().setNumIterations(16);
    space.setMaxSubSteps(4);
    space.setAccuracy(0.016666668F);
    space.getSolverInfo().setSplitImpulseEnabled(true);
  }
  public void addCollisionObject(PhysicsCollisionObject o){ space.addCollisionObject(o);}
  public void removeCollisionObject(PhysicsCollisionObject o){ space.removeCollisionObject(o);}
  public void addJoint(PhysicsJoint j){ space.addJoint(j);}
  public void removeJoint(PhysicsJoint j){ space.removeJoint(j);}
}
