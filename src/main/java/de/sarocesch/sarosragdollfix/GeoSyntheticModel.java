package de.sarocesch.sarosragdollfix;

import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.entity.Entity;

// A plain ModelPart tree built from a geo.json / Citadel model, so Saro's skeleton builder can handle it like a vanilla mob.
public final class GeoSyntheticModel extends HierarchicalModel<Entity> {
   private final ModelPart root;

   public GeoSyntheticModel(ModelPart root) {
      super();
      this.root = root;
   }

   @Override
   public ModelPart root() {
      return this.root;
   }

   @Override
   public void setupAnim(Entity entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
   }
}
