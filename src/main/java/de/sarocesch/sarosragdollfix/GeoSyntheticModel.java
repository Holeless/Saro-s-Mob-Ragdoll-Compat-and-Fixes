package de.sarocesch.sarosragdollfix;

import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.entity.Entity;

/**
 * A plain vanilla-style model (a ModelPart tree) built from a GeckoLib .geo.json file. Saro's Mob Ragdoll already knows
 * how to turn such a model into one physics body per limb; this class only gives it something it understands.
 */
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
