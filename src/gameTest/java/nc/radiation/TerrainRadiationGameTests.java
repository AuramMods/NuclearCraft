package nc.radiation;

import com.google.gson.JsonParser;
import io.netty.buffer.Unpooled;
import nc.NuclearCraft;
import nc.init.NCTerrainRecipes;
import nc.recipe.TerrainRecipe;
import nc.recipe.TerrainRecipe.Kind;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.List;

@GameTestHolder(NuclearCraft.MOD_ID)
@PrefixGameTestTemplate(false)
public final class TerrainRadiationGameTests {
    private TerrainRadiationGameTests() {}

    @GameTest(template = "empty")
    public static void radiationUsesLegacyTerrainThresholds(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos center = helper.absolutePos(new BlockPos(2, 2, 2));
        var chunk = level.getChunkAt(center);
        var terrain = RadiationTerrain.forTick(level, 1);
        var earth = terrain.mutations().stream().filter(recipe -> recipe.id().getPath().endsWith("/soil"))
                .findFirst().orElseThrow().result();
        helper.assertTrue(terrain.mutations().size() == 4 && terrain.purifications().size() == 1
                        && terrain.mutationThreshold() == 1D && terrain.purificationThreshold() == 0.001D,
                "The production data pack contains all legacy terrain recipes");
        BlockPos sample = null;
        long seed = 0;
        for (; sample == null; seed++) {
            var random = RandomSource.create(seed);
            var point = chunk.getPos().getBlockAt(random.nextInt(16), chunk.getMinBuildHeight()
                    + random.nextInt(chunk.getHeight()), random.nextInt(16));
            if (point.getY() >= 200 && point.getY() < level.getMaxBuildHeight() - 1) {
                sample = point;
            }
        }
        seed--;
        var original = level.getBlockState(sample);
        try {
            level.setBlockAndUpdate(sample, Blocks.DIRT.defaultBlockState());
            terrain.apply(level, chunk, 9.99D, RandomSource.create(seed));
            helper.assertTrue(level.getBlockState(sample).is(Blocks.DIRT), "Dirt resists below 10 Rad/t");
            terrain.apply(level, chunk, 10D, RandomSource.create(seed));
            helper.assertTrue(level.getBlockState(sample) == earth, "Dirt mutates at 10 Rad/t");
            terrain.apply(level, chunk, 0.001D, RandomSource.create(seed));
            helper.assertTrue(level.getBlockState(sample) == earth, "Wasteland Earth stays at 0.001 Rad/t");
            terrain.apply(level, chunk, 0.00099D, RandomSource.create(seed));
            helper.assertTrue(level.getBlockState(sample).is(Blocks.DIRT), "Wasteland Earth recovers below 0.001 Rad/t");
            var leaves = Blocks.OAK_LEAVES.defaultBlockState().setValue(BlockStateProperties.PERSISTENT, true);
            level.setBlockAndUpdate(sample, leaves);
            terrain.apply(level, chunk, 1D, RandomSource.create(seed));
            helper.assertTrue(level.isEmptyBlock(sample), "Leaves disappear at 1 Rad/t");
            level.setBlockAndUpdate(sample, earth);
            terrain.apply(level, chunk, 0D, RandomSource.create(seed));
            helper.assertTrue(level.getBlockState(sample).is(Blocks.DIRT), "Recovery samples fully clean chunks");
            helper.assertTrue(RadiationTerrain.attempts(1D, 15) == 1
                            && RadiationTerrain.attempts(Double.POSITIVE_INFINITY, 15) == 15,
                    "Logarithmic sampling is capped at the configured rate");
        } finally {
            level.setBlockAndUpdate(sample, original);
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void terrainRecipesSyncTagsAndStateProperties(GameTestHelper helper) {
        var id = NuclearCraft.id("qa/terrain_recipe");
        var serializer = NCTerrainRecipes.serializer(Kind.MUTATION);
        var recipe = serializer.fromJson(id, JsonParser.parseString("""
                {"input":{"tag":"minecraft:leaves","properties":{"persistent":"true"}},
                 "result":{"block":"minecraft:oak_log","properties":{"axis":"x"}},
                 "radiation_threshold":7.5,"priority":3}
                """).getAsJsonObject());
        var packet = new FriendlyByteBuf(Unpooled.buffer());
        TerrainRecipe decoded;
        try {
            serializer.toNetwork(packet, recipe);
            decoded = serializer.fromNetwork(id, packet);
        } finally {
            packet.release();
        }
        var leaves = Blocks.OAK_LEAVES.defaultBlockState();
        helper.assertTrue(recipe.equals(decoded) && decoded.input().matches(leaves.setValue(BlockStateProperties.PERSISTENT, true))
                        && !decoded.input().matches(leaves.setValue(BlockStateProperties.PERSISTENT, false))
                        && !decoded.input().matches(Blocks.STONE.defaultBlockState()),
                "The Forge recipe packet preserves the block tag, state filter, output, threshold, and priority");
        helper.assertTrue(decoded.getType() == NCTerrainRecipes.type(Kind.MUTATION)
                        && helper.getLevel().getRecipeManager().getAllRecipesFor(NCTerrainRecipes.type(Kind.WASTELAND)).equals(List.of()),
                "The default map uses the built-in replacements and Forge registers custom mapping recipes");
        helper.succeed();
    }
}
