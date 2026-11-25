package nc.worldgen;

import nc.NuclearCraft;
import nc.init.NCOres.Ore;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.levelgen.feature.configurations.OreConfiguration;
import net.minecraft.world.level.levelgen.placement.CountPlacement;
import net.minecraft.world.level.levelgen.placement.HeightRangePlacement;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(NuclearCraft.MOD_ID)
@PrefixGameTestTemplate(false)
public final class OreWorldgenGameTests {
    private OreWorldgenGameTests() {}

    @GameTest(template = "empty")
    public static void stockOresExposeWorldgenData(GameTestHelper helper) {
        var placedFeatures = helper.getLevel().registryAccess().registryOrThrow(Registries.PLACED_FEATURE);
        for (Ore ore : Ore.values()) {
            var id = NuclearCraft.id("ore_" + ore.getSerializedName());
            var placed = placedFeatures.getOrThrow(ResourceKey.create(Registries.PLACED_FEATURE, id));
            var configured = placed.feature();
            helper.assertTrue(configured.is(ResourceKey.create(Registries.CONFIGURED_FEATURE, id)),
                    id + " uses its registered configured feature");
            helper.assertTrue(configured.value().feature() instanceof LegacyOreFeature,
                    id + " generates the legacy vein shape");
            helper.assertTrue(configured.value().config() instanceof OreConfiguration config && config.size == ore.size(),
                    id + " exposes its vein size in a vanilla ore configuration");
            helper.assertTrue(placed.placement().stream().anyMatch(CountPlacement.class::isInstance),
                    id + " exposes its attempt count as a vanilla placement modifier");
            helper.assertTrue(placed.placement().stream().anyMatch(HeightRangePlacement.class::isInstance),
                    id + " exposes its height range as a vanilla placement modifier");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void smallOreVeinPlacesTheLegacyBlocks(GameTestHelper helper) {
        var level = helper.getLevel();
        var stock = level.registryAccess().registryOrThrow(Registries.CONFIGURED_FEATURE)
                .getOrThrow(ResourceKey.create(Registries.CONFIGURED_FEATURE, NuclearCraft.id("ore_thorium")));
        var config = (OreConfiguration) stock.config();
        var feature = new ConfiguredFeature<>((LegacyOreFeature) stock.feature(),
                new OreConfiguration(config.targetStates, 3));
        var target = config.targetStates.get(0).state.getBlock();
        var first = new BlockPos(2, 2, 1);
        var second = new BlockPos(2, 3, 1);
        var origin = helper.absolutePos(new BlockPos(2, 4, 2)).offset(-8, 0, -8);
        var generator = level.getChunkSource().getGenerator();
        var lower = new BlockPos(0, 1, 0);
        var upper = new BlockPos(4, 4, 4);
        for (var pos : BlockPos.betweenClosed(lower, upper)) {
            helper.setBlock(pos, Blocks.STONE);
        }
        helper.setBlock(first, Blocks.NETHERRACK);
        helper.setBlock(second, Blocks.END_STONE);
        helper.assertTrue(feature.place(level, generator, new LegacyRandomSource(0L), origin), "Vein places ore");
        for (var pos : BlockPos.betweenClosed(lower, upper)) {
            helper.assertBlockPresent(pos.equals(first) || pos.equals(second) ? target : Blocks.STONE, pos);
        }

        helper.setBlock(first, Blocks.POLISHED_GRANITE);
        helper.setBlock(second, Blocks.DIORITE);
        helper.assertTrue(feature.place(level, generator, new LegacyRandomSource(0L), origin), "Vein replaces natural stone");
        helper.assertBlockPresent(Blocks.POLISHED_GRANITE, first);
        helper.assertBlockPresent(target, second);
        helper.succeed();
    }
}
