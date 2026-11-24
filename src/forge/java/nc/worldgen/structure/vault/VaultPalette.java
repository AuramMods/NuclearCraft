package nc.worldgen.structure.vault;

import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

enum VaultPalette {
    WHITE(Blocks.WHITE_CONCRETE, Blocks.WHITE_CONCRETE_POWDER, Blocks.WHITE_TERRACOTTA),
    ORANGE(Blocks.ORANGE_CONCRETE, Blocks.ORANGE_CONCRETE_POWDER, Blocks.ORANGE_TERRACOTTA),
    LIGHT_BLUE(Blocks.LIGHT_BLUE_CONCRETE, Blocks.LIGHT_BLUE_CONCRETE_POWDER, Blocks.LIGHT_BLUE_TERRACOTTA),
    YELLOW(Blocks.YELLOW_CONCRETE, Blocks.YELLOW_CONCRETE_POWDER, Blocks.YELLOW_TERRACOTTA),
    LIME(Blocks.LIME_CONCRETE, Blocks.LIME_CONCRETE_POWDER, Blocks.LIME_TERRACOTTA),
    GRAY(Blocks.GRAY_CONCRETE, Blocks.GRAY_CONCRETE_POWDER, Blocks.GRAY_TERRACOTTA),
    LIGHT_GRAY(Blocks.LIGHT_GRAY_CONCRETE, Blocks.LIGHT_GRAY_CONCRETE_POWDER, Blocks.LIGHT_GRAY_TERRACOTTA),
    BLUE(Blocks.BLUE_CONCRETE, Blocks.BLUE_CONCRETE_POWDER, Blocks.BLUE_TERRACOTTA),
    GREEN(Blocks.GREEN_CONCRETE, Blocks.GREEN_CONCRETE_POWDER, Blocks.GREEN_TERRACOTTA),
    BLACK(Blocks.BLACK_CONCRETE, Blocks.BLACK_CONCRETE_POWDER, Blocks.BLACK_TERRACOTTA);

    private final BlockState concrete;
    private final BlockState powder;
    private final BlockState terracotta;

    VaultPalette(Block concrete, Block powder, Block terracotta) {
        this.concrete = concrete.defaultBlockState();
        this.powder = powder.defaultBlockState();
        this.terracotta = terracotta.defaultBlockState();
    }

    BlockState choose(RandomSource random, VaultSectorType sector, boolean supported) {
        if (random.nextInt(sector == VaultSectorType.DEEP ? 4 : 8) == 0) {
            return supported ? powder : terracotta;
        }
        return concrete;
    }
}
