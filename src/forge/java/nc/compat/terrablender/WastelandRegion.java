package nc.compat.terrablender;

import com.mojang.datafixers.util.Pair;
import nc.NuclearCraft;
import nc.init.NCFluidProducts;
import nc.init.NCWasteland;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.SurfaceRules;
import terrablender.api.Region;
import terrablender.api.RegionType;
import terrablender.api.Regions;
import terrablender.api.SurfaceRuleManager;

import java.util.function.Consumer;

public final class WastelandRegion extends Region {
    private WastelandRegion(int weight) {
        super(NuclearCraft.id("wasteland"), RegionType.OVERWORLD, weight);
    }

    public static void register(int weight) {
        Regions.register(new WastelandRegion(weight));
        SurfaceRuleManager.addSurfaceRules(SurfaceRuleManager.RuleCategory.OVERWORLD, NuclearCraft.MOD_ID,
                SurfaceRules.ifTrue(SurfaceRules.isBiome(NCWasteland.BIOME),
                        SurfaceRules.ifTrue(SurfaceRules.abovePreliminarySurface(), SurfaceRules.sequence(
                                SurfaceRules.ifTrue(SurfaceRules.ON_FLOOR,
                                        SurfaceRules.state(NCFluidProducts.WASTELAND_EARTH.get().defaultBlockState())),
                                SurfaceRules.ifTrue(SurfaceRules.UNDER_FLOOR,
                                        SurfaceRules.state(Blocks.SAND.defaultBlockState()))))));
    }

    @Override
    public void addBiomes(Registry<Biome> registry, Consumer<Pair<Climate.ParameterPoint, ResourceKey<Biome>>> mapper) {
        addModifiedVanillaOverworldBiomes(mapper, builder -> builder.replaceBiome(Biomes.DESERT, NCWasteland.BIOME));
    }
}
