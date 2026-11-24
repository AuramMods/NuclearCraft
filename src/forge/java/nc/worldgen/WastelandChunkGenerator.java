package nc.worldgen;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import nc.NuclearCraft;
import nc.worldgen.structure.vault.WastelandVault;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;

public final class WastelandChunkGenerator extends NoiseBasedChunkGenerator {
    public static final Codec<WastelandChunkGenerator> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            WastelandBiomeSource.CODEC.fieldOf("biome_source").forGetter(generator -> generator.wastelandBiomes),
            NoiseGeneratorSettings.CODEC.fieldOf("settings").forGetter(WastelandChunkGenerator::generatorSettings)
    ).apply(instance, WastelandChunkGenerator::new));

    private static final TagKey<StructureSet> WASTELAND_STRUCTURES =
            TagKey.create(Registries.STRUCTURE_SET, NuclearCraft.id("wasteland_structures"));
    private final WastelandBiomeSource wastelandBiomes;

    private WastelandChunkGenerator(WastelandBiomeSource biomes, Holder<NoiseGeneratorSettings> settings) {
        super(biomes, settings);
        this.wastelandBiomes = biomes;
    }

    @Override
    protected Codec<? extends ChunkGenerator> codec() {
        return CODEC;
    }

    @Override
    public ChunkGeneratorStructureState createState(HolderLookup<StructureSet> structures, RandomState random, long seed) {
        wastelandBiomes.setSeed(seed);
        return ChunkGeneratorStructureState.createForFlat(random, seed, wastelandBiomes,
                structures.listElements().filter(holder -> holder.is(WASTELAND_STRUCTURES)).<Holder<StructureSet>>map(holder -> holder));
    }

    @Override
    public void createStructures(RegistryAccess registries, ChunkGeneratorStructureState state, StructureManager structures,
                                 ChunkAccess chunk, StructureTemplateManager templates) {
        super.createStructures(registries, state, structures, chunk, templates);
        if (structures.shouldGenerateStructures()) {
            WastelandVault.createNaturalStart(registries, this, state, chunk);
        }
    }

    @Override
    public void applyCarvers(WorldGenRegion region, long seed, RandomState random, BiomeManager biomes,
                             StructureManager structures, ChunkAccess chunk, GenerationStep.Carving step) {
        // The legacy Wasteland disables cave and ravine carvers in every biome.
    }
}
