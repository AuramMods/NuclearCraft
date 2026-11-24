package nc;

import nc.fluid.FluidConfig;
import nc.init.HeatExchangerConfig;
import nc.init.NCBatteries;
import nc.init.NCCollectors;
import nc.init.NCFission;
import nc.init.NCPebbleFission;
import nc.init.NCSaltFission;
import nc.init.NCFluids;
import nc.init.NCGenerators;
import nc.init.NCItemProcessors;
import nc.init.NCMachines;
import nc.init.NCRadiation;
import nc.init.NCRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

import java.util.Objects;

@Mod(NuclearCraft.MOD_ID)
public final class NuclearCraft {
    public static final String MOD_ID = "nuclearcraft";

    public static ResourceLocation id(String path) {
        return Objects.requireNonNull(ResourceLocation.tryBuild(MOD_ID, path), path);
    }

    public NuclearCraft(FMLJavaModLoadingContext context) {
        NCBatteries.register(context);
        NCCollectors.register(context);
        NCGenerators.register(context);
        NCRadiation.register(context);
        FluidConfig.register(context);
        NCFission.registerConfig(context);
        NCPebbleFission.registerConfig(context);
        NCSaltFission.registerConfig(context);
        HeatExchangerConfig.register(context);
        NCMachines.registerConfig(context);
        NCItemProcessors.registerConfig(context);
        NCRegistries.register(context.getModEventBus());
        NCFluids.register(context.getModEventBus());
    }
}
