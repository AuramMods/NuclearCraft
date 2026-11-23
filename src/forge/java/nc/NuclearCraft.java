package nc;

import nc.init.NCCollectors;
import nc.init.NCFission;
import nc.init.NCFluids;
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
        NCCollectors.register(context);
        NCFission.registerConfig(context);
        NCRegistries.register(context.getModEventBus());
        NCFluids.register(context.getModEventBus());
    }
}
