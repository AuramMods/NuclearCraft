package nc.init;

import nc.NuclearCraft;
import nc.multiblock.fission.FissionFuelCell;
import nc.multiblock.fission.FissionFuelCellBlock;
import nc.multiblock.fission.FissionPartBlock;
import nc.multiblock.fission.FissionPartBlock.Role;
import nc.multiblock.fission.FissionPortBlockEntity;
import nc.multiblock.fission.FissionSourceBlock;
import nc.multiblock.fission.FissionWallBlock;
import nc.multiblock.fission.SolidFissionController;
import nc.multiblock.fission.SolidFissionMenu;
import nc.recipe.SolidFissionRecipe;
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
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.registries.RegistryObject;
import org.jetbrains.annotations.Nullable;

import java.util.function.DoubleSupplier;
import java.util.function.Supplier;

public final class NCFission {
    private static final ForgeConfigSpec SPEC;
    private static final ForgeConfigSpec.DoubleValue RADIUM_EFFICIENCY;
    private static final ForgeConfigSpec.DoubleValue POLONIUM_EFFICIENCY;
    private static final ForgeConfigSpec.DoubleValue CALIFORNIUM_EFFICIENCY;
    private static final ForgeConfigSpec.IntValue NEUTRON_REACH;
    private static final ForgeConfigSpec.DoubleValue INTRINSIC_FLUX_EFFICIENCY;

    private static final TagKey<Block> GRAPHITE_BLOCKS = storageBlocks("graphite");
    private static final TagKey<Block> BERYLLIUM_BLOCKS = storageBlocks("beryllium");
    public static final Moderator GRAPHITE;
    public static final Moderator BERYLLIUM;
    public static final Moderator HEAVY_WATER;
    public static final Reflector BERYLLIUM_CARBON;
    public static final Reflector LEAD_STEEL;

    static {
        var builder = new ForgeConfigSpec.Builder();
        builder.push("neutron_sources");
        RADIUM_EFFICIENCY = builder.defineInRange("radium_beryllium_efficiency", 0.9D, 0D, 255D);
        POLONIUM_EFFICIENCY = builder.defineInRange("polonium_beryllium_efficiency", 0.95D, 0D, 255D);
        CALIFORNIUM_EFFICIENCY = builder.defineInRange("californium_efficiency", 1D, 0D, 255D);
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
        SPEC = builder.build();
    }

    public static final RegistryObject<FissionPartBlock> CASING = part("fission_casing", Role.CASING);
    public static final RegistryObject<FissionWallBlock> CONTROLLER = wall("solid_fission_controller", Role.CONTROLLER);
    public static final RegistryObject<FissionWallBlock> VENT = wall("fission_vent", Role.VENT);
    public static final RegistryObject<FissionWallBlock> CELL_PORT = wall("fission_cell_port", Role.FUEL_PORT);
    public static final RegistryObject<FissionFuelCellBlock> CELL = part("solid_fission_cell",
            () -> new FissionFuelCellBlock(properties()));
    public static final RegistryObject<FissionPartBlock> WATER_SINK = part("water_heat_sink", Role.WATER_SINK);
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

    public static final RegistryObject<Item> LECF_251_OXIDE =
            NCRegistries.ITEMS.register("lecf_251_oxide", () -> new Item(new Item.Properties()));
    public static final RegistryObject<Item> DEPLETED_LECF_251_OXIDE =
            NCRegistries.ITEMS.register("depleted_lecf_251_oxide", () -> new Item(new Item.Properties()));

    public static final RegistryObject<BlockEntityType<SolidFissionController>> CONTROLLER_ENTITY =
            NCRegistries.BLOCK_ENTITIES.register("solid_fission_controller", () ->
                    BlockEntityType.Builder.of(SolidFissionController::new, CONTROLLER.get()).build(null));
    public static final RegistryObject<BlockEntityType<FissionPortBlockEntity>> PORT_ENTITY =
            NCRegistries.BLOCK_ENTITIES.register("fission_port", () ->
                    BlockEntityType.Builder.of(FissionPortBlockEntity::new, CELL_PORT.get(), VENT.get()).build(null));
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
                        output.accept(VENT.get());
                        output.accept(WATER_SINK.get());
                        output.accept(HEAVY_WATER_MODERATOR.get());
                        output.accept(BERYLLIUM_CARBON_REFLECTOR.get());
                        output.accept(LEAD_STEEL_REFLECTOR.get());
                        output.accept(RADIUM_BERYLLIUM_SOURCE.get());
                        output.accept(POLONIUM_BERYLLIUM_SOURCE.get());
                        output.accept(CALIFORNIUM_SOURCE.get());
                        output.accept(LECF_251_OXIDE.get());
                        output.accept(DEPLETED_LECF_251_OXIDE.get());
                        NCFissionFuels.displayItems(output);
                        output.accept(NCFluids.HIGH_PRESSURE_STEAM.bucket().get());
                    }).build());

    private static RegistryObject<FissionPartBlock> part(String id, Role role) {
        return part(id, () -> new FissionPartBlock(properties(), role));
    }

    private static RegistryObject<FissionWallBlock> wall(String id, Role role) {
        return part(id, () -> new FissionWallBlock(properties(), role));
    }

    private static RegistryObject<FissionSourceBlock> source(String id, ForgeConfigSpec.DoubleValue efficiency) {
        DoubleSupplier multiplier = () -> configured(efficiency);
        return part(id, () -> new FissionSourceBlock(properties(), multiplier));
    }

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

    private static <T extends Block> RegistryObject<T> part(String id, Supplier<T> supplier) {
        RegistryObject<T> block = NCRegistries.BLOCKS.register(id, supplier);
        NCRegistries.ITEMS.register(id, () -> new BlockItem(block.get(), new Item.Properties()));
        return block;
    }

    private static BlockBehaviour.Properties properties() {
        return BlockBehaviour.Properties.copy(Blocks.IRON_BLOCK);
    }

    private NCFission() {}

    static void register() {
        NCFissionFuels.register();
    }

    public static void registerConfig(ModLoadingContext context) {
        context.registerConfig(ModConfig.Type.SERVER, SPEC, "nuclearcraft-fission.toml");
    }
}
