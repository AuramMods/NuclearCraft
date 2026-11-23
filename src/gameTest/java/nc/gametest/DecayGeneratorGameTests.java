package nc.gametest;

import nc.NuclearCraft;
import nc.init.NCGenerators;
import nc.init.NCItemProcessors.Machine;
import nc.processor.ItemProcessorBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.Objects;

@GameTestHolder(NuclearCraft.MOD_ID)
@PrefixGameTestTemplate(false)
public final class DecayGeneratorGameTests {
    private static final BlockPos POS = new BlockPos(2, 1, 2);

    @GameTest(template = "empty", timeoutTicks = 285)
    public static void decayGeneratorSuppliesAProcessor(GameTestHelper helper) {
        helper.setBlock(POS, NCGenerators.DECAY_GENERATOR.get());
        helper.setBlock(POS.north(), Objects.requireNonNull(ForgeRegistries.BLOCKS.getValue(NuclearCraft.id("uranium_block"))));
        helper.setBlock(POS.south(), Objects.requireNonNull(ForgeRegistries.BLOCKS.getValue(NuclearCraft.id("thorium_block"))));
        var generator = helper.getBlockEntity(POS);
        var capability = generator.getCapability(ForgeCapabilities.ENERGY, Direction.WEST);
        var output = capability.orElseThrow(AssertionError::new);
        helper.assertTrue(output.canExtract() && !output.canReceive() && output.receiveEnergy(40, false) == 0,
                "Every generator face is energy output only");

        helper.startSequence().thenWaitUntil(() -> helper.assertTrue(output.getEnergyStored() >= 17,
                "Adjacent legacy fuel blocks charge the generator")).thenExecute(() -> {
                    int before = output.getEnergyStored();
                    helper.assertTrue(output.extractEnergy(17, true) == 17 && output.getEnergyStored() == before,
                            "A simulated energy extraction preserves the buffer");
                    helper.assertTrue(output.extractEnergy(17, false) == 17 && output.getEnergyStored() == before - 17,
                            "Executing the same extraction debits the buffer once");
                    var saved = generator.saveWithoutMetadata();
                    helper.setBlock(POS, Blocks.AIR);
                    helper.assertTrue(!capability.isPresent() && output.extractEnergy(17, false) == 0,
                            "A removed generator revokes old output handles");
                    helper.setBlock(POS, NCGenerators.DECAY_GENERATOR.get());
                    var restored = helper.getBlockEntity(POS);
                    restored.load(saved);
                    var power = restored.getCapability(ForgeCapabilities.ENERGY, Direction.WEST).orElseThrow(AssertionError::new);
                    helper.assertTrue(power.getEnergyStored() == before - 17, "The saved buffer is restored exactly");

                    helper.setBlock(POS.east(), Machine.MANUFACTORY.block());
                    var machine = (ItemProcessorBlockEntity) helper.getBlockEntity(POS.east());
                    machine.getCapability(ForgeCapabilities.ENERGY, Direction.WEST).orElseThrow(AssertionError::new)
                            .receiveEnergy(3900, false);
                    machine.inventory().setStackInSlot(0, new ItemStack(Items.COAL));
                }).thenWaitUntil(() -> {
                    var machine = (ItemProcessorBlockEntity) helper.getBlockEntity(POS.east());
                    helper.assertTrue(machine.inventory().getStackInSlot(1).is(
                                    ForgeRegistries.ITEMS.getValue(NuclearCraft.id("coal_dust"))),
                            "The generator transfers the additional FE needed to process coal");
                }).thenSucceed();
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void decayGeneratorUsesDatapackBlockTransformation(GameTestHelper helper) {
        var crafting = MaterialGameTests.craftingGrid(3, 3);
        var lead = Objects.requireNonNull(ForgeRegistries.ITEMS.getValue(NuclearCraft.id("lead_ingot")));
        for (int slot = 0; slot < 9; slot++) {
            crafting.setItem(slot, new ItemStack(slot == 4 ? Items.REDSTONE : slot % 2 == 0 ? lead : Items.COBBLESTONE));
        }
        var level = helper.getLevel();
        var recipe = level.getRecipeManager().getRecipeFor(RecipeType.CRAFTING, crafting, level).orElseThrow();
        helper.assertTrue(recipe.assemble(crafting, level.registryAccess()).is(NCGenerators.DECAY_ITEM.get()),
                "Craft the generator using lead, cobblestone, and redstone");
        helper.setBlock(POS, NCGenerators.DECAY_GENERATOR.get());
        helper.setBlock(POS.north(), Blocks.DIAMOND_BLOCK);
        helper.setBlock(POS.south(), Blocks.GOLD_BLOCK);
        helper.succeedWhen(() -> {
            helper.assertBlockPresent(Blocks.EMERALD_BLOCK, POS.north());
            helper.assertBlockPresent(Blocks.GOLD_BLOCK, POS.south());
            var energy = helper.getBlockEntity(POS).getCapability(ForgeCapabilities.ENERGY).orElseThrow(AssertionError::new);
            helper.assertTrue(energy.getEnergyStored() == 0, "The inert result and foreign block supply no energy");
        });
    }
}
