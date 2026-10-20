import com.jme3.bullet.collision.shapes.BoxCollisionShape;
import com.jme3.bullet.joints.PhysicsJoint;
import com.jme3.bullet.objects.PhysicsRigidBody;
import com.jme3.math.*;
import de.sarocesch.sarosmobragdoll.client.skeleton.Bone;
import de.sarocesch.sarosmobragdoll.ragdoll.physics.*;
import de.sarocesch.sarosragdollfix.RagdollLaunch;
import java.lang.reflect.Field;
import java.util.*;

public class Test {
  static Bone bone(int parent, float[] half, float[] c, float[] pivot) {
    float vol = 8*half[0]*half[1]*half[2];
    return new Bone(null, parent, Math.max(0.2f, vol*90f), new Vector3f(half[0],half[1],half[2]), new Vector3f(c[0],c[1],c[2]), new Quaternion(), new Vector3f(pivot[0],pivot[1],pivot[2]), new Vector3f(), 1f);
  }
  static List<Bone> humanoid() {
    List<Bone> b = new ArrayList<>();
    b.add(bone(-1, new float[]{.25f,.375f,.125f}, new float[]{0,1.125f,0}, new float[]{0,1.5f,0}));
    b.add(bone(0, new float[]{.25f,.25f,.25f}, new float[]{0,1.75f,0}, new float[]{0,1.5f,0}));
    b.add(bone(0, new float[]{.125f,.375f,.125f}, new float[]{-.375f,1.125f,0}, new float[]{-.375f,1.5f,0}));
    b.add(bone(0, new float[]{.125f,.375f,.125f}, new float[]{.375f,1.125f,0}, new float[]{.375f,1.5f,0}));
    b.add(bone(0, new float[]{.125f,.375f,.125f}, new float[]{-.125f,.375f,0}, new float[]{-.125f,.75f,0}));
    b.add(bone(0, new float[]{.125f,.375f,.125f}, new float[]{.125f,.375f,0}, new float[]{.125f,.75f,0}));
    return b;
  }
  /** what buildRigid() makes: one box = entity bounding box, standing on its base */
  static List<Bone> rigid(float w, float h) {
    List<Bone> b = new ArrayList<>();
    Vector3f half = new Vector3f(w*.5f,h*.5f,w*.5f), c = new Vector3f(0,h*.5f,0);
    float mass = Math.max(0.2f, Math.min(40f, 8*half.x*half.y*half.z*90f));
    b.add(new Bone(null,-1,mass,half,c,new Quaternion(),new Vector3f(c),new Vector3f(),1f));
    return b;
  }
  static float tilt(Quaternion q){ Vector3f up=q.mult(new Vector3f(0,1,0),new Vector3f()); return (float)Math.toDegrees(Math.acos(Math.max(-1,Math.min(1,up.y)))); }

  static void run(String label, List<Bone> bones, boolean old, Vector3f incoming, double w, double h, RagdollLaunch.Settings s) throws Exception {
    BasePhysicsWorld world = new BasePhysicsWorld();
    PhysicsRigidBody floor = new PhysicsRigidBody(new BoxCollisionShape(100,1,100), 0f);
    floor.setPhysicsLocation(new Vector3f(0,-1,0)); floor.setFriction(0.9f); floor.setCollisionGroup(1); floor.setCollideWithGroups(2);
    world.addCollisionObject(floor);
    BoneRagdollPhysics p = new BoneRagdollPhysics();
    p.build(bones, 0, new Vector3f(0,0,0), 180f);
    if (!old) { // exactly what the mixin does after build()
      Field jf = BoneRagdollPhysics.class.getDeclaredField("joints"); jf.setAccessible(true);
      RagdollLaunch.applyJointLimits((List<PhysicsJoint>) jf.get(p), s.jointLimitDegrees);
    }
    p.addToWorld(world);
    if (old) p.setUniformVelocity(incoming); else RagdollLaunch.apply(p, incoming, w, h, s, new Random(7));
    Vector3f pos = new Vector3f(); float[] marks = {0.25f,0.5f,1f,2f};
    StringBuilder sb = new StringBuilder();
    float peak=0;
    for (int t=1;t<=200;t++){ p.clampVelocities(); world.space.update(0.05f);
      p.getBody(0).getPhysicsLocation(pos);
      int root=0; float hv=-1; for(int i=0;i<p.size();i++){ if(p.getBody(i).getMass()>hv){hv=p.getBody(i).getMass();root=i;} }
      p.getBody(root).getPhysicsLocation(pos);
      float tl = tilt(p.getBody(root).getPhysicsRotation(null));
      for (float m: marks) if (Math.abs(t*0.05f-m)<1e-4) sb.append(String.format(" t=%.2f:tilt=%3.0f", m, tl));
    }
    Vector3f end = new Vector3f(); int root=0; float hv=-1; for(int i=0;i<p.size();i++) if(p.getBody(i).getMass()>hv){hv=p.getBody(i).getMass();root=i;}
    p.getBody(root).getPhysicsLocation(end);
    float finalTilt = tilt(p.getBody(root).getPhysicsRotation(null));
    System.out.printf("%-46s travel=%5.2f blocks | %s | final tilt=%3.0f%n", label, Math.hypot(end.x,end.z), sb, finalTilt);
    p.removeFromWorld();
  }
  /** Extracts the Bullet native that ships inside Saro's jar (same as the mod does) and loads it. */
  static void loadNative() throws Exception {
    String os = System.getProperty("os.name","").toLowerCase(), arch = System.getProperty("os.arch","").toLowerCase();
    boolean arm = arch.contains("aarch64") || arch.contains("arm");
    String name = os.contains("win") ? "Windows64ReleaseSp_18.1.0_bulletjme.dll"
      : os.contains("mac") ? (arm ? "MacOSX_ARM64ReleaseSp_18.1.0_libbulletjme.dylib" : "MacOSX64ReleaseSp_18.1.0_libbulletjme.dylib")
      : (arm ? "Linux_ARM64ReleaseSp_18.1.0_libbulletjme.so" : "Linux64ReleaseSp_18.1.0_libbulletjme.so");
    try (java.io.InputStream in = Test.class.getResourceAsStream("/natives/" + name)) {
      if (in == null) throw new IllegalStateException("natives/" + name + " not found: is the Saro jar on the classpath?");
      java.nio.file.Path tmp = java.nio.file.Files.createTempFile("bulletjme", name.substring(name.lastIndexOf('.')));
      java.nio.file.Files.copy(in, tmp, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
      System.load(tmp.toAbsolutePath().toString());
    }
  }
  public static void main(String[] a) throws Exception {
    loadNative();
    RagdollLaunch.Settings s = new RagdollLaunch.Settings();
    Vector3f hit = new Vector3f(6,2,0), weak = new Vector3f(0.3f,-0.1f,0.1f);
    System.out.println("--- player-sized humanoid (6 parts)");
    run("OLD  hit by attacker (6 b/s, +2, uniform)", humanoid(), true, hit, .6, 1.8, s);
    run("NEW  hit by attacker", humanoid(), false, hit, .6, 1.8, s);
    run("OLD  no attacker (nearly no push)", humanoid(), true, weak, .6, 1.8, s);
    run("NEW  no attacker", humanoid(), false, weak, .6, 1.8, s);
    System.out.println("--- rigid single box (what Alex's Mobs / Naturalist mobs use), 0.6 x 1.8");
    run("OLD  hit", rigid(.6f,1.8f), true, hit, .6, 1.8, s);
    run("NEW  hit", rigid(.6f,1.8f), false, hit, .6, 1.8, s);
    run("OLD  no attacker", rigid(.6f,1.8f), true, weak, .6, 1.8, s);
    run("NEW  no attacker", rigid(.6f,1.8f), false, weak, .6, 1.8, s);
    System.out.println("--- rigid box, big mob 3.5 x 4 (elephant-like)");
    run("OLD  hit", rigid(3.5f,4f), true, hit, 3.5, 4, s);
    run("NEW  hit", rigid(3.5f,4f), false, hit, 3.5, 4, s);
    System.out.println("--- rigid box, small mob 0.4 x 0.5 (chicken-like)");
    run("OLD  hit", rigid(.4f,.5f), true, hit, .4, .5, s);
    run("NEW  hit", rigid(.4f,.5f), false, hit, .4, .5, s);
  }
}
