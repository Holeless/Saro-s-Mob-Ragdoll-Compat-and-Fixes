package de.sarocesch.sarosragdollfix;

import com.mojang.logging.LogUtils;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import org.slf4j.Logger;

// Client side addon for Saro's Mob Ragdoll. The work is done by the mixins, this just registers the config.
@Mod(SarosRagdollFix.MODID)
public class SarosRagdollFix {
   public static final String MODID = "saros_ragdoll_fix";
   public static final Logger LOG = LogUtils.getLogger();

   public SarosRagdollFix() {
      ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, FixConfig.SPEC);
   }
}
