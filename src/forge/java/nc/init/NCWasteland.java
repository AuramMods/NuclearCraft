package nc.init;

import com.mojang.serialization.Codec;
import nc.NuclearCraft;
import nc.compat.terrablender.WastelandRegion;
import nc.worldgen.WastelandBiomeSource;
import nc.worldgen.WastelandChunkGenerator;
import nc.worldgen.WastelandEntrances;
import nc.worldgen.WastelandPortalBlock;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.chunk.ChunkGenerator;
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
    private static final DeferredRegister<Codec<? extends BiomeModifier>> BIOME_MODIFIERS =
            DeferredRegister.create(ForgeRegistries.Keys.BIOME_MODIFIER_SERIALIZERS, NuclearCraft.MOD_ID);
    private static final DeferredRegister<Codec<? extends BiomeSource>> BIOME_SOURCES =
            DeferredRegister.create(Registries.BIOME_SOURCE, NuclearCraft.MOD_ID);
    private static final DeferredRegister<Codec<? extends ChunkGenerator>> CHUNK_GENERATORS =
            DeferredRegister.create(Registries.CHUNK_GENERATOR, NuclearCraft.MOD_ID);

    private static final ForgeConfigSpec.Builder CONFIG = new ForgeConfigSpec.Builder();
    private static final ForgeConfigSpec.BooleanValue ENABLED = CONFIG
            .comment("Allow Wasteland portal travel. The dimension keeps its resource ID for existing saves.")
            .define("wasteland_dimension_gen", true);
    private static final ForgeConfigSpec SPEC = CONFIG.build();

    private static final ForgeConfigSpec.Builder WORLDGEN_CONFIG = new ForgeConfigSpec.Builder();
    private static final ForgeConfigSpec.BooleanValue OVERWORLD_BIOME = WORLDGEN_CONFIG.define("wasteland_biome", true);
    private static final ForgeConfigSpec.IntValue OVERWORLD_WEIGHT = WORLDGEN_CONFIG
            .defineInRange("wasteland_biome_weight", 5, 0, 255);
    private static final ForgeConfigSpec WORLDGEN_SPEC = WORLDGEN_CONFIG.build();

    public static final RegistryObject<WastelandPortalBlock> PORTAL =
            NCRegistries.BLOCKS.register("wasteland_portal", WastelandPortalBlock::new);
    public static final RegistryObject<Item> PORTAL_ITEM = NCRegistries.ITEMS.register("wasteland_portal",
            () -> new BlockItem(PORTAL.get(), new Item.Properties()));

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

    public static boolean enabled() {
        return ENABLED.get();
    }
}
