package nc.init;

import nc.NuclearCraft;
import nc.multiblock.hx.CondenserController;
import nc.multiblock.hx.CondenserMenu;
import nc.multiblock.hx.CondenserPort;
import nc.multiblock.hx.ExchangerPartBlock;
import nc.multiblock.hx.ExchangerPartBlock.Part;
import nc.multiblock.hx.ExchangerTubeBlock;
import nc.multiblock.hx.ExchangerWallBlock;
import nc.recipe.CondenserRecipe;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.registries.RegistryObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;

public final class NCCondenser {
    private static final List<RegistryObject<? extends Block>> PARTS = new ArrayList<>();

    public static final RegistryObject<ExchangerPartBlock> CASING = block("heat_exchanger_casing",
            () -> new ExchangerPartBlock(metal(), Part.CASING));
    public static final RegistryObject<ExchangerPartBlock> GLASS = block("heat_exchanger_glass",
            () -> new ExchangerPartBlock(BlockBehaviour.Properties.copy(Blocks.GLASS).noOcclusion(), Part.GLASS));
    public static final RegistryObject<ExchangerPartBlock> REDSTONE = block("heat_exchanger_redstone_port",
            () -> new ExchangerPartBlock(metal(), Part.REDSTONE));
    public static final RegistryObject<ExchangerWallBlock> CONTROLLER = wall("condenser_controller", Part.CONTROLLER);
    public static final RegistryObject<ExchangerWallBlock> INLET = wall("heat_exchanger_inlet", Part.INLET);
    public static final RegistryObject<ExchangerWallBlock> OUTLET = wall("heat_exchanger_outlet", Part.OUTLET);
    public static final RegistryObject<ExchangerTubeBlock> COPPER = tube(Part.COPPER);
    public static final RegistryObject<ExchangerTubeBlock> HARD_CARBON = tube(Part.HARD_CARBON);
    public static final RegistryObject<ExchangerTubeBlock> THERMOCONDUCTING = tube(Part.THERMOCONDUCTING);

    public static final RegistryObject<Item> MULTITOOL = NCRegistries.ITEMS.register("multitool",
            () -> new Item(new Item.Properties().stacksTo(1)) {
                @Override
                public InteractionResult onItemUseFirst(ItemStack stack, UseOnContext context) {
                    BlockState state = context.getLevel().getBlockState(context.getClickedPos());
                    return state.getBlock() instanceof ExchangerTubeBlock tube
                            ? tube.configure(state, context) : InteractionResult.PASS;
                }
            });

    public static final RegistryObject<BlockEntityType<CondenserController>> CONTROLLER_ENTITY =
            NCRegistries.BLOCK_ENTITIES.register("condenser_controller", () ->
                    BlockEntityType.Builder.of(CondenserController::new, CONTROLLER.get()).build(null));
    public static final RegistryObject<BlockEntityType<CondenserPort>> PORT_ENTITY =
            NCRegistries.BLOCK_ENTITIES.register("heat_exchanger_port", () ->
                    BlockEntityType.Builder.of(CondenserPort::new, INLET.get(), OUTLET.get()).build(null));
    public static final RegistryObject<MenuType<CondenserMenu>> MENU = NCRegistries.MENUS.register("condenser_controller",
            () -> new MenuType<>(CondenserMenu::new, FeatureFlags.DEFAULT_FLAGS));
    public static final RegistryObject<RecipeType<CondenserRecipe>> RECIPES =
            NCRegistries.RECIPE_TYPES.register("condenser", () -> RecipeType.simple(NuclearCraft.id("condenser")));
    public static final RegistryObject<RecipeSerializer<CondenserRecipe>> SERIALIZER =
            NCRegistries.RECIPE_SERIALIZERS.register("condenser", CondenserRecipe.Serializer::new);

    private static final RegistryObject<CreativeModeTab> TAB = NCRegistries.TABS.register("heat_exchanger", () ->
            CreativeModeTab.builder().title(Component.translatable("itemGroup.nuclearcraft.heat_exchanger"))
                    .icon(() -> CONTROLLER.get().asItem().getDefaultInstance())
                    .displayItems((parameters, output) -> {
                        PARTS.forEach(part -> output.accept(part.get()));
                        output.accept(MULTITOOL.get());
                        output.accept(NCFluids.CONDENSATE_WATER.bucket().get());
                    }).build());

    private static RegistryObject<ExchangerWallBlock> wall(String id, Part part) {
        return block(id, () -> new ExchangerWallBlock(metal(), part));
    }

    private static RegistryObject<ExchangerTubeBlock> tube(Part part) {
        return block("heat_exchanger_tube_" + part.name().toLowerCase(Locale.ROOT),
                () -> new ExchangerTubeBlock(metal().noOcclusion().dynamicShape(), part));
    }

    private static <T extends Block> RegistryObject<T> block(String id, Supplier<T> factory) {
        RegistryObject<T> block = NCRegistries.BLOCKS.register(id, factory);
        NCRegistries.ITEMS.register(id, () -> new BlockItem(block.get(), new Item.Properties()));
        PARTS.add(block);
        return block;
    }

    private static BlockBehaviour.Properties metal() {
        return BlockBehaviour.Properties.copy(Blocks.IRON_BLOCK);
    }

    private NCCondenser() {}

    static void register() {
        // Populate the shared deferred registers before Forge's registry events.
    }
}
