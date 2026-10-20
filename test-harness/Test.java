import com.jme3.bullet.collision.shapes.BoxCollisionShape;
import com.jme3.bullet.joints.PhysicsJoint;
import com.jme3.bullet.objects.PhysicsRigidBody;
import com.jme3.math.*;
import de.sarocesch.sarosmobragdoll.client.skeleton.Bone;
import de.sarocesch.sarosmobragdoll.ragdoll.physics.*;
import de.sarocesch.sarosragdollfix.RagdollLaunch;
import de.sarocesch.sarosragdollfix.SleepPose;
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

  static int heaviest(BoneRagdollPhysics p) {
    int root = 0; float hv = -1;
    for (int i = 0; i < p.size(); i++) if (p.getBody(i).getMass() > hv) { hv = p.getBody(i).getMass(); root = i; }
    return root;
  }

  static BasePhysicsWorld floorWorld() {
    BasePhysicsWorld world = new BasePhysicsWorld();
    PhysicsRigidBody floor = new PhysicsRigidBody(new BoxCollisionShape(100,1,100), 0f);
    floor.setPhysicsLocation(new Vector3f(0,-1,0)); floor.setFriction(0.9f); floor.setCollisionGroup(1); floor.setCollideWithGroups(2);
    world.addCollisionObject(floor);
    return world;
  }

  static void run(String label, List<Bone> bones, boolean old, Vector3f incoming, double w, double h, RagdollLaunch.Settings s) throws Exception {
    BasePhysicsWorld world = floorWorld();
    BoneRagdollPhysics p = new BoneRagdollPhysics();
    p.build(bones, 0, new Vector3f(0,0,0), 180f);
    if (!old) { // same as the mixin, right after build()
      Field jf = BoneRagdollPhysics.class.getDeclaredField("joints"); jf.setAccessible(true);
      RagdollLaunch.applyJointLimits((List<PhysicsJoint>) jf.get(p), s.jointLimitDegrees);
    }
    p.addToWorld(world);
    if (old) p.setUniformVelocity(incoming); else RagdollLaunch.apply(p, incoming, w, h, s, new Random(7));
    int root = heaviest(p);
    float[] marks = {0.25f,0.5f,1f,2f};
    StringBuilder sb = new StringBuilder();
    for (int t=1;t<=200;t++){ p.clampVelocities(); world.space.update(0.05f);
      float tl = tilt(p.getBody(root).getPhysicsRotation(null));
      for (float m: marks) if (Math.abs(t*0.05f-m)<1e-4) sb.append(String.format(" t=%.2f:tilt=%3.0f", m, tl));
    }
    Vector3f end = new Vector3f();
    p.getBody(root).getPhysicsLocation(end);
    float finalTilt = tilt(p.getBody(root).getPhysicsRotation(null));
    System.out.printf("%-46s travel=%5.2f blocks | %s | final tilt=%3.0f%n", label, Math.hypot(end.x,end.z), sb, finalTilt);
    p.removeFromWorld();
  }

  /** a sleeper laid down with its head towards +X, feet at (0, 0.1, 0): torso should end up flat and the head on the +X side */
  static void sleep(String label, boolean faceDown) throws Exception {
    BasePhysicsWorld world = floorWorld();
    BoneRagdollPhysics p = new BoneRagdollPhysics();
    p.build(humanoid(), 0, new Vector3f(0,0,0), 180f);
    p.addToWorld(world);
    SleepPose.layDown(p, new Vector3f(0,0,0), 180f, new Vector3f(1,0,0), new Vector3f(0,0.1f,0), faceDown);
    for (int t=0;t<100;t++){ p.clampVelocities(); world.space.update(0.05f); }
    Vector3f torso = new Vector3f(), head = new Vector3f();
    p.getBody(0).getPhysicsLocation(torso);
    p.getBody(1).getPhysicsLocation(head);
    System.out.printf("%-46s torso tilt=%3.0f | head is %+.2f blocks along +X from the torso%n", label, tilt(p.getBody(0).getPhysicsRotation(null)), head.x - torso.x);
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
    System.out.println("--- sleeping (laid down, head towards +X)");
    sleep("on the back", false);
    sleep("face down", true);
  }
}
