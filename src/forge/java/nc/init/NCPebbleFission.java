package nc.init;

import nc.NuclearCraft;
import nc.multiblock.fission.FissionFuelCell;
import nc.multiblock.fission.FissionFuelCellBlock;
import nc.multiblock.fission.FissionPartBlock.Role;
import nc.multiblock.fission.FissionWallBlock;
import nc.multiblock.fission.PebbleCoolerBlock;
import nc.multiblock.fission.SolidFissionController;
import nc.multiblock.fission.SolidFissionMenu;
import nc.recipe.SolidFissionRecipe;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.registries.RegistryObject;

import java.util.EnumMap;
import java.util.Map;

public final class NCPebbleFission {
    private static final Map<NCGasCoolants, ForgeConfigSpec.IntValue> COOLING = new EnumMap<>(NCGasCoolants.class);
    private static final Map<NCGasCoolants, RegistryObject<PebbleCoolerBlock>> COOLERS = new EnumMap<>(NCGasCoolants.class);
    private static final Map<NCGasCoolants, RegistryObject<FissionWallBlock>> PORTS = new EnumMap<>(NCGasCoolants.class);
    private static final ForgeConfigSpec SPEC;
    private static final ForgeConfigSpec.DoubleValue HEAT_PER_MB;
    private static final ForgeConfigSpec.DoubleValue INTRINSIC_EFFICIENCY;
    private static final ForgeConfigSpec.DoubleValue EMERGENCY_MULTIPLIER;

    static {
        var builder = new ForgeConfigSpec.Builder();
        HEAT_PER_MB = builder.defineInRange("coolant_heat_per_mb", 32D, 0.001D, Integer.MAX_VALUE);
        INTRINSIC_EFFICIENCY = builder.defineInRange("intrinsic_flux_efficiency", 1D, 0D, 255D);
        EMERGENCY_MULTIPLIER = builder.defineInRange("emergency_cooling_multiplier", 1D, 0.001D, 255D);
        builder.push("cooling_rate");
        for (NCGasCoolants gas : NCGasCoolants.values()) {
            COOLING.put(gas, builder.defineInRange(gas.id(), gas.defaultCooling(), 0, 32_767));
            COOLERS.put(gas, NCFission.part(gas.coolerId(), () -> new PebbleCoolerBlock(NCFission.properties(), gas)));
            PORTS.put(gas, NCFission.part(gas.portId(), () -> new FissionWallBlock(NCFission.properties(), Role.COOLER_PORT, gas)));
        }
        builder.pop();
        SPEC = builder.build();
    }

    public static final RegistryObject<FissionWallBlock> CONTROLLER = NCFission.wall("pebble_fission_controller", Role.PEBBLE_CONTROLLER);
    public static final RegistryObject<FissionFuelCellBlock> CHAMBER = NCFission.part("pebble_fission_chamber",
            () -> new FissionFuelCellBlock(NCFission.properties(), Role.PEBBLE_CHAMBER));
    public static final RegistryObject<FissionWallBlock> CHAMBER_PORT = NCFission.wall("fission_chamber_port", Role.CHAMBER_PORT);

    public static final RegistryObject<BlockEntityType<SolidFissionController>> CONTROLLER_ENTITY =
            NCRegistries.BLOCK_ENTITIES.register("pebble_fission_controller", () ->
                    BlockEntityType.Builder.of(SolidFissionController::new, CONTROLLER.get()).build(null));
    public static final RegistryObject<BlockEntityType<FissionFuelCell>> CHAMBER_ENTITY =
            NCRegistries.BLOCK_ENTITIES.register("pebble_fission_chamber", () ->
                    BlockEntityType.Builder.of(FissionFuelCell::new, CHAMBER.get()).build(null));
    public static final RegistryObject<MenuType<SolidFissionMenu>> MENU = NCRegistries.MENUS.register("pebble_fission_controller",
            () -> new MenuType<>((id, inventory) -> new SolidFissionMenu(id, inventory, true), FeatureFlags.DEFAULT_FLAGS));
    public static final RegistryObject<RecipeType<SolidFissionRecipe>> FUEL_TYPE = NCRegistries.RECIPE_TYPES.register("pebble_fission",
            () -> RecipeType.simple(NuclearCraft.id("pebble_fission")));
    public static final RegistryObject<RecipeSerializer<SolidFissionRecipe>> FUEL_SERIALIZER =
            NCRegistries.RECIPE_SERIALIZERS.register("pebble_fission", () -> new SolidFissionRecipe.Serializer(true));

    private NCPebbleFission() {}

    public static void registerConfig(ModLoadingContext context) {
        context.registerConfig(ModConfig.Type.SERVER, SPEC, "nuclearcraft-pebble-fission.toml");
    }

    static void displayItems(CreativeModeTab.Output output) {
        output.accept(CONTROLLER.get());
        output.accept(CHAMBER.get());
        output.accept(CHAMBER_PORT.get());
        for (NCGasCoolants gas : NCGasCoolants.values()) {
            output.accept(cooler(gas));
            output.accept(port(gas));
            output.accept(gas.cold().getBucket());
            output.accept(gas.hot().getBucket());
            output.accept(gas.exhaust().getBucket());
        }
        output.accept(NCFluids.EMERGENCY_COOLANT.bucket().get());
        output.accept(NCFluids.HEATED_EMERGENCY_COOLANT.bucket().get());
    }

    public static PebbleCoolerBlock cooler(NCGasCoolants gas) { return COOLERS.get(gas).get(); }
    public static FissionWallBlock port(NCGasCoolants gas) { return PORTS.get(gas).get(); }
    public static int cooling(NCGasCoolants gas) { return value(COOLING.get(gas)); }
    public static double heatPerMb() { return value(HEAT_PER_MB); }
    public static double intrinsicEfficiency() { return value(INTRINSIC_EFFICIENCY); }
    public static double emergencyMultiplier() { return value(EMERGENCY_MULTIPLIER); }

    private static <T> T value(ForgeConfigSpec.ConfigValue<T> value) {
        return SPEC.isLoaded() ? value.get() : value.getDefault();
    }
}
