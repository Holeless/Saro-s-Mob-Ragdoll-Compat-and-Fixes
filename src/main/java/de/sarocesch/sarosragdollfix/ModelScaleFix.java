package de.sarocesch.sarosragdollfix;

import net.minecraft.world.entity.EntityType;
import org.joml.Matrix4f;

/**
 * Vanilla models that scale themselves inside {@code renderToBuffer}. Saro's skeleton builder reads the raw ModelPart
 * tree, so it never sees that scale and the ragdoll comes out too big.
 *
 * <p>Adult rabbit: {@code RabbitModel.renderToBuffer} does {@code scale(0.6); translate(0, 1, 0)} around every part, so
 * without this the ragdoll is 1/0.6 = 1.67 times too big. The same scale-then-translate form Saro uses for baby models is
 * applied to the part matrix, so the bodies are sized and placed like the real mob (feet on the ground).
 */
public final class ModelScaleFix {
   public static final float RABBIT_SCALE = 0.6F;
   public static final float RABBIT_LIFT_BLOCKS = 1.0F;

   private static final ThreadLocal<Boolean> ACTIVE = new ThreadLocal<>();

   private ModelScaleFix() {
   }

   /** Call before building a skeleton; {@link #end()} in a finally block afterwards. */
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

   /** prefix * (scale then translate), the same shape as the original mod's baby transform. */
   public static Matrix4f adjust(Matrix4f prefix) {
      return new Matrix4f(prefix).mul(new Matrix4f().scale(RABBIT_SCALE).translate(0.0F, RABBIT_LIFT_BLOCKS, 0.0F));
   }

   public static float adjust(float scale) {
      return scale * RABBIT_SCALE;
   }
}
