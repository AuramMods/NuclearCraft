package nc.gametest;

import nc.NuclearCraft;
import nc.init.NCFluids;
import nc.init.NCGasCoolants;
import nc.init.NCMachines;
import nc.multiblock.machine.Infiltrator;
import nc.multiblock.machine.MachinePartBlock;
import nc.multiblock.machine.MachinePartBlock.Mode;
import nc.multiblock.machine.MachinePartBlock.Part;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.Fluids;
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
public final class InfiltratorGameTests {
    private static final BlockPos CORNER = new BlockPos(1, 1, 1);
    private static final BlockPos CONTROLLER = new BlockPos(1, 2, 2);
    private static final BlockPos POWER = new BlockPos(1, 2, 3);
    private static final BlockPos ITEM_FIRST = new BlockPos(2, 2, 1);
    private static final BlockPos ITEM_SECOND = new BlockPos(3, 2, 1);
    private static final BlockPos FLUID_FIRST = new BlockPos(2, 2, 4);
    private static final BlockPos FLUID_SECOND = new BlockPos(3, 2, 4);
    private static final BlockPos PRESSURE = new BlockPos(2, 1, 2);
    private static final BlockPos VENT = new BlockPos(3, 1, 3);
    private static final BlockPos PRODUCT = new BlockPos(4, 2, 2);
    private static final BlockPos STOP = new BlockPos(4, 2, 3);
    private static final BlockPos HEATER = new BlockPos(3, 2, 2);

    @GameTest(template = "empty", timeoutTicks = 1325)
    public static void infiltratesBothLegacyMaterialsWithReusablePressureFluid(GameTestHelper helper) {
        var controller = build(helper);
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(controller.assembled(), "The Infiltrator casing assembles"))
                .thenExecute(() -> {
                    var gas = fluid(helper, PRESSURE, Direction.DOWN);
                    var power = energy(helper);
                    helper.assertTrue(gas.getTankCapacity(0) == 64_000 && power.getMaxEnergyStored() == 32_768_000,
                            "The pressure and energy capacities use the exterior structure volume");
                    helper.assertTrue(gas.fill(new FluidStack(Fluids.WATER, 4_000), EXECUTE) == 0,
                            "The pressure tank rejects a fluid with no registered pressure recipe");
                    helper.assertTrue(items(helper, ITEM_SECOND, Direction.NORTH).insertItem(0, material("sic_fiber", 2), false).isEmpty()
                            && fluid(helper, FLUID_SECOND, Direction.SOUTH).fill(fluid("polymethylsilylene_methylene", 144), EXECUTE) == 144,
                            "One-item and one-fluid recipes accept their inputs in either slot");
                    power.receiveEnergy(2_000_000, false);
                })
                .thenIdle(5)
                .thenExecute(() -> {
                    var gas = fluid(helper, PRESSURE, Direction.DOWN);
                    var helium = new FluidStack(NCGasCoolants.HELIUM.cold(), 64_000);
                    helper.assertTrue(energy(helper).getEnergyStored() == 2_000_000
                            && gas.fill(helium, SIMULATE) == 64_000 && gas.getFluidInTank(0).isEmpty(),
                            "The tower waits for gas and simulating a fill preserves the empty reservoir");
                    helper.assertTrue(gas.fill(helium, EXECUTE) == 64_000
                            && gas.drain(64_000, EXECUTE).isEmpty(), "The reservoir inlet only accepts gas");
                })
                .thenWaitUntil(() -> product(helper, "sic_sic_cmc_ingot"))
                .thenExecute(() -> {
                    helper.assertTrue(energy(helper).getEnergyStored() == 2_000_000 - 400 * 960,
                            "Two heated chambers process SiC/SiC CMC in exactly 400 ticks at 960 FE/t");
                    helper.assertTrue(items(helper, ITEM_FIRST, Direction.NORTH).insertItem(0, material("zirconia_dust", 1), false).isEmpty()
                            && items(helper, ITEM_SECOND, Direction.NORTH).insertItem(0, material("sintered_zirconia", 1), false).isEmpty()
                            && fluid(helper, FLUID_FIRST, Direction.SOUTH).fill(fluid("polyphenylene_sulfide", 144), EXECUTE) == 144,
                            "Zirfon accepts both required items in shapeless order and a measured molten input");
                })
                .thenWaitUntil(() -> product(helper, "zirfon"))
                .thenExecute(() -> {
                    var vent = fluid(helper, VENT, Direction.DOWN);
                    helper.assertTrue(energy(helper).getEnergyStored() == 2_000_000 - 1200 * 960
                            && vent.drain(64_000, SIMULATE).getAmount() == 64_000,
                            "Zirfon gets no heating speed bonus and neither recipe consumes helium");
                    helper.assertTrue(vent.drain(64_000, EXECUTE).getAmount() == 64_000
                            && fluid(helper, PRESSURE, Direction.DOWN).getFluidInTank(0).isEmpty(),
                            "The output reservoir port drains the same stored pressure gas");
                }).thenSucceed();
    }

    @GameTest(template = "empty")
    public static void preservesInfiltratorProgressAndBlocksIncompatibleInputs(GameTestHelper helper) {
        var controller = build(helper);
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(controller.assembled(), "The tower assembles"))
                .thenExecute(() -> {
                    energy(helper).receiveEnergy(1_000_000, false);
                    fluid(helper, PRESSURE, Direction.DOWN).fill(new FluidStack(NCFluids.NITROGEN.source().get(), 32_000), EXECUTE);
                    var inlet = items(helper, ITEM_FIRST, Direction.NORTH);
                    helper.assertTrue(inlet.insertItem(0, material("sic_fiber", 2), true).isEmpty() && inlet.getStackInSlot(0).isEmpty(),
                            "Simulated input keeps the selected slot empty");
                    inlet.insertItem(0, material("sic_fiber", 2), false);
                    helper.assertTrue(items(helper, ITEM_SECOND, Direction.NORTH).insertItem(0, material("sic_fiber", 2), true).getCount() == 2
                            && fluid(helper, FLUID_FIRST, Direction.SOUTH).fill(fluid("polyphenylene_sulfide", 144), SIMULATE) == 0,
                            "Smart matching rejects an incompatible second item and molten input");
                    fluid(helper, FLUID_FIRST, Direction.SOUTH).fill(fluid("polymethylsilylene_methylene", 144), EXECUTE);
                    var saved = controller.saveWithoutMetadata();
                    var items = new ListTag();
                    items.add(new ItemStack(Items.STONE, 64).save(new CompoundTag()).merge(slot(2)));
                    items.add(material("sic_fiber", 2).save(new CompoundTag()).merge(slot(0)));
                    saved.getCompound("inventory").put("Items", items);
                    controller.load(saved);
                })
                .thenIdle(8)
                .thenExecute(() -> {
                    helper.assertTrue(energy(helper).getEnergyStored() == 1_000_000 && !helper.getBlockState(HEATER).getValue(BlockStateProperties.LIT),
                            "An incompatible full product slot prevents ingredient and FE consumption");
                    items(helper, PRODUCT, Direction.EAST).extractItem(0, 64, false);
                })
                .thenIdle(8)
                .thenExecute(() -> {
                    var saved = controller.saveWithoutMetadata();
                    helper.assertTrue(helper.getBlockState(HEATER).getValue(BlockStateProperties.LIT)
                                    && saved.getDouble("progress") > 0 && saved.getLong("energy") < 1_000_000,
                            "The cleared output allows pressure-controlled processing and lights the heater");
                    var handle = helper.getBlockEntity(ITEM_FIRST).getCapability(ForgeCapabilities.ITEM_HANDLER, Direction.NORTH);
                    var cached = handle.orElseThrow(AssertionError::new);
                    helper.setBlock(CORNER, Blocks.AIR);
                    helper.assertTrue(!controller.assembled() && !handle.isPresent() && cached.getSlots() == 0,
                            "Breaking the casing clears a cached selected item handler");
                    helper.setBlock(STOP.east(), Blocks.REDSTONE_BLOCK);
                    controller.load(saved);
                    helper.setBlock(CORNER, NCMachines.block(Part.FRAME));
                })
                .thenWaitUntil(() -> helper.assertTrue(controller.assembled(), "The Infiltrator reforms with its saved material"))
                .thenExecute(() -> helper.assertTrue(controller.saveWithoutMetadata().getDouble("progress") > 0
                                && fluid(helper, PRESSURE, Direction.DOWN).getFluidInTank(0).getAmount() == 32_000
                                && items(helper, ITEM_FIRST, Direction.NORTH).getStackInSlot(0).is(material("sic_fiber", 1).getItem())
                                && !helper.getBlockState(HEATER).getValue(BlockStateProperties.LIT),
                        "The redstone hold preserves progress, feed and partial pressure through reassembly"))
                .thenSucceed();
    }

    @GameTest(template = "empty")
    public static void chanceProductRequiresMaximumRoom(GameTestHelper helper) {
        var controller = build(helper);
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(controller.assembled(), "The Infiltrator assembles"))
                .thenExecute(() -> {
                    items(helper, ITEM_SECOND, Direction.NORTH).insertItem(0, new ItemStack(Items.GRAVEL, 3), false);
                    fluid(helper, PRESSURE, Direction.DOWN)
                            .fill(new FluidStack(NCGasCoolants.HELIUM.cold(), 64_000), EXECUTE);
                    energy(helper).receiveEnergy(10_000, false);
                    var state = controller.saveWithoutMetadata();
                    state.getCompound("inventory").getList("Items", Tag.TAG_COMPOUND)
                            .add(new ItemStack(Items.FLINT, 61).save(new CompoundTag()).merge(slot(2)));
                    controller.load(state);
                })
                .thenIdle(5)
                .thenExecute(() -> {
                    helper.assertTrue(energy(helper).getEnergyStored() == 10_000
                                    && items(helper, ITEM_SECOND, Direction.NORTH).getStackInSlot(0).getCount() == 3,
                            "Three free spaces against a maximum of four leaves both feed and FE intact");
                    items(helper, PRODUCT, Direction.EAST).extractItem(0, 1, false);
                })
                .thenWaitUntil(() -> helper.assertTrue(items(helper, PRODUCT, Direction.EAST).getStackInSlot(0).getCount() == 62,
                        "The batch adds exactly two flint to the 60 retained in its output"))
                .thenExecute(() -> helper.assertTrue(items(helper, ITEM_SECOND, Direction.NORTH).getStackInSlot(0).isEmpty()
                                && fluid(helper, PRESSURE, Direction.DOWN).getFluidInTank(0).getAmount() == 64_000,
                        "Only the counted feed is used, and the pressure charge is retained"))
                .thenSucceed();
    }

    @GameTest(template = "empty")
    public static void infiltratorRadiationFollowsPressureAndMachineState(GameTestHelper helper) {
        var controller = build(helper);
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(controller.assembled(), "The Infiltrator assembles"))
                .thenExecute(() -> {
                    helper.assertTrue(items(helper, ITEM_FIRST, Direction.NORTH)
                            .insertItem(0, new ItemStack(Items.COAL), false).isEmpty(), "The scripted feed fits");
                    energy(helper).receiveEnergy(100_000, false);
                    helper.assertTrue(controller.radiationEmission() == 0, "No pressure means no emission");
                    fluid(helper, PRESSURE, Direction.DOWN)
                            .fill(new FluidStack(NCGasCoolants.HELIUM.cold(), 32_000), EXECUTE);
                })
                .thenWaitUntil(() -> helper.assertTrue(controller.radiationEmission() == 6,
                        "Two heated chambers with half pressure emit six Rad/t from a three Rad/t recipe"))
                .thenExecute(() -> helper.setBlock(STOP.east(), Blocks.REDSTONE_BLOCK))
                .thenWaitUntil(() -> helper.assertTrue(controller.radiationEmission() == 0, "Redstone halts radiation"))
                .thenExecute(() -> helper.setBlock(STOP.east(), Blocks.AIR))
                .thenWaitUntil(() -> helper.assertTrue(controller.radiationEmission() == 6,
                        "Releasing the redstone hold restores emission"))
                .thenExecute(() -> {
                    helper.setBlock(CORNER, Blocks.AIR);
                    helper.assertTrue(!controller.assembled() && controller.radiationEmission() == 0,
                            "Breaking the shell drops the radiation source immediately");
                }).thenSucceed();
    }

    private static CompoundTag slot(int slot) {
        var tag = new CompoundTag();
        tag.putInt("Slot", slot);
        return tag;
    }

    private static Infiltrator build(GameTestHelper helper) {
        for (BlockPos pos : BlockPos.betweenClosed(CORNER, new BlockPos(4, 4, 4))) {
            if (pos.getX() == 1 || pos.getX() == 4 || pos.getY() == 1 || pos.getY() == 4
                    || pos.getZ() == 1 || pos.getZ() == 4) helper.setBlock(pos, NCMachines.block(Part.FRAME));
        }
        helper.setBlock(CONTROLLER, NCMachines.block(Part.INFILTRATOR));
        helper.setBlock(POWER, NCMachines.block(Part.POWER));
        helper.setBlock(STOP, NCMachines.block(Part.REDSTONE));
        helper.setBlock(HEATER, NCMachines.block(Part.INFILTRATOR_HEATER));
        helper.setBlock(new BlockPos(2, 2, 2), NCMachines.block(Part.PRESSURE_CHAMBER));
        helper.setBlock(new BlockPos(3, 2, 3), NCMachines.block(Part.PRESSURE_CHAMBER));
        port(helper, ITEM_FIRST, Part.PROCESS, Mode.ITEM_IN, 0);
        port(helper, ITEM_SECOND, Part.PROCESS, Mode.ITEM_IN, 1);
        port(helper, FLUID_FIRST, Part.PROCESS, Mode.FLUID_IN, 0);
        port(helper, FLUID_SECOND, Part.PROCESS, Mode.FLUID_IN, 1);
        port(helper, PRODUCT, Part.PROCESS, Mode.ITEM_OUT, 0);
        port(helper, PRESSURE, Part.RESERVOIR, Mode.ITEM_IN, 0);
        port(helper, VENT, Part.RESERVOIR, Mode.ITEM_OUT, 0);
        return (Infiltrator) helper.getBlockEntity(CONTROLLER);
    }

    private static void port(GameTestHelper helper, BlockPos pos, Part part, Mode mode, int index) {
        helper.setBlock(pos, NCMachines.block(part).defaultBlockState().setValue(MachinePartBlock.MODE, mode));
        var tag = new CompoundTag();
        tag.putInt(mode.fluid() ? "tank_index" : "slot_index", index);
        helper.getBlockEntity(pos).load(tag);
    }

    private static void product(GameTestHelper helper, String name) {
        var handler = items(helper, PRODUCT, Direction.EAST);
        var expected = material(name, 1);
        helper.assertTrue(ItemStack.matches(handler.extractItem(0, 1, true), expected), "The measured product is ready");
        helper.assertTrue(handler.getStackInSlot(0).getCount() == 1 && ItemStack.matches(handler.extractItem(0, 1, false), expected),
                "The output port extracts the same product exactly once");
    }

    private static IEnergyStorage energy(GameTestHelper helper) {
        return helper.getBlockEntity(POWER).getCapability(ForgeCapabilities.ENERGY, Direction.WEST).orElseThrow(AssertionError::new);
    }

    private static IItemHandler items(GameTestHelper helper, BlockPos pos, Direction direction) {
        return helper.getBlockEntity(pos).getCapability(ForgeCapabilities.ITEM_HANDLER, direction).orElseThrow(AssertionError::new);
    }

    private static IFluidHandler fluid(GameTestHelper helper, BlockPos pos, Direction direction) {
        return helper.getBlockEntity(pos).getCapability(ForgeCapabilities.FLUID_HANDLER, direction).orElseThrow(AssertionError::new);
    }

    private static ItemStack material(String id, int count) {
        return new ItemStack(ForgeRegistries.ITEMS.getValue(NuclearCraft.id(id)), count);
    }

    private static FluidStack fluid(String id, int amount) {
        return new FluidStack(ForgeRegistries.FLUIDS.getValue(NuclearCraft.id(id)), amount);
    }
}
