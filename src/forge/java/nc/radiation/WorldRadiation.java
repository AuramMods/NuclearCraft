package nc.radiation;

import it.unimi.dsi.fastutil.longs.Long2DoubleOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import nc.NuclearCraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.QuartPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

@Mod.EventBusSubscriber(modid = NuclearCraft.MOD_ID)
public final class WorldRadiation {
    private static final String SAVE_ID = "nuclearcraft_radiation";
    private static final Direction[] SIDES = Direction.values();
    private static final Direction[] NEIGHBORS = {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};
    private static final Map<ServerLevel, LoadedChunks> LOADED = new IdentityHashMap<>();

    private WorldRadiation() {}

    @SubscribeEvent
    public static void onChunkLoad(ChunkEvent.Load event) {
        if (event.getLevel() instanceof ServerLevel level && event.getChunk() instanceof LevelChunk chunk) {
            LOADED.computeIfAbsent(level, ignored -> new LoadedChunks()).add(chunk);
        }
    }

    @SubscribeEvent
    public static void onChunkUnload(ChunkEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) {
            LoadedChunks chunks = LOADED.get(level);
            if (chunks != null) {
                chunks.remove(event.getChunk().getPos().toLong());
            }
        }
    }

    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) {
            LOADED.remove(level);
        }
    }

    @SubscribeEvent
    public static void onTick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.level instanceof ServerLevel level)
                || !RadiationConfig.ENABLED.get()) {
            return;
        }
        LoadedChunks loaded = LOADED.get(level);
        if (loaded == null || loaded.chunks.isEmpty()) {
            return;
        }
        int count = loaded.chunks.size();
        int size = Math.min(count, RadiationConfig.CHUNKS_PER_TICK.get());
        int start = loaded.random.nextInt(count);
        Levels readings = store(level);
        Direction side = SIDES[(int) Math.floorMod(level.getGameTime(), SIDES.length)];
        var selected = new LevelChunk[size];
        for (int i = 0; i < size; i++) {
            selected[i] = loaded.chunks.get((start + i) % count);
        }
        for (int i = 0; i < size; i++) {
            sample(level, selected[i], readings, side, i == 0, loaded.random);
        }
        for (LevelChunk chunk : selected) {
            spread(loaded, chunk, readings);
        }
    }

    public static double rate(ServerLevel level, BlockPos pos) {
        return RadiationConfig.ENABLED.get() ? store(level).rates.get(ChunkPos.asLong(pos)) : 0;
    }

    public static void addLeak(ServerLevel level, BlockPos pos, double amount) {
        if (amount > 0 && Double.isFinite(amount) && RadiationConfig.ENABLED.get()) {
            Levels readings = store(level);
            long chunk = ChunkPos.asLong(pos);
            readings.set(chunk, readings.rates.get(chunk) + amount);
        }
    }

    private static void sample(ServerLevel level, LevelChunk chunk, Levels readings, Direction side,
                               boolean checkBlock, RandomSource random) {
        var pos = chunk.getPos();
        BlockPos sample = pos.getBlockAt(random.nextInt(16),
                level.getMinBuildHeight() + random.nextInt(level.getHeight()), random.nextInt(16));
        String dimension = level.dimension().location().toString();
        double sources = configuredRate(RadiationConfig.WORLD_RATES.get(), dimension, 0);
        double limit = RadiationConfig.CHUNK_LIMIT.get();
        limit = Math.min(limit < 0 ? Double.MAX_VALUE : limit,
                configuredRate(RadiationConfig.WORLD_LIMITS.get(), dimension, Double.MAX_VALUE));
        var biome = chunk.getNoiseBiome(QuartPos.fromBlock(sample.getX()), QuartPos.fromBlock(sample.getY()),
                QuartPos.fromBlock(sample.getZ())).unwrapKey();
        if (biome.isPresent()) {
            String id = biome.get().location().toString();
            if (!RadiationConfig.BIOME_BLACKLIST.get().contains(dimension)) {
                sources += configuredRate(RadiationConfig.BIOME_RATES.get(), id, 0);
            }
            limit = Math.min(limit, configuredRate(RadiationConfig.BIOME_LIMITS.get(), id, Double.MAX_VALUE));
        }
        if (checkBlock && RadiationConfig.CHECK_BLOCKS.get()) {
            sources += RadiationSources.itemRate(new ItemStack(chunk.getBlockState(sample).getBlock()));
        }
        if (RadiationConfig.DROPPED_SOURCES.get()) {
            AABB bounds = new AABB(pos.getMinBlockX(), level.getMinBuildHeight(), pos.getMinBlockZ(),
                    pos.getMaxBlockX() + 1, level.getMaxBuildHeight(), pos.getMaxBlockZ() + 1);
            for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, bounds,
                    entity -> !entity.isRemoved() && entity.chunkPosition().equals(pos))) {
                sources += RadiationSources.itemRate(item.getItem());
            }
        }
        if (RadiationConfig.INVENTORY_SOURCES.get()) {
            for (var player : level.players()) {
                if (!player.isAlive() || !player.chunkPosition().equals(pos)) {
                    continue;
                }
                var inventory = player.getInventory();
                for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
                    // Carried slots contribute one source each to ambient radiation in 2o.9.7.
                    sources += RadiationSources.stackSourceRate(inventory.getItem(slot));
                }
            }
        }
        if (RadiationConfig.MACHINE_SOURCES.get()) {
            double containers = RadiationConfig.CONTAINER_SOURCES.get();
            for (BlockEntity entity : chunk.getBlockEntities().values()) {
                if (!entity.isRemoved()) {
                    double emission = entity instanceof ChunkRadiationSource source ? source.radiationEmission() : 0;
                    sources += emission + (containers > 0 ? containerRate(entity, side) * containers : 0);
                }
            }
        }

        long key = pos.toLong();
        double current = readings.rates.get(key);
        double speed = current < sources ? RadiationConfig.SPREAD.get() : RadiationConfig.CHUNK_DECAY.get();
        readings.set(key, Math.min(limit, Math.max(0, current + (sources - current) * speed)));
    }

    private static double containerRate(BlockEntity entity, Direction side) {
        double rate = 0;
        if (!(entity instanceof RandomizableContainerBlockEntity chest) || !chest.saveWithoutMetadata().contains("LootTable")) {
            var handler = entity.getCapability(ForgeCapabilities.ITEM_HANDLER, side).orElse(null);
            if (handler != null) {
                for (int slot = 0; slot < handler.getSlots(); ++slot) {
                    rate += RadiationSources.itemRate(handler.getStackInSlot(slot));
                }
            }
        }
        var tanks = entity.getCapability(ForgeCapabilities.FLUID_HANDLER, side).orElse(null);
        if (tanks != null) {
            for (int tank = 0; tank < tanks.getTanks(); ++tank) {
                rate += RadiationSources.fluidRate(tanks.getFluidInTank(tank));
            }
        }
        return rate;
    }

    private static void spread(LoadedChunks loaded, LevelChunk chunk, Levels readings) {
        ChunkPos pos = chunk.getPos();
        long key = pos.toLong();
        double source = readings.rates.get(key);
        if (source <= 0) {
            return;
        }
        long targetKey = 0;
        int neighbors = 0;
        for (Direction direction : NEIGHBORS) {
            long neighbor = ChunkPos.asLong(pos.x + direction.getStepX(), pos.z + direction.getStepZ());
            if (loaded.indices.containsKey(neighbor) && loaded.random.nextInt(++neighbors) == 0) {
                targetKey = neighbor;
            }
        }
        if (neighbors > 0) {
            double target = readings.rates.get(targetKey);
            if (target == 0 || source / target > 1D + RadiationConfig.GRADIENT.get()) {
                double transfer = (source - target) * RadiationConfig.SPREAD.get();
                readings.set(key, source - transfer);
                readings.set(targetKey, target + transfer);
            }
        }
    }

    private static double configuredRate(List<? extends String> entries, String id, double fallback) {
        for (String entry : entries) {
            int delimiter = entry.lastIndexOf('_');
            if (delimiter == id.length() && entry.startsWith(id)) {
                return Double.parseDouble(entry.substring(delimiter + 1));
            }
        }
        return fallback;
    }

    private static Levels store(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(Levels::load, Levels::new, SAVE_ID);
    }

    private static final class LoadedChunks {
        private final RandomSource random = RandomSource.create();
        private final ArrayList<LevelChunk> chunks = new ArrayList<>();
        private final Long2IntOpenHashMap indices = new Long2IntOpenHashMap();

        LoadedChunks() {
            indices.defaultReturnValue(-1);
        }

        void add(LevelChunk chunk) {
            int previous = indices.putIfAbsent(chunk.getPos().toLong(), chunks.size());
            if (previous < 0) {
                chunks.add(chunk);
            } else {
                chunks.set(previous, chunk);
            }
        }

        void remove(long key) {
            int index = indices.remove(key);
            if (index >= 0) {
                LevelChunk last = chunks.remove(chunks.size() - 1);
                if (index < chunks.size()) {
                    chunks.set(index, last);
                    indices.put(last.getPos().toLong(), index);
                }
            }
        }
    }

    private static final class Levels extends SavedData {
        private final Long2DoubleOpenHashMap rates = new Long2DoubleOpenHashMap();

        void set(long key, double rate) {
            double next = Double.isFinite(rate) && rate >= RadiationConfig.MINIMUM_RATE.get() ? rate : 0;
            if (next != rates.get(key)) {
                if (next == 0) {
                    rates.remove(key);
                } else {
                    rates.put(key, next);
                }
                setDirty();
            }
        }

        private static Levels load(CompoundTag saved) {
            var result = new Levels();
            for (Tag entry : saved.getList("chunks", Tag.TAG_COMPOUND)) {
                CompoundTag chunk = (CompoundTag) entry;
                double rate = chunk.getDouble("rate");
                if (Double.isFinite(rate) && rate > 0) {
                    result.rates.put(chunk.getLong("pos"), rate);
                }
            }
            return result;
        }

        @Override
        public CompoundTag save(CompoundTag tag) {
            ListTag chunks = new ListTag();
            for (var entry : rates.long2DoubleEntrySet()) {
                CompoundTag chunk = new CompoundTag();
                chunk.putLong("pos", entry.getLongKey());
                chunk.putDouble("rate", entry.getDoubleValue());
                chunks.add(chunk);
            }
            tag.put("chunks", chunks);
            return tag;
        }
    }
}
