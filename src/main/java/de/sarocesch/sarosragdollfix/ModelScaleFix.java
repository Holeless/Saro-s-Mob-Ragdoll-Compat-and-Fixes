package de.sarocesch.sarosragdollfix;

import net.minecraft.world.entity.EntityType;
import org.joml.Matrix4f;

// Some vanilla models scale themselves inside renderToBuffer, which Saro's skeleton builder never sees.
// Adult rabbit: scale(0.6) + translate(0, 1, 0), without this the ragdoll is 1.67x too big.
public final class ModelScaleFix {
   public static final float RABBIT_SCALE = 0.6F;
   public static final float RABBIT_LIFT_BLOCKS = 1.0F;

   private static final ThreadLocal<Boolean> ACTIVE = new ThreadLocal<>();

   private ModelScaleFix() {
   }

   /** Call before building a skeleton, and end() in a finally. */
   public static void begin(EntityType<?> type, boolean baby) {
      if (!baby && type == EntityType.RABBIT && FixConfig.FIX_MODEL_SCALE.get()) {
         ACTIVE.set(Boolean.TRUE);
      } else {
         ACTIVE.remove();
      }
   }

   public static void end() {
      ACTIVE.remove();
   }

   public static boolean active() {
      return ACTIVE.get() == Boolean.TRUE;
   }

   public static Matrix4f adjust(Matrix4f prefix) {
      return new Matrix4f(prefix).mul(new Matrix4f().scale(RABBIT_SCALE).translate(0.0F, RABBIT_LIFT_BLOCKS, 0.0F));
   }

   public static float adjust(float scale) {
      return scale * RABBIT_SCALE;
   }
}
