package nc.fluid;

import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

import java.util.List;

public final class FluidConfig {
    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();
    private static final ForgeConfigSpec.ConfigValue<List<? extends String>> CORIUM_DIMENSIONS = BUILDER.comment(
            "Dimensions where corium naturally solidifies. For modded dimensions use their Forge resource IDs.")
            .defineListAllowEmpty("corium_solidification",
                    () -> List.of("minecraft:overworld", "minecraft:the_end", "nuclearcraft:wasteland"),
                    value -> value instanceof String name && ResourceLocation.tryParse(name) != null);
    private static final ForgeConfigSpec.BooleanValue CORIUM_IS_BLACKLIST = BUILDER
            .define("corium_solidification_list_type", false);
    private static final ForgeConfigSpec.BooleanValue PLASMA_FIRE = BUILDER.define("fusion_plasma_craziness", true);
    private static final ForgeConfigSpec SPEC = BUILDER.build();

    private FluidConfig() {}

    public static void register(FMLJavaModLoadingContext context) {
        context.registerConfig(ModConfig.Type.SERVER, SPEC, "nuclearcraft-fluids.toml");
    }

    static boolean coriumCools(ResourceLocation dimension) {
        return CORIUM_DIMENSIONS.get().contains(dimension.toString()) != CORIUM_IS_BLACKLIST.get();
    }

    static boolean plasmaFire() {
        return PLASMA_FIRE.get();
    }
}
