package nc.gametest;

import com.mojang.authlib.GameProfile;
import nc.NuclearCraft;
import nc.init.NCRadiation;
import nc.processor.ProcessorFluidTanks;
import nc.processor.RadiationScrubber;
import nc.processor.ScrubberMenu;
import nc.radiation.ScrubberEnvironment;
import nc.radiation.WorldRadiation;
import nc.recipe.ScrubberRecipe;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.UUID;

import static net.minecraftforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE;
import static net.minecraftforge.fluids.capability.IFluidHandler.FluidAction.SIMULATE;

@GameTestHolder(NuclearCraft.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ScrubberGameTests {
    private static final BlockPos POS = new BlockPos(2, 2, 2);

    private ScrubberGameTests() {}

    @GameTest(template = "empty", timeoutTicks = 2000)
    public static void scrubberReservesBoraxAndPausesOnRedstone(GameTestHelper helper) {
        var level = helper.getLevel();
        helper.setBlock(POS, NCRadiation.SCRUBBER.get());
        var scrubber = machine(helper);
        var down = scrubber.getCapability(ForgeCapabilities.ITEM_HANDLER, Direction.DOWN).orElseThrow(AssertionError::new);
        var energy = scrubber.getCapability(ForgeCapabilities.ENERGY, Direction.NORTH).orElseThrow(AssertionError::new);
        var borax = item("borax");
        helper.assertTrue(down.insertItem(0, new ItemStack(Items.DIRT), false).is(Items.DIRT), "Unrelated items are rejected");
        helper.assertTrue(down.insertItem(1, borax, false).getCount() == 1, "The output slot rejects insertion");
        helper.assertTrue(down.insertItem(0, borax, true).isEmpty() && down.getStackInSlot(0).isEmpty(),
                "Simulated insertion does not consume borax");
        helper.assertTrue(down.insertItem(0, borax, false).isEmpty(), "Insert borax through a Forge item face");
        helper.assertTrue(tank(scrubber, Direction.DOWN).fill(fluid("radaway", 250), EXECUTE) == 0,
                "The tank rejects an incompatible second input while borax is queued");
        helper.assertTrue(energy.getMaxEnergyStored() == 19_200_000 && !energy.canExtract(),
                "The Scrubber retains the legacy capacity and only accepts power");
        energy.receiveEnergy(2_400_000, false);
        BlockPos absolute = helper.absolutePos(POS);
        WorldRadiation.addLeak(level, absolute, 100);

        helper.startSequence().thenWaitUntil(() -> {
            helper.assertTrue(scrubber.measuredRemoval() > 0 && down.getStackInSlot(0).isEmpty(),
                    "A powered batch must reserve borax and remove chunk radiation");
        }).thenExecute(() -> {
            double open = ScrubberEnvironment.openness(level, absolute);
            helper.setBlock(POS.above(), Blocks.GLASS);
            helper.assertTrue(ScrubberEnvironment.openness(level, absolute) < open,
                    "A full translucent block lowers the Scrubber's environmental efficiency");
            helper.setBlock(POS.above(), Blocks.OAK_LEAVES);
            helper.assertTrue(Math.abs(ScrubberEnvironment.openness(level, absolute) - open) < 1E-10,
                    "Foliage does not count against the Scrubber");
            helper.setBlock(POS.above(), Blocks.AIR);

            var player = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "ScrubberMenu"));
            player.moveTo(absolute.getX() - 0.5D, absolute.getY(), absolute.getZ() + 0.5D);
            ScrubberMenu menu = scrubber.createMenu(1, player.getInventory(), player);
            helper.assertTrue(menu.clickMenuButton(player, ScrubberMenu.REDSTONE_BUTTON), "Enable redstone control");
            helper.setBlock(POS.east(), Blocks.REDSTONE_BLOCK);
            var saved = scrubber.saveWithoutMetadata();
            helper.assertTrue(ItemStack.of(saved.getCompound("reserved_item")).is(borax.getItem()),
                    "An in-flight batch persists the specific material it consumed");
            saved.putInt("progress", 11_996);
            saved.putInt("energy", 800);
            scrubber.load(saved);
        }).thenIdle(5).thenExecute(() -> {
            var paused = scrubber.saveWithoutMetadata();
            helper.assertTrue(paused.getInt("progress") == 11_996 && paused.getInt("energy") == 800
                            && scrubber.contribution() == 0,
                    "Redstone holds the reserved batch and spends no energy or scrubs radiation");
            helper.setBlock(POS.east(), Blocks.AIR);
        }).thenWaitUntil(() -> {
            var out = scrubber.getCapability(ForgeCapabilities.ITEM_HANDLER, Direction.UP).orElseThrow(AssertionError::new);
            helper.assertTrue(out.getStackInSlot(1).is(item("irradiated_borax").getItem())
                            && energy.getEnergyStored() == 0,
                    "Four resumed ticks use exactly 800 FE and produce one irradiated borax");
            helper.assertTrue(out.extractItem(1, 1, true).getCount() == 1 && out.getStackInSlot(1).getCount() == 1
                            && out.extractItem(1, 1, false).getCount() == 1 && out.getStackInSlot(1).isEmpty(),
                    "Simulated and actual extraction preserve the output");
        }).thenSucceed();
    }

    @GameTest(template = "empty", timeoutTicks = 2550)
    public static void scrubberFluidRecipeSurvivesBlockedOutputAndMidBatchReload(GameTestHelper helper) {
        helper.setBlock(POS, NCRadiation.SCRUBBER.get());
        var original = machine(helper);
        var fluid = tank(original, Direction.WEST);
        var power = original.getCapability(ForgeCapabilities.ENERGY, Direction.DOWN).orElseThrow(AssertionError::new);
        var primed = original.saveWithoutMetadata();
        primed.put("output_fluid", fluid("ethanol", ProcessorFluidTanks.CAPACITY).writeToNBT(new CompoundTag()));
        original.load(primed);
        var restored = tank(original, Direction.WEST);
        helper.assertTrue(restored.fill(fluid("radaway", 250), SIMULATE) == 250
                        && restored.getFluidInTank(0).isEmpty(),
                "Fluid input simulation does not fill the tank");
        helper.assertTrue(restored.fill(fluid("radaway", 250), EXECUTE) == 250
                        && restored.drain(fluid("radaway", 250), EXECUTE).isEmpty(),
                "The input tank accepts exactly 250 mB and cannot drain on a default face");
        var items = original.getCapability(ForgeCapabilities.ITEM_HANDLER, Direction.NORTH).orElseThrow(AssertionError::new);
        helper.assertTrue(items.insertItem(0, item("borax"), false).getCount() == 1,
                "A different medium cannot jam a queued fluid batch");
        power.receiveEnergy(96_000, false);

        helper.startSequence().thenIdle(12).thenExecute(() -> {
            var blocked = original.saveWithoutMetadata();
            helper.assertTrue(blocked.getInt("progress") == 0 && power.getEnergyStored() == 96_000
                            && FluidStack.loadFluidStackFromNBT(blocked.getCompound("reserved_fluid"))
                            .isFluidStackIdentical(fluid("radaway", 250)),
                    "A blocked output holds the 250 mB reservation without power or progress");
            helper.assertTrue(restored.drain(250, SIMULATE).isFluidStackIdentical(fluid("ethanol", 250))
                            && restored.getFluidInTank(1).getAmount() == 16_000
                            && restored.drain(250, EXECUTE).isFluidStackIdentical(fluid("ethanol", 250)),
                    "Draining 250 mB of product creates room");
        }).thenIdle(30).thenExecute(() -> {
            var running = original.saveWithoutMetadata();
            helper.assertTrue(running.getInt("progress") > 0, "Processing begins once output room returns");
            helper.setBlock(POS, Blocks.AIR);
            helper.assertTrue(fluid.getTanks() == 2 && fluid.fill(fluid("radaway", 250), EXECUTE) == 0
                            && restored.drain(250, EXECUTE).isEmpty() && power.receiveEnergy(10, false) == 0,
                    "Retained capabilities cannot transfer through the removed machine");
            helper.setBlock(POS, NCRadiation.SCRUBBER.get());
            machine(helper).load(running);
        }).thenWaitUntil(() -> {
            var loaded = machine(helper);
            IFluidHandler face = tank(loaded, Direction.EAST);
            var saved = loaded.saveWithoutMetadata();
            helper.assertTrue(face.getFluidInTank(1).isFluidStackIdentical(fluid("ethanol", 16_000))
                            && face.getFluidInTank(0).isEmpty() && saved.getInt("energy") == 0
                            && !saved.contains("reserved_fluid"),
                    "The restored reservation produces just one 250 mB ethanol batch for 96,000 FE");
        }).thenSucceed();
    }

    @GameTest(template = "empty")
    public static void scrubberLoadsAllThreeLegacyRecipes(GameTestHelper helper) {
        var recipes = helper.getLevel().getRecipeManager();
        helper.assertTrue(recipes.getAllRecipesFor(NCRadiation.SCRUBBER_RECIPES.get()).size() == 3
                        && recipes.byKey(NuclearCraft.id("crafting/radiation_scrubber")).isPresent(),
                "All three media and the machine crafting recipe load");
        var slow = (ScrubberRecipe) recipes.byKey(NuclearCraft.id("radiation_scrubber/radaway_slow")).orElseThrow();
        helper.assertTrue(slow.ticks() == 96_000 && slow.power() == 20 && slow.efficiency() == 0.25D
                        && slow.fluidInput().matches(fluid("radaway_slow", 250))
                        && slow.fluidOutput().isFluidStackIdentical(fluid("redstone_ethanol", 250)),
                "Slow RadAway retains the legacy duration, power, efficiency, and fluid amounts");
        helper.succeed();
    }

    private static RadiationScrubber machine(GameTestHelper helper) {
        return (RadiationScrubber) helper.getBlockEntity(POS);
    }

    private static ItemStack item(String path) {
        return new ItemStack(ForgeRegistries.ITEMS.getValue(NuclearCraft.id(path)));
    }

    private static FluidStack fluid(String path, int amount) {
        return new FluidStack(ForgeRegistries.FLUIDS.getValue(NuclearCraft.id(path)), amount);
    }

    private static IFluidHandler tank(RadiationScrubber scrubber, Direction side) {
        return scrubber.getCapability(ForgeCapabilities.FLUID_HANDLER, side).orElseThrow(AssertionError::new);
    }
}
