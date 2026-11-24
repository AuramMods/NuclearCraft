package nc.worldgen;

import com.google.gson.JsonParser;
import nc.NuclearCraft;
import nc.init.NCTerrainRecipes;
import nc.init.NCWasteland;
import nc.recipe.TerrainRecipe.Kind;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.List;

@GameTestHolder(NuclearCraft.MOD_ID)
@PrefixGameTestTemplate(false)
public final class WastelandMappingGameTests {
    private WastelandMappingGameTests() {}

    @GameTest(template = "empty")
    public static void wastelandUsesMappedAndPreservedStates(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(2, 2, 2));
        var random = RandomSource.create(43L);
        var wetWall = Blocks.COBBLESTONE_WALL.defaultBlockState().setValue(BlockStateProperties.WATERLOGGED, true);
        for (var block : List.of(Blocks.STONE, Blocks.OAK_LOG, Blocks.COARSE_DIRT, Blocks.LADDER, NCWasteland.PORTAL.get())) {
            helper.assertTrue(WastelandMapping.replacement(level, pos, block.defaultBlockState(), List.of(), random).is(block),
                    "Wasteland mapping preserves " + block);
        }
        for (var block : List.of(Blocks.GRASS_BLOCK, Blocks.DIRT, Blocks.DIRT_PATH, Blocks.FARMLAND)) {
            helper.assertTrue(WastelandMapping.replacement(level, pos, block.defaultBlockState(), List.of(), random).is(Blocks.COARSE_DIRT),
                    "Wasteland maps soil " + block + " to coarse dirt");
        }
        for (var block : List.of(Blocks.OAK_LEAVES, Blocks.OAK_DOOR, Blocks.WATER, Blocks.SNOW)) {
            helper.assertTrue(WastelandMapping.replacement(level, pos, block.defaultBlockState(), List.of(), random).isAir(),
                    "Wasteland mapping removes " + block);
        }
        var dryWall = WastelandMapping.replacement(level, pos, wetWall, List.of(), random);
        helper.assertTrue(dryWall.is(Blocks.COBBLESTONE_WALL) && dryWall.getFluidState().isEmpty(),
                "Wasteland mapping drains waterlogged building blocks");
        var recipe = NCTerrainRecipes.serializer(Kind.WASTELAND).fromJson(NuclearCraft.id("qa/terrain_override"),
                JsonParser.parseString("""
                        {"input":{"block":"minecraft:stone"},
                         "result":{"block":"minecraft:oak_log","properties":{"axis":"z"}}}
                        """).getAsJsonObject());
        helper.assertTrue(WastelandMapping.replacement(level, pos, Blocks.STONE.defaultBlockState(), List.of(recipe), random)
                        == recipe.result(),
                "A datapack mapping overrides fallback behavior and sets output block properties");
        helper.succeed();
    }
}
