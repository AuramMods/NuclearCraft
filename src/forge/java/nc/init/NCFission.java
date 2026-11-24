package nc.init;

import nc.NuclearCraft;
import nc.multiblock.fission.FissionControlBlockEntity;
import nc.multiblock.fission.FissionFuelCell;
import nc.multiblock.fission.FissionIrradiator;
import nc.multiblock.fission.FissionIrradiatorBlock;
import nc.multiblock.fission.FissionIrradiatorMenu;
import nc.multiblock.fission.FissionManagerBlock;
import nc.multiblock.fission.FissionShieldBlock;
import nc.multiblock.fission.FissionFuelCellBlock;
import nc.multiblock.fission.FissionPlacementRules;
import nc.multiblock.fission.FissionPartBlock;
import nc.multiblock.fission.FissionPartBlock.Role;
import nc.multiblock.fission.FissionPortBlockEntity;
import nc.multiblock.fission.FissionSourceBlock;
import nc.multiblock.fission.FissionWallBlock;
import nc.multiblock.fission.SolidHeatSinkBlock;
import nc.multiblock.fission.SolidFissionController;
import nc.multiblock.fission.SolidFissionMenu;
import nc.recipe.SolidFissionRecipe;
import nc.recipe.FissionIrradiatorRecipe;
import nc.recipe.FissionIrradiatorRecipe.Material;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.RegistryObject;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.DoubleSupplier;
import java.util.function.Supplier;

public final class NCFission {
    private static final Map<NCHeatSinks, RegistryObject<SolidHeatSinkBlock>> SINKS = new EnumMap<>(NCHeatSinks.class);
    private static final Map<NCHeatSinks, ForgeConfigSpec.IntValue> COOLING = new EnumMap<>(NCHeatSinks.class);
    private static final CoolingPlacementConfig<NCHeatSinks> PLACEMENT;
    private static final ForgeConfigSpec SPEC;
    private static final ForgeConfigSpec.DoubleValue RADIUM_EFFICIENCY;
    private static final ForgeConfigSpec.DoubleValue POLONIUM_EFFICIENCY;
    private static final ForgeConfigSpec.DoubleValue CALIFORNIUM_EFFICIENCY;
    private static final ForgeConfigSpec.DoubleValue BORON_SILVER_HEAT;
    private static final ForgeConfigSpec.DoubleValue BORON_SILVER_EFFICIENCY;
    private static final ForgeConfigSpec.DoubleValue GADOLINIUM_SAMARIUM_HEAT;
    private static final ForgeConfigSpec.DoubleValue GADOLINIUM_SAMARIUM_EFFICIENCY;
    private static final ForgeConfigSpec.IntValue NEUTRON_REACH;
    private static final ForgeConfigSpec.DoubleValue INTRINSIC_FLUX_EFFICIENCY;
    private static final ForgeConfigSpec.DoubleValue MELTDOWN_RADIATION;
    private static final Map<Material, ForgeConfigSpec.DoubleValue> IRRADIATOR_HEAT = new EnumMap<>(Material.class);
    private static final Map<Material, ForgeConfigSpec.DoubleValue> IRRADIATOR_EFFICIENCY = new EnumMap<>(Material.class);

    private static final TagKey<Block> GRAPHITE_BLOCKS = storageBlocks("graphite");
    private static final TagKey<Block> BERYLLIUM_BLOCKS = storageBlocks("beryllium");
    public static final Moderator GRAPHITE;
    public static final Moderator BERYLLIUM;
    public static final Moderator HEAVY_WATER;
    public static final Reflector BERYLLIUM_CARBON;
    public static final Reflector LEAD_STEEL;

    static {
        var builder = new ForgeConfigSpec.Builder();
        MELTDOWN_RADIATION = builder.defineInRange("fission_meltdown_radiation_multiplier", 1D, 0D, 255D);
        builder.push("neutron_sources");
        RADIUM_EFFICIENCY = builder.defineInRange("radium_beryllium_efficiency", 0.9D, 0D, 255D);
        POLONIUM_EFFICIENCY = builder.defineInRange("polonium_beryllium_efficiency", 0.95D, 0D, 255D);
        CALIFORNIUM_EFFICIENCY = builder.defineInRange("californium_efficiency", 1D, 0D, 255D);
        builder.pop();
        builder.push("neutron_shields");
        BORON_SILVER_HEAT = builder.defineInRange("boron_silver_heat_per_flux", 5D, 0D, 32767D);
        BORON_SILVER_EFFICIENCY = builder.defineInRange("boron_silver_efficiency", 0.5D, 0D, 255D);
        GADOLINIUM_SAMARIUM_HEAT = builder.defineInRange("gadolinium_samarium_heat_per_flux", 10D, 0D, 32767D);
        GADOLINIUM_SAMARIUM_EFFICIENCY = builder.defineInRange("gadolinium_samarium_efficiency", 0.75D, 0D, 255D);
        builder.pop();
        builder.push("irradiator");
        for (Material material : Material.values()) {
            builder.push(material.id());
            IRRADIATOR_HEAT.put(material, builder.defineInRange("heat_per_flux", 0D, 0D, 32_767D));
            IRRADIATOR_EFFICIENCY.put(material, builder.defineInRange("efficiency", material.defaultEfficiency, 0D, 32_767D));
            builder.pop();
        }
        builder.pop();
        builder.push("neutrons");
        NEUTRON_REACH = builder.defineInRange("reach", 4, 1, 255);
        INTRINSIC_FLUX_EFFICIENCY = builder.defineInRange("intrinsic_flux_efficiency", 1D, 0D, 255D);
        builder.pop();
        builder.push("moderators");
        GRAPHITE = moderator(builder, "graphite", 10, 1.1D);
        BERYLLIUM = moderator(builder, "beryllium", 22, 1.05D);
        HEAVY_WATER = moderator(builder, "heavy_water", 36, 1D);
        builder.pop();
        builder.push("reflectors");
        BERYLLIUM_CARBON = reflector(builder, "beryllium_carbon", 1D, 0.5D);
        LEAD_STEEL = reflector(builder, "lead_steel", 0.5D, 0.25D);
        builder.pop();
        builder.push("cooling_rate");
        for (NCHeatSinks sink : NCHeatSinks.values()) {
            COOLING.put(sink, builder.defineInRange(sink.material(), sink.defaultCooling(), 0, 32767));
            SINKS.put(sink, part(sink.id(), () -> new SolidHeatSinkBlock(
                    properties().lightLevel(state -> sink.light()), sink)));
        }
        builder.pop();
        PLACEMENT = new CoolingPlacementConfig<>(builder, NCHeatSinks.class, FissionPlacementRules.Part.SINK,
                NCHeatSinks::material, new String[] {
                    "one cell", "one moderator", "one cell && one moderator",
                    "one redstone sink", "two axial glowstone sinks", "one obsidian sink",
                    "two moderators", "one cell && one casing", "exactly two iron sinks",
                    "two water sinks", "exactly one water sink && two lead sinks", "one reflector",
                    "one reflector && one iron sink", "one cell && one gold sink", "one moderator && one prismarine sink",
                    "one water sink", "two axial lapis sinks", "one iron sink",
                    "exactly one quartz sink && one casing", "exactly two axial lead sinks && one casing", "exactly one moderator && one casing",
                    "two cells", "one quartz sink && one lapis sink", "two glowstone sinks && one tin sink",
                    "one gold sink && one prismarine sink", "one redstone sink && one end_stone sink", "one end_stone sink && one copper sink",
                    "two axial reflectors", "two copper sinks && one purpur sink", "exactly two redstone sinks",
                    "three moderators", "three cells"
                });
        SPEC = builder.build();
    }

    public static final RegistryObject<FissionPartBlock> CASING = part("fission_casing", Role.CASING);
    public static final RegistryObject<FissionWallBlock> CONTROLLER = wall("solid_fission_controller", Role.CONTROLLER);
    public static final RegistryObject<FissionWallBlock> VENT = wall("fission_vent", Role.VENT);
    public static final RegistryObject<FissionWallBlock> CELL_PORT = wall("fission_cell_port", Role.FUEL_PORT);
    public static final RegistryObject<FissionIrradiatorBlock> IRRADIATOR = part("fission_irradiator",
            () -> new FissionIrradiatorBlock(properties()));
    public static final RegistryObject<FissionWallBlock> IRRADIATOR_PORT = wall("fission_irradiator_port", Role.IRRADIATOR_PORT);
    public static final RegistryObject<BlockEntityType<FissionIrradiator>> IRRADIATOR_ENTITY =
            NCRegistries.BLOCK_ENTITIES.register("fission_irradiator", () ->
                    BlockEntityType.Builder.of(FissionIrradiator::new, IRRADIATOR.get()).build(null));
    public static final RegistryObject<BlockEntityType<FissionPortBlockEntity>> IRRADIATOR_PORT_ENTITY =
            NCRegistries.BLOCK_ENTITIES.register("fission_irradiator_port", () ->
                    BlockEntityType.Builder.of(FissionPortBlockEntity::new, IRRADIATOR_PORT.get()).build(null));
    public static final RegistryObject<MenuType<FissionIrradiatorMenu>> IRRADIATOR_MENU =
            irradiatorMenu(false);
    public static final RegistryObject<MenuType<FissionIrradiatorMenu>> IRRADIATOR_PORT_MENU =
            irradiatorMenu(true);
    public static final RegistryObject<RecipeType<FissionIrradiatorRecipe>> IRRADIATION =
            NCRegistries.RECIPE_TYPES.register("fission_irradiator", () -> RecipeType.simple(NuclearCraft.id("fission_irradiator")));
    public static final RegistryObject<RecipeSerializer<FissionIrradiatorRecipe>> IRRADIATOR_SERIALIZER =
            NCRegistries.RECIPE_SERIALIZERS.register("fission_irradiator", FissionIrradiatorRecipe.Serializer::new);

    public static final RegistryObject<FissionFuelCellBlock> CELL = part("solid_fission_cell",
            () -> new FissionFuelCellBlock(properties()));
    public static final RegistryObject<SolidHeatSinkBlock> WATER_SINK = SINKS.get(NCHeatSinks.WATER);
    public static final RegistryObject<FissionPartBlock> HEAVY_WATER_MODERATOR = part("heavy_water_moderator", Role.MODERATOR);
    public static final RegistryObject<FissionPartBlock> BERYLLIUM_CARBON_REFLECTOR =
            part("beryllium_carbon_reflector", Role.REFLECTOR);
    public static final RegistryObject<FissionPartBlock> LEAD_STEEL_REFLECTOR =
            part("lead_steel_reflector", Role.REFLECTOR);

    public static final RegistryObject<FissionSourceBlock> RADIUM_BERYLLIUM_SOURCE =
            source("fission_source_radium_beryllium", RADIUM_EFFICIENCY);
    public static final RegistryObject<FissionSourceBlock> POLONIUM_BERYLLIUM_SOURCE =
            source("fission_source_polonium_beryllium", POLONIUM_EFFICIENCY);
    public static final RegistryObject<FissionSourceBlock> CALIFORNIUM_SOURCE =
            source("fission_source_californium", CALIFORNIUM_EFFICIENCY);

    public static final RegistryObject<FissionManagerBlock> SOURCE_MANAGER = part("fission_source_manager",
            () -> new FissionManagerBlock(properties(), Role.SOURCE_MANAGER));
    public static final RegistryObject<FissionManagerBlock> SHIELD_MANAGER = part("fission_shield_manager",
            () -> new FissionManagerBlock(properties(), Role.SHIELD_MANAGER));
    public static final RegistryObject<FissionShieldBlock> BORON_SILVER_SHIELD = part("boron_silver_shield",
            () -> new FissionShieldBlock(properties(), () -> configured(BORON_SILVER_HEAT),
                    () -> configured(BORON_SILVER_EFFICIENCY)));
    public static final RegistryObject<FissionShieldBlock> GADOLINIUM_SAMARIUM_SHIELD = part("gadolinium_samarium_shield",
            () -> new FissionShieldBlock(properties(), () -> configured(GADOLINIUM_SAMARIUM_HEAT),
                    () -> configured(GADOLINIUM_SAMARIUM_EFFICIENCY)));

    private static final RegistryObject<BlockEntityType<FissionControlBlockEntity>> SOURCE_ENTITY =
            NCRegistries.BLOCK_ENTITIES.register("fission_source", () -> BlockEntityType.Builder.of(
                    FissionControlBlockEntity::new, RADIUM_BERYLLIUM_SOURCE.get(), POLONIUM_BERYLLIUM_SOURCE.get(),
                    CALIFORNIUM_SOURCE.get()).build(null));
    private static final RegistryObject<BlockEntityType<FissionControlBlockEntity>> SHIELD_ENTITY =
            NCRegistries.BLOCK_ENTITIES.register("fission_shield", () -> BlockEntityType.Builder.of(
                    FissionControlBlockEntity::new, BORON_SILVER_SHIELD.get(), GADOLINIUM_SAMARIUM_SHIELD.get()).build(null));
    private static final RegistryObject<BlockEntityType<FissionControlBlockEntity>> SOURCE_MANAGER_ENTITY =
            NCRegistries.BLOCK_ENTITIES.register("fission_source_manager", () -> BlockEntityType.Builder.of(
                    FissionControlBlockEntity::new, SOURCE_MANAGER.get()).build(null));
    private static final RegistryObject<BlockEntityType<FissionControlBlockEntity>> SHIELD_MANAGER_ENTITY =
            NCRegistries.BLOCK_ENTITIES.register("fission_shield_manager", () -> BlockEntityType.Builder.of(
                    FissionControlBlockEntity::new, SHIELD_MANAGER.get()).build(null));

    public static BlockEntityType<FissionControlBlockEntity> controlEntity(Role role) {
        return switch (role) {
            case SOURCE -> SOURCE_ENTITY.get();
            case SHIELD -> SHIELD_ENTITY.get();
            case SOURCE_MANAGER -> SOURCE_MANAGER_ENTITY.get();
            case SHIELD_MANAGER -> SHIELD_MANAGER_ENTITY.get();
            default -> throw new IllegalArgumentException("Unexpected fission control role " + role);
        };
    }

    public static final RegistryObject<Item> LECF_251_OXIDE =
            NCRegistries.ITEMS.register("lecf_251_oxide", () -> new Item(new Item.Properties()));
    public static final RegistryObject<Item> DEPLETED_LECF_251_OXIDE =
            NCRegistries.ITEMS.register("depleted_lecf_251_oxide", () -> new Item(new Item.Properties()));

    public static final RegistryObject<BlockEntityType<SolidFissionController>> CONTROLLER_ENTITY =
            NCRegistries.BLOCK_ENTITIES.register("solid_fission_controller", () ->
                    BlockEntityType.Builder.of(SolidFissionController::new, CONTROLLER.get()).build(null));
    public static final RegistryObject<BlockEntityType<FissionPortBlockEntity>> PORT_ENTITY =
            NCRegistries.BLOCK_ENTITIES.register("fission_port", () ->
                    BlockEntityType.Builder.of(FissionPortBlockEntity::new, portBlocks()).build(null));
    public static final RegistryObject<BlockEntityType<FissionFuelCell>> CELL_ENTITY =
            NCRegistries.BLOCK_ENTITIES.register("solid_fission_cell", () ->
                    BlockEntityType.Builder.of(FissionFuelCell::new, CELL.get()).build(null));

    public static final RegistryObject<MenuType<SolidFissionMenu>> MENU =
            NCRegistries.MENUS.register("solid_fission_controller", () ->
                    new MenuType<>(SolidFissionMenu::new, FeatureFlags.DEFAULT_FLAGS));
    public static final RegistryObject<RecipeType<SolidFissionRecipe>> FUEL_TYPE =
            NCRegistries.RECIPE_TYPES.register("solid_fission", () -> RecipeType.simple(NuclearCraft.id("solid_fission")));
    public static final RegistryObject<RecipeSerializer<SolidFissionRecipe>> FUEL_SERIALIZER =
            NCRegistries.RECIPE_SERIALIZERS.register("solid_fission", SolidFissionRecipe.Serializer::new);

    public static final RegistryObject<CreativeModeTab> TAB = NCRegistries.TABS.register("fission", () ->
            CreativeModeTab.builder().title(Component.translatable("itemGroup.nuclearcraft.fission"))
                    .icon(() -> CONTROLLER.get().asItem().getDefaultInstance())
                    .displayItems((parameters, output) -> {
                        output.accept(CASING.get());
                        output.accept(CONTROLLER.get());
                        output.accept(CELL.get());
                        output.accept(CELL_PORT.get());
                        output.accept(IRRADIATOR.get());
                        output.accept(IRRADIATOR_PORT.get());
                        output.accept(VENT.get());
                        for (NCHeatSinks sink : NCHeatSinks.values()) {
                            output.accept(heatSink(sink));
                        }
                        output.accept(HEAVY_WATER_MODERATOR.get());
                        output.accept(BERYLLIUM_CARBON_REFLECTOR.get());
                        output.accept(LEAD_STEEL_REFLECTOR.get());
                        output.accept(RADIUM_BERYLLIUM_SOURCE.get());
                        output.accept(POLONIUM_BERYLLIUM_SOURCE.get());
                        output.accept(CALIFORNIUM_SOURCE.get());
                        output.accept(SOURCE_MANAGER.get());
                        output.accept(SHIELD_MANAGER.get());
                        output.accept(BORON_SILVER_SHIELD.get());
                        output.accept(GADOLINIUM_SAMARIUM_SHIELD.get());
                        output.accept(LECF_251_OXIDE.get());
                        output.accept(DEPLETED_LECF_251_OXIDE.get());
                        NCFissionFuels.displayItems(output);
                        NCPebbleFission.displayItems(output);
                        NCSaltFission.displayItems(output);
                        output.accept(NCFluids.HIGH_PRESSURE_STEAM.bucket().get());
                    }).build());

    private static RegistryObject<FissionPartBlock> part(String id, Role role) {
        return part(id, () -> new FissionPartBlock(properties(), role));
    }

    static RegistryObject<FissionWallBlock> wall(String id, Role role) {
        return part(id, () -> new FissionWallBlock(properties(), role));
    }

    private static RegistryObject<FissionSourceBlock> source(String id, ForgeConfigSpec.DoubleValue efficiency) {
        DoubleSupplier multiplier = () -> configured(efficiency);
        return part(id, () -> new FissionSourceBlock(properties(), multiplier));
    }

    public static SolidHeatSinkBlock heatSink(NCHeatSinks material) { return SINKS.get(material).get(); }
    public static int cooling(NCHeatSinks material) { return configured(COOLING.get(material)); }
    public static FissionPlacementRules<NCHeatSinks> placementRules() { return PLACEMENT.rules(); }

    public static @Nullable Moderator moderator(BlockState state) {
        if (state.is(GRAPHITE_BLOCKS)) {
            return GRAPHITE;
        }
        if (state.is(BERYLLIUM_BLOCKS)) {
            return BERYLLIUM;
        }
        return state.is(HEAVY_WATER_MODERATOR.get()) ? HEAVY_WATER : null;
    }

    public static @Nullable Reflector reflector(BlockState state) {
        if (state.is(BERYLLIUM_CARBON_REFLECTOR.get())) {
            return BERYLLIUM_CARBON;
        }
        return state.is(LEAD_STEEL_REFLECTOR.get()) ? LEAD_STEEL : null;
    }

    public static int neutronReach() {
        return configured(NEUTRON_REACH);
    }

    public static double intrinsicFluxEfficiency() {
        return configured(INTRINSIC_FLUX_EFFICIENCY);
    }

    private static TagKey<Block> storageBlocks(String material) {
        return TagKey.create(Registries.BLOCK, ResourceLocation.tryBuild("forge", "storage_blocks/" + material));
    }

    private static Moderator moderator(ForgeConfigSpec.Builder builder, String name, int flux, double efficiency) {
        builder.push(name);
        var rule = new Moderator(builder.defineInRange("flux", flux, 0, 32_767),
                builder.defineInRange("efficiency", efficiency, 0D, 255D));
        builder.pop();
        return rule;
    }

    private static Reflector reflector(ForgeConfigSpec.Builder builder, String name, double reflectivity, double efficiency) {
        builder.push(name);
        var rule = new Reflector(builder.defineInRange("reflectivity", reflectivity, 0D, 1D),
                builder.defineInRange("efficiency", efficiency, 0D, 255D));
        builder.pop();
        return rule;
    }

    private static <T> T configured(ForgeConfigSpec.ConfigValue<T> value) {
        return SPEC.isLoaded() ? value.get() : value.getDefault();
    }

    public record Moderator(ForgeConfigSpec.IntValue fluxValue, ForgeConfigSpec.DoubleValue efficiencyValue) {
        public int flux() { return configured(fluxValue); }
        public double efficiency() { return configured(efficiencyValue); }
    }

    public record Reflector(ForgeConfigSpec.DoubleValue reflectivityValue, ForgeConfigSpec.DoubleValue efficiencyValue) {
        public double reflectivity() { return configured(reflectivityValue); }
        public double efficiency() { return configured(efficiencyValue); }
    }

    static <T extends Block> RegistryObject<T> part(String id, Supplier<T> supplier) {
        RegistryObject<T> block = NCRegistries.BLOCKS.register(id, supplier);
        NCRegistries.ITEMS.register(id, () -> new BlockItem(block.get(), new Item.Properties()));
        return block;
    }

    static BlockBehaviour.Properties properties() {
        return BlockBehaviour.Properties.copy(Blocks.IRON_BLOCK);
    }

    private static RegistryObject<MenuType<FissionIrradiatorMenu>> irradiatorMenu(boolean port) {
        return NCRegistries.MENUS.register(port ? "fission_irradiator_port" : "fission_irradiator",
                () -> new MenuType<>((id, inventory) -> new FissionIrradiatorMenu(id, inventory, port), FeatureFlags.DEFAULT_FLAGS));
    }

    public static double irradiatorHeat(Material material) {
        return configured(IRRADIATOR_HEAT.get(material));
    }

    public static double irradiatorEfficiency(Material material) {
        return configured(IRRADIATOR_EFFICIENCY.get(material));
    }

    public static long maxModeratorLineFlux() {
        return Math.max(1L, (long) neutronReach() * Math.max(GRAPHITE.flux(), Math.max(BERYLLIUM.flux(), HEAVY_WATER.flux())));
    }

    private NCFission() {}

    static void register() {
        NCFissionFuels.register();
    }

    private static Block[] portBlocks() {
        List<Block> blocks = new ArrayList<>(List.of(CELL_PORT.get(), VENT.get(), NCPebbleFission.CHAMBER_PORT.get(), NCSaltFission.VESSEL_PORT.get()));
        for (NCGasCoolants gas : NCGasCoolants.values()) {
            blocks.add(NCPebbleFission.port(gas));
        }
        for (NCSaltCoolants coolant : NCSaltCoolants.values()) {
            blocks.add(NCSaltFission.port(coolant));
        }
        return blocks.toArray(Block[]::new);
    }

    public static double meltdownRadiationMultiplier() {
        return configured(MELTDOWN_RADIATION);
    }

    public static void registerConfig(FMLJavaModLoadingContext context) {
        PLACEMENT.register(context, SPEC, "nuclearcraft-fission.toml");
    }
}
