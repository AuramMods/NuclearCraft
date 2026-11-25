package nc.init;

import nc.NuclearCraft;
import nc.multiblock.fission.FissionPlacementRules;
import nc.multiblock.fission.FissionFuelCell;
import nc.multiblock.fission.FissionFuelCellBlock;
import nc.multiblock.fission.FissionPartBlock.Role;
import nc.multiblock.fission.FissionWallBlock;
import nc.multiblock.fission.PebbleCoolant;
import nc.multiblock.fission.PebbleCooler;
import nc.multiblock.fission.PebbleCoolerBlock;
import nc.multiblock.fission.SolidFissionController;
import nc.multiblock.fission.SolidFissionMenu;
import nc.recipe.FissionCoolantRecipe;
import nc.recipe.FluidIngredient;
import nc.recipe.SolidFissionRecipe;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public final class NCPebbleFission {
    private static final Map<NCGasCoolants, ForgeConfigSpec.IntValue> COOLING = new EnumMap<>(NCGasCoolants.class);
    private static final Map<NCGasCoolants, RegistryObject<PebbleCoolerBlock>> COOLERS = new EnumMap<>(NCGasCoolants.class);
    private static final Map<NCGasCoolants, RegistryObject<FissionWallBlock>> PORTS = new EnumMap<>(NCGasCoolants.class);
    private static final CoolingPlacementConfig<String> PLACEMENT;
    private static final Set<String> RESERVED_COOLANT_IDS = Set.of("any", "filtered", "fractions");
    private static List<PebbleCoolerBlock> registeredCoolers = List.of();
    private static Map<String, PebbleCoolant> coolantCatalog = Map.of();
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
        PLACEMENT = new CoolingPlacementConfig<>(builder, Arrays.stream(NCGasCoolants.values()).map(NCGasCoolants::id).toList(),
                FissionPlacementRules.Part.COOLER, name -> name, new String[] {
                    "one chamber", "exactly two chambers", "three chambers",
                    "exactly two oxygen coolers", "one moderator", "two axial moderators",
                    "three moderators", "exactly one oxygen cooler", "exactly one methane cooler",
                    "one ethene cooler && exactly one moderator", "one fluorine cooler", "exactly two nitrogen coolers && one casing",
                    "two hydrogen coolers", "one reflector", "one sulfur_dioxide cooler",
                    "two axial sulfur_trioxide coolers && one casing"
                }, NCPebbleFission::scriptedRules);
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
    public static final RegistryObject<BlockEntityType<PebbleCooler>> COOLER_ENTITY =
            NCRegistries.BLOCK_ENTITIES.register("pebble_fission_cooler", () ->
                    BlockEntityType.Builder.of(PebbleCooler::new, registerCoolerCatalog()).build(null));
    public static final RegistryObject<MenuType<SolidFissionMenu>> MENU = NCRegistries.MENUS.register("pebble_fission_controller",
            () -> new MenuType<>((id, inventory) -> new SolidFissionMenu(id, inventory, true), FeatureFlags.DEFAULT_FLAGS));
    public static final RegistryObject<RecipeType<SolidFissionRecipe>> FUEL_TYPE = NCRegistries.RECIPE_TYPES.register("pebble_fission",
            () -> RecipeType.simple(NuclearCraft.id("pebble_fission")));
    public static final RegistryObject<RecipeSerializer<SolidFissionRecipe>> FUEL_SERIALIZER =
            NCRegistries.RECIPE_SERIALIZERS.register("pebble_fission", () -> new SolidFissionRecipe.Serializer(true));

    public static final RegistryObject<RecipeType<FissionCoolantRecipe>> COOLER_TYPE =
            NCRegistries.RECIPE_TYPES.register("gas_cooler", () -> RecipeType.simple(NuclearCraft.id("gas_cooler")));
    public static final RegistryObject<RecipeSerializer<FissionCoolantRecipe>> COOLER_SERIALIZER =
            NCRegistries.RECIPE_SERIALIZERS.register("gas_cooler", () -> new FissionCoolantRecipe.Serializer(true));

    private NCPebbleFission() {}

    public static void registerConfig(FMLJavaModLoadingContext context) {
        PLACEMENT.register(context, SPEC, "nuclearcraft-pebble-fission.toml");
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

    public static PebbleCoolant scriptedCoolant(String id, String input, int inputAmount, String output, int outputAmount,
                                                int cooling, String placementRule) {
        if (!id.matches("[a-z0-9_]+") || RESERVED_COOLANT_IDS.contains(id)
                || Arrays.stream(NCGasCoolants.values()).anyMatch(gas -> gas.id().equals(id))) {
            throw new IllegalArgumentException("Invalid scripted cooler ID " + id);
        }
        if (inputAmount <= 0 || outputAmount <= 0 || cooling < 0) {
            throw new IllegalArgumentException("A gas cooler requires positive batches and nonnegative cooling");
        }
        boolean tag = input.startsWith("#");
        var ingredient = new FluidIngredient(new ResourceLocation(tag ? input.substring(1) : input), tag, inputAmount);
        return new ScriptedCoolant(id, ingredient, new ResourceLocation(output), outputAmount, cooling,
                Objects.requireNonNull(placementRule));
    }

    public static PebbleCoolerBlock createScriptedCooler(PebbleCoolant gas) {
        return new PebbleCoolerBlock(NCFission.properties(), gas);
    }

    public static FissionWallBlock createScriptedCoolerPort(PebbleCoolant gas) {
        return new FissionWallBlock(NCFission.properties(), Role.COOLER_PORT, gas);
    }

    public static BlockItem createScriptedCoolerItem(String blockId) {
        var block = ForgeRegistries.BLOCKS.getValue(new ResourceLocation(blockId));
        if (!(block instanceof PebbleCoolerBlock || block instanceof FissionWallBlock wall && wall.coolant() != null)) {
            throw new IllegalArgumentException("No NuclearCraft cooler or cooler port with ID " + blockId);
        }
        return new BlockItem(block, new Item.Properties());
    }

    private static Block[] registerCoolerCatalog() {
        registeredCoolers = ForgeRegistries.BLOCKS.getValues().stream().filter(PebbleCoolerBlock.class::isInstance)
                .map(PebbleCoolerBlock.class::cast).toList();
        coolantCatalog = registeredCoolers.stream().map(PebbleCoolerBlock::gas).distinct()
                .collect(Collectors.toUnmodifiableMap(PebbleCoolant::id, gas -> gas));
        return registeredCoolers.toArray(Block[]::new);
    }

    private static Map<String, String> scriptedRules() {
        return coolantCatalog.values().stream().filter(ScriptedCoolant.class::isInstance).map(ScriptedCoolant.class::cast)
                .collect(Collectors.toMap(ScriptedCoolant::id, ScriptedCoolant::placementRule));
    }

    public static List<PebbleCoolerBlock> coolers() { return registeredCoolers; }
    public static Map<String, PebbleCoolant> coolants() { return coolantCatalog; }

    public static String placementRule(PebbleCoolant gas) {
        String rule = PLACEMENT.rules().text(gas.id());
        return rule == null && gas instanceof ScriptedCoolant scripted ? scripted.placementRule() : Objects.requireNonNullElse(rule, "");
    }

    private record ScriptedCoolant(String id, FluidIngredient inputIngredient, ResourceLocation output, int outputAmount,
                                  int cooling, String placementRule) implements PebbleCoolant {
        @Override
        public Fluid hot() {
            return Objects.requireNonNullElse(ForgeRegistries.FLUIDS.getValue(output), Fluids.EMPTY);
        }
    }

    public static FissionPlacementRules<String> placementRules() { return PLACEMENT.rules(); }
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
