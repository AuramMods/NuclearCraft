package nc.init;

import nc.NuclearCraft;
import nc.processor.ItemProcessorBlockEntity;
import nc.processor.ItemProcessorMenu;
import nc.processor.ProcessorBlock;
import nc.recipe.ItemProcessorRecipe;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraftforge.registries.RegistryObject;

public final class NCItemProcessors {
    private NCItemProcessors() {}

    static void register() {
        Machine.values();
    }

    static void displayItems(CreativeModeTab.Output output) {
        for (Machine machine : Machine.values()) {
            output.accept(machine.item.get());
        }
    }

    public enum Machine {
        MANUFACTORY("manufactory", 1, false),
        ALLOY_FURNACE("alloy_furnace", 2, true);

        private final String id;
        private final int inputs;
        private final boolean losesProgress;
        private final RegistryObject<ProcessorBlock<ItemProcessorBlockEntity>> block;
        private final RegistryObject<Item> item;
        private final RegistryObject<BlockEntityType<ItemProcessorBlockEntity>> entity;
        private final RegistryObject<MenuType<ItemProcessorMenu>> menu;
        private final RegistryObject<RecipeType<ItemProcessorRecipe>> recipes;
        private final RegistryObject<RecipeSerializer<ItemProcessorRecipe>> serializer;

        Machine(String id, int inputs, boolean losesProgress) {
            this.id = id;
            this.inputs = inputs;
            this.losesProgress = losesProgress;
            block = NCRegistries.BLOCKS.register(id, () -> new ProcessorBlock<>(
                    BlockBehaviour.Properties.copy(Blocks.IRON_BLOCK)
                            .lightLevel(state -> state.getValue(ProcessorBlock.LIT) ? 7 : 0),
                    this::entityType, ItemProcessorBlockEntity::tick, ItemProcessorBlockEntity::inventory));
            item = NCRegistries.ITEMS.register(id, () -> new BlockItem(block.get(), new Item.Properties()));
            entity = NCRegistries.BLOCK_ENTITIES.register(id, () -> BlockEntityType.Builder.of(
                    (pos, state) -> new ItemProcessorBlockEntity(pos, state, this), block.get()).build(null));
            menu = NCRegistries.MENUS.register(id,
                    () -> new MenuType<>((containerId, player) -> new ItemProcessorMenu(containerId, player, this),
                            FeatureFlags.DEFAULT_FLAGS));
            recipes = NCRegistries.RECIPE_TYPES.register(id, () -> RecipeType.simple(NuclearCraft.id(id)));
            serializer = NCRegistries.RECIPE_SERIALIZERS.register(id, () -> new ItemProcessorRecipe.Serializer(this));
        }

        public String id() {
            return id;
        }

        public int inputs() {
            return inputs;
        }

        public boolean losesProgress() {
            return losesProgress;
        }

        public ProcessorBlock<ItemProcessorBlockEntity> block() {
            return block.get();
        }

        public BlockEntityType<ItemProcessorBlockEntity> entityType() {
            return entity.get();
        }

        public MenuType<ItemProcessorMenu> menu() {
            return menu.get();
        }

        public RecipeType<ItemProcessorRecipe> recipes() {
            return recipes.get();
        }

        public RecipeSerializer<ItemProcessorRecipe> serializer() {
            return serializer.get();
        }
    }
}
