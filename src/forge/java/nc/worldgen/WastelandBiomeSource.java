package nc.worldgen;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.util.random.WeightedEntry;
import net.minecraft.util.random.WeightedRandomList;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;

import java.util.stream.Stream;

public final class WastelandBiomeSource extends BiomeSource {
    public static final Codec<WastelandBiomeSource> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            WeightedRandomList.codec(WeightedEntry.Wrapper.codec(Biome.CODEC)).fieldOf("biomes").forGetter(source -> source.biomes)
    ).apply(instance, WastelandBiomeSource::new));

    private static final int REGION_SIZE = 128;
    private final WeightedRandomList<WeightedEntry.Wrapper<Holder<Biome>>> biomes;
    private final int totalWeight;
    private volatile long seed;

    private WastelandBiomeSource(WeightedRandomList<WeightedEntry.Wrapper<Holder<Biome>>> biomes) {
        this.biomes = biomes;
        this.totalWeight = biomes.unwrap().stream().mapToInt(entry -> entry.getWeight().asInt()).sum();
        if (totalWeight == 0) {
            throw new IllegalArgumentException("The Wasteland biome source needs at least one positive biome weight");
        }
    }

    void setSeed(long seed) {
        this.seed = seed;
    }

    @Override
    protected Codec<? extends BiomeSource> codec() {
        return CODEC;
    }

    @Override
    protected Stream<Holder<Biome>> collectPossibleBiomes() {
        return biomes.unwrap().stream().filter(entry -> entry.getWeight().asInt() > 0).map(WeightedEntry.Wrapper::getData);
    }

    @Override
    public Holder<Biome> getNoiseBiome(int quartX, int quartY, int quartZ, Climate.Sampler sampler) {
        int x = QuartPos.toBlock(quartX);
        int z = QuartPos.toBlock(quartZ);
        long worldSeed = seed;
        double primaryX = sampleFractalNoise(x / 64D, z / 64D, 0x4CF5AD432745937FL);
        double primaryZ = sampleFractalNoise(x / 64D, z / 64D, 0x9E3779B97F4A7C15L);
        double secondaryX = sampleFractalNoise((x + 4096D) / 32D, (z - 4096D) / 32D, 0xC2B2AE3D27D4EB4FL);
        double secondaryZ = sampleFractalNoise((x - 8192D) / 32D, (z + 8192D) / 32D, 0x165667B19E3779F9L);
        double warpedX = x + 64D * (primaryX * 0.72D + secondaryX * 0.28D);
        double warpedZ = z + 64D * (primaryZ * 0.72D + secondaryZ * 0.28D);
        double rotatedX = (warpedX + warpedZ) * 0.7071067811865476D;
        double rotatedZ = (warpedZ - warpedX) * 0.7071067811865476D;
        int baseX = (int) Math.floor(rotatedX / REGION_SIZE);
        int baseZ = (int) Math.floor(rotatedZ / REGION_SIZE);
        long nearestSeed = 0L;
        double nearestDistance = Double.POSITIVE_INFINITY;
        for (int dz = -1; dz <= 1; dz++) {
            for (int dx = -1; dx <= 1; dx++) {
                int regionX = baseX + dx;
                int regionZ = baseZ + dz;
                long regionSeed = longHash(worldSeed ^ (long) regionX * 341873128712L ^ (long) regionZ * 132897987541L);
                double deltaX = rotatedX - (regionX + getUniformDouble(regionSeed ^ 0x9E3779B97F4A7C15L)) * REGION_SIZE;
                double deltaZ = rotatedZ - (regionZ + getUniformDouble(regionSeed ^ 0xC2B2AE3D27D4EB4FL)) * REGION_SIZE;
                double distance = deltaX * deltaX + deltaZ * deltaZ;
                if (distance < nearestDistance) {
                    nearestDistance = distance;
                    nearestSeed = regionSeed;
                }
            }
        }
        int roll = (int) ((longHash(nearestSeed ^ 0x94D049BB133111EBL) & Long.MAX_VALUE) % totalWeight);
        for (var entry : biomes.unwrap()) {
            roll -= entry.getWeight().asInt();
            if (roll < 0) {
                return entry.getData();
            }
        }
        throw new IllegalStateException("Could not select a Wasteland biome");
    }

    private static double sampleFractalNoise(double x, double z, long salt) {
        return sampleValueNoise(x, z, salt) * 0.6D
                + sampleValueNoise(x * 2D, z * 2D, salt ^ 0x9E3779B97F4A7C15L) * 0.28D
                + sampleValueNoise(x * 4D, z * 4D, salt ^ 0xC2B2AE3D27D4EB4FL) * 0.12D;
    }

    private static double sampleValueNoise(double x, double z, long salt) {
        int x0 = (int) Math.floor(x);
        int z0 = (int) Math.floor(z);
        double dx = x - x0;
        double dz = z - z0;
        double sx = dx * dx * (3D - 2D * dx);
        double sz = dz * dz * (3D - 2D * dz);
        double v00 = getUniformNoise(salt, x0, z0);
        double v10 = getUniformNoise(salt, x0 + 1, z0);
        double v01 = getUniformNoise(salt, x0, z0 + 1);
        double v11 = getUniformNoise(salt, x0 + 1, z0 + 1);
        double r0 = v00 + (v10 - v00) * sx;
        double r1 = v01 + (v11 - v01) * sx;
        return r0 + (r1 - r0) * sz;
    }

    private static long longHash(long value) {
        value ^= value >>> 33;
        value *= 0xFF51AFD7ED558CCDL;
        value ^= value >>> 33;
        value *= 0xC4CEB9FE1A85EC53L;
        return value ^ value >>> 33;
    }

    private static double getUniformDouble(long value) {
        return (longHash(value) >>> 11) * 0x1P-53;
    }

    private static double getUniformNoise(long salt, int x, int z) {
        return getUniformDouble(longHash(salt ^ (long) x * 0x9E3779B97F4A7C15L ^ (long) z * 0xC2B2AE3D27D4EB4FL))
                * 2D - 1D;
    }
}
