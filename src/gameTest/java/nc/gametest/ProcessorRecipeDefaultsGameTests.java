package nc.gametest;

import nc.NuclearCraft;
import nc.init.NCItemProcessors.Rate;
import nc.recipe.ProcessingRecipe;
import nc.recipe.ProcessorRecipeDefaults;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.EnumSet;

@GameTestHolder(NuclearCraft.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ProcessorRecipeDefaultsGameTests {
    @GameTest(template = "empty")
    public static void marksEveryShippedProcessorCatalogAndLeavesDatapacksUnmarked(GameTestHelper helper) {
        var manager = helper.getLevel().getRecipeManager();
        var marked = EnumSet.noneOf(Rate.class);
        for (var recipe : manager.getRecipes()) {
            if (recipe instanceof ProcessingRecipe processing && processing.defaults() != null) {
                marked.add(processing.defaults().processor());
            }
        }
        helper.assertTrue(marked.equals(EnumSet.complementOf(EnumSet.of(Rate.ELECTRIC_FURNACE))),
                "Every shipped processor catalog other than vanilla smelting is controlled by its original switch");

        var ore = (ProcessingRecipe) manager.byKey(NuclearCraft.id("manufactory/copper_dust_from_ore")).orElseThrow();
        var ingot = (ProcessingRecipe) manager.byKey(NuclearCraft.id("manufactory/copper_dust_from_ingot")).orElseThrow();
        var conditional = (ProcessingRecipe) manager.byKey(NuclearCraft.id("rock_crusher/soulless_sandstone")).orElseThrow();
        var custom = (ProcessingRecipe) manager.byKey(NuclearCraft.id("melter/processor_upgrade_qa")).orElseThrow();
        helper.assertTrue(new ProcessorRecipeDefaults(Rate.MANUFACTORY, true).equals(ore.defaults())
                        && new ProcessorRecipeDefaults(Rate.MANUFACTORY, false).equals(ingot.defaults())
                        && new ProcessorRecipeDefaults(Rate.ROCK_CRUSHER, false).equals(conditional.defaults())
                        && custom.defaults() == null,
                "Bundled ore and conditional recipes carry their defaults, and custom recipes keep independent provenance");
        helper.succeed();
    }
}
