package nc.radiation;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

import java.util.List;

public final class RadiationHudConfig {
    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();
    public static final ForgeConfigSpec.DoubleValue SIZE = BUILDER.defineInRange("radiation_hud_size", 1D, 0.1D, 10D);
    public static final ForgeConfigSpec.DoubleValue ANGLE =
            BUILDER.defineInRange("radiation_hud_position", 225D, 0D, 360D);
    public static final ForgeConfigSpec.ConfigValue<List<? extends Number>> CARTESIAN =
            BUILDER.comment("Optional x and y coordinates as fractions of the screen width and height.")
                    .defineListAllowEmpty("radiation_hud_position_cartesian", List.of(),
                            value -> value instanceof Number number
                                    && number.doubleValue() >= 0 && number.doubleValue() <= 1);
    public static final ForgeConfigSpec.BooleanValue TEXT_OUTLINE = BUILDER.define("radiation_hud_text_outline", false);
    public static final ForgeConfigSpec.BooleanValue CHUNK_BOUNDARIES =
            BUILDER.define("radiation_chunk_boundaries", true);
    private static final ForgeConfigSpec SPEC = BUILDER.build();

    private RadiationHudConfig() {}

    public static void register(FMLJavaModLoadingContext context) {
        context.registerConfig(ModConfig.Type.CLIENT, SPEC, "nuclearcraft-radiation-client.toml");
    }
}
