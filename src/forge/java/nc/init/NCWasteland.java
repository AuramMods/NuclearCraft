package nc.init;

import com.mojang.serialization.Codec;
import nc.NuclearCraft;
import nc.compat.terrablender.WastelandRegion;
import nc.worldgen.GlowingMushroomBlock;
import nc.worldgen.GlowingMushroomPatch;
import nc.worldgen.HugeGlowingMushroomBlock;
import nc.worldgen.HugeGlowingMushroomFeature;
import nc.worldgen.WastelandBiomeSource;
import nc.worldgen.WastelandChunkGenerator;
import nc.worldgen.WastelandEntrances;
import nc.worldgen.WastelandPortalBlock;
import nc.worldgen.structure.vault.WastelandVault;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.common.world.BiomeModifier;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.List;
import java.util.stream.IntStream;

public final class NCWasteland {
    public static final ResourceKey<Level> DIMENSION = ResourceKey.create(Registries.DIMENSION, NuclearCraft.id("wasteland"));
    public static final ResourceKey<Biome> BIOME = ResourceKey.create(Registries.BIOME, NuclearCraft.id("nuclear_wasteland"));
    public static final TagKey<Biome> MUSHROOM_BIOMES = TagKey.create(Registries.BIOME, NuclearCraft.id("glowing_mushroom_biomes"));
    private static final DeferredRegister<Codec<? extends BiomeModifier>> BIOME_MODIFIERS =
            DeferredRegister.create(ForgeRegistries.Keys.BIOME_MODIFIER_SERIALIZERS, NuclearCraft.MOD_ID);
    private static final DeferredRegister<Codec<? extends BiomeSource>> BIOME_SOURCES =
            DeferredRegister.create(Registries.BIOME_SOURCE, NuclearCraft.MOD_ID);
    private static final DeferredRegister<Codec<? extends ChunkGenerator>> CHUNK_GENERATORS =
            DeferredRegister.create(Registries.CHUNK_GENERATOR, NuclearCraft.MOD_ID);
    private static final DeferredRegister<Feature<?>> FEATURES = DeferredRegister.create(Registries.FEATURE, NuclearCraft.MOD_ID);

    private static final ForgeConfigSpec.Builder CONFIG = new ForgeConfigSpec.Builder();
    private static final ForgeConfigSpec.BooleanValue ENABLED = CONFIG
            .comment("Allow Wasteland portal travel. The dimension keeps its resource ID for existing saves.")
            .define("wasteland_dimension_gen", true);
    private static final ForgeConfigSpec.BooleanValue MAPPING_ENABLED =
            CONFIG.define("wasteland_mapping_enabled", true);
    private static final ForgeConfigSpec.IntValue MAPPING_CHUNKS_PER_TICK = CONFIG
            .comment("Chunks mapped per server tick after feature generation. Zero keeps newly generated chunks queued and maps one at a time.")
            .defineInRange("wasteland_mapping_chunks_per_tick", 8, 0, 400);
    private static final ForgeConfigSpec SPEC = CONFIG.build();

    private static final ForgeConfigSpec.Builder WORLDGEN_CONFIG = new ForgeConfigSpec.Builder();
    private static final ForgeConfigSpec.BooleanValue OVERWORLD_BIOME = WORLDGEN_CONFIG.define("wasteland_biome", true);
    private static final ForgeConfigSpec.IntValue OVERWORLD_WEIGHT = WORLDGEN_CONFIG
            .defineInRange("wasteland_biome_weight", 5, 0, 255);
    private static final ForgeConfigSpec.IntValue MUSHROOM_SPREAD_RATE = WORLDGEN_CONFIG
            .defineInRange("mushroom_spread_rate", 16, 0, 511);
    private static final ForgeConfigSpec.BooleanValue MUSHROOM_GENERATION = WORLDGEN_CONFIG.define("mushroom_gen", true);
    private static final ForgeConfigSpec.IntValue MUSHROOM_GENERATION_SIZE = WORLDGEN_CONFIG
            .defineInRange("mushroom_gen_size", 32, 0, 511);
    private static final ForgeConfigSpec.IntValue MUSHROOM_GENERATION_RATE = WORLDGEN_CONFIG
            .defineInRange("mushroom_gen_rate", 64, 0, 511);
    private static final ForgeConfigSpec WORLDGEN_SPEC = WORLDGEN_CONFIG.build();

    public static final RegistryObject<WastelandPortalBlock> PORTAL =
            NCRegistries.BLOCKS.register("wasteland_portal", WastelandPortalBlock::new);
    public static final RegistryObject<Item> PORTAL_ITEM = NCRegistries.ITEMS.register("wasteland_portal",
            () -> new BlockItem(PORTAL.get(), new Item.Properties()));

    public static final RegistryObject<GlowingMushroomBlock> GLOWING_MUSHROOM = NCRegistries.BLOCKS.register("glowing_mushroom",
            () -> new GlowingMushroomBlock(BlockBehaviour.Properties.copy(Blocks.BROWN_MUSHROOM).lightLevel(state -> 15)));
    public static final RegistryObject<HugeGlowingMushroomBlock> GLOWING_MUSHROOM_BLOCK =
            NCRegistries.BLOCKS.register("glowing_mushroom_block", () -> new HugeGlowingMushroomBlock(
                    BlockBehaviour.Properties.copy(Blocks.BROWN_MUSHROOM_BLOCK)
                            .strength(0.2F).sound(SoundType.WOOD).lightLevel(state -> 15)));
    public static final RegistryObject<Item> GLOWING_MUSHROOM_ITEM = NCRegistries.ITEMS.register("glowing_mushroom",
            () -> new BlockItem(GLOWING_MUSHROOM.get(), new Item.Properties()));
    public static final RegistryObject<Item> GLOWING_MUSHROOM_BLOCK_ITEM = NCRegistries.ITEMS.register("glowing_mushroom_block",
            () -> new BlockItem(GLOWING_MUSHROOM_BLOCK.get(), new Item.Properties()));

    public static final List<RegistryObject<SoundEvent>> AMBIENCE = IntStream.rangeClosed(1, 9).mapToObj(track -> {
        String name = "music.nc.wasteland_ambience_" + track;
        return NCRegistries.SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(NuclearCraft.id(name)));
    }).toList();

    private NCWasteland() {}

    public static void register(FMLJavaModLoadingContext context) {
        BIOME_MODIFIERS.register("wasteland_entrances", () -> WastelandEntrances.CODEC);
        BIOME_MODIFIERS.register(context.getModEventBus());
        BIOME_SOURCES.register("wasteland", () -> WastelandBiomeSource.CODEC);
        CHUNK_GENERATORS.register("wasteland", () -> WastelandChunkGenerator.CODEC);
        BIOME_SOURCES.register(context.getModEventBus());
        CHUNK_GENERATORS.register(context.getModEventBus());
        FEATURES.register("glowing_mushroom_patch", GlowingMushroomPatch::new);
        FEATURES.register("huge_glowing_mushroom", HugeGlowingMushroomFeature::new);
        FEATURES.register(context.getModEventBus());
        WastelandVault.register(context.getModEventBus());
        context.registerConfig(ModConfig.Type.SERVER, SPEC, "nuclearcraft-wasteland.toml");
        context.registerConfig(ModConfig.Type.COMMON, WORLDGEN_SPEC, "nuclearcraft-wasteland-worldgen.toml");
        context.getModEventBus().addListener(NCWasteland::setupOverworldBiome);
    }

    private static void setupOverworldBiome(FMLCommonSetupEvent event) {
        if (ModList.get().isLoaded("terrablender") && hasOverworldWasteland()) {
            event.enqueueWork(() -> WastelandRegion.register(OVERWORLD_WEIGHT.get()));
        }
    }

    public static boolean hasOverworldWasteland() {
        return OVERWORLD_BIOME.get() && OVERWORLD_WEIGHT.get() > 0;
    }

    public static boolean mappingEnabled() {
        return MAPPING_ENABLED.get();
    }

    public static int mappingChunksPerTick() {
        return MAPPING_CHUNKS_PER_TICK.get();
    }

    public static int mushroomSpreadRate() {
        return MUSHROOM_SPREAD_RATE.get();
    }

    public static int mushroomGenerationSize() {
        return MUSHROOM_GENERATION.get() ? MUSHROOM_GENERATION_SIZE.get() : 0;
    }

    public static int mushroomGenerationRate() {
        return MUSHROOM_GENERATION_RATE.get();
    }

    public static boolean enabled() {
        return ENABLED.get();
    }
}
