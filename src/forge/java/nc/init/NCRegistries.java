package nc.init;

import nc.NuclearCraft;
import nc.processor.FluidProcessorBlockEntity;
import nc.processor.FluidProcessorBlockEntity.Machine;
import nc.processor.FluidProcessorMenu;
import nc.processor.MelterBlock;
import nc.processor.MelterBlockEntity;
import nc.processor.MelterMenu;
import nc.processor.ProcessorBlock;
import nc.recipe.ChemicalReactorRecipe;
import nc.recipe.ElectrolyzerRecipe;
import nc.recipe.EnricherRecipe;
import nc.recipe.FluidContainerRecipe;
import nc.recipe.MelterRecipe;
import nc.recipe.SaltMixerRecipe;
import nc.recipe.SupercoolerRecipe;
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

    public static final RegistryObject<RecipeSerializer<FluidContainerRecipe>> FLUID_CONTAINER_CRAFTING =
            RECIPE_SERIALIZERS.register("fluid_container_crafting", FluidContainerRecipe.Serializer::new);

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

    public static final RegistryObject<ProcessorBlock<FluidProcessorBlockEntity>> ELECTROLYZER =
            BLOCKS.register("electrolyzer", () -> new ProcessorBlock<>(BlockBehaviour.Properties.copy(Blocks.IRON_BLOCK),
                    () -> NCRegistries.ELECTROLYZER_ENTITY.get(), FluidProcessorBlockEntity::tick, null));
    public static final RegistryObject<Item> ELECTROLYZER_ITEM =
            ITEMS.register("electrolyzer", () -> new BlockItem(ELECTROLYZER.get(), new Item.Properties()));
    public static final RegistryObject<BlockEntityType<FluidProcessorBlockEntity>> ELECTROLYZER_ENTITY =
            BLOCK_ENTITIES.register("electrolyzer", () ->
                    BlockEntityType.Builder.of((pos, state) -> new FluidProcessorBlockEntity(Machine.ELECTROLYZER, pos, state),
                            ELECTROLYZER.get()).build(null));
    public static final RegistryObject<MenuType<FluidProcessorMenu>> ELECTROLYZER_MENU =
            MENUS.register("electrolyzer", () -> new MenuType<>(
                    (id, player) -> new FluidProcessorMenu(id, player, Machine.ELECTROLYZER), FeatureFlags.DEFAULT_FLAGS));
    public static final RegistryObject<RecipeType<ElectrolyzerRecipe>> ELECTROLYSIS =
            RECIPE_TYPES.register("electrolyzer", () -> RecipeType.simple(NuclearCraft.id("electrolyzer")));
    public static final RegistryObject<RecipeSerializer<ElectrolyzerRecipe>> ELECTROLYZER_SERIALIZER =
            RECIPE_SERIALIZERS.register("electrolyzer", ElectrolyzerRecipe.Serializer::new);

    public static final RegistryObject<ProcessorBlock<FluidProcessorBlockEntity>> SALT_MIXER =
            BLOCKS.register("salt_mixer", () -> new ProcessorBlock<>(BlockBehaviour.Properties.copy(Blocks.IRON_BLOCK),
                    () -> NCRegistries.SALT_MIXER_ENTITY.get(), FluidProcessorBlockEntity::tick, null));
    public static final RegistryObject<Item> SALT_MIXER_ITEM =
            ITEMS.register("salt_mixer", () -> new BlockItem(SALT_MIXER.get(), new Item.Properties()));
    public static final RegistryObject<BlockEntityType<FluidProcessorBlockEntity>> SALT_MIXER_ENTITY =
            BLOCK_ENTITIES.register("salt_mixer", () ->
                    BlockEntityType.Builder.of((pos, state) -> new FluidProcessorBlockEntity(Machine.SALT_MIXER, pos, state),
                            SALT_MIXER.get()).build(null));
    public static final RegistryObject<MenuType<FluidProcessorMenu>> SALT_MIXER_MENU =
            MENUS.register("salt_mixer", () -> new MenuType<>(
                    (id, player) -> new FluidProcessorMenu(id, player, Machine.SALT_MIXER), FeatureFlags.DEFAULT_FLAGS));
    public static final RegistryObject<RecipeType<SaltMixerRecipe>> MIXING =
            RECIPE_TYPES.register("salt_mixer", () -> RecipeType.simple(NuclearCraft.id("salt_mixer")));
    public static final RegistryObject<RecipeSerializer<SaltMixerRecipe>> SALT_MIXER_SERIALIZER =
            RECIPE_SERIALIZERS.register("salt_mixer", SaltMixerRecipe.Serializer::new);

    public static final RegistryObject<ProcessorBlock<FluidProcessorBlockEntity>> CHEMICAL_REACTOR =
            BLOCKS.register("chemical_reactor", () -> new ProcessorBlock<>(BlockBehaviour.Properties.copy(Blocks.IRON_BLOCK),
                    () -> NCRegistries.CHEMICAL_REACTOR_ENTITY.get(), FluidProcessorBlockEntity::tick, null));
    public static final RegistryObject<Item> CHEMICAL_REACTOR_ITEM =
            ITEMS.register("chemical_reactor", () -> new BlockItem(CHEMICAL_REACTOR.get(), new Item.Properties()));
    public static final RegistryObject<BlockEntityType<FluidProcessorBlockEntity>> CHEMICAL_REACTOR_ENTITY =
            BLOCK_ENTITIES.register("chemical_reactor", () ->
                    BlockEntityType.Builder.of((pos, state) -> new FluidProcessorBlockEntity(Machine.CHEMICAL_REACTOR, pos, state),
                            CHEMICAL_REACTOR.get()).build(null));
    public static final RegistryObject<MenuType<FluidProcessorMenu>> CHEMICAL_REACTOR_MENU =
            MENUS.register("chemical_reactor", () -> new MenuType<>(
                    (id, player) -> new FluidProcessorMenu(id, player, Machine.CHEMICAL_REACTOR), FeatureFlags.DEFAULT_FLAGS));
    public static final RegistryObject<RecipeType<ChemicalReactorRecipe>> CHEMICAL_REACTIONS =
            RECIPE_TYPES.register("chemical_reactor", () -> RecipeType.simple(NuclearCraft.id("chemical_reactor")));
    public static final RegistryObject<RecipeSerializer<ChemicalReactorRecipe>> CHEMICAL_REACTOR_SERIALIZER =
            RECIPE_SERIALIZERS.register("chemical_reactor", ChemicalReactorRecipe.Serializer::new);

    public static final RegistryObject<ProcessorBlock<FluidProcessorBlockEntity>> ENRICHER =
            BLOCKS.register("enricher", () -> new ProcessorBlock<>(BlockBehaviour.Properties.copy(Blocks.IRON_BLOCK),
                    () -> NCRegistries.ENRICHER_ENTITY.get(), FluidProcessorBlockEntity::tick, FluidProcessorBlockEntity::inventory));
    public static final RegistryObject<Item> ENRICHER_ITEM =
            ITEMS.register("enricher", () -> new BlockItem(ENRICHER.get(), new Item.Properties()));
    public static final RegistryObject<BlockEntityType<FluidProcessorBlockEntity>> ENRICHER_ENTITY =
            BLOCK_ENTITIES.register("enricher", () ->
                    BlockEntityType.Builder.of((pos, state) -> new FluidProcessorBlockEntity(Machine.ENRICHER, pos, state),
                            ENRICHER.get()).build(null));
    public static final RegistryObject<MenuType<FluidProcessorMenu>> ENRICHER_MENU =
            MENUS.register("enricher", () -> new MenuType<>(
                    (id, player) -> new FluidProcessorMenu(id, player, Machine.ENRICHER), FeatureFlags.DEFAULT_FLAGS));
    public static final RegistryObject<RecipeType<EnricherRecipe>> ENRICHING =
            RECIPE_TYPES.register("enricher", () -> RecipeType.simple(NuclearCraft.id("enricher")));
    public static final RegistryObject<RecipeSerializer<EnricherRecipe>> ENRICHER_SERIALIZER =
            RECIPE_SERIALIZERS.register("enricher", EnricherRecipe.Serializer::new);

    public static final RegistryObject<ProcessorBlock<FluidProcessorBlockEntity>> SUPERCOOLER =
            BLOCKS.register("supercooler", () -> new ProcessorBlock<>(BlockBehaviour.Properties.copy(Blocks.IRON_BLOCK),
                    () -> NCRegistries.SUPERCOOLER_ENTITY.get(), FluidProcessorBlockEntity::tick, null));
    public static final RegistryObject<Item> SUPERCOOLER_ITEM =
            ITEMS.register("supercooler", () -> new BlockItem(SUPERCOOLER.get(), new Item.Properties()));
    public static final RegistryObject<BlockEntityType<FluidProcessorBlockEntity>> SUPERCOOLER_ENTITY =
            BLOCK_ENTITIES.register("supercooler", () ->
                    BlockEntityType.Builder.of((pos, state) -> new FluidProcessorBlockEntity(Machine.SUPERCOOLER, pos, state),
                            SUPERCOOLER.get()).build(null));
    public static final RegistryObject<MenuType<FluidProcessorMenu>> SUPERCOOLER_MENU =
            MENUS.register("supercooler", () -> new MenuType<>(
                    (id, player) -> new FluidProcessorMenu(id, player, Machine.SUPERCOOLER), FeatureFlags.DEFAULT_FLAGS));
    public static final RegistryObject<RecipeType<SupercoolerRecipe>> SUPERCOOLING =
            RECIPE_TYPES.register("supercooler", () -> RecipeType.simple(NuclearCraft.id("supercooler")));
    public static final RegistryObject<RecipeSerializer<SupercoolerRecipe>> SUPERCOOLER_SERIALIZER =
            RECIPE_SERIALIZERS.register("supercooler", SupercoolerRecipe.Serializer::new);

    private static final RegistryObject<CreativeModeTab> MACHINES = TABS.register("machines", () ->
            CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.nuclearcraft.machines"))
                    .icon(() -> MELTER_ITEM.get().getDefaultInstance())
                    .displayItems((parameters, output) -> {
                        output.accept(MELTER_ITEM.get());
                        output.accept(ELECTROLYZER_ITEM.get());
                        output.accept(SALT_MIXER_ITEM.get());
                        output.accept(CHEMICAL_REACTOR_ITEM.get());
                        output.accept(ENRICHER_ITEM.get());
                        output.accept(SUPERCOOLER_ITEM.get());
                        NCItemProcessors.displayItems(output);
                        NCCollectors.displayItems(output);
                        NCGenerators.displayItems(output);
                        output.accept(NCFluids.SULFUR.bucket().get());
                        output.accept(NCFluids.STANDARD_STEAM.bucket().get());
                        output.accept(NCFluids.EXHAUST_STEAM.bucket().get());
                        output.accept(NCFluids.LOW_PRESSURE_STEAM.bucket().get());
                        output.accept(NCFluids.LOW_QUALITY_STEAM.bucket().get());
                        output.accept(NCFluids.OXYGEN.bucket().get());
                        output.accept(NCFluids.NITROGEN.bucket().get());
                        output.accept(NCFluids.HEAVY_WATER.bucket().get());
                        output.accept(NCFluids.HYDROGEN.bucket().get());
                        output.accept(NCFluids.DEUTERIUM.bucket().get());
                        output.accept(NCFluids.HELIUM_3.bucket().get());
                        output.accept(NCFluids.FLUORINE.bucket().get());
                        output.accept(NCFluids.LOW_ENRICHED_WATER.bucket().get());
                        output.accept(NCFluids.HIGH_ENRICHED_WATER.bucket().get());
                    })
                    .build());

    private NCRegistries() {}

    public static void register(IEventBus bus) {
        NCMaterials.register();
        NCItemProcessors.register();
        NCFission.register();
        NCTurbine.register();
        NCHeatExchangers.register();
        BLOCKS.register(bus);
        ITEMS.register(bus);
        BLOCK_ENTITIES.register(bus);
        MENUS.register(bus);
        TABS.register(bus);
        RECIPE_TYPES.register(bus);
        RECIPE_SERIALIZERS.register(bus);
    }
}
