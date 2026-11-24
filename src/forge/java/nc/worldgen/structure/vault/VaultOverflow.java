package nc.worldgen.structure.vault;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import nc.NuclearCraft;
import nc.worldgen.WastelandChunkGenerator;
import nc.worldgen.WastelandMapping;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ImposterProtoChunk;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.ChunkDataEvent;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.Consumer;

@Mod.EventBusSubscriber(modid = NuclearCraft.MOD_ID)
public final class VaultOverflow {
    private static final int CHUNKS_PER_TICK = 4;
    private static final String COMPLETED = "nuclearcraft_vault_overflow_complete";
    private static final Map<LevelChunk, LongSet> COMPLETED_BY_CHUNK = Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<ServerLevel, Map<Long, LevelChunk>> QUEUES = new IdentityHashMap<>();

    private VaultOverflow() {}

    @SubscribeEvent
    public static void onChunkDataLoad(ChunkDataEvent.Load event) {
        if (event.getData().contains(COMPLETED, Tag.TAG_LONG_ARRAY)) {
            LevelChunk chunk = unwrap(event.getChunk());
            if (chunk != null) {
                COMPLETED_BY_CHUNK.put(chunk, new LongOpenHashSet(event.getData().getLongArray(COMPLETED)));
            }
        }
    }

    @SubscribeEvent
    public static void onChunkDataSave(ChunkDataEvent.Save event) {
        var completed = COMPLETED_BY_CHUNK.get(unwrap(event.getChunk()));
        if (completed != null) {
            event.getData().putLongArray(COMPLETED, completed.toLongArray());
        }
    }

    @SubscribeEvent
    public static void onChunkLoad(ChunkEvent.Load event) {
        if (!(event.getLevel() instanceof ServerLevel level)
                || !(level.getChunkSource().getGenerator() instanceof WastelandChunkGenerator)
                || !(event.getChunk() instanceof LevelChunk chunk)) {
            return;
        }
        var vault = WastelandVault.get(level);
        if (vault != null && (chunk.getStartForStructure(vault) != null || !chunk.getReferencesForStructure(vault).isEmpty()
                || !data(level).forChunk(chunk.getPos()).isEmpty())) {
            QUEUES.computeIfAbsent(level, ignored -> new LinkedHashMap<>()).put(chunk.getPos().toLong(), chunk);
        }
    }

    @SubscribeEvent
    public static void onChunkUnload(ChunkEvent.Unload event) {
        var queue = QUEUES.get(event.getLevel());
        if (queue != null) {
            queue.remove(event.getChunk().getPos().toLong(), event.getChunk());
        }
    }

    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        QUEUES.remove(event.getLevel());
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.level instanceof ServerLevel level)) {
            return;
        }
        var queue = QUEUES.get(level);
        if (queue == null || queue.isEmpty()) {
            return;
        }
        var vault = WastelandVault.get(level);
        if (vault == null) {
            return;
        }
        var ready = new ArrayList<LevelChunk>(CHUNKS_PER_TICK);
        for (var chunk : queue.values()) {
            var pos = chunk.getPos();
            if (level.getChunkSource().getChunkNow(pos.x, pos.z) == chunk) {
                ready.add(chunk);
                if (ready.size() == CHUNKS_PER_TICK) {
                    break;
                }
            }
        }
        var data = data(level);
        Consumer<ChunkPos> enqueueLoaded = pos -> {
            var loaded = level.getChunkSource().getChunkNow(pos.x, pos.z);
            if (loaded != null) {
                queue.putIfAbsent(pos.toLong(), loaded);
            }
        };
        for (var chunk : ready) {
            if (!queue.remove(chunk.getPos().toLong(), chunk)) {
                continue;
            }
            var origin = chunk.getStartForStructure(vault);
            if (origin != null) {
                data.discover(origin, enqueueLoaded);
            }
            // Read the starts of nearby references even if the entrance chunk hasn't reached FULL yet.
            level.structureManager().fillStartsForStructure(vault, chunk.getReferencesForStructure(vault),
                    start -> data.discover(start, enqueueLoaded));
            placePending(level, chunk, data.forChunk(chunk.getPos()));
        }
    }

    private static void placePending(ServerLevel level, LevelChunk chunk, List<StructureStart> starts) {
        var completed = COMPLETED_BY_CHUNK.getOrDefault(chunk, LongSet.of());
        for (var start : starts) {
            long key = start.getChunkPos().toLong();
            if (!completed.contains(key)) {
                WastelandMapping.finishPending(level, chunk);
                WastelandVault.placeChunk(level, start, chunk);
                // Record completion in the same chunk save as the generated blocks and block entities.
                var updated = new LongOpenHashSet(completed);
                updated.add(key);
                COMPLETED_BY_CHUNK.put(chunk, updated);
                completed = updated;
                chunk.setUnsaved(true);
            }
        }
    }

    static List<StructureStart> completedStarts(ServerLevel level, LevelChunk chunk) {
        var completed = COMPLETED_BY_CHUNK.get(chunk);
        return completed == null ? List.of() : data(level).forChunk(chunk.getPos()).stream()
                .filter(start -> completed.contains(start.getChunkPos().toLong())).toList();
    }

    private static LevelChunk unwrap(ChunkAccess chunk) {
        if (chunk instanceof LevelChunk full) {
            return full;
        }
        return chunk instanceof ImposterProtoChunk imposter ? imposter.getWrapped() : null;
    }

    private static OverflowData data(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(tag -> new OverflowData(level, tag),
                () -> new OverflowData(level), "nuclearcraft_vault_overflow");
    }

    static final class OverflowData extends SavedData {
        private final ServerLevel level;
        private final LongSet seen = new LongOpenHashSet();
        private final Long2ObjectMap<StructureStart> oversized = new Long2ObjectOpenHashMap<>();
        private final Long2ObjectMap<List<StructureStart>> byChunk = new Long2ObjectOpenHashMap<>();

        OverflowData(ServerLevel level) {
            this.level = level;
        }

        OverflowData(ServerLevel level, CompoundTag tag) {
            this(level);
            var context = StructurePieceSerializationContext.fromLevel(level);
            for (Tag entry : tag.getList("Starts", Tag.TAG_COMPOUND)) {
                var start = StructureStart.loadStaticStart(context, (CompoundTag) entry, level.getSeed());
                if (start != null) {
                    index(start, pos -> {});
                }
            }
        }

        void discover(StructureStart start, Consumer<ChunkPos> newlyIndexed) {
            if (index(start, newlyIndexed)) {
                setDirty();
            }
        }

        private boolean index(StructureStart start, Consumer<ChunkPos> newlyIndexed) {
            var pieces = start.getPieces();
            var origin = start.getChunkPos();
            if (pieces.isEmpty() || !(pieces.get(pieces.size() - 1) instanceof VaultPiece entrance)
                    || !entrance.isNaturalEntrance() || !seen.add(origin.toLong())) {
                return false;
            }
            for (var pos : WastelandVault.affectedChunks(pieces)) {
                if (pos.getChessboardDistance(origin) <= WastelandVault.REFERENCE_RADIUS) {
                    continue;
                }
                oversized.putIfAbsent(origin.toLong(), start);
                byChunk.computeIfAbsent(pos.toLong(), ignored -> new ArrayList<>()).add(start);
                newlyIndexed.accept(pos);
            }
            return oversized.containsKey(origin.toLong());
        }

        List<StructureStart> forChunk(ChunkPos pos) {
            return byChunk.getOrDefault(pos.toLong(), List.of());
        }

        @Override
        public CompoundTag save(CompoundTag tag) {
            var context = StructurePieceSerializationContext.fromLevel(level);
            var starts = new ListTag();
            for (var start : oversized.values()) {
                starts.add(start.createTag(context, start.getChunkPos()));
            }
            tag.put("Starts", starts);
            return tag;
        }
    }
}
