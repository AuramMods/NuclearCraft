package nc.gametest;

import nc.NuclearCraft;
import nc.init.NCMachines;
import nc.multiblock.machine.LargeElectrolyzer;
import nc.multiblock.machine.MachinePartBlock;
import nc.multiblock.machine.MachinePartBlock.Mode;
import nc.multiblock.machine.MachinePartBlock.Part;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.registries.ForgeRegistries;

import static net.minecraftforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE;
import static net.minecraftforge.fluids.capability.IFluidHandler.FluidAction.SIMULATE;

@GameTestHolder(NuclearCraft.MOD_ID)
@PrefixGameTestTemplate(false)
public final class LargeElectrolyzerGameTests {
    private static final BlockPos CORNER = new BlockPos(1, 1, 1);
    private static final BlockPos CONTROLLER = new BlockPos(1, 2, 2);
    private static final BlockPos INLET = new BlockPos(2, 2, 1);
    private static final BlockPos SECOND_INLET = new BlockPos(3, 2, 1);
    private static final BlockPos ITEM_INPUT = new BlockPos(4, 2, 1);
    private static final BlockPos ITEM_OUTPUT = new BlockPos(5, 3, 2);
    private static final BlockPos ELECTROLYTE = new BlockPos(1, 2, 3);
    private static final BlockPos DRAIN = new BlockPos(1, 3, 3);
    private static final BlockPos POWER = new BlockPos(5, 2, 2);
    private static final BlockPos CATHODE = new BlockPos(2, 2, 2);
    private static final BlockPos CATHODE_ROOF = new BlockPos(2, 4, 2);
    private static final BlockPos ANODE_ROOF = new BlockPos(4, 4, 2);
    private static final BlockPos SEPARATOR = new BlockPos(3, 2, 3);
    private static final BlockPos[] PRODUCTS = {
            new BlockPos(2, 2, 4), new BlockPos(3, 2, 4), new BlockPos(4, 2, 4), new BlockPos(4, 3, 4)};

    @GameTest(template = "empty", timeoutTicks = 600)
    public static void runsAllLegacyLargeElectrolyzerRecipes(GameTestHelper helper) {
        var machine = build(helper);
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(machine.assembled(), "Paired electrodes separated by a diaphragm form the machine"))
                .thenExecute(() -> {
                    helper.assertTrue(helper.getBlockState(CATHODE_ROOF).getValue(BlockStateProperties.FACING) == Direction.UP
                                    && helper.getBlockState(CATHODE_ROOF.below(3)).getValue(BlockStateProperties.FACING) == Direction.DOWN,
                            "Roof and floor terminal models face outward");
                    var gas = fluid(helper, ELECTROLYTE, Direction.WEST);
                    helper.assertTrue(gas.getTankCapacity(0) == 80_000 && energy(helper).getMaxEnergyStored() == 512_000
                                    && gas.fill(stock("minecraft:water", 80_000), SIMULATE) == 0,
                            "The reusable electrolyte accepts only configured fluids");
                    feed(helper, "minecraft:water", 500);
                    var fluoride = stock("nuclearcraft:potassium_fluoride_solution", 80_000);
                    helper.assertTrue(gas.fill(fluoride, SIMULATE) == 80_000 && gas.getFluidInTank(0).isEmpty(),
                            "Simulating electrolyte insertion leaves the reservoir unchanged");
                    gas.fill(fluoride, EXECUTE);
                })
                .thenIdle(10)
                .thenExecute(() -> {
                    helper.assertTrue(energy(helper).getEnergyStored() == 512_000 && fluid(helper, PRODUCTS[0], Direction.SOUTH)
                                    .getFluidInTank(0).isEmpty(), "The wrong electrolyte group stalls the recipe without costing FE");
                    var vent = fluid(helper, DRAIN, Direction.WEST);
                    helper.assertTrue(vent.drain(80_000, EXECUTE).getAmount() == 80_000, "The output reservoir drains the reusable charge");
                    fluid(helper, ELECTROLYTE, Direction.WEST).fill(stock("nuclearcraft:potassium_hydroxide_solution", 80_000), EXECUTE);
                })
                .thenWaitUntil(() -> output(helper, "hydrogen", 500, "oxygen", 250, null, 0))
                .thenExecute(() -> {
                    helper.assertTrue(energy(helper).getEnergyStored() == 512_000 - 87 * 2_560,
                            "Region contact and electrode spacing use the legacy 87 ticks at 2,560 FE/t");
                    feed(helper, "nuclearcraft:le_water", 500);
                })
                .thenWaitUntil(() -> output(helper, "hydrogen", 375, "deuterium", 125, "oxygen", 250))
                .thenExecute(() -> feed(helper, "nuclearcraft:he_water", 500))
                .thenWaitUntil(() -> output(helper, "hydrogen", 250, "deuterium", 250, "oxygen", 250))
                .thenExecute(() -> feed(helper, "nuclearcraft:heavy_water", 500))
                .thenWaitUntil(() -> output(helper, "deuterium", 500, "oxygen", 250, null, 0))
                .thenExecute(() -> {
                    var vent = fluid(helper, DRAIN, Direction.WEST);
                    helper.assertTrue(vent.drain(80_000, EXECUTE).isFluidStackIdentical(
                            stock("nuclearcraft:potassium_hydroxide_solution", 80_000)),
                            "The same electrolyte charge survives all four water recipes");
                    fluid(helper, ELECTROLYTE, Direction.WEST).fill(stock("nuclearcraft:potassium_fluoride_solution", 80_000), EXECUTE);
                    feed(helper, "nuclearcraft:hydrofluoric_acid", 250);
                }).thenWaitUntil(() -> output(helper, "hydrogen", 250, "fluorine", 250, null, 0))
                .thenSucceed();
    }

    @GameTest(template = "empty")
    public static void rejectsShortCircuitsAndUnevenOrUnpairedElectrodes(GameTestHelper helper) {
        var machine = build(helper);
        helper.setBlock(ANODE_ROOF, NCMachines.block(Part.FRAME));
        helper.startSequence()
                .thenIdle(4)
                .thenExecute(() -> {
                    helper.assertTrue(!machine.assembled(), "A floor terminal requires a matching terminal directly above");
                    helper.setBlock(ANODE_ROOF, NCMachines.block(Part.ANODE_TERMINAL));
                    helper.setBlock(SEPARATOR, Blocks.AIR);
                    helper.setBlock(SEPARATOR.above(), Blocks.AIR);
                })
                .thenIdle(4)
                .thenExecute(() -> {
                    helper.assertTrue(!machine.assembled(), "Sharing a region forms a short circuit");
                    helper.setBlock(SEPARATOR, NCMachines.block(Part.STEEL_DIAPHRAGM));
                })
                .thenIdle(4)
                .thenExecute(() -> {
                    helper.assertTrue(!machine.assembled(), "An incomplete vertical diaphragm fails symmetry");
                    helper.setBlock(SEPARATOR.above(), NCMachines.block(Part.STEEL_DIAPHRAGM));
                })
                .thenWaitUntil(() -> helper.assertTrue(machine.assembled(), "The corrected geometry assembles"))
                .thenExecute(() -> helper.setBlock(CATHODE.above(), Blocks.COPPER_BLOCK))
                .thenWaitUntil(() -> helper.assertTrue(!machine.assembled(), "A changed vanilla electrode invalidates the machine"))
                .thenExecute(() -> helper.setBlock(CATHODE.above(), Blocks.IRON_BLOCK))
                .thenWaitUntil(() -> helper.assertTrue(machine.assembled(), "Restoring the electrode allows reassembly"))
                .thenSucceed();
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void migratedDiaphragmsKeepLegacyOptionalValues(GameTestHelper helper) {
        var machine = build(helper);
        for (int y = 2; y <= 3; y++) {
            helper.setBlock(new BlockPos(3, y, 2), Blocks.CALCITE);
            helper.setBlock(new BlockPos(3, y, 3), Blocks.CALCITE);
        }
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(machine.assembled(), "A default diaphragm still separates the electrodes"))
                .thenExecute(() -> {
                    fluid(helper, ELECTROLYTE, Direction.WEST).fill(stock("nuclearcraft:potassium_hydroxide_solution", 80_000), EXECUTE);
                    feed(helper, "minecraft:water", 500);
                })
                .thenIdle(10)
                .thenExecute(() -> {
                    helper.assertTrue(energy(helper).getEnergyStored() == 512_000,
                            "A diaphragm with no specified efficiency has zero speed and spends no FE");
                    for (int y = 2; y <= 3; y++) {
                        helper.setBlock(new BlockPos(3, y, 2), Blocks.QUARTZ_BLOCK);
                        helper.setBlock(new BlockPos(3, y, 3), Blocks.QUARTZ_BLOCK);
                    }
                })
                .thenWaitUntil(() -> {
                    var saved = machine.saveWithoutMetadata();
                    helper.assertTrue(FluidStack.loadFluidStackFromNBT(saved.getCompound("output_0"))
                                    .isFluidStackIdentical(stock("nuclearcraft:hydrogen", 500))
                                    && FluidStack.loadFluidStackFromNBT(saved.getCompound("output_1"))
                                    .isFluidStackIdentical(stock("nuclearcraft:oxygen", 250)),
                            "The replacement diaphragm reassembles and processes the unchanged water feed");
                    helper.assertTrue(saved.getLong("energy") == 512_000 - 87 * 2_560,
                            "A diaphragm with efficiency 0.8 and omitted contact spends 2,560 FE per tick");
                })
                .thenSucceed();
    }

    @GameTest(template = "empty")
    public static void blocksPartialProductionForSparseMixedRecipes(GameTestHelper helper) {
        var machine = build(helper);
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(machine.assembled(), "The server-supplied mixed recipe can use the standard shell"))
                .thenExecute(() -> {
                    fluid(helper, ELECTROLYTE, Direction.WEST).fill(stock("nuclearcraft:potassium_fluoride_solution", 80_000), EXECUTE);
                    var inlet = items(helper, ITEM_INPUT, Direction.NORTH);
                    helper.assertTrue(!helper.getBlockEntity(ITEM_INPUT).getCapability(ForgeCapabilities.ITEM_HANDLER, Direction.SOUTH).isPresent()
                                    && inlet.insertItem(0, new ItemStack(Items.COAL, 2), false).isEmpty(),
                            "Only the outward side can insert into the selected item slot");
                    helper.assertTrue(fluid(helper, SECOND_INLET, Direction.NORTH).fill(stock("minecraft:lava", 100), EXECUTE) == 100,
                            "The recipe accepts its fluid in the second input");
                    energy(helper).receiveEnergy(10_000, false);
                    var tag = machine.saveWithoutMetadata();
                    tag.put("output_3", stock("nuclearcraft:steam", 79_901).writeToNBT(new CompoundTag()));
                    machine.load(tag);
                })
                .thenIdle(10)
                .thenExecute(() -> {
                    helper.assertTrue(energy(helper).getEnergyStored() == 10_000 && machine.radiationEmission() == 0
                                    && items(helper, ITEM_OUTPUT, Direction.EAST).getStackInSlot(0).isEmpty(),
                            "99 mB of room against a 100 mB maximum also blocks the item product and FE consumption");
                    fluid(helper, PRODUCTS[3], Direction.SOUTH).drain(1, EXECUTE);
                })
                .thenWaitUntil(() -> helper.assertTrue(Math.abs(machine.radiationEmission() - 0.9216) < 1E-9,
                        "Electrode geometry and electrolyte efficiency scale the recipe's radiation rate"))
                .thenWaitUntil(() -> helper.assertTrue(items(helper, ITEM_OUTPUT, Direction.EAST).getStackInSlot(0).is(Items.CHARCOAL),
                        "The datapack recipe produces an item in product slot four"))
                .thenExecute(() -> {
                    helper.assertTrue(items(helper, ITEM_OUTPUT, Direction.EAST).extractItem(0, 1, false).is(Items.CHARCOAL)
                                    && items(helper, ITEM_OUTPUT, Direction.EAST).getStackInSlot(0).isEmpty()
                                    && fluid(helper, PRODUCTS[3], Direction.SOUTH).getFluidInTank(0).isFluidStackIdentical(
                                            stock("nuclearcraft:steam", 79_950))
                                    && fluid(helper, SECOND_INLET, Direction.NORTH).getFluidInTank(0).isEmpty()
                                    && energy(helper).getEnergyStored() == 8_720,
                            "Both sparse outputs produce only their minimums at the scripted cost");
                }).thenSucceed();
    }

    private static LargeElectrolyzer build(GameTestHelper helper) {
        for (BlockPos pos : BlockPos.betweenClosed(CORNER, new BlockPos(5, 4, 4))) {
            if (pos.getX() == 1 || pos.getX() == 5 || pos.getY() == 1 || pos.getY() == 4
                    || pos.getZ() == 1 || pos.getZ() == 4) helper.setBlock(pos, NCMachines.block(Part.FRAME));
        }
        helper.setBlock(CONTROLLER, NCMachines.block(Part.ELECTROLYZER));
        helper.setBlock(POWER, NCMachines.block(Part.POWER));
        helper.setBlock(CATHODE_ROOF, NCMachines.block(Part.CATHODE_TERMINAL));
        helper.setBlock(CATHODE_ROOF.below(3), NCMachines.block(Part.CATHODE_TERMINAL));
        helper.setBlock(ANODE_ROOF, NCMachines.block(Part.ANODE_TERMINAL));
        helper.setBlock(ANODE_ROOF.below(3), NCMachines.block(Part.ANODE_TERMINAL));
        var copperOxide = ForgeRegistries.BLOCKS.getValue(NuclearCraft.id("copper_oxide_block"));
        for (int y = 2; y <= 3; y++) {
            helper.setBlock(new BlockPos(2, y, 2), Blocks.IRON_BLOCK);
            helper.setBlock(new BlockPos(4, y, 2), copperOxide);
            helper.setBlock(new BlockPos(3, y, 2), NCMachines.block(Part.STEEL_DIAPHRAGM));
            helper.setBlock(new BlockPos(3, y, 3), NCMachines.block(Part.STEEL_DIAPHRAGM));
        }
        port(helper, INLET, Part.PROCESS, Mode.FLUID_IN, 0);
        port(helper, SECOND_INLET, Part.PROCESS, Mode.FLUID_IN, 1);
        port(helper, ITEM_INPUT, Part.PROCESS, Mode.ITEM_IN, 1);
        port(helper, ITEM_OUTPUT, Part.PROCESS, Mode.ITEM_OUT, 3);
        port(helper, ELECTROLYTE, Part.RESERVOIR, Mode.ITEM_IN, 0);
        port(helper, DRAIN, Part.RESERVOIR, Mode.ITEM_OUT, 0);
        for (int slot = 0; slot < PRODUCTS.length; slot++) port(helper, PRODUCTS[slot], Part.PROCESS, Mode.FLUID_OUT, slot);
        return (LargeElectrolyzer) helper.getBlockEntity(CONTROLLER);
    }

    private static void port(GameTestHelper helper, BlockPos pos, Part part, Mode mode, int index) {
        helper.setBlock(pos, NCMachines.block(part).defaultBlockState().setValue(MachinePartBlock.MODE, mode));
        var tag = new CompoundTag();
        tag.putInt(mode.fluid() ? "tank_index" : "slot_index", index);
        helper.getBlockEntity(pos).load(tag);
    }

    private static void feed(GameTestHelper helper, String id, int amount) {
        energy(helper).receiveEnergy(512_000, false);
        helper.assertTrue(fluid(helper, INLET, Direction.NORTH).fill(stock(id, amount), EXECUTE) == amount, "The measured feed fits");
    }

    private static void output(GameTestHelper helper, String first, int firstAmount, String second, int secondAmount,
                               String third, int thirdAmount) {
        var expected = new FluidStack[]{stock("nuclearcraft:" + first, firstAmount), stock("nuclearcraft:" + second, secondAmount),
                third == null ? FluidStack.EMPTY : stock("nuclearcraft:" + third, thirdAmount)};
        for (int i = 0; i < expected.length; i++) {
            helper.assertTrue(fluid(helper, PRODUCTS[i], Direction.SOUTH).getFluidInTank(0).isFluidStackIdentical(expected[i]),
                    "Product tank " + (i + 1) + " has the legacy fluid and quantity");
        }
        for (int i = 0; i < expected.length; i++) fluid(helper, PRODUCTS[i], Direction.SOUTH).drain(expected[i].getAmount(), EXECUTE);
    }

    private static IEnergyStorage energy(GameTestHelper helper) {
        return helper.getBlockEntity(POWER).getCapability(ForgeCapabilities.ENERGY, Direction.EAST).orElseThrow(AssertionError::new);
    }

    private static IItemHandler items(GameTestHelper helper, BlockPos pos, Direction direction) {
        return helper.getBlockEntity(pos).getCapability(ForgeCapabilities.ITEM_HANDLER, direction).orElseThrow(AssertionError::new);
    }

    private static IFluidHandler fluid(GameTestHelper helper, BlockPos pos, Direction direction) {
        return helper.getBlockEntity(pos).getCapability(ForgeCapabilities.FLUID_HANDLER, direction).orElseThrow(AssertionError::new);
    }

    private static FluidStack stock(String id, int amount) {
        return new FluidStack(ForgeRegistries.FLUIDS.getValue(ResourceLocation.tryParse(id)), amount);
    }
}
