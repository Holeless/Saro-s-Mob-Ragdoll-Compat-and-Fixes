package de.sarocesch.sarosragdollfix;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.entity.Entity;

// Runs a render layer once on a mob and writes down which colour it multiplies each RenderType with. Wool colour, the
// pattern colour on tropical fish and wolf collars are all a tint on the same texture, so the original, which only looks at
// which textures a layer uses, drew every sheep white and every fish grey.
// Method names are the obfuscated ones at runtime, so the consumer calls are told apart by their parameter types.
public final class TintProbe implements MultiBufferSource {
   private final Map<RenderType, float[]> tints = new LinkedHashMap<>();

   private TintProbe() {
   }

   /** Tint per RenderType this layer draws for the given mob, only entries that are not plain white. */
   public static Map<RenderType, float[]> tintsUsedBy(Object layer, Entity entity) {
      TintProbe probe = new TintProbe();
      try {
         Method render = findRender(layer, entity);
         if (render != null) {
            render.invoke(layer, new PoseStack(), probe, 15728880, entity, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F);
         }
      } catch (Throwable t) {
         SarosRagdollFix.LOG.debug("[ragdoll-fix] tint probe failed for {}", layer.getClass().getName(), t);
      }

      Map<RenderType, float[]> out = new LinkedHashMap<>();
      for (Map.Entry<RenderType, float[]> e : probe.tints.entrySet()) {
         float[] c = e.getValue();
         if (c[0] < 0.999F || c[1] < 0.999F || c[2] < 0.999F) {
            out.put(e.getKey(), c);
         }
      }

      return out;
   }

   @Nullable
   private static Method findRender(Object layer, Entity entity) {
      for (Class<?> c = layer.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
         for (Method m : c.getDeclaredMethods()) {
            Class<?>[] p = m.getParameterTypes();
            if (p.length == 10 && p[0] == PoseStack.class && p[1] == MultiBufferSource.class && p[2] == int.class && p[3].isInstance(entity)) {
               boolean floats = true;
               for (int i = 4; i < 10; i++) {
                  floats &= p[i] == float.class;
               }

               if (floats) {
                  m.setAccessible(true);
                  return m;
               }
            }
         }
      }

      return null;
   }

   @Override
   public VertexConsumer getBuffer(RenderType type) {
      return (VertexConsumer)Proxy.newProxyInstance(VertexConsumer.class.getClassLoader(), new Class<?>[]{VertexConsumer.class}, new Recorder(type));
   }

   private final class Recorder implements InvocationHandler {
      private final RenderType type;

      Recorder(RenderType type) {
         this.type = type;
      }

      @Override
      public Object invoke(Object proxy, Method method, Object[] args) {
         Class<?>[] p = method.getParameterTypes();
         if (p.length == 14 && p[3] == float.class && p[4] == float.class && p[5] == float.class) {
            // the per-vertex call cubes use: x y z r g b a u v overlay light nx ny nz
            this.note((Float)args[3], (Float)args[4], (Float)args[5]);
         } else if (p.length == 4 && method.getReturnType() == VertexConsumer.class) {
            if (p[0] == int.class) {
               this.note((Integer)args[0] / 255.0F, (Integer)args[1] / 255.0F, (Integer)args[2] / 255.0F);
            } else if (p[0] == float.class) {
               this.note((Float)args[0], (Float)args[1], (Float)args[2]);
            }
         }

         Class<?> rt = method.getReturnType();
         if (rt == void.class) {
            return null;
         }

         if (VertexConsumer.class.isAssignableFrom(rt)) {
            return proxy;
         }

         if (rt == boolean.class) {
            return Boolean.FALSE;
         }

         if (rt == int.class) {
            return 0;
         }

         if (rt == float.class) {
            return 0.0F;
         }

         if (rt == double.class) {
            return 0.0D;
         }

         if (rt == long.class) {
            return 0L;
         }

         return null;
      }

      private void note(float r, float g, float b) {
         if (!TintProbe.this.tints.containsKey(this.type)) {
            TintProbe.this.tints.put(this.type, new float[]{r, g, b});
         }
      }
   }
}
