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
        MANUFACTORY("manufactory", 1, 1, false, 56, -1, 116, 74, 35, 16),
        SEPARATOR("separator", 1, 2, false, 42, -1, 102, 60, 34, 18),
        ALLOY_FURNACE("alloy_furnace", 2, 1, true, 46, -1, 126, 84, 35, 16),
        INFUSER("infuser", 1, 1, true, 46, 66, 126, 84, 35, 16);

        private final String id;
        private final int inputs;
        private final int outputs;
        private final boolean losesProgress;
        private final int inputX;
        private final int fluidInputX;
        private final int outputX;
        private final int progressX;
        private final int progressY;
        private final int progressHeight;
        private final RegistryObject<ProcessorBlock<ItemProcessorBlockEntity>> block;
        private final RegistryObject<Item> item;
        private final RegistryObject<BlockEntityType<ItemProcessorBlockEntity>> entity;
        private final RegistryObject<MenuType<ItemProcessorMenu>> menu;
        private final RegistryObject<RecipeType<ItemProcessorRecipe>> recipes;
        private final RegistryObject<RecipeSerializer<ItemProcessorRecipe>> serializer;

        Machine(String id, int inputs, int outputs, boolean losesProgress, int inputX, int fluidInputX, int outputX,
                int progressX, int progressY, int progressHeight) {
            this.id = id;
            this.inputs = inputs;
            this.outputs = outputs;
            this.losesProgress = losesProgress;
            this.inputX = inputX;
            this.fluidInputX = fluidInputX;
            this.outputX = outputX;
            this.progressX = progressX;
            this.progressY = progressY;
            this.progressHeight = progressHeight;
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

        public int outputs() {
            return outputs;
        }

        public int slots() {
            return inputs + outputs;
        }

        public int inputX(int slot) {
            return inputX + slot * 20;
        }

        public boolean hasFluidInput() {
            return fluidInputX >= 0;
        }

        public int fluidInputX() {
            return fluidInputX;
        }

        public int outputX(int slot) {
            return outputX + slot * 28;
        }

        public int progressX() {
            return progressX;
        }

        public int progressY() {
            return progressY;
        }

        public int progressHeight() {
            return progressHeight;
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
