package nc.worldgen.structure.vault;


import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.Blocks;

import net.minecraft.util.RandomSource;

enum VaultSectorType {

    STANDARD,
    DEEP;

    public BlockState getEntranceShaftShellState(RandomSource random) {
        return switch (this) {
            case STANDARD, DEEP -> Blocks.OBSIDIAN.defaultBlockState();
        };
    }

    public BlockState getEntranceHatchState(RandomSource random) {
        return switch (this) {
            case STANDARD, DEEP -> Blocks.STONE_BRICKS.defaultBlockState();
        };
    }

    public BlockState getCorridorWallState(RandomSource random) {
        return switch (this) {
            case STANDARD -> VaultPalette.WHITE.choose(random, this, false);
            case DEEP -> VaultPalette.LIGHT_GRAY.choose(random, this, false);
        };
    }

    public BlockState getCorridorFloorState(RandomSource random) {
        return switch (this) {
            case STANDARD -> VaultPalette.GRAY.choose(random, this, false);
            case DEEP -> VaultPalette.BLACK.choose(random, this, false);
        };
    }

    public BlockState getCorridorLightState(RandomSource random) {
        return switch (this) {
            case STANDARD -> Blocks.SEA_LANTERN.defaultBlockState();
            case DEEP -> Blocks.REDSTONE_LAMP.defaultBlockState();
        };
    }

    public BlockState getLadderShaftShellState(RandomSource random) {
        return switch (this) {
            case STANDARD, DEEP -> Blocks.OBSIDIAN.defaultBlockState();
        };
    }

    public BlockState getStairsShellState(RandomSource random) {
        return switch (this) {
            case STANDARD, DEEP -> Blocks.OBSIDIAN.defaultBlockState();
        };
    }

    public BlockState getStairsStepState(RandomSource random) {
        return switch (this) {
            case STANDARD -> Blocks.QUARTZ_STAIRS.defaultBlockState();
            case DEEP -> Blocks.STONE_BRICK_STAIRS.defaultBlockState();
        };
    }
}
