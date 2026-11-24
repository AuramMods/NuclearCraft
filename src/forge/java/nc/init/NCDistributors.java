package nc.init;

import nc.distributor.DistributorBlock;
import nc.distributor.DistributorBlockEntity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraftforge.registries.RegistryObject;

import java.util.Arrays;

public final class NCDistributors {
    public static final RegistryObject<BlockEntityType<DistributorBlockEntity>> ENTITY =
            NCRegistries.BLOCK_ENTITIES.register("distributor", () -> BlockEntityType.Builder.of(
                    DistributorBlockEntity::new, Arrays.stream(Part.values()).map(Part::block).toArray(Block[]::new))
                    .build(null));

    private NCDistributors() {}

    static void register() {
        Part.values();
    }

    static void displayItems(CreativeModeTab.Output output) {
        for (Part part : Part.values()) {
            output.accept(part.block());
        }
    }

    public enum Part {
        BUFFER("distributor_buffer"),
        INLET("distributor_inlet"),
        OUTLET("distributor_outlet");

        private final RegistryObject<DistributorBlock> block;

        Part(String id) {
            block = NCRegistries.BLOCKS.register(id, () -> new DistributorBlock(
                    BlockBehaviour.Properties.copy(Blocks.IRON_BLOCK), this));
            NCRegistries.ITEMS.register(id, () -> new BlockItem(block(), new Item.Properties()));
        }

        public DistributorBlock block() {
            return block.get();
        }
    }
}
