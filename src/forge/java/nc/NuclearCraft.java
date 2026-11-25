package nc;

import nc.compat.mekanism.GasFluidCapability;
import nc.compat.patchouli.Guidebook;
import nc.compat.projecte.ProjectEIntegration;
import nc.compat.tconstruct.TinkersModifiers;
import nc.fluid.FluidConfig;
import nc.multiblock.fission.FissionHighlights;
import nc.init.HeatExchangerConfig;
import nc.init.TurbineConfig;
import nc.init.NCAdvancements;
import nc.init.NCBatteries;
import nc.init.NCCollectors;
import nc.init.NCComputerPorts;
import nc.init.NCClientPresentation;
import nc.init.NCEntities;
import nc.init.NCEmbersRecipes;
import nc.init.NCEquipment;
import nc.init.NCFission;
import nc.init.NCPebbleFission;
import nc.init.NCSaltFission;
import nc.init.NCFluids;
import nc.init.NCGenerators;
import nc.init.NCItemProcessors;
import nc.init.NCLoot;
import nc.init.NCOres;
import nc.init.NCProcessorAvailability;
import nc.init.NCMachines;
import nc.init.NCRadiation;
import nc.init.NCScriptAddons;
import nc.init.NCQuantum;
import nc.init.NCRegistries;
import nc.init.NCWasteland;
import nc.init.NCTerrainRecipes;
import nc.init.NCTinkersMaterials;
import nc.processor.CommonCapabilitiesRecipes;
import nc.recipe.MaterialRecipeOutputs;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.fml.ModList;
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
        NCScriptAddons.register(context.getModEventBus());
        context.getModEventBus().addListener(NCAdvancements::setup);
        NCBatteries.register(context);
        NCCollectors.register(context);
        NCComputerPorts.register(context);
        NCClientPresentation.register(context);
        NCEntities.register(context);
        NCLoot.register(context);
        NCOres.register(context);
        NCEquipment.register(context);
        NCGenerators.register(context);
        NCQuantum.register(context);
        NCRadiation.register(context);
        Guidebook.register(context);
        ProjectEIntegration.register(context);
        MaterialRecipeOutputs.register(context);
        if (ModList.get().isLoaded("commoncapabilities")) {
            CommonCapabilitiesRecipes.register();
        }
        NCTinkersMaterials.register(context);
        if (ModList.get().isLoaded("mekanism")) {
            GasFluidCapability.register();
        }
        if (ModList.get().isLoaded("tconstruct")) {
            TinkersModifiers.register(context.getModEventBus());
        }
        if (ModList.get().isLoaded("embers")) {
            NCEmbersRecipes.register();
        }
        FluidConfig.register(context);
        NCFission.registerConfig(context);
        FissionHighlights.register();
        NCPebbleFission.registerConfig(context);
        NCSaltFission.registerConfig(context);
        HeatExchangerConfig.register(context);
        TurbineConfig.register(context);
        NCMachines.registerConfig(context);
        NCProcessorAvailability.register(context);
        NCItemProcessors.registerConfig(context);
        NCWasteland.register(context);
        NCTerrainRecipes.register();
        NCRegistries.register(context.getModEventBus());
        NCFluids.register(context.getModEventBus());
    }
}
