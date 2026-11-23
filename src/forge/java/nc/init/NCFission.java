package nc.init;

import nc.NuclearCraft;
import nc.multiblock.fission.FissionFuelCell;
import nc.multiblock.fission.FissionFuelCellBlock;
import nc.multiblock.fission.FissionPartBlock;
import nc.multiblock.fission.FissionPartBlock.Role;
import nc.multiblock.fission.FissionPortBlockEntity;
import nc.multiblock.fission.FissionWallBlock;
import nc.multiblock.fission.SolidFissionController;
import nc.multiblock.fission.SolidFissionMenu;
import nc.recipe.SolidFissionRecipe;
import net.minecraft.network.chat.Component;
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
import net.minecraftforge.registries.RegistryObject;

import java.util.function.Supplier;

public final class NCFission {
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
                        output.accept(LECF_251_OXIDE.get());
                        output.accept(DEPLETED_LECF_251_OXIDE.get());
                        output.accept(NCFluids.HIGH_PRESSURE_STEAM.bucket().get());
                    }).build());

    private static RegistryObject<FissionPartBlock> part(String id, Role role) {
        return part(id, () -> new FissionPartBlock(properties(), role));
    }

    private static RegistryObject<FissionWallBlock> wall(String id, Role role) {
        return part(id, () -> new FissionWallBlock(properties(), role));
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
        // Registers the entries above on the shared deferred registers before their events.
    }
}
