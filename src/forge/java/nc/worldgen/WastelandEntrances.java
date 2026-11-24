package nc.worldgen;

import com.mojang.serialization.Codec;
import nc.init.NCWasteland;
import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraftforge.common.world.BiomeModifier;
import net.minecraftforge.common.world.ModifiableBiomeInfo;
import net.minecraftforge.fml.ModList;

public record WastelandEntrances(Holder<PlacedFeature> portal) implements BiomeModifier {
    public static final Codec<WastelandEntrances> CODEC = PlacedFeature.CODEC.fieldOf("portal")
            .xmap(WastelandEntrances::new, WastelandEntrances::portal).codec();

    @Override
    public void modify(Holder<Biome> biome, Phase phase, ModifiableBiomeInfo.BiomeInfo.Builder builder) {
        if (phase == Phase.ADD && biome.is(Biomes.DESERT) && NCWasteland.hasOverworldWasteland()
                && !ModList.get().isLoaded("terrablender")) {
            builder.getGenerationSettings().addFeature(GenerationStep.Decoration.LAKES, portal);
        }
    }

    @Override
    public Codec<? extends BiomeModifier> codec() {
        return CODEC;
    }
}
