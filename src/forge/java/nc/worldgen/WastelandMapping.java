package nc.worldgen;

import nc.NuclearCraft;
import nc.init.NCWasteland;
import nc.recipe.TerrainRecipe;
import nc.recipe.TerrainRecipe.Kind;
import nc.worldgen.structure.vault.WastelandVault;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ImposterProtoChunk;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.common.IPlantable;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.ChunkDataEvent;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

@Mod.EventBusSubscriber(modid = NuclearCraft.MOD_ID)
public final class WastelandMapping {
    private static final String PENDING_KEY = "nuclearcraft_wasteland_mapping_pending";
    private static final Set<LevelChunk> PENDING =
            Collections.synchronizedSet(Collections.newSetFromMap(new WeakHashMap<>()));
    private static final Map<ServerLevel, Map<Long, LevelChunk>> QUEUES = new IdentityHashMap<>();
    private static final TagKey<Block> GLASS = TagKey.create(Registries.BLOCK, ResourceLocation.tryBuild("forge", "glass"));
    private static final TagKey<Block> GLASS_PANES = TagKey.create(Registries.BLOCK, ResourceLocation.tryBuild("forge", "glass_panes"));
    private static final TagKey<Block> PRESERVED = BlockTags.create(NuclearCraft.id("wasteland_preserved"));

    private WastelandMapping() {}

    @SubscribeEvent
    public static void onChunkDataLoad(ChunkDataEvent.Load event) {
        LevelChunk chunk = unwrap(event.getChunk());
        if (chunk != null && chunk.getLevel().dimension().equals(NCWasteland.DIMENSION)
                && event.getData().getBoolean(PENDING_KEY)) {
            PENDING.add(chunk);
        }
    }

    @SubscribeEvent
    public static void onChunkDataSave(ChunkDataEvent.Save event) {
        LevelChunk chunk = unwrap(event.getChunk());
        if (chunk != null && PENDING.contains(chunk)) {
            event.getData().putBoolean(PENDING_KEY, true);
        }
    }

    @SubscribeEvent
    public static void onChunkLoad(ChunkEvent.Load event) {
        if (!(event.getLevel() instanceof ServerLevel level) || !level.dimension().equals(NCWasteland.DIMENSION)
                || !(event.getChunk() instanceof LevelChunk chunk)) {
            return;
        }
        if (event.isNewChunk() && NCWasteland.mappingEnabled()) {
            PENDING.add(chunk);
            chunk.setUnsaved(true);
        }
        if (PENDING.contains(chunk)) {
            // The load event can run before the chunk is generally accessible through the level.
            QUEUES.computeIfAbsent(level, ignored -> new LinkedHashMap<>()).put(chunk.getPos().toLong(), chunk);
        }
    }

    @SubscribeEvent
    public static void onChunkUnload(ChunkEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) {
            var queue = QUEUES.get(level);
            if (queue != null) {
                queue.remove(event.getChunk().getPos().toLong(), event.getChunk());
            }
        }
    }

    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) {
            QUEUES.remove(level);
        }
    }

    @SubscribeEvent
    public static void onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.level instanceof ServerLevel level)
                || !NCWasteland.mappingEnabled()) {
            return;
        }
        var queue = QUEUES.get(level);
        if (queue == null || queue.isEmpty()) {
            return;
        }
        int remaining = Math.max(1, NCWasteland.mappingChunksPerTick());
        var recipes = TerrainRecipe.ordered(level, Kind.WASTELAND);
        var ready = new ArrayList<LevelChunk>(remaining);
        for (LevelChunk chunk : queue.values()) {
            ChunkPos pos = chunk.getPos();
            if (level.getChunkSource().getChunkNow(pos.x, pos.z) == chunk) {
                ready.add(chunk);
                if (ready.size() == remaining) {
                    break;
                }
            }
        }
        for (LevelChunk chunk : ready) {
            long pos = chunk.getPos().toLong();
            if (queue.get(pos) == chunk) {
                // Block callbacks may add or remove chunks from the queue.
                finish(level, chunk, recipes);
            }
        }
    }

    public static void finishPending(ServerLevel level, LevelChunk chunk) {
        if (PENDING.contains(chunk) && NCWasteland.mappingEnabled()) {
            finish(level, chunk, TerrainRecipe.ordered(level, Kind.WASTELAND));
        }
    }

    private static void finish(ServerLevel level, LevelChunk chunk, List<TerrainRecipe> recipes) {
        map(level, chunk, recipes);
        PENDING.remove(chunk);
        var queue = QUEUES.get(level);
        if (queue != null) {
            queue.remove(chunk.getPos().toLong(), chunk);
        }
        chunk.setUnsaved(true);
    }

    static void map(ServerLevel level, LevelChunk chunk, List<TerrainRecipe> recipes) {
        var origin = chunk.getPos();
        var random = RandomSource.create(level.getSeed() ^ origin.toLong());
        var mutable = new BlockPos.MutableBlockPos();
        var vaults = WastelandVault.pieceBounds(level, chunk);
        boolean mapsAir = recipes.stream().anyMatch(recipe -> recipe.input().matches(Blocks.AIR.defaultBlockState())
                || recipe.input().matches(Blocks.CAVE_AIR.defaultBlockState())
                || recipe.input().matches(Blocks.VOID_AIR.defaultBlockState()));
        LevelChunkSection[] sections = chunk.getSections();
        for (int index = 0; index < sections.length; index++) {
            LevelChunkSection section = sections[index];
            if (section.hasOnlyAir() && !mapsAir) {
                continue;
            }
            int bottom = chunk.getMinBuildHeight() + index * 16;
            for (int y = 0; y < 16; y++) {
                for (int z = 0; z < 16; z++) {
                    for (int x = 0; x < 16; x++) {
                        BlockState state = section.getBlockState(x, y, z);
                        if (state.hasBlockEntity() || state.isAir() && !mapsAir) {
                            continue;
                        }
                        mutable.set(origin.getMinBlockX() + x, bottom + y, origin.getMinBlockZ() + z);
                        if (insideVault(vaults, mutable)) {
                            continue;
                        }
                        BlockState replacement = replacement(level, mutable, state, recipes, random);
                        if (state != replacement) {
                            level.setBlock(mutable, replacement, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
                        }
                    }
                }
            }
        }
    }

    private static boolean insideVault(List<BoundingBox> vaults, BlockPos pos) {
        for (var bounds : vaults) {
            if (bounds.isInside(pos)) {
                return true;
            }
        }
        return false;
    }

    static BlockState replacement(ServerLevel level, BlockPos pos, BlockState state,
                                  List<TerrainRecipe> recipes, RandomSource random) {
        for (var recipe : recipes) {
            if (recipe.input().matches(state)) {
                return recipe.result();
            }
        }
        if (state.isAir() || state.is(PRESERVED) || state.is(BlockTags.LOGS) || state.is(Blocks.COARSE_DIRT)) {
            return state;
        }
        BlockState air = Blocks.AIR.defaultBlockState();
        if (state.is(GLASS) || state.is(GLASS_PANES) || state.is(BlockTags.WOOL)
                || state.is(BlockTags.WOOL_CARPETS)) {
            return random.nextBoolean() ? air : withoutWater(state);
        }
        if (state.is(BlockTags.WOODEN_DOORS) || state.is(BlockTags.LEAVES) || state.is(BlockTags.SNOW)
                || state.canBeReplaced()) {
            return air;
        }
        if (state.is(BlockTags.DIRT) || state.is(Blocks.DIRT_PATH) || state.is(Blocks.FARMLAND)) {
            return Blocks.COARSE_DIRT.defaultBlockState();
        }
        if (state.getBlock() instanceof IPlantable) {
            BlockState bush = Blocks.DEAD_BUSH.defaultBlockState();
            return random.nextBoolean() && bush.canSurvive(level, pos) ? bush : air;
        }
        if (state.is(BlockTags.PLANKS) || state.is(BlockTags.WOODEN_FENCES) || state.is(BlockTags.WOODEN_SLABS)
                || state.is(BlockTags.WOODEN_STAIRS) || state.is(BlockTags.WOODEN_TRAPDOORS)
                || state.is(BlockTags.MINEABLE_WITH_AXE) && state.getMapColor(level, pos) == MapColor.WOOD) {
            return random.nextInt(8) == 0 ? air : withoutWater(state);
        }
        if (state.getFluidState().is(FluidTags.WATER)) {
            return state.hasProperty(BlockStateProperties.WATERLOGGED) ? withoutWater(state) : air;
        }
        return state;
    }

    private static BlockState withoutWater(BlockState state) {
        return state.hasProperty(BlockStateProperties.WATERLOGGED)
                ? state.setValue(BlockStateProperties.WATERLOGGED, false) : state;
    }

    private static LevelChunk unwrap(ChunkAccess chunk) {
        if (chunk instanceof LevelChunk levelChunk) {
            return levelChunk;
        }
        return chunk instanceof ImposterProtoChunk imposter ? imposter.getWrapped() : null;
    }
}
