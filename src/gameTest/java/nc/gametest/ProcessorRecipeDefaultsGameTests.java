package nc.gametest;

import com.google.gson.JsonParser;
import io.netty.buffer.Unpooled;
import nc.NuclearCraft;
import nc.init.NCItemProcessors.Machine;
import nc.init.NCItemProcessors.Rate;
import nc.recipe.ElectrolyzerRecipe;
import nc.recipe.ItemProcessorRecipe;
import nc.recipe.MelterRecipe;
import nc.recipe.ProcessingRecipe;
import nc.recipe.ProcessorRecipeDefaults;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.fluids.FluidStack;
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

    @GameTest(template = "empty")
    public static void syncsBatchProvenanceForStockAndPackElectrolysis(GameTestHelper helper) {
        var id = NuclearCraft.id("electrolyzer/water");
        var stock = (ElectrolyzerRecipe) helper.getLevel().getRecipeManager().byKey(id).orElseThrow();
        var replacement = new ElectrolyzerRecipe(id, stock.input(), stock.products(), stock.ticks(),
                stock.energyPerTick(), stock.radiation(), null);
        var serializer = new ElectrolyzerRecipe.Serializer();
        var buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            serializer.toNetwork(buffer, stock);
            serializer.toNetwork(buffer, replacement);
            var receivedStock = serializer.fromNetwork(id, buffer);
            var receivedReplacement = serializer.fromNetwork(id, buffer);
            helper.assertTrue(new ProcessorRecipeDefaults(Rate.ELECTROLYZER, false).equals(receivedStock.defaults())
                            && receivedStock.input().matchedAmount(new FluidStack(Fluids.WATER, 500), 1) == 500 && receivedStock.results().get(1).getAmount() == 250
                            && receivedReplacement.defaults() == null && !buffer.isReadable(),
                    "The recipe packet preserves stock provenance and authored volumes, including same-ID replacements");
        } finally {
            buffer.release();
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void syncsEmptyMelterAndSparseAssemblerInputs(GameTestHelper helper) {
        var melterId = NuclearCraft.id("qa_empty_melter");
        var melterSerializer = new MelterRecipe.Serializer();
        var melter = melterSerializer.fromJson(melterId, JsonParser.parseString("""
                {"ingredient": null, "result": {"fluid": "minecraft:water", "amount": 7},
                 "ticks": 4, "energy_per_tick": 5}
                """).getAsJsonObject());
        var assemblerId = NuclearCraft.id("qa_sparse_assembler");
        var assemblerSerializer = new ItemProcessorRecipe.Serializer(Machine.ASSEMBLER);
        var assembler = assemblerSerializer.fromJson(assemblerId, JsonParser.parseString("""
                {"ingredients": [{}, {"ingredient": {"item": "minecraft:diamond"}}, null, {}],
                 "result": {"item": "minecraft:gold_ingot"}, "ticks": 4, "energy_per_tick": 5}
                """).getAsJsonObject());

        var buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            melterSerializer.toNetwork(buffer, melter);
            assemblerSerializer.toNetwork(buffer, assembler);
            var receivedMelter = melterSerializer.fromNetwork(melterId, buffer);
            var receivedAssembler = assemblerSerializer.fromNetwork(assemblerId, buffer);
            helper.assertTrue(receivedMelter.matches(new SimpleContainer(1), helper.getLevel())
                            && !receivedMelter.matches(new SimpleContainer(new ItemStack(Items.DIAMOND)), helper.getLevel()),
                    "The synced Melter recipe matches only an empty item slot");
            var supplies = new SimpleContainer(4);
            supplies.setItem(3, new ItemStack(Items.DIAMOND));
            int[] consumed = receivedAssembler.match(supplies);
            helper.assertTrue(consumed != null && consumed[0] == 0 && consumed[3] == 1,
                    "The synced Assembler recipe can use the diamond in any input slot");
            supplies.setItem(0, new ItemStack(Items.DIAMOND));
            helper.assertTrue(receivedAssembler.match(supplies) == null && !buffer.isReadable(),
                    "An extra item prevents the sparse recipe from matching");
        } finally {
            buffer.release();
        }
        helper.succeed();
    }
}
