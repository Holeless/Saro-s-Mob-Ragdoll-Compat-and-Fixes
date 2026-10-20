package de.sarocesch.sarosragdollfix;

import com.mojang.blaze3d.vertex.VertexConsumer;

// Multiplies the colour of whatever goes through it, for wounds with non-red blood.
public final class TintedConsumer implements VertexConsumer {
   private final VertexConsumer inner;
   private final float r;
   private final float g;
   private final float b;

   public TintedConsumer(VertexConsumer inner, float r, float g, float b) {
      this.inner = inner;
      this.r = r;
      this.g = g;
      this.b = b;
   }

   @Override
   public VertexConsumer vertex(double x, double y, double z) {
      this.inner.vertex(x, y, z);
      return this;
   }

   @Override
   public VertexConsumer color(int red, int green, int blue, int alpha) {
      this.inner.color((int)(red * this.r), (int)(green * this.g), (int)(blue * this.b), alpha);
      return this;
   }

   @Override
   public VertexConsumer uv(float u, float v) {
      this.inner.uv(u, v);
      return this;
   }

   @Override
   public VertexConsumer overlayCoords(int u, int v) {
      this.inner.overlayCoords(u, v);
      return this;
   }

   @Override
   public VertexConsumer uv2(int u, int v) {
      this.inner.uv2(u, v);
      return this;
   }

   @Override
   public VertexConsumer normal(float x, float y, float z) {
      this.inner.normal(x, y, z);
      return this;
   }

   @Override
   public void endVertex() {
      this.inner.endVertex();
   }

   @Override
   public void defaultColor(int red, int green, int blue, int alpha) {
      this.inner.defaultColor(red, green, blue, alpha);
   }

   @Override
   public void unsetDefaultColor() {
      this.inner.unsetDefaultColor();
   }
}
