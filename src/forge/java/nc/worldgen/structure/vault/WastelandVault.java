package nc.worldgen.structure.vault;

import com.mojang.serialization.Codec;
import nc.NuclearCraft;
import nc.worldgen.WastelandMapping;
import net.minecraft.commands.Commands;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.QuartPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.PiecesContainer;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import javax.annotation.Nullable;

@Mod.EventBusSubscriber(modid = NuclearCraft.MOD_ID)
public final class WastelandVault extends Structure {
    public static final Codec<WastelandVault> CODEC = simpleCodec(WastelandVault::new);
    private static final TagKey<Biome> NATURAL_BIOMES =
            TagKey.create(Registries.BIOME, NuclearCraft.id("natural_vaults"));
    static final int REFERENCE_RADIUS = 8;
    private static final DeferredRegister<StructureType<?>> TYPES =
            DeferredRegister.create(Registries.STRUCTURE_TYPE, NuclearCraft.MOD_ID);
    private static final DeferredRegister<StructurePieceType> PIECE_TYPES =
            DeferredRegister.create(Registries.STRUCTURE_PIECE, NuclearCraft.MOD_ID);
    private static final RegistryObject<StructureType<WastelandVault>> TYPE =
            TYPES.register("wasteland_vault", () -> () -> CODEC);
    static final RegistryObject<StructurePieceType> PIECE = PIECE_TYPES.register("vault_piece",
            () -> (StructurePieceType.ContextlessType) VaultPiece::new);

    public WastelandVault(StructureSettings settings) {
        super(settings);
    }

    public static void register(IEventBus bus) {
        TYPES.register(bus);
        PIECE_TYPES.register(bus);
    }

    @Override
    public StructureType<?> type() {
        return TYPE.get();
    }

    @Override
    protected Optional<GenerationStub> findGenerationPoint(GenerationContext context) {
        int x = context.chunkPos().getMiddleBlockX();
        int z = context.chunkPos().getMiddleBlockZ();
        int y = context.chunkGenerator().getBaseHeight(x, z, Heightmap.Types.WORLD_SURFACE_WG,
                context.heightAccessor(), context.randomState());
        BlockPos entrance = new BlockPos(x, y, z);
        List<VaultPiece> pieces = VaultLayout.create(entrance, context.heightAccessor().getMaxBuildHeight(), context.random());
        return pieces.isEmpty() ? Optional.empty()
                : Optional.of(new GenerationStub(entrance, builder -> pieces.forEach(builder::addPiece)));
    }

    public static void createNaturalStart(RegistryAccess registries, ChunkGenerator generator,
                                          ChunkGeneratorStructureState state, ChunkAccess chunk) {
        var registry = registries.registryOrThrow(Registries.STRUCTURE);
        if (!(registry.get(NuclearCraft.id("wasteland_vault")) instanceof WastelandVault vault)) {
            return;
        }
        var existing = chunk.getStartForStructure(vault);
        if (existing != null && existing.isValid()) {
            return;
        }
        var village = VaultEntrance.village(registry, chunk);
        var origin = chunk.getPos();
        long worldSeed = state.getLevelSeed();
        if (!winsPlacementRoll(worldSeed, origin, village != null)) {
            return;
        }
        if (village == null && !generator.getBiomeSource().getNoiseBiome(QuartPos.fromBlock(origin.getMiddleBlockX()),
                0, QuartPos.fromBlock(origin.getMiddleBlockZ()), state.randomState().sampler()).is(NATURAL_BIOMES)) {
            return;
        }
        var entrance = VaultEntrance.findBeforeTerrain(generator, chunk, state.randomState(), village);
        if (entrance != null) {
            var start = makeStart(vault, origin, entrance, chunk.getMaxBuildHeight(), worldSeed, true);
            if (start.isValid()) {
                chunk.setStartForStructure(vault, start);
            }
        }
    }

    static boolean winsPlacementRoll(long worldSeed, ChunkPos pos, boolean village) {
        long seed = pos.x * 341873128712L + pos.z * 132897987541L + worldSeed + 10384598L;
        return new LegacyRandomSource(seed).nextInt(village ? 2 : 8192) == 0;
    }

    @SubscribeEvent
    public static void registerCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("nc_generate_vault")
                .requires(source -> source.hasPermission(2))
                .executes(context -> generateVault(context.getSource())));
    }

    private static int generateVault(CommandSourceStack source) {
        ServerLevel level = source.getLevel();
        ChunkPos origin = new ChunkPos(BlockPos.containing(source.getPosition()));
        var chunk = level.getChunk(origin.x, origin.z);
        Structure structure = get(level);
        if (structure == null) {
            source.sendFailure(Component.translatable("commands.nuclearcraft.generate_vault.missing"));
            return 0;
        }
        StructureStart previous = chunk.getStartForStructure(structure);
        if (previous != null && previous.isValid()) {
            source.sendFailure(Component.translatable("commands.nuclearcraft.generate_vault.exists"));
            return 0;
        }
        BlockPos entrance = VaultEntrance.find(level, chunk);
        if (entrance == null) {
            source.sendFailure(Component.translatable("commands.nuclearcraft.generate_vault.no_entrance"));
            return 0;
        }

        StructureStart start = makeStart(structure, origin, entrance, level.getMaxBuildHeight(), level.getSeed(), false);
        if (!start.isValid()) {
            source.sendFailure(Component.translatable("commands.nuclearcraft.generate_vault.no_entrance"));
            return 0;
        }
        for (ChunkPos affected : affectedChunks(start.getPieces())) {
            var target = level.getChunk(affected.x, affected.z);
            WastelandMapping.finishPending(level, target);
            placeChunk(level, start, target);
            // The chunk loader discards vanilla structure references beyond eight chunks.
            if (affected.getChessboardDistance(origin) <= REFERENCE_RADIUS) {
                target.addReferenceForStructure(structure, origin.toLong());
            }
        }
        chunk.setStartForStructure(structure, start);
        chunk.setUnsaved(true);
        source.sendSuccess(() -> Component.translatable("commands.nuclearcraft.generate_vault.success",
                entrance.getX(), entrance.getY(), entrance.getZ()), true);
        return 1;
    }

    @Nullable
    static Structure get(ServerLevel level) {
        return level.registryAccess().registryOrThrow(Registries.STRUCTURE).get(NuclearCraft.id("wasteland_vault"));
    }

    public static List<BoundingBox> pieceBounds(ServerLevel level, LevelChunk chunk) {
        var vault = get(level);
        var pos = chunk.getPos();
        var nearby = vault == null || chunk.getReferencesForStructure(vault).isEmpty() ? List.<StructureStart>of()
                : level.structureManager().startsForStructure(pos, structure -> structure == vault);
        return Stream.concat(nearby.stream(), VaultOverflow.completedStarts(level, chunk).stream())
                .flatMap(start -> start.getPieces().stream()).map(StructurePiece::getBoundingBox)
                .filter(bounds -> bounds.intersects(pos.getMinBlockX(), pos.getMinBlockZ(),
                        pos.getMaxBlockX(), pos.getMaxBlockZ())).toList();
    }

    private static StructureStart makeStart(Structure vault, ChunkPos origin, BlockPos entrance, int maxHeight,
                                            long seed, boolean natural) {
        var random = new WorldgenRandom(new LegacyRandomSource(0L));
        random.setLargeFeatureSeed(seed, origin.x, origin.z);
        var pieces = VaultLayout.create(entrance, maxHeight, random);
        if (pieces.isEmpty()) {
            return StructureStart.INVALID_START;
        }
        if (natural) {
            pieces.get(pieces.size() - 1).markNaturalEntrance();
        }
        return new StructureStart(vault, origin, 0, new PiecesContainer(List.copyOf(pieces)));
    }

    static void placeChunk(ServerLevel level, StructureStart start, LevelChunk chunk) {
        var pos = chunk.getPos();
        BoundingBox clip = new BoundingBox(pos.getMinBlockX(), level.getMinBuildHeight(), pos.getMinBlockZ(),
                pos.getMaxBlockX(), level.getMaxBuildHeight() - 1, pos.getMaxBlockZ());
        start.placeInChunk(level, level.structureManager(), level.getChunkSource().getGenerator(),
                RandomSource.create(level.getSeed() ^ pos.toLong()), clip, pos);
    }

    static Set<ChunkPos> affectedChunks(List<? extends StructurePiece> pieces) {
        Set<ChunkPos> result = new LinkedHashSet<>();
        for (StructurePiece piece : pieces) {
            var box = piece.getBoundingBox();
            ChunkPos.rangeClosed(new ChunkPos(new BlockPos(box.minX(), 0, box.minZ())),
                    new ChunkPos(new BlockPos(box.maxX(), 0, box.maxZ()))).forEach(result::add);
        }
        return result;
    }
}
