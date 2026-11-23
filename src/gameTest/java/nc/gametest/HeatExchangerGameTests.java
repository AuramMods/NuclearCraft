package nc.gametest;

import nc.NuclearCraft;
import nc.init.NCGasCoolants;
import nc.init.NCHeatExchangers;
import nc.init.NCFluids;
import nc.multiblock.hx.ExchangerTubeBlock;
import nc.multiblock.hx.ExchangerTubeBlock.Setting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandler.FluidAction;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(NuclearCraft.MOD_ID)
@PrefixGameTestTemplate(false)
public final class HeatExchangerGameTests {
    private static final BlockPos CONTROLLER = new BlockPos(2, 3, 1);
    private static final BlockPos SHELL_IN = new BlockPos(5, 3, 3);
    private static final BlockPos SHELL_OUT = new BlockPos(1, 3, 3);
    private static final BlockPos TUBE_IN = new BlockPos(3, 3, 1);
    private static final BlockPos TUBE_OUT = new BlockPos(3, 3, 5);
    private static final BlockPos FRAME = new BlockPos(1, 1, 1);

    @GameTest(template = "turbine", timeoutTicks = 150)
    public static void heatExchangerRecoversGasAndProducesSteam(GameTestHelper helper) {
        build(helper, 3);
        helper.startSequence()
                .thenWaitUntil(() -> fluid(helper, TUBE_IN, Direction.NORTH))
                .thenExecute(() -> {
                    fill(helper, TUBE_IN, Direction.NORTH, new FluidStack(NCGasCoolants.OXYGEN.exhaust(), 1024));
                    fill(helper, SHELL_IN, Direction.EAST, new FluidStack(Fluids.WATER, 64));
                    helper.assertTrue(!helper.getBlockEntity(SHELL_IN).getCapability(ForgeCapabilities.FLUID_HANDLER,
                            Direction.WEST).isPresent(), "The shell inlet must provide Forge access outward only");
                    helper.setBlock(CONTROLLER.north(), Blocks.REDSTONE_BLOCK);
                })
                .thenWaitUntil(() -> assertOutputs(helper, SHELL_OUT))
                .thenExecute(() -> {
                    helper.assertTrue(fluid(helper, TUBE_IN, Direction.NORTH).getFluidInTank(0).isEmpty()
                            && fluid(helper, SHELL_IN, Direction.EAST).getFluidInTank(0).isEmpty(),
                            "The matched circuit charges should be fully consumed");
                    IFluidHandler output = fluid(helper, TUBE_OUT, Direction.SOUTH);
                    FluidStack simulated = output.drain(128, FluidAction.SIMULATE);
                    helper.assertTrue(simulated.getAmount() == 128 && output.getFluidInTank(0).getAmount() == 256,
                            "Simulated extraction should leave the recovered oxygen intact");
                })
                .thenSucceed();
    }

    @GameTest(template = "turbine", timeoutTicks = 180)
    public static void heatExchangerBaffleRedirectsShellThroughTubes(GameTestHelper helper) {
        build(helper, 2);
        BlockPos input = SHELL_IN.below();
        BlockPos output = SHELL_OUT.below();
        BlockPos baffle = new BlockPos(3, 2, 3);
        helper.startSequence()
                .thenWaitUntil(() -> fluid(helper, input, Direction.EAST))
                .thenExecute(() -> {
                    fill(helper, TUBE_IN, Direction.NORTH, new FluidStack(NCGasCoolants.OXYGEN.exhaust(), 1024));
                    fill(helper, input, Direction.EAST, new FluidStack(Fluids.WATER, 64));
                    helper.setBlock(CONTROLLER.north(), Blocks.REDSTONE_BLOCK);
                })
                .thenExecuteAfter(5, () -> {
                    helper.assertTrue(fluid(helper, TUBE_OUT, Direction.SOUTH).getFluidInTank(0).isEmpty(),
                            "The shortest shell path bypasses the tubes before the baffle is installed");
                    helper.setBlock(baffle, NCHeatExchangers.BAFFLE.get());
                })
                .thenWaitUntil(() -> assertOutputs(helper, output))
                .thenSucceed();
    }

    @GameTest(template = "turbine", timeoutTicks = 180)
    public static void heatExchangerRetainsBothSidesDuringBackpressure(GameTestHelper helper) {
        build(helper, 3);
        helper.startSequence()
                .thenWaitUntil(() -> fluid(helper, SHELL_IN, Direction.EAST))
                .thenExecute(() -> {
                    fill(helper, TUBE_IN, Direction.NORTH, new FluidStack(NCGasCoolants.OXYGEN.exhaust(), 1024));
                    fill(helper, SHELL_IN, Direction.EAST, new FluidStack(Fluids.WATER, 64));
                    var controller = helper.getBlockEntity(CONTROLLER);
                    int capacity = fluid(helper, SHELL_OUT, Direction.WEST).getTankCapacity(0);
                    CompoundTag saved = controller.saveWithoutMetadata();
                    saved.getCompound("shell").put("output", new FluidStack(NCFluids.HIGH_PRESSURE_STEAM.source().get(),
                            capacity).writeToNBT(new CompoundTag()));
                    controller.load(saved);
                    helper.setBlock(CONTROLLER.north(), Blocks.REDSTONE_BLOCK);
                })
                .thenWaitUntil(() -> fluid(helper, SHELL_OUT, Direction.WEST))
                .thenExecuteAfter(5, () -> {
                    helper.assertTrue(fluid(helper, TUBE_IN, Direction.NORTH).getFluidInTank(0).getAmount() == 1024
                            && fluid(helper, SHELL_IN, Direction.EAST).getFluidInTank(0).getAmount() == 64,
                            "A full shell outlet must preserve both the tube and the shell inputs");
                    var old = helper.getBlockEntity(TUBE_IN).getCapability(ForgeCapabilities.FLUID_HANDLER, Direction.NORTH);
                    IFluidHandler retained = old.orElseThrow(AssertionError::new);
                    helper.setBlock(FRAME, Blocks.AIR);
                    helper.assertTrue(!old.isPresent() && retained.getTankCapacity(0) == 0,
                            "Breaking a frame must invalidate the attached pipe handler");
                    CompoundTag saved = helper.getBlockEntity(CONTROLLER).saveWithoutMetadata();
                    helper.setBlock(CONTROLLER, Blocks.AIR);
                    helper.setBlock(CONTROLLER, NCHeatExchangers.CONTROLLER.get());
                    helper.getBlockEntity(CONTROLLER).load(saved);
                    helper.setBlock(FRAME, NCHeatExchangers.CASING.get());
                })
                .thenWaitUntil(() -> fluid(helper, SHELL_OUT, Direction.WEST))
                .thenExecute(() -> {
                    IFluidHandler output = fluid(helper, SHELL_OUT, Direction.WEST);
                    helper.assertTrue(output.getFluidInTank(0).getAmount() == output.getTankCapacity(0),
                            "The replacement controller must recover the full shell outlet");
                    helper.assertTrue(output.drain(256, FluidAction.EXECUTE).getAmount() == 256,
                            "Reopening enough outlet space should allow the stalled batches to resume");
                })
                .thenWaitUntil(() -> {
                    helper.assertTrue(fluid(helper, TUBE_OUT, Direction.SOUTH).getFluidInTank(0).getAmount() == 256,
                            "The paused gas must finish processing after reassembly");
                    IFluidHandler output = fluid(helper, SHELL_OUT, Direction.WEST);
                    helper.assertTrue(output.getFluidInTank(0).getAmount() == output.getTankCapacity(0),
                            "The resumed batches must replace precisely the shell product removed");
                })
                .thenSucceed();
    }

    @GameTest(template = "turbine")
    public static void heatExchangerLoadsAllLegacyTransferRecipes(GameTestHelper helper) {
        var recipes = helper.getLevel().getRecipeManager().getAllRecipesFor(NCHeatExchangers.HEAT_EXCHANGER_RECIPES.get());
        helper.assertTrue(recipes.size() == 70, "All 6 steam and water, 32 gas, and 32 NaK recipes must load");
        var oxygen = recipes.stream().filter(r -> r.input().accepts(new FluidStack(NCGasCoolants.OXYGEN.exhaust(), 4)))
                .findFirst().orElseThrow();
        helper.assertTrue(oxygen.input().amount() == 4 && oxygen.output().getFluid() == NCGasCoolants.OXYGEN.cold()
                && oxygen.output().getAmount() == 1 && oxygen.heatRequired() == 16D && !oxygen.heating(),
                "Oxygen exhaust must preserve its legacy compression, fluid, and heat values");
        helper.succeed();
    }

    private static void assertOutputs(GameTestHelper helper, BlockPos shellOutlet) {
        var gas = fluid(helper, TUBE_OUT, Direction.SOUTH).getFluidInTank(0);
        var steam = fluid(helper, shellOutlet, Direction.WEST).getFluidInTank(0);
        helper.assertTrue(gas.getFluid() == NCGasCoolants.OXYGEN.cold() && gas.getAmount() == 256,
                "1,024 mB of exhaust must restore exactly 256 mB of oxygen");
        helper.assertTrue(steam.getFluid() == NCFluids.HIGH_PRESSURE_STEAM.source().get() && steam.getAmount() == 256,
                "64 mB of water must become exactly 256 mB of high-pressure steam");
    }

    private static void build(GameTestHelper helper, int shellHeight) {
        for (BlockPos pos : BlockPos.betweenClosed(new BlockPos(1, 1, 1), new BlockPos(5, 5, 5))) {
            if (pos.getX() == 1 || pos.getX() == 5 || pos.getY() == 1 || pos.getY() == 5
                    || pos.getZ() == 1 || pos.getZ() == 5) {
                helper.setBlock(pos, NCHeatExchangers.CASING.get());
            }
        }
        helper.setBlock(CONTROLLER, NCHeatExchangers.CONTROLLER.get());
        helper.setBlock(TUBE_IN, NCHeatExchangers.INLET.get());
        helper.setBlock(TUBE_OUT, NCHeatExchangers.OUTLET.get());
        helper.setBlock(new BlockPos(5, shellHeight, 3), NCHeatExchangers.INLET.get());
        helper.setBlock(new BlockPos(1, shellHeight, 3), NCHeatExchangers.OUTLET.get());
        for (int z = 2; z <= 4; z++) {
            var tube = NCHeatExchangers.THERMOCONDUCTING.get();
            helper.setBlock(new BlockPos(3, 3, z), ExchangerTubeBlock.withSetting(
                    ExchangerTubeBlock.withSetting(tube.defaultBlockState(), Direction.NORTH, Setting.OPEN),
                    Direction.SOUTH, Setting.OPEN));
        }
    }

    private static void fill(GameTestHelper helper, BlockPos pos, Direction side, FluidStack stack) {
        helper.assertTrue(fluid(helper, pos, side).fill(stack, FluidAction.EXECUTE) == stack.getAmount(),
                "The exchanger port should accept its full measured charge");
    }

    private static IFluidHandler fluid(GameTestHelper helper, BlockPos pos, Direction side) {
        return helper.getBlockEntity(pos).getCapability(ForgeCapabilities.FLUID_HANDLER, side)
                .orElseThrow(() -> new GameTestAssertException("Port not assembled at " + pos));
    }
}
