package de.sarocesch.sarosragdollfix;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;

// Saro's ClientMobRagdoll.render() asks for the base VertexConsumer once, before the bone loop, and keeps using it for
// every bone. But after each bone it draws that bone's overlay layers (glowing eyes, ...), and asking the buffer source
// for a different RenderType ends the base batch, which hands the same shared BufferBuilder to the layer's batch.
// Every bone after the first one then writes its body cubes into the layer batch (or into a finished one), so only the
// first bone (the torso) and the layers ever show up.
//
// This wraps the buffer source for one render() call. The first consumer it gives out (the base one) is a proxy that asks
// the real source for its RenderType again on every call, so the base batch is simply reopened after each layer, the
// same as vanilla does when an entity renderer alternates between its model and its layers.
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

      // ModelPart cubes write through this one. Forward it whole so the builder keeps its fast path.
      @Override
      public void vertex(
         float x, float y, float z, float red, float green, float blue, float alpha, float u, float v, int overlay, int light, float nx, float ny, float nz
      ) {
         this.current().vertex(x, y, z, red, green, blue, alpha, u, v, overlay, light, nx, ny, nz);
      }
   }
}
