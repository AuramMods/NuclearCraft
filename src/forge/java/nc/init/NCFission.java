package nc.init;

import nc.NuclearCraft;
import nc.multiblock.fission.FissionConductor;
import nc.multiblock.fission.FissionConductorBlock;
import nc.multiblock.fission.FissionControlBlockEntity;
import nc.multiblock.fission.FissionDecaySettings;
import nc.multiblock.fission.FissionFuelCell;
import nc.multiblock.fission.FissionGlassBlock;
import nc.multiblock.fission.FissionMonitor;
import nc.multiblock.fission.FissionIrradiator;
import nc.multiblock.fission.FissionIrradiatorBlock;
import nc.multiblock.fission.FissionIrradiatorMenu;
import nc.multiblock.fission.FissionManagerBlock;
import nc.multiblock.fission.FissionShieldBlock;
import nc.multiblock.fission.FissionFuelCellBlock;
import nc.multiblock.fission.FissionPlacementRules;
import nc.multiblock.fission.FissionPartBlock;
import nc.multiblock.fission.FissionPartBlock.Role;
import nc.multiblock.fission.FissionPartMenu;
import nc.multiblock.fission.FissionPortBlockEntity;
import nc.multiblock.fission.FissionSourceBlock;
import nc.multiblock.fission.FissionWallBlock;
import nc.multiblock.fission.SolidHeatSinkBlock;
import nc.multiblock.fission.SolidFissionController;
import nc.multiblock.fission.SolidFissionMenu;
import nc.recipe.FissionHeatRecipe;
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
import net.minecraftforge.fml.event.config.ModConfigEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.RegistryObject;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.DoubleSupplier;
import java.util.function.Supplier;

public final class NCFission {
    public static final int MAX_CONFIGURED_SIZE = 255;
    private static final AtomicLong CONFIG_REVISION = new AtomicLong();
    private static final ForgeConfigSpec.IntValue MIN_SIZE;
    private static final ForgeConfigSpec.IntValue MAX_SIZE;
    private static final ForgeConfigSpec.DoubleValue FUEL_TIME;
    private static final ForgeConfigSpec.DoubleValue FUEL_HEAT;
    private static final ForgeConfigSpec.DoubleValue FUEL_EFFICIENCY;
    private static final ForgeConfigSpec.DoubleValue FUEL_RADIATION;
    private static final LegacyFissionFuelConfig LEGACY_FUEL;
    private static final ForgeConfigSpec.IntValue COOLING_LENIENCY;
    private static final ForgeConfigSpec.ConfigValue<List<? extends Number>> SPARSITY;
    private static final ForgeConfigSpec.ConfigValue<List<Boolean>> DISSIPATION;
    private static final ForgeConfigSpec.DoubleValue DISSIPATION_RATE;
    private static final ForgeConfigSpec.DoubleValue EMERGENCY_COOLING;
    private static final ForgeConfigSpec.DoubleValue HEATING_GAS;
    private static final ForgeConfigSpec.DoubleValue HEATING_NAK;
    private static final ForgeConfigSpec.DoubleValue SOUND_VOLUME;
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
    private static final ForgeConfigSpec.BooleanValue OVERHEAT;
    private static final ForgeConfigSpec.BooleanValue HEAT_DAMAGE;
    private static final ForgeConfigSpec.BooleanValue FUEL_DECAY;
    private static final ForgeConfigSpec.ConfigValue<List<? extends Number>> DECAY_BUILDUP;
    private static final ForgeConfigSpec.ConfigValue<List<? extends Number>> DECAY_LIFETIME;
    private static final ForgeConfigSpec.ConfigValue<List<? extends Number>> DECAY_EQUILIBRIUM;
    private static final ForgeConfigSpec.ConfigValue<List<? extends Number>> DECAY_DAUGHTERS;
    private static final ForgeConfigSpec.ConfigValue<List<? extends Number>> DECAY_TERMS;
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
        MIN_SIZE = builder.defineInRange("fission_min_size", 1, 1, MAX_CONFIGURED_SIZE);
        MAX_SIZE = builder.defineInRange("fission_max_size", 24, 3, MAX_CONFIGURED_SIZE);
        FUEL_TIME = builder.defineInRange("fission_fuel_time_multiplier", 1D, 0.001D, 255D);
        FUEL_HEAT = builder.defineInRange("fission_fuel_heat_multiplier", 1D, 0D, 255D);
        FUEL_EFFICIENCY = builder.defineInRange("fission_fuel_efficiency_multiplier", 1D, 0D, 255D);
        FUEL_RADIATION = builder.defineInRange("fission_fuel_radiation_multiplier", 1D, 0D, 255D);
        LEGACY_FUEL = new LegacyFissionFuelConfig(builder);
        COOLING_LENIENCY = builder.defineInRange("fission_cooling_efficiency_leniency", 10, 0, 32_767);
        SPARSITY = builder.comment("Minimum efficiency followed by the useful-part density for full efficiency.")
                .define("fission_sparsity_penalty_params", List.of(0.5D, 0.75D),
                        entry -> entry instanceof List<?> values && values.size() == 2 && values.stream().allMatch(
                                value -> value instanceof Number number && number.doubleValue() >= 0D && number.doubleValue() <= 1D));
        DISSIPATION = builder.comment("Passive casing heat dissipation while switched off, then while switched on.")
                .define("fission_heat_dissipation", List.of(true, false),
                        entry -> entry instanceof List<?> values && values.size() == 2 && values.stream().allMatch(Boolean.class::isInstance));
        DISSIPATION_RATE = builder.defineInRange("fission_heat_dissipation_rate", 0D, 0D, 1D);
        EMERGENCY_COOLING = builder.defineInRange("fission_emergency_cooling_multiplier", 1D, 0.001D, 255D);
        HEATING_GAS = builder.defineInRange("fission_heating_gas_coolant_heat_mult", 1.5D, 0.001D, Integer.MAX_VALUE);
        HEATING_NAK = builder.defineInRange("fission_heating_nak_coolant_heat_mult", 2D, 0.001D, Integer.MAX_VALUE);
        SOUND_VOLUME = builder.defineInRange("fission_sound_volume", 1D, 0D, 15D);
        OVERHEAT = builder.define("fission_overheat", true);
        HEAT_DAMAGE = builder.define("fission_heat_damage", false);
        FUEL_DECAY = builder.define("fission_decay_mechanics", false);
        DECAY_BUILDUP = decayList(builder, "fission_decay_build_up_times", Integer.MAX_VALUE, List.of(24_000D, 24_000D, 24_000D));
        DECAY_LIFETIME = decayList(builder, "fission_decay_lifetimes", Integer.MAX_VALUE, List.of(6_000D, 8_000D, 12_000D));
        DECAY_EQUILIBRIUM = decayList(builder, "fission_decay_equilibrium_factors", 255D, List.of(1D, 5D, 1D));
        DECAY_DAUGHTERS = decayList(builder, "fission_decay_daughter_multipliers", 255D, List.of(5D, 5D));
        DECAY_TERMS = decayList(builder, "fission_decay_term_multipliers", 1D, List.of(0.95D, 0.05D));
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
    public static final RegistryObject<FissionGlassBlock> GLASS = part("fission_glass",
            () -> new FissionGlassBlock(properties().destroyTime(1.5F).noOcclusion()
                    .isViewBlocking((state, level, pos) -> false).isSuffocating((state, level, pos) -> false)));
    public static final RegistryObject<FissionWallBlock> MONITOR = wall("fission_monitor", Role.MONITOR);
    public static final RegistryObject<BlockEntityType<FissionMonitor>> MONITOR_ENTITY =
            NCRegistries.BLOCK_ENTITIES.register("fission_monitor", () ->
                    BlockEntityType.Builder.of(FissionMonitor::new, MONITOR.get()).build(null));
    public static final RegistryObject<FissionWallBlock> POWER_PORT = wall("fission_power_port", Role.POWER_PORT);
    public static final RegistryObject<BlockEntityType<FissionPortBlockEntity>> POWER_PORT_ENTITY =
            NCRegistries.BLOCK_ENTITIES.register("fission_power_port", () ->
                    BlockEntityType.Builder.of(FissionPortBlockEntity::new, POWER_PORT.get()).build(null));
    public static final RegistryObject<FissionConductorBlock> CONDUCTOR = part("fission_conductor",
            () -> new FissionConductorBlock(properties()));
    public static final RegistryObject<BlockEntityType<FissionConductor>> CONDUCTOR_ENTITY =
            NCRegistries.BLOCK_ENTITIES.register("fission_conductor", () ->
                    BlockEntityType.Builder.of(FissionConductor::new, CONDUCTOR.get()).build(null));
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
    private static final Map<FissionPartMenu.Kind, RegistryObject<MenuType<FissionPartMenu>>> PART_MENUS =
            new EnumMap<>(FissionPartMenu.Kind.class);

    static {
        for (var kind : FissionPartMenu.Kind.values()) {
            PART_MENUS.put(kind, NCRegistries.MENUS.register(kind.id(),
                    () -> new MenuType<>((id, inventory) -> new FissionPartMenu(id, inventory, kind), FeatureFlags.DEFAULT_FLAGS)));
        }
    }

    public static MenuType<FissionPartMenu> partMenu(FissionPartMenu.Kind kind) { return PART_MENUS.get(kind).get(); }

    public static final RegistryObject<RecipeType<SolidFissionRecipe>> FUEL_TYPE =
            NCRegistries.RECIPE_TYPES.register("solid_fission", () -> RecipeType.simple(NuclearCraft.id("solid_fission")));
    public static final RegistryObject<RecipeSerializer<SolidFissionRecipe>> FUEL_SERIALIZER =
            NCRegistries.RECIPE_SERIALIZERS.register("solid_fission", SolidFissionRecipe.Serializer::new);

    public static final RegistryObject<RecipeType<FissionHeatRecipe>> HEATING_TYPE =
            NCRegistries.RECIPE_TYPES.register("fission_heating",
                    () -> RecipeType.simple(NuclearCraft.id("fission_heating")));
    public static final RegistryObject<RecipeSerializer<FissionHeatRecipe>> HEATING_SERIALIZER =
            NCRegistries.RECIPE_SERIALIZERS.register("fission_heating", () -> new FissionHeatRecipe.Serializer(false));
    public static final RegistryObject<RecipeType<FissionHeatRecipe>> EMERGENCY_COOLING_TYPE =
            NCRegistries.RECIPE_TYPES.register("fission_emergency_cooling",
                    () -> RecipeType.simple(NuclearCraft.id("fission_emergency_cooling")));
    public static final RegistryObject<RecipeSerializer<FissionHeatRecipe>> EMERGENCY_COOLING_SERIALIZER =
            NCRegistries.RECIPE_SERIALIZERS.register("fission_emergency_cooling", () -> new FissionHeatRecipe.Serializer(true));

    public static final RegistryObject<CreativeModeTab> TAB = NCRegistries.TABS.register("fission", () ->
            CreativeModeTab.builder().title(Component.translatable("itemGroup.nuclearcraft.fission"))
                    .icon(() -> CONTROLLER.get().asItem().getDefaultInstance())
                    .displayItems((parameters, output) -> {
                        output.accept(CASING.get());
                        output.accept(GLASS.get());
                        output.accept(MONITOR.get());
                        output.accept(POWER_PORT.get());
                        output.accept(CONDUCTOR.get());
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

    public static int minSize() { return configured(MIN_SIZE); }
    public static int maxSize() { return configured(MAX_SIZE); }
    public static int searchLength() { return maxSize() + 2; }
    public static long configRevision() { return CONFIG_REVISION.get(); }
    public static double fuelTimeMultiplier() { return configured(FUEL_TIME); }
    public static double fuelHeatMultiplier() { return configured(FUEL_HEAT); }
    public static double fuelEfficiencyMultiplier() { return configured(FUEL_EFFICIENCY); }
    public static double fuelRadiationMultiplier() { return configured(FUEL_RADIATION); }
    static @Nullable Object legacyFuelValue(LegacyFissionFuelConfig.Selection selection, LegacyFissionFuelConfig.Setting setting) {
        return SPEC.isLoaded() ? LEGACY_FUEL.value(selection, setting) : null;
    }

    public static int coolingLeniency() { return configured(COOLING_LENIENCY); }
    public static double emergencyCoolingMultiplier() { return configured(EMERGENCY_COOLING); }
    public static double heatingGasMultiplier() { return configured(HEATING_GAS); }
    public static double heatingNakMultiplier() { return configured(HEATING_NAK); }
    public static double soundVolume() { return configured(SOUND_VOLUME); }

    public static double sparsityEfficiency(double density) {
        var values = configured(SPARSITY);
        double floor = values.get(0).doubleValue();
        double threshold = values.get(1).doubleValue();
        return density >= threshold ? 1D : floor + (1D - floor) * Math.sin(density * Math.PI / (2D * threshold));
    }

    public static long casingHeatLost(long heat, int surface, boolean requested) {
        if (heat <= 0 || !configured(DISSIPATION).get(requested ? 1 : 0)) {
            return 0L;
        }
        return Math.min(heat, Math.max(1L, (long) (configured(DISSIPATION_RATE) * heat * surface)));
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

    public static boolean overheats() {
        return configured(OVERHEAT);
    }

    public static boolean heatDamage() {
        return configured(HEAT_DAMAGE);
    }

    private static ForgeConfigSpec.ConfigValue<List<? extends Number>> decayList(ForgeConfigSpec.Builder builder,
            String key, double max, List<? extends Number> defaults) {
        return builder.defineList(key, defaults, value -> value instanceof Number number
                && number.doubleValue() >= 0 && number.doubleValue() <= max);
    }

    private static double decayValue(ForgeConfigSpec.ConfigValue<List<? extends Number>> value, int index) {
        var configured = configured(value);
        return (index < configured.size() ? configured : value.getDefault()).get(index).doubleValue();
    }

    private static FissionDecaySettings.Step decayStep(int index, double daughterMultiplier) {
        return new FissionDecaySettings.Step(decayValue(DECAY_BUILDUP, index), decayValue(DECAY_LIFETIME, index),
                decayValue(DECAY_EQUILIBRIUM, index), daughterMultiplier);
    }

    public static boolean fuelDecayEnabled() {
        return configured(FUEL_DECAY);
    }

    public static @Nullable FissionDecaySettings decaySettings() {
        if (!fuelDecayEnabled()) {
            return null;
        }
        return new FissionDecaySettings(decayStep(0, decayValue(DECAY_DAUGHTERS, 0)),
                decayStep(1, decayValue(DECAY_DAUGHTERS, 1)), decayStep(2, 0),
                decayValue(DECAY_TERMS, 0), decayValue(DECAY_TERMS, 1));
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
        context.getModEventBus().addListener((ModConfigEvent.Reloading event) -> {
            if (event.getConfig().getSpec() == SPEC) {
                CONFIG_REVISION.incrementAndGet();
            }
        });
        PLACEMENT.register(context, SPEC, "nuclearcraft-fission.toml");
    }
}
