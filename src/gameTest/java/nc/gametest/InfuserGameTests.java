package nc.gametest;

import nc.NuclearCraft;
import nc.init.NCCollectors.Collector;
import nc.init.NCFluids;
import nc.init.NCItemProcessors.Machine;
import nc.processor.ItemProcessorBlockEntity;
import nc.processor.ProcessorFluidTanks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandler.FluidAction;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;

@GameTestHolder(NuclearCraft.MOD_ID)
@PrefixGameTestTemplate(false)
public final class InfuserGameTests {
    private static final BlockPos INFUSER = new BlockPos(2, 1, 2);

    @GameTest(template = "empty", timeoutTicks = 1_950)
    public static void infusesSupercoldIceAndMakesWorkingCollectors(GameTestHelper helper) {
        for (String recipe : new String[]{"supercold_ice", "water_source", "cobblestone_generator"}) {
            helper.assertTrue(helper.getLevel().getRecipeManager().byKey(NuclearCraft.id("infuser/" + recipe)).isPresent(),
                    "The default Infuser catalog includes " + recipe);
        }
        BlockPos cold = INFUSER.east(2);
        BlockPos water = INFUSER.south(2);
        BlockPos cobble = cold.south(2);
        helper.setBlock(INFUSER, Machine.INFUSER.block());
        helper.setBlock(cold, Machine.INFUSER.block());
        var machine = (ItemProcessorBlockEntity) helper.getBlockEntity(INFUSER);
        var freezer = (ItemProcessorBlockEntity) helper.getBlockEntity(cold);
        var fluid = fluid(machine, Direction.WEST);
        var helium = fluid(freezer, Direction.UP);
        var items = machine.getCapability(ForgeCapabilities.ITEM_HANDLER, Direction.UP).orElseThrow(AssertionError::new);
        var power = machine.getCapability(ForgeCapabilities.ENERGY).orElseThrow(AssertionError::new);
        var ice = freezer.getCapability(ForgeCapabilities.ITEM_HANDLER, Direction.UP).orElseThrow(AssertionError::new);
        var coolingPower = freezer.getCapability(ForgeCapabilities.ENERGY).orElseThrow(AssertionError::new);
        var coolant = ForgeRegistries.FLUIDS.getValue(NuclearCraft.id("liquid_helium"));
        helper.assertTrue(ice.insertItem(0, new ItemStack(Blocks.ICE), false).isEmpty()
                        && helium.fill(new FluidStack(coolant, 100), FluidAction.EXECUTE) == 100
                        && coolingPower.receiveEnergy(1_500, false) == 1_500,
                "Provide enough liquid helium and charge for both ice types");
        helper.assertTrue(items.insertItem(0, stack("empty_frame", 2), false).isEmpty()
                        && fluid.fill(new FluidStack(Fluids.WATER, 4_000), FluidAction.EXECUTE) == 4_000
                        && power.receiveEnergy(12_000, false) == 12_000,
                "Supply two frames, four buckets of water, and two batches of FE");

        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(ItemStack.matches(ice.getStackInSlot(1), stack("supercold_ice", 1)),
                        "Ordinary ice makes the first Supercold Ice block"))
                .thenExecute(() -> helper.assertTrue(ice.insertItem(0, new ItemStack(Blocks.PACKED_ICE), false).isEmpty(),
                        "The same recipe accepts packed ice"))
                .thenWaitUntil(() -> helper.assertTrue(ItemStack.matches(ice.getStackInSlot(1), stack("supercold_ice", 2))
                                && coolingPower.getEnergyStored() == 0 && helium.getFluidInTank(0).isEmpty(),
                        "Each ice batch consumes 50 mB of helium and 750 FE"))
                .thenWaitUntil(() -> helper.assertTrue(ItemStack.matches(items.getStackInSlot(1), stack("water_source", 2))
                                && power.getEnergyStored() == 0 && fluid.getFluidInTank(0).isEmpty(),
                        "Two water sources each consume one frame, 2,000 mB of water, and 6,000 FE"))
                .thenExecute(() -> {
                    var sources = items.extractItem(1, 2, false);
                    helper.setBlock(water, Collector.WATER.block());
                    helper.assertTrue(items.insertItem(0, sources.copyWithCount(1), false).isEmpty()
                                    && fluid.fill(new FluidStack(Fluids.LAVA, 1_000), FluidAction.EXECUTE) == 1_000
                                    && power.receiveEnergy(6_000, false) == 6_000,
                            "Reuse one manufactured water source in the cobblestone recipe");
                })
                .thenWaitUntil(() -> helper.assertTrue(ItemStack.matches(items.getStackInSlot(1), stack("cobblestone_generator", 1))
                                && items.getStackInSlot(0).isEmpty() && fluid.getFluidInTank(0).isEmpty()
                                && power.getEnergyStored() == 0,
                        "The generator consumes one water source, 1,000 mB of lava, and 6,000 FE"))
                .thenExecute(() -> helper.setBlock(cobble, Collector.COBBLESTONE.block()))
                .thenWaitUntil(() -> {
                    var source = helper.getBlockEntity(water).getCapability(ForgeCapabilities.FLUID_HANDLER)
                            .orElseThrow(AssertionError::new);
                    var generator = helper.getBlockEntity(cobble).getCapability(ForgeCapabilities.ITEM_HANDLER)
                            .orElseThrow(AssertionError::new);
                    helper.assertTrue(source.drain(1, FluidAction.SIMULATE).getFluid() == Fluids.WATER
                                    && generator.getStackInSlot(0).is(Items.COBBLESTONE),
                            "The infused blocks generate water and cobblestone when placed");
                })
                .thenSucceed();
    }

    @GameTest(template = "empty", timeoutTicks = 740)
    public static void infusesPelletWithTaggedOxygenAfterReload(GameTestHelper helper) {
        helper.setBlock(INFUSER, Machine.INFUSER.block());
        var processor = (ItemProcessorBlockEntity) helper.getBlockEntity(INFUSER);
        var original = processor.getCapability(ForgeCapabilities.FLUID_HANDLER, Direction.NORTH);
        var stale = original.orElseThrow(AssertionError::new);
        processor.cycleFluidSide(Direction.NORTH);
        FluidStack oxygen = new FluidStack(NCFluids.OXYGEN.source().get(), 999);
        helper.assertTrue(!original.isPresent() && stale.fill(oxygen, FluidAction.EXECUTE) == 0,
                "A face change revokes an already acquired input handler");
        var inlet = fluid(processor, Direction.UP);
        var outlet = fluid(processor, Direction.NORTH);
        helper.assertTrue(inlet.fill(new FluidStack(NCFluids.HIGH_PRESSURE_STEAM.source().get(), 1000),
                        FluidAction.EXECUTE) == 0,
                "An unrelated fluid is rejected");
        helper.assertTrue(inlet.fill(oxygen, FluidAction.SIMULATE) == 999 && inlet.getFluidInTank(0).isEmpty(),
                "Simulation reports the transfer without filling the tank");
        helper.assertTrue(inlet.fill(oxygen, FluidAction.EXECUTE) == 999 && inlet.drain(999, FluidAction.EXECUTE).isEmpty(),
                "The default face accepts input only");
        helper.assertTrue(outlet.fill(oxygen, FluidAction.EXECUTE) == 0 && outlet.drain(999, FluidAction.SIMULATE).getAmount() == 999,
                "Output configuration allows recovery without accepting more fluid");

        var items = processor.getCapability(ForgeCapabilities.ITEM_HANDLER, Direction.NORTH).orElseThrow(AssertionError::new);
        helper.assertTrue(items.insertItem(0, stack("leu_235_pellet", 1), false).isEmpty(),
                "Fluid configuration leaves the item's face available");
        var power = processor.getCapability(ForgeCapabilities.ENERGY, Direction.SOUTH).orElseThrow(AssertionError::new);
        power.receiveEnergy(6000, false);

        helper.runAtTickTime(12, () -> {
            helper.assertTrue(power.getEnergyStored() == 6000 && items.getStackInSlot(0).getCount() == 1,
                    "One millibucket short of the recipe must preserve the pellet and energy");
            processor.inventory().setStackInSlot(1, stack("leu_235_oxide", 64));
            helper.assertTrue(inlet.fill(new FluidStack(NCFluids.OXYGEN.source().get(), 1), FluidAction.EXECUTE) == 1,
                    "A second connection can provide the rest of the fluid");
        });
        helper.runAtTickTime(25, () -> {
            helper.assertTrue(power.getEnergyStored() == 6000 && inlet.getFluidInTank(0).getAmount() == 1000,
                    "A full item output prevents both ingredient and energy consumption");
            helper.assertTrue(items.extractItem(1, 64, false).getCount() == 64, "Clear the product slot through its item capability");
        });
        helper.runAtTickTime(42, () -> {
            var saved = processor.saveWithoutMetadata();
            helper.assertTrue(saved.getLong("progress_half_ticks") > 0, "Save the Infuser during its recipe");
            helper.setBlock(INFUSER, Blocks.AIR);
            helper.assertTrue(inlet.fill(oxygen, FluidAction.EXECUTE) == 0, "Removal revokes a cached fluid handler");
            helper.setBlock(INFUSER, Machine.INFUSER.block());
            var restored = (ItemProcessorBlockEntity) helper.getBlockEntity(INFUSER);
            restored.load(saved);
            var restoredOutput = fluid(restored, Direction.NORTH);
            helper.assertTrue(restoredOutput.fill(oxygen, FluidAction.SIMULATE) == 0
                            && restoredOutput.drain(1000, FluidAction.SIMULATE).getAmount() == 1000,
                    "Fluid volume and output mode survive reload");
            helper.succeedWhen(() -> {
                var restoredItems = restored.getCapability(ForgeCapabilities.ITEM_HANDLER, Direction.NORTH)
                        .orElseThrow(AssertionError::new);
                helper.assertTrue(restoredItems.getStackInSlot(1).is(stack("leu_235_oxide", 1).getItem())
                                && restoredItems.getStackInSlot(1).getCount() == 1 && restoredItems.getStackInSlot(0).isEmpty(),
                        "Infusion resumes and yields one reactor fuel pellet");
                var charge = restored.getCapability(ForgeCapabilities.ENERGY, Direction.SOUTH)
                        .orElseThrow(AssertionError::new);
                helper.assertTrue(charge.getEnergyStored() == 0 && restoredOutput.getFluidInTank(0).isEmpty(),
                        "The completed batch consumed exactly 6,000 FE and 1,000 mB");
            });
        });
    }

    @GameTest(template = "empty")
    public static void autoOutputRetainsFluidTheDestinationCannotAccept(GameTestHelper helper) {
        BlockPos receiver = INFUSER.east();
        helper.setBlock(INFUSER, Machine.INFUSER.block());
        helper.setBlock(receiver, Machine.INFUSER.block());
        var source = (ItemProcessorBlockEntity) helper.getBlockEntity(INFUSER);
        var target = (ItemProcessorBlockEntity) helper.getBlockEntity(receiver);
        var sourceTank = fluid(source, Direction.UP);
        var targetTank = fluid(target, Direction.WEST);
        var type = NCFluids.HEAVY_WATER.source().get();
        helper.assertTrue(sourceTank.fill(new FluidStack(type, 1200), FluidAction.EXECUTE) == 1200,
                "Accept heavy water through the shared tag");
        helper.assertTrue(targetTank.fill(new FluidStack(type, ProcessorFluidTanks.CAPACITY - 100), FluidAction.EXECUTE)
                == ProcessorFluidTanks.CAPACITY - 100, "Leave exactly 100 mB of space in the recipient");
        source.cycleFluidSide(Direction.EAST);
        source.cycleFluidSide(Direction.EAST);
        helper.succeedWhen(() -> helper.assertTrue(targetTank.getFluidInTank(0).getAmount() == ProcessorFluidTanks.CAPACITY
                        && sourceTank.getFluidInTank(0).getAmount() == 1100,
                "Auto-output sends exactly what the recipient can accept and retains the rest"));
    }

    @GameTest(template = "empty", timeoutTicks = 720)
    public static void infusesTritiumLampAndSuppliesLight(GameTestHelper helper) {
        helper.setBlock(INFUSER, Machine.INFUSER.block());
        var processor = (ItemProcessorBlockEntity) helper.getBlockEntity(INFUSER);
        var items = processor.getCapability(ForgeCapabilities.ITEM_HANDLER, Direction.UP).orElseThrow(AssertionError::new);
        var power = processor.getCapability(ForgeCapabilities.ENERGY, Direction.UP).orElseThrow(AssertionError::new);
        var inlet = fluid(processor, Direction.UP);
        var tritium = ForgeRegistries.FLUIDS.getValue(NuclearCraft.id("tritium"));
        helper.assertTrue(items.insertItem(0, new ItemStack(Blocks.GLASS), false).isEmpty(),
                "The Infuser accepts glass for the lamp");
        helper.assertTrue(inlet.fill(new FluidStack(tritium, 1000), FluidAction.EXECUTE) == 1000,
                "The Infuser accepts a bucket of tritium");
        helper.assertTrue(power.receiveEnergy(6000, false) == 6000, "The Infuser accepts the full batch charge");

        var lamp = ForgeRegistries.BLOCKS.getValue(NuclearCraft.id("tritium_lamp"));
        helper.succeedWhen(() -> {
            helper.assertTrue(items.getStackInSlot(1).is(lamp.asItem()), "The Infuser has yet to produce the lamp");
            helper.assertTrue(items.getStackInSlot(0).isEmpty() && inlet.getFluidInTank(0).isEmpty()
                    && power.getEnergyStored() == 0, "Production consumed the glass, tritium and 6,000 FE");
            helper.setBlock(INFUSER.east(), lamp);
            var state = helper.getBlockState(INFUSER.east());
            helper.assertTrue(state.getLightEmission(helper.getLevel(), helper.absolutePos(INFUSER.east())) == 15,
                    "The placed lamp supplies block light level 15");
            helper.assertTrue(state.is(BlockTags.MINEABLE_WITH_PICKAXE), "A pickaxe can recover the lamp");
            for (String id : new String[]{"fission_computer_port", "heat_exchanger_computer_port",
                    "turbine_computer_port", "machine_computer_port", "quantum_computer_port"}) {
                var block = ForgeRegistries.BLOCKS.getValue(NuclearCraft.id(id));
                helper.assertTrue(block.defaultBlockState().is(BlockTags.MINEABLE_WITH_PICKAXE),
                        "A pickaxe can recover " + id);
            }
        });
    }

    @GameTest(template = "empty", timeoutTicks = 720)
    public static void infusesEndStoneFromTaggedSandstone(GameTestHelper helper) {
        helper.setBlock(INFUSER, Machine.INFUSER.block());
        var processor = (ItemProcessorBlockEntity) helper.getBlockEntity(INFUSER);
        var items = processor.getCapability(ForgeCapabilities.ITEM_HANDLER, Direction.UP).orElseThrow(AssertionError::new);
        var power = processor.getCapability(ForgeCapabilities.ENERGY, Direction.UP).orElseThrow(AssertionError::new);
        var inlet = fluid(processor, Direction.NORTH);
        var ender = ForgeRegistries.FLUIDS.getValue(NuclearCraft.id("ender"));
        helper.assertTrue(items.insertItem(0, new ItemStack(Blocks.RED_SANDSTONE), false).isEmpty(),
                "The Infuser accepts red sandstone through the Forge sandstone tag");
        helper.assertTrue(inlet.fill(new FluidStack(ender, 250), FluidAction.EXECUTE) == 250,
                "The Infuser accepts 250 mB of its own liquid ender");
        helper.assertTrue(power.receiveEnergy(6000, false) == 6000, "The Infuser accepts the full batch charge");

        helper.succeedWhen(() -> {
            var output = items.getStackInSlot(1);
            helper.assertTrue(output.is(Blocks.END_STONE.asItem()) && output.getCount() == 1,
                    "The Infuser produces exactly one End Stone");
            helper.assertTrue(items.getStackInSlot(0).isEmpty() && inlet.getFluidInTank(0).isEmpty()
                    && power.getEnergyStored() == 0, "Production consumes the sandstone, liquid ender and 6,000 FE");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 720)
    public static void infusesPebbleCoolersAndMoltenSaltHeaterPorts(GameTestHelper helper) {
        var cooler = prepareInfusion(helper, INFUSER, "empty_cooler", "oxygen");
        var heaterPort = prepareInfusion(helper, INFUSER.east(), "fission_heater_port_standard", "cryotheum");

        helper.succeedWhen(() -> {
            checkInfusion(helper, cooler, "pebble_fission_cooler_oxygen");
            checkInfusion(helper, heaterPort, "fission_heater_port_cryotheum");
        });
    }

    private static ItemProcessorBlockEntity prepareInfusion(GameTestHelper helper, BlockPos pos, String input, String inputFluid) {
        helper.setBlock(pos, Machine.INFUSER.block());
        var processor = (ItemProcessorBlockEntity) helper.getBlockEntity(pos);
        var items = processor.getCapability(ForgeCapabilities.ITEM_HANDLER, Direction.UP).orElseThrow(AssertionError::new);
        var power = processor.getCapability(ForgeCapabilities.ENERGY, Direction.UP).orElseThrow(AssertionError::new);
        var material = ForgeRegistries.FLUIDS.getValue(NuclearCraft.id(inputFluid));
        helper.assertTrue(items.insertItem(0, stack(input, 1), false).isEmpty(), "Insert one " + input + " through a face");
        helper.assertTrue(fluid(processor, Direction.NORTH).fill(new FluidStack(material, 1000), FluidAction.EXECUTE) == 1000,
                "Insert 1,000 mB of " + inputFluid + " through a face");
        helper.assertTrue(power.receiveEnergy(6000, false) == 6000, "The Infuser accepts the full batch charge");
        return processor;
    }

    private static void checkInfusion(GameTestHelper helper, ItemProcessorBlockEntity processor, String output) {
        var items = processor.getCapability(ForgeCapabilities.ITEM_HANDLER, Direction.UP).orElseThrow(AssertionError::new);
        var power = processor.getCapability(ForgeCapabilities.ENERGY, Direction.UP).orElseThrow(AssertionError::new);
        ItemStack result = items.getStackInSlot(1);
        helper.assertTrue(result.is(stack(output, 1).getItem()) && result.getCount() == 1, "Infuse one " + output);
        helper.assertTrue(items.getStackInSlot(0).isEmpty() && fluid(processor, Direction.NORTH).getFluidInTank(0).isEmpty()
                && power.getEnergyStored() == 0, "The batch consumes one part, 1,000 mB of fluid, and 6,000 FE");
    }

    private static IFluidHandler fluid(ItemProcessorBlockEntity processor, Direction side) {
        return processor.getCapability(ForgeCapabilities.FLUID_HANDLER, side).orElseThrow(AssertionError::new);
    }

    private static ItemStack stack(String name, int count) {
        return new ItemStack(ForgeRegistries.ITEMS.getValue(NuclearCraft.id(name)), count);
    }
}
