package nc.init;

import nc.NuclearCraft;
import nc.processor.MelterBlock;
import nc.processor.MelterBlockEntity;
import nc.processor.MelterMenu;
import nc.recipe.MelterRecipe;
import net.minecraft.core.registries.Registries;
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
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class NCRegistries {
    static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, NuclearCraft.MOD_ID);
    static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, NuclearCraft.MOD_ID);
    static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, NuclearCraft.MOD_ID);
    static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(ForgeRegistries.MENU_TYPES, NuclearCraft.MOD_ID);
    static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, NuclearCraft.MOD_ID);
    static final DeferredRegister<RecipeType<?>> RECIPE_TYPES =
            DeferredRegister.create(ForgeRegistries.RECIPE_TYPES, NuclearCraft.MOD_ID);
    static final DeferredRegister<RecipeSerializer<?>> RECIPE_SERIALIZERS =
            DeferredRegister.create(ForgeRegistries.RECIPE_SERIALIZERS, NuclearCraft.MOD_ID);

    public static final RegistryObject<MelterBlock> MELTER = BLOCKS.register("melter", () -> new MelterBlock(
            BlockBehaviour.Properties.copy(Blocks.IRON_BLOCK)
                    .lightLevel(state -> state.getValue(MelterBlock.LIT) ? 7 : 0)));
    public static final RegistryObject<Item> MELTER_ITEM =
            ITEMS.register("melter", () -> new BlockItem(MELTER.get(), new Item.Properties()));
    public static final RegistryObject<BlockEntityType<MelterBlockEntity>> MELTER_ENTITY =
            BLOCK_ENTITIES.register("melter", () -> BlockEntityType.Builder.of(MelterBlockEntity::new, MELTER.get()).build(null));
    public static final RegistryObject<MenuType<MelterMenu>> MELTER_MENU =
            MENUS.register("melter", () -> new MenuType<>(MelterMenu::new, FeatureFlags.DEFAULT_FLAGS));
    public static final RegistryObject<RecipeType<MelterRecipe>> MELTING =
            RECIPE_TYPES.register("melter", () -> RecipeType.simple(NuclearCraft.id("melter")));
    public static final RegistryObject<RecipeSerializer<MelterRecipe>> MELTER_SERIALIZER =
            RECIPE_SERIALIZERS.register("melter", MelterRecipe.Serializer::new);

    private static final RegistryObject<CreativeModeTab> MACHINES = TABS.register("machines", () ->
            CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.nuclearcraft.machines"))
                    .icon(() -> MELTER_ITEM.get().getDefaultInstance())
                    .displayItems((parameters, output) -> {
                        output.accept(MELTER_ITEM.get());
                        output.accept(NCFluids.SULFUR.bucket().get());
                        output.accept(NCFluids.STANDARD_STEAM.bucket().get());
                        output.accept(NCFluids.EXHAUST_STEAM.bucket().get());
                        output.accept(NCFluids.LOW_PRESSURE_STEAM.bucket().get());
                        output.accept(NCFluids.LOW_QUALITY_STEAM.bucket().get());
                    })
                    .build());

    private NCRegistries() {}

    public static void register(IEventBus bus) {
        NCMaterials.register();
        NCFission.register();
        NCTurbine.register();
        BLOCKS.register(bus);
        ITEMS.register(bus);
        BLOCK_ENTITIES.register(bus);
        MENUS.register(bus);
        TABS.register(bus);
        RECIPE_TYPES.register(bus);
        RECIPE_SERIALIZERS.register(bus);
    }
}
