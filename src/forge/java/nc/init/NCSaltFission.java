package nc.init;

import nc.NuclearCraft;
import nc.multiblock.fission.FissionPlacementRules;
import nc.multiblock.fission.FissionPartBlock.Role;
import nc.multiblock.fission.FissionVariant;
import nc.multiblock.fission.FissionWallBlock;
import nc.multiblock.fission.SaltFissionPartMenu;
import nc.multiblock.fission.SaltHeater;
import nc.multiblock.fission.SaltHeaterBlock;
import nc.multiblock.fission.SaltVessel;
import nc.multiblock.fission.SaltVesselBlock;
import nc.multiblock.fission.SolidFissionController;
import nc.multiblock.fission.SolidFissionMenu;
import nc.recipe.SaltFissionRecipe;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.RegistryObject;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public final class NCSaltFission {
    private static final Map<NCSaltCoolants, RegistryObject<SaltHeaterBlock>> HEATERS = new EnumMap<>(NCSaltCoolants.class);
    private static final Map<NCSaltCoolants, RegistryObject<FissionWallBlock>> PORTS = new EnumMap<>(NCSaltCoolants.class);
    private static final Map<NCSaltCoolants, ForgeConfigSpec.IntValue> COOLING = new EnumMap<>(NCSaltCoolants.class);
    private static final Map<SaltFissionPartMenu.Kind, RegistryObject<MenuType<SaltFissionPartMenu>>> PART_MENUS =
            new EnumMap<>(SaltFissionPartMenu.Kind.class);
    private static final CoolingPlacementConfig<NCSaltCoolants> PLACEMENT;
    private static final ForgeConfigSpec SPEC;
    private static final ForgeConfigSpec.DoubleValue INTRINSIC_EFFICIENCY;
    private static final ForgeConfigSpec.DoubleValue TIME_MULTIPLIER;
    private static final ForgeConfigSpec.DoubleValue HEAT_MULTIPLIER;
    private static final ForgeConfigSpec.DoubleValue EFFICIENCY_MULTIPLIER;
    private static final ForgeConfigSpec.DoubleValue EMERGENCY_MULTIPLIER;

    static {
        var builder = new ForgeConfigSpec.Builder();
        INTRINSIC_EFFICIENCY = builder.defineInRange("intrinsic_flux_efficiency", 1D, 0D, 255D);
        TIME_MULTIPLIER = builder.defineInRange("fuel_time_multiplier", 1D, 0.001D, 255D);
        HEAT_MULTIPLIER = builder.defineInRange("fuel_heat_multiplier", 1D, 0D, 255D);
        EFFICIENCY_MULTIPLIER = builder.defineInRange("fuel_efficiency_multiplier", 1D, 0D, 255D);
        EMERGENCY_MULTIPLIER = builder.defineInRange("emergency_cooling_multiplier", 1D, 0.001D, 255D);
        int[] rates = {55, 50, 85, 80, 70, 105, 90, 100, 110, 115, 145, 65, 95, 200, 195, 75,
                120, 60, 160, 130, 125, 150, 175, 170, 165, 180, 140, 135, 185, 190, 155, 205};
        builder.push("cooling_rate");
        for (var coolant : NCSaltCoolants.values()) {
            String material = coolant.material();
            COOLING.put(coolant, builder.defineInRange(material, rates[coolant.ordinal()], 0, 32767));
            HEATERS.put(coolant, NCFission.part(material + "_coolant_heater",
                    () -> new SaltHeaterBlock(NCFission.properties(), coolant)));
            PORTS.put(coolant, NCFission.part("fission_heater_port_" + material,
                    () -> FissionWallBlock.heaterPort(NCFission.properties(), coolant)));
        }
        builder.pop();
        PLACEMENT = new CoolingPlacementConfig<>(builder, List.of(NCSaltCoolants.values()),
                FissionPlacementRules.Part.HEATER, NCSaltCoolants::material, new String[] {
                    "one vessel", "one moderator", "one vessel && one moderator",
                    "one redstone heater", "two axial glowstone heaters", "one obsidian heater",
                    "two moderators", "one vessel && one casing", "exactly two iron heaters",
                    "two standard heaters", "exactly one standard heater && two lead heaters", "one reflector",
                    "one reflector && one iron heater", "one vessel && one gold heater", "one moderator && one prismarine heater",
                    "one standard heater", "two axial lapis heaters", "one iron heater",
                    "exactly one quartz heater && one casing", "exactly two axial lead heaters && one casing", "exactly one moderator && one casing",
                    "two vessels", "one quartz heater && one lapis heater", "two glowstone heaters && one tin heater",
                    "one gold heater && one prismarine heater", "one redstone heater && one end_stone heater", "one end_stone heater && one copper heater",
                    "two axial reflectors", "two copper heaters && one purpur heater", "exactly two redstone heaters",
                    "three moderators", "three vessels"
                });
        SPEC = builder.build();
        for (var kind : SaltFissionPartMenu.Kind.values()) {
            PART_MENUS.put(kind, NCRegistries.MENUS.register(kind.id(),
                    () -> new MenuType<>((id, inventory) -> new SaltFissionPartMenu(id, inventory, kind), FeatureFlags.DEFAULT_FLAGS)));
        }
    }

    public static final RegistryObject<FissionWallBlock> CONTROLLER = NCFission.wall("salt_fission_controller", Role.SALT_CONTROLLER);
    public static final RegistryObject<FissionWallBlock> VESSEL_PORT = NCFission.wall("fission_vessel_port", Role.VESSEL_PORT);
    public static final RegistryObject<SaltVesselBlock> VESSEL = NCFission.part("salt_fission_vessel",
            () -> new SaltVesselBlock(NCFission.properties()));
    public static final RegistryObject<BlockEntityType<SolidFissionController>> CONTROLLER_ENTITY =
            NCRegistries.BLOCK_ENTITIES.register("salt_fission_controller", () ->
                    BlockEntityType.Builder.of(SolidFissionController::new, CONTROLLER.get()).build(null));
    public static final RegistryObject<BlockEntityType<SaltVessel>> VESSEL_ENTITY =
            NCRegistries.BLOCK_ENTITIES.register("salt_fission_vessel", () ->
                    BlockEntityType.Builder.of(SaltVessel::new, VESSEL.get()).build(null));
    public static final RegistryObject<BlockEntityType<SaltHeater>> HEATER_ENTITY =
            NCRegistries.BLOCK_ENTITIES.register("salt_fission_heater", () ->
                    BlockEntityType.Builder.of(SaltHeater::new,
                            HEATERS.values().stream().map(RegistryObject::get).toArray(Block[]::new)).build(null));
    public static final RegistryObject<MenuType<SolidFissionMenu>> MENU = NCRegistries.MENUS.register("salt_fission_controller",
            () -> new MenuType<>((id, player) -> new SolidFissionMenu(id, player, FissionVariant.SALT), FeatureFlags.DEFAULT_FLAGS));
    public static final RegistryObject<RecipeType<SaltFissionRecipe>> FUEL_TYPE =
            NCRegistries.RECIPE_TYPES.register("salt_fission", () -> RecipeType.simple(NuclearCraft.id("salt_fission")));
    public static final RegistryObject<RecipeSerializer<SaltFissionRecipe>> FUEL_SERIALIZER =
            NCRegistries.RECIPE_SERIALIZERS.register("salt_fission", SaltFissionRecipe.Serializer::new);

    private NCSaltFission() {}

    public static void registerConfig(FMLJavaModLoadingContext context) {
        PLACEMENT.register(context, SPEC, "nuclearcraft-salt-fission.toml");
    }

    static void displayItems(CreativeModeTab.Output output) {
        output.accept(CONTROLLER.get());
        output.accept(VESSEL.get());
        output.accept(VESSEL_PORT.get());
        for (NCSaltCoolants coolant : NCSaltCoolants.values()) {
            output.accept(heater(coolant));
            output.accept(port(coolant));
        }
    }

    public static FissionPlacementRules<NCSaltCoolants> placementRules() { return PLACEMENT.rules(); }
    public static MenuType<SaltFissionPartMenu> partMenu(SaltFissionPartMenu.Kind kind) { return PART_MENUS.get(kind).get(); }
    public static SaltHeaterBlock heater(NCSaltCoolants coolant) { return HEATERS.get(coolant).get(); }
    public static FissionWallBlock port(NCSaltCoolants coolant) { return PORTS.get(coolant).get(); }
    public static int cooling(NCSaltCoolants coolant) { return value(COOLING.get(coolant)); }
    public static double intrinsicEfficiency() { return value(INTRINSIC_EFFICIENCY); }
    public static double timeMultiplier() { return value(TIME_MULTIPLIER); }
    public static double heatMultiplier() { return value(HEAT_MULTIPLIER); }
    public static double efficiencyMultiplier() { return value(EFFICIENCY_MULTIPLIER); }
    public static double emergencyMultiplier() { return value(EMERGENCY_MULTIPLIER); }

    private static <T> T value(ForgeConfigSpec.ConfigValue<T> value) {
        return SPEC.isLoaded() ? value.get() : value.getDefault();
    }
}
