package de.sarocesch.sarosmobragdoll;
import net.minecraftforge.common.ForgeConfigSpec;
public class ModConfig {
  public static final ForgeConfigSpec.ConfigValue<String> PHYSICS_MODE = new ForgeConfigSpec.ConfigValue<>();
  static { PHYSICS_MODE.v = "smooth"; }
}
