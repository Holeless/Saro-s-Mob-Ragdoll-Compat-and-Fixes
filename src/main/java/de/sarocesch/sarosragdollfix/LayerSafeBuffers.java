package de.sarocesch.sarosragdollfix;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;

// Saro's render() gets the base VertexConsumer once and reuses it for every bone, but it draws each bone's layers
// (glowing eyes etc.) in between. Asking for a layer's RenderType ends the base batch, so every bone after the first
// wrote into the wrong buffer and vanished.
// The first consumer handed out here (the base one) asks the real source for its RenderType on every call, which
// reopens the base batch after a layer, same as vanilla entity renderers do.
public final class LayerSafeBuffers implements MultiBufferSource {
   private final MultiBufferSource inner;
   private boolean baseGiven;

   public LayerSafeBuffers(MultiBufferSource inner) {
      this.inner = inner;
   }

   @Override
   public VertexConsumer getBuffer(RenderType type) {
      if (this.baseGiven) {
         return this.inner.getBuffer(type);
      }

      this.baseGiven = true;
      return new Base(this.inner, type);
   }

   private static final class Base implements VertexConsumer {
      private final MultiBufferSource source;
      private final RenderType type;

      Base(MultiBufferSource source, RenderType type) {
         this.source = source;
         this.type = type;
      }

      private VertexConsumer current() {
         return this.source.getBuffer(this.type);
      }

      @Override
      public VertexConsumer vertex(double x, double y, double z) {
         this.current().vertex(x, y, z);
         return this;
      }

      @Override
      public VertexConsumer color(int red, int green, int blue, int alpha) {
         this.current().color(red, green, blue, alpha);
         return this;
      }

      @Override
      public VertexConsumer uv(float u, float v) {
         this.current().uv(u, v);
         return this;
      }

      @Override
      public VertexConsumer overlayCoords(int u, int v) {
         this.current().overlayCoords(u, v);
         return this;
      }

      @Override
      public VertexConsumer uv2(int u, int v) {
         this.current().uv2(u, v);
         return this;
      }

      @Override
      public VertexConsumer normal(float x, float y, float z) {
         this.current().normal(x, y, z);
         return this;
      }

      @Override
      public void endVertex() {
         this.current().endVertex();
      }

      @Override
      public void defaultColor(int red, int green, int blue, int alpha) {
         this.current().defaultColor(red, green, blue, alpha);
      }

      @Override
      public void unsetDefaultColor() {
         this.current().unsetDefaultColor();
      }

      // ModelPart cubes write through this overload
      @Override
      public void vertex(
         float x, float y, float z, float red, float green, float blue, float alpha, float u, float v, int overlay, int light, float nx, float ny, float nz
      ) {
         this.current().vertex(x, y, z, red, green, blue, alpha, u, v, overlay, light, nx, ny, nz);
      }
   }
}
