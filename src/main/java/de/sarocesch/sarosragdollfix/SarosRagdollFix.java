package de.sarocesch.sarosragdollfix;

import com.mojang.logging.LogUtils;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import org.slf4j.Logger;

/**
 * Client-side fix and compat addon for Saro's Mob Ragdoll (1.20.1). All the work happens in the mixins; this class only
 * registers the config.
 */
@Mod(SarosRagdollFix.MODID)
public class SarosRagdollFix {
   public static final String MODID = "saros_ragdoll_fix";
   public static final Logger LOG = LogUtils.getLogger();

   public SarosRagdollFix() {
      ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, FixConfig.SPEC);
   }
}
