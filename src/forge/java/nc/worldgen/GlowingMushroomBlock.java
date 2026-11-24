package nc.worldgen;

import nc.NuclearCraft;
import nc.init.NCWasteland;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.MushroomBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraftforge.common.PlantType;

public final class GlowingMushroomBlock extends MushroomBlock {
    private static final ResourceKey<ConfiguredFeature<?, ?>> HUGE_FEATURE =
            ResourceKey.create(Registries.CONFIGURED_FEATURE, NuclearCraft.id("huge_glowing_mushroom"));

    public GlowingMushroomBlock(Properties properties) {
        super(properties, HUGE_FEATURE);
    }

    @Override
    public PlantType getPlantType(BlockGetter level, BlockPos pos) {
        return PlantType.CAVE;
    }

    @Override
    public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        if (pos.getY() <= level.getMinBuildHeight() || level.isOutsideBuildHeight(pos)) {
            return false;
        }
        BlockPos ground = pos.below();
        return level.getBlockState(ground).canSustainPlant(level, ground, Direction.UP, this);
    }

    @Override
    public boolean isValidBonemealTarget(LevelReader level, BlockPos pos, BlockState state, boolean clientSide) {
        return level.getBiome(pos).is(NCWasteland.MUSHROOM_BIOMES);
    }

    @Override
    public boolean growMushroom(ServerLevel level, BlockPos pos, BlockState state, RandomSource random) {
        return isValidBonemealTarget(level, pos, state, false) && super.growMushroom(level, pos, state, random);
    }

    @Override
    public void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        int rate = NCWasteland.mushroomSpreadRate();
        if (rate <= 0 || random.nextInt(Math.max(1, 400 / rate)) != 0
                || !level.hasChunksAt(pos.offset(-4, -1, -4), pos.offset(4, 1, 4))) {
            return;
        }
        spread(state, level, pos, random);
    }

    private void spread(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        int count = 0;
        for (BlockPos nearby : BlockPos.betweenClosed(pos.offset(-4, -1, -4), pos.offset(4, 1, 4))) {
            if (level.getBlockState(nearby).is(this) && ++count > 4) {
                return;
            }
        }
        for (int attempt = 0; attempt < 4; attempt++) {
            BlockPos target = pos.offset(random.nextInt(4) - random.nextInt(4),
                    random.nextInt(4) - random.nextInt(4), random.nextInt(4) - random.nextInt(4));
            if (level.isEmptyBlock(target) && state.canSurvive(level, target)) {
                level.setBlock(target, state, Block.UPDATE_CLIENTS);
                return;
            }
        }
    }
}
