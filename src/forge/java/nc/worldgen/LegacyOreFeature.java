package nc.worldgen;

import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.OreFeature;
import net.minecraft.world.level.levelgen.feature.configurations.OreConfiguration;

public final class LegacyOreFeature extends Feature<OreConfiguration> {
    private static final float PI = (float) Math.PI;

    public LegacyOreFeature() {
        super(OreConfiguration.CODEC);
    }

    @Override
    public boolean place(FeaturePlaceContext<OreConfiguration> context) {
        int size = context.config().size;
        if (size == 0) {
            return false;
        }

        // The legacy size represents the desired ore yield, which sets the length and width of the vein.
        double count = size <= 6 ? Math.pow(size + 4, 0.75D) : (Math.pow(size, 1D / 3.551D) - 0.853D) / 0.148D;
        var random = context.random();
        var origin = context.origin();
        float angle = random.nextFloat() * PI;
        float xSpread = Mth.sin(angle) * (float) count / 8F;
        float zSpread = Mth.cos(angle) * (float) count / 8F;
        float xCenter = origin.getX() + 8;
        float zCenter = origin.getZ() + 8;
        double startX = xCenter + xSpread;
        double endX = xCenter - xSpread;
        double startY = origin.getY() + random.nextInt(3) - 2;
        double endY = origin.getY() + random.nextInt(3) - 2;
        double startZ = zCenter + zSpread;
        double endZ = zCenter - zSpread;

        var pos = new BlockPos.MutableBlockPos();
        boolean placed = false;
        for (int index = 0; index < (int) count; index++) {
            float progress = index / (float) count;
            double radius = ((Mth.sin(PI * progress) + 1F) * (random.nextDouble() * count / 16D) + 1D) / 2D;
            placed |= placeSphere(context, pos, Mth.lerp(progress, startX, endX),
                    Mth.lerp(progress, startY, endY), Mth.lerp(progress, startZ, endZ), radius);
        }
        return placed;
    }

    private static boolean placeSphere(FeaturePlaceContext<OreConfiguration> context, BlockPos.MutableBlockPos pos,
                                       double centerX, double centerY, double centerZ, double radius) {
        WorldGenLevel level = context.level();
        int minY = Math.max(level.getMinBuildHeight(), Mth.floor(centerY - radius));
        int maxY = Math.min(level.getMaxBuildHeight() - 1, Mth.floor(centerY + radius));
        int maxX = Mth.floor(centerX + radius);
        int minZ = Mth.floor(centerZ - radius);
        int maxZ = Mth.floor(centerZ + radius);
        boolean placed = false;
        for (int x = Mth.floor(centerX - radius); x <= maxX; x++) {
            double dx = (x + 0.5D - centerX) / radius;
            for (int y = minY; y <= maxY; y++) {
                double dy = (y + 0.5D - centerY) / radius;
                double distanceXY = dx * dx + dy * dy;
                if (distanceXY >= 1D) {
                    continue;
                }
                for (int z = minZ; z <= maxZ; z++) {
                    double dz = (z + 0.5D - centerZ) / radius;
                    if (distanceXY + dz * dz < 1D) {
                        pos.set(x, y, z);
                        placed |= replaceOreTarget(context, pos);
                    }
                }
            }
        }
        return placed;
    }

    private static boolean replaceOreTarget(FeaturePlaceContext<OreConfiguration> context, BlockPos.MutableBlockPos pos) {
        var level = context.level();
        if (!level.ensureCanWrite(pos)) {
            return false;
        }
        var state = level.getBlockState(pos);
        var config = context.config();
        for (var target : config.targetStates) {
            if (OreFeature.canPlaceOre(state, level::getBlockState, context.random(), config, target, pos)) {
                return level.setBlock(pos, target.state, Block.UPDATE_CLIENTS);
            }
        }
        return false;
    }
}
