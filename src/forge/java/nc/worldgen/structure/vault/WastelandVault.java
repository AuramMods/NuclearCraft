package nc.worldgen.structure.vault;

import com.mojang.serialization.Codec;
import nc.NuclearCraft;
import net.minecraft.commands.Commands;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
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

@Mod.EventBusSubscriber(modid = NuclearCraft.MOD_ID)
public final class WastelandVault extends Structure {
    public static final Codec<WastelandVault> CODEC = simpleCodec(WastelandVault::new);
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
        Structure structure = level.registryAccess().registryOrThrow(Registries.STRUCTURE)
                .get(NuclearCraft.id("wasteland_vault"));
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

        WorldgenRandom random = new WorldgenRandom(new LegacyRandomSource(0L));
        random.setLargeFeatureSeed(level.getSeed(), origin.x, origin.z);
        List<VaultPiece> pieces = VaultLayout.create(entrance, level.getMaxBuildHeight(), random);
        if (pieces.isEmpty()) {
            source.sendFailure(Component.translatable("commands.nuclearcraft.generate_vault.no_entrance"));
            return 0;
        }
        StructureStart start = new StructureStart(structure, origin, 0, new PiecesContainer(List.copyOf(pieces)));
        for (ChunkPos affected : affectedChunks(pieces)) {
            var target = level.getChunk(affected.x, affected.z);
            BoundingBox clip = new BoundingBox(affected.getMinBlockX(), level.getMinBuildHeight(), affected.getMinBlockZ(),
                    affected.getMaxBlockX(), level.getMaxBuildHeight() - 1, affected.getMaxBlockZ());
            start.placeInChunk(level, level.structureManager(), level.getChunkSource().getGenerator(),
                    RandomSource.create(), clip, affected);
            // The chunk loader discards vanilla structure references beyond eight chunks.
            if (affected.getChessboardDistance(origin) <= 8) {
                target.addReferenceForStructure(structure, origin.toLong());
            }
        }
        chunk.setStartForStructure(structure, start);
        chunk.setUnsaved(true);
        source.sendSuccess(() -> Component.translatable("commands.nuclearcraft.generate_vault.success",
                entrance.getX(), entrance.getY(), entrance.getZ()), true);
        return 1;
    }

    private static Set<ChunkPos> affectedChunks(List<VaultPiece> pieces) {
        Set<ChunkPos> result = new LinkedHashSet<>();
        for (VaultPiece piece : pieces) {
            var box = piece.getBoundingBox();
            ChunkPos.rangeClosed(new ChunkPos(new BlockPos(box.minX(), 0, box.minZ())),
                    new ChunkPos(new BlockPos(box.maxX(), 0, box.maxZ()))).forEach(result::add);
        }
        return result;
    }
}
