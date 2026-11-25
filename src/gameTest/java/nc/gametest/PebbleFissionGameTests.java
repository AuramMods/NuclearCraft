package nc.gametest;

import com.mojang.authlib.GameProfile;
import nc.NuclearCraft;
import nc.radiation.ChunkRadiationSource;
import nc.radiation.WorldRadiation;
import nc.init.NCFission;
import nc.init.NCGasCoolants;
import nc.init.NCPebbleFission;
import nc.init.NCFluids;
import nc.multiblock.fission.FissionWallBlock;
import nc.multiblock.fission.FissionPartMenu;
import nc.multiblock.fission.SolidFissionController;
import nc.multiblock.turbine.TurbineController;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidUtil;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandler.FluidAction;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegisterEvent;

import java.util.Objects;
import java.util.OptionalInt;
import java.util.UUID;

@GameTestHolder(NuclearCraft.MOD_ID)
@PrefixGameTestTemplate(false)
@Mod.EventBusSubscriber(modid = NuclearCraft.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class PebbleFissionGameTests {
    private static final BlockPos MIN = new BlockPos(1, 1, 1);
    private static final BlockPos MAX = new BlockPos(9, 5, 5);
    private static final BlockPos CONTROLLER = new BlockPos(1, 3, 3);
    private static final BlockPos CHAMBER = new BlockPos(5, 3, 3);
    private static final BlockPos SOURCE = new BlockPos(5, 3, 1);
    private static final BlockPos FUEL_IN = new BlockPos(3, 1, 3);
    private static final BlockPos FUEL_OUT = new BlockPos(3, 5, 3);
    private static final BlockPos COLD = new BlockPos(3, 3, 1);
    private static final BlockPos HOT = new BlockPos(3, 3, 5);
    private static final BlockPos COLD_NITROGEN = new BlockPos(4, 3, 1);
    private static final BlockPos HOT_NITROGEN = new BlockPos(4, 3, 5);

    @SubscribeEvent
    public static void registerScriptedCoolant(RegisterEvent event) {
        event.register(ForgeRegistries.Keys.BLOCKS, helper -> {
            var gas = NCPebbleFission.scriptedCoolant("qa_oxygen", "#forge:oxygen", 3,
                    "nuclearcraft:oxygen_hot", 5, 60, "one chamber");
            helper.register(NuclearCraft.id("pebble_fission_cooler_qa_oxygen"), NCPebbleFission.createScriptedCooler(gas));
            helper.register(NuclearCraft.id("fission_cooler_port_qa_oxygen"), NCPebbleFission.createScriptedCoolerPort(gas));
            var recipeGas = NCPebbleFission.scriptedCoolant("qa_gas_recipe", "#forge:oxygen", 3,
                    "nuclearcraft:oxygen_hot", 5, 60, "one chamber");
            helper.register(NuclearCraft.id("pebble_fission_cooler_qa_gas_recipe"), NCPebbleFission.createScriptedCooler(recipeGas));
            helper.register(NuclearCraft.id("fission_cooler_port_qa_gas_recipe"), NCPebbleFission.createScriptedCoolerPort(recipeGas));
        });
    }

    @GameTest(template = "fission", timeoutTicks = 200)
    public static void scriptedPebbleCoolingProcessesWholeBatchesAcrossReload(GameTestHelper helper) {
        buildReactor(helper);
        var cooler = ForgeRegistries.BLOCKS.getValue(NuclearCraft.id("pebble_fission_cooler_qa_oxygen"));
        var port = ForgeRegistries.BLOCKS.getValue(NuclearCraft.id("fission_cooler_port_qa_oxygen"));
        helper.setBlock(COLD, port);
        helper.setBlock(HOT, output(port));
        for (Direction side : new Direction[]{Direction.UP, Direction.DOWN, Direction.NORTH, Direction.SOUTH}) {
            helper.setBlock(CHAMBER.relative(side), cooler);
        }
        var oxygen = NCGasCoolants.OXYGEN.cold();
        helper.startSequence()
                .thenWaitUntil(() -> requireAssembled(helper))
                .thenExecute(() -> {
                    var player = inspector(helper);
                    helper.useBlock(COLD, player);
                    var menu = (FissionPartMenu) player.containerMenu;
                    helper.assertTrue(menu.gasInput() != null && menu.gasInput().tag()
                                    && menu.gasInput().id().toString().equals("forge:oxygen") && menu.gasInput().amount() == 3,
                            "The shared port menu sends the scripted input definition");
                    var input = fluids(helper, COLD, Direction.NORTH);
                    helper.assertTrue(input.fill(new FluidStack(NCGasCoolants.NITROGEN.cold(), 60), FluidAction.SIMULATE) == 0,
                            "A scripted input rejects fluids outside its tag");
                    helper.assertTrue(input.fill(new FluidStack(oxygen, 2), FluidAction.EXECUTE) == 2,
                            "The port can buffer a partial batch");
                    items(helper, FUEL_IN, Direction.DOWN).insertItem(0, new ItemStack(item("leu_235_triso")), false);
                    runReactor(helper);
                })
                .thenExecuteAfter(12, () -> {
                    helper.assertTrue(amount(helper, COLD, Direction.NORTH) == 2 && amount(helper, HOT, Direction.SOUTH) == 0,
                            "An incomplete batch produces no gas and preserves its input");
                    helper.assertTrue(fluids(helper, COLD, Direction.NORTH).fill(new FluidStack(oxygen, 60), FluidAction.EXECUTE) == 60,
                            "Fill the rest through the scripted port");
                })
                .thenWaitUntil(() -> {
                    var product = fluids(helper, HOT, Direction.SOUTH).getFluidInTank(0);
                    helper.assertTrue(product.getFluid() == NCGasCoolants.OXYGEN.hot() && product.getAmount() == 100,
                            "Twenty batches convert 60 mB of oxygen into 100 mB of hot oxygen");
                })
                .thenExecute(() -> {
                    helper.setBlock(CONTROLLER.west(), Blocks.AIR);
                    helper.setBlock(MIN, Blocks.AIR);
                    var saved = controller(helper).saveWithoutMetadata();
                    helper.setBlock(CONTROLLER, Blocks.AIR);
                    helper.setBlock(CONTROLLER, NCPebbleFission.CONTROLLER.get());
                    controller(helper).load(saved);
                    helper.setBlock(MIN, NCFission.CASING.get());
                })
                .thenWaitUntil(() -> requireAssembled(helper))
                .thenExecute(() -> {
                    var drained = fluids(helper, HOT, Direction.SOUTH).drain(100, FluidAction.EXECUTE);
                    helper.assertTrue(drained.getFluid() == NCGasCoolants.OXYGEN.hot() && drained.getAmount() == 100
                                    && amount(helper, COLD, Direction.NORTH) == 2 && amount(helper, HOT, Direction.SOUTH) == 0,
                            "Reassembly restores the scripted product and the two-millibucket remainder");
                })
                .thenSucceed();
    }

    @GameTest(template = "fission", timeoutTicks = 300)
    public static void authoredGasCoolingUsesCountedAlternativesAndChanceProducts(GameTestHelper helper) {
        buildReactor(helper);
        var cooler = ForgeRegistries.BLOCKS.getValue(NuclearCraft.id("pebble_fission_cooler_qa_gas_recipe"));
        var port = ForgeRegistries.BLOCKS.getValue(NuclearCraft.id("fission_cooler_port_qa_gas_recipe"));
        helper.setBlock(COLD, port);
        helper.setBlock(HOT, output(port));
        for (Direction side : new Direction[]{Direction.UP, Direction.DOWN, Direction.NORTH, Direction.SOUTH}) {
            helper.setBlock(CHAMBER.relative(side), cooler);
        }
        var nitrogen = NCGasCoolants.NITROGEN;
        helper.startSequence()
                .thenWaitUntil(() -> requireAssembled(helper))
                .thenExecute(() -> {
                    var input = fluids(helper, COLD, Direction.NORTH);
                    helper.assertTrue(input.fill(new FluidStack(NCGasCoolants.OXYGEN.cold(), 31), FluidAction.SIMULATE) == 0,
                            "The authored recipes replace the startup fluid for this material");
                    helper.assertTrue(input.fill(new FluidStack(nitrogen.cold(), 31), FluidAction.SIMULATE) == 31
                                    && input.getFluidInTank(0).isEmpty()
                                    && input.fill(new FluidStack(nitrogen.cold(), 31), FluidAction.EXECUTE) == 31,
                            "The port accepts the new fluid without mutating either side of a simulated fill");
                    var player = inspector(helper);
                    helper.useBlock(COLD, player);
                    var menu = (FissionPartMenu) player.containerMenu;
                    helper.assertTrue(menu.gasInput() != null && menu.gasInput().tag()
                                    && menu.gasInput().amount() == 18 && menu.cooling() == 210,
                            "The reactor port reports the selected counted dose and rate");
                    var oldCooler = helper.getBlockEntity(CHAMBER.below());
                    var oldPart = oldCooler.saveWithoutMetadata();
                    oldPart.remove("progress");
                    oldCooler.load(oldPart);
                    var convertedCooler = helper.getBlockEntity(CHAMBER.above());
                    var convertedPart = convertedCooler.saveWithoutMetadata();
                    convertedPart.remove("progress");
                    convertedPart.putDouble("time", 0.375D);
                    convertedCooler.load(convertedPart);
                    var saved = controller(helper).saveWithoutMetadata();
                    var fractions = new ListTag();
                    var paid = new CompoundTag();
                    paid.putLong("pos", helper.absolutePos(CHAMBER.below()).asLong());
                    paid.putDouble("value", 0.625D);
                    fractions.add(paid);
                    saved.getCompound("gas_cooling").put("fractions", fractions);
                    controller(helper).load(saved);
                })
                .thenExecuteAfter(5, () -> {
                    var fractions = controller(helper).saveWithoutMetadata().getCompound("gas_cooling")
                            .getList("fractions", Tag.TAG_COMPOUND);
                    helper.assertTrue(fractions.isEmpty() && helper.getBlockEntity(CHAMBER.below())
                                    .saveWithoutMetadata().getDouble("progress") == 0.625D,
                            "An unpowered cooler imports its old controller progress with a valid batch");
                    helper.assertTrue(helper.getBlockEntity(CHAMBER.above()).saveWithoutMetadata()
                                    .getDouble("progress") == 0.375D,
                            "The 1.12.2 cooler's saved time loads as local progress");
                    helper.setBlock(CHAMBER.below(), Blocks.AIR);
                    helper.setBlock(CHAMBER.below(), cooler);
                })
                .thenWaitUntil(() -> requireAssembled(helper))
                .thenExecute(() -> {
                    var player = inspector(helper);
                    helper.useBlock(CHAMBER.below(), player);
                    helper.assertTrue(player.containerMenu instanceof FissionPartMenu menu && menu.connected()
                                    && menu.progress() == 0F,
                            "A replacement cooler starts at zero while the shared tank is still ready");
                    helper.assertTrue(helper.getBlockEntity(CHAMBER.above()).saveWithoutMetadata()
                                    .getDouble("progress") == 0.375D,
                            "The surviving cooler still holds its progress through reassembly");
                    items(helper, FUEL_IN, Direction.DOWN).insertItem(0, new ItemStack(item("leu_235_triso")), false);
                    runReactor(helper);
                })
                .thenWaitUntil(() -> {
                    var product = fluids(helper, HOT, Direction.SOUTH).getFluidInTank(0);
                    helper.assertTrue(product.isFluidStackIdentical(new FluidStack(nitrogen.hot(), 6))
                                    && amount(helper, COLD, Direction.NORTH) == 3,
                            "An 18 mB dose and a 10 mB dose leave 3 mB of feed and produce 6 mB of hot nitrogen");
                })
                .thenExecute(() -> {
                    for (Direction side : new Direction[]{Direction.UP, Direction.DOWN, Direction.NORTH, Direction.SOUTH}) {
                        helper.assertTrue(helper.getBlockEntity(CHAMBER.relative(side)).saveWithoutMetadata()
                                        .getDouble("progress") == 0D,
                                "The short remainder clears progress on every cooler sharing the circuit");
                    }
                    var player = inspector(helper);
                    helper.useBlock(COLD, player);
                    var menu = (FissionPartMenu) player.containerMenu;
                    helper.assertTrue(menu.gasInput() != null && menu.gasInput().tag(),
                            "Partial feed still displays the matching input definition");
                })
                .thenSucceed();
    }

    @GameTest(template = "turbine", timeoutTicks = 1200)
    public static void pebbleReactorSendsHotGasToTurbine(GameTestHelper helper) {
        buildReactor(helper);
        BlockPos offset = new BlockPos(1, 0, 6);
        TurbineGameTests.buildTurbine(helper, offset);
        BlockPos turbine = new BlockPos(1, 3, 2).offset(offset);
        BlockPos inlet = new BlockPos(2, 3, 1).offset(offset);
        BlockPos outlet = new BlockPos(2, 2, 4).offset(offset);
        BlockPos coil = new BlockPos(2, 3, 4).offset(offset);
        helper.startSequence()
                .thenWaitUntil(() -> {
                    requireAssembled(helper);
                    var engine = (TurbineController) helper.getBlockEntity(turbine);
                    helper.assertTrue(engine.assembled(), "Gas turbine is still assembling");
                })
                .thenExecute(() -> {
                    var fuel = items(helper, FUEL_IN, Direction.DOWN);
                    helper.assertTrue(fuel.insertItem(0, new ItemStack(item("leu_235_oxide")), true).getCount() == 1,
                            "A chamber port accepts TRISO fuel");
                    helper.assertTrue(fuel.insertItem(0, new ItemStack(item("leu_235_triso")), false).isEmpty(),
                            "Feed a real TRISO fuel pellet");
                    IFluidHandler cold = fluids(helper, COLD, Direction.NORTH);
                    helper.assertTrue(cold.fill(new FluidStack(NCGasCoolants.NITROGEN.cold(), 900), FluidAction.SIMULATE) == 0,
                            "The oxygen circuit rejects nitrogen");
                    var oxygen = new FluidStack(NCGasCoolants.OXYGEN.cold(), 900);
                    helper.assertTrue(cold.fill(oxygen, FluidAction.SIMULATE) == 900 && cold.getFluidInTank(0).isEmpty(),
                            "A simulated gas fill leaves the contents in place");
                    helper.assertTrue(cold.fill(oxygen, FluidAction.EXECUTE) == 900, "Fill the oxygen circuit through its outward face");
                    runReactor(helper);
                    helper.setBlock(turbine.west(), Blocks.REDSTONE_BLOCK);
                    IFluidHandler hot = fluids(helper, HOT, Direction.SOUTH);
                    IFluidHandler destination = fluids(helper, inlet, Direction.NORTH);
                    helper.onEachTick(() -> FluidUtil.tryFluidTransfer(destination, hot, 1000, true));
                })
                .thenWaitUntil(() -> helper.assertTrue(
                        ((ChunkRadiationSource) helper.getBlockEntity(CHAMBER)).radiationEmission() > 0,
                        "An active pebble fuel chamber emits radiation"))
                .thenWaitUntil(() -> {
                    var exhaust = fluids(helper, outlet, Direction.SOUTH).getFluidInTank(0);
                    helper.assertTrue(exhaust.getFluid() == NCGasCoolants.OXYGEN.exhaust() && exhaust.getAmount() == 3600,
                            "900 mB of gas must make 1,800 mB of hot gas and 3,600 mB of exhaust");
                    var energy = helper.getBlockEntity(coil).getCapability(ForgeCapabilities.ENERGY, Direction.SOUTH)
                            .orElseThrow(AssertionError::new);
                    helper.assertTrue(energy.getEnergyStored() > 0, "The gas expansion drives the Forge Energy dynamo");
                })
                .thenSucceed();
    }

    @GameTest(template = "fission", timeoutTicks = 340)
    public static void pebbleCoolerCircuitsPreserveSeparateGasesAcrossRebuild(GameTestHelper helper) {
        buildReactor(helper);
        helper.setBlock(CHAMBER.above().north(), NCPebbleFission.cooler(NCGasCoolants.NITROGEN));
        helper.setBlock(COLD_NITROGEN, NCPebbleFission.port(NCGasCoolants.NITROGEN));
        helper.setBlock(HOT_NITROGEN, output(NCPebbleFission.port(NCGasCoolants.NITROGEN)));
        int[] savedGas = new int[4];
        helper.startSequence()
                .thenWaitUntil(() -> requireAssembled(helper))
                .thenExecute(() -> {
                    fluids(helper, COLD, Direction.NORTH).fill(new FluidStack(NCGasCoolants.OXYGEN.cold(), 900), FluidAction.EXECUTE);
                    fluids(helper, COLD_NITROGEN, Direction.NORTH).fill(new FluidStack(NCGasCoolants.NITROGEN.cold(), 350), FluidAction.EXECUTE);
                    items(helper, FUEL_IN, Direction.DOWN).insertItem(0, new ItemStack(item("leu_235_triso")), false);
                    runReactor(helper);
                })
                .thenWaitUntil(() -> {
                    helper.assertTrue(amount(helper, HOT, Direction.SOUTH) >= 20, "The oxygen placements cool a running chamber");
                    helper.assertTrue(amount(helper, HOT_NITROGEN, Direction.SOUTH) >= 20,
                            "Nitrogen uses its two neighboring oxygen coolers");
                })
                .thenExecute(() -> {
                    helper.setBlock(CONTROLLER.west(), Blocks.AIR);
                    var port = helper.getBlockEntity(HOT_NITROGEN);
                    var capability = port.getCapability(ForgeCapabilities.FLUID_HANDLER, Direction.SOUTH);
                    var cached = capability.orElseThrow(AssertionError::new);
                    helper.assertTrue(!port.getCapability(ForgeCapabilities.FLUID_HANDLER, Direction.NORTH).isPresent(),
                            "The inward face must not expose a shell port");
                    savedGas[0] = amount(helper, COLD, Direction.NORTH);
                    savedGas[1] = amount(helper, HOT, Direction.SOUTH);
                    savedGas[2] = amount(helper, COLD_NITROGEN, Direction.NORTH);
                    savedGas[3] = amount(helper, HOT_NITROGEN, Direction.SOUTH);
                    helper.assertTrue(savedGas[1] == 2 * (900 - savedGas[0]) && savedGas[3] == 2 * (350 - savedGas[2]),
                            "Each gas uses only its own circuit at the legacy one-to-two volume ratio");
                    helper.setBlock(MIN, Blocks.AIR);
                    helper.assertTrue(!capability.isPresent() && cached.drain(1, FluidAction.EXECUTE).isEmpty(),
                            "A broken frame revokes cached gas handlers immediately");
                    var saved = controller(helper).saveWithoutMetadata();
                    helper.setBlock(CONTROLLER, Blocks.AIR);
                    helper.setBlock(CONTROLLER, NCPebbleFission.CONTROLLER.get());
                    controller(helper).load(saved);
                    helper.setBlock(MIN, NCFission.CASING.get());
                })
                .thenWaitUntil(() -> requireAssembled(helper))
                .thenExecute(() -> {
                    helper.assertTrue(savedGas[0] == amount(helper, COLD, Direction.NORTH)
                                    && savedGas[1] == amount(helper, HOT, Direction.SOUTH)
                                    && savedGas[2] == amount(helper, COLD_NITROGEN, Direction.NORTH)
                                    && savedGas[3] == amount(helper, HOT_NITROGEN, Direction.SOUTH),
                            "All four gas amounts survive loading and reassembly exactly");
                })
                .thenSucceed();
    }

    @GameTest(template = "fission", timeoutTicks = 100)
    public static void idlePebbleVentsCoolCasingWithoutMixingGases(GameTestHelper helper) {
        buildReactor(helper);
        helper.setBlock(COLD_NITROGEN, NCFission.VENT.get());
        helper.setBlock(HOT_NITROGEN, output(NCFission.VENT.get()));
        helper.startSequence()
                .thenWaitUntil(() -> requireAssembled(helper))
                .thenExecute(() -> {
                    var inlet = fluids(helper, COLD_NITROGEN, Direction.NORTH);
                    var gasInlet = fluids(helper, COLD, Direction.NORTH);
                    var coolant = new FluidStack(NCFluids.EMERGENCY_COOLANT.source().get(), 100);
                    helper.assertTrue(inlet.fill(new FluidStack(NCGasCoolants.OXYGEN.cold(), 100), FluidAction.EXECUTE) == 0
                                    && gasInlet.fill(coolant, FluidAction.EXECUTE) == 0,
                            "Emergency vents and gas cooler ports accept different coolant supplies");
                    helper.assertTrue(inlet.fill(coolant, FluidAction.EXECUTE) == 100, "Load emergency coolant into the idle reactor");
                    var saved = controller(helper).saveWithoutMetadata();
                    saved.putLong("heat", 123);
                    var oxygen = saved.getCompound("gas_cooling").getCompound("oxygen");
                    oxygen.put("input", new FluidStack(NCGasCoolants.OXYGEN.cold(), 120).writeToNBT(new CompoundTag()));
                    controller(helper).load(saved);
                    helper.assertTrue(gasInlet.getFluidInTank(0).getAmount() == 120,
                            "A previously bound gas handler reads the new contents after a live NBT load");
                })
                .thenWaitUntil(() -> {
                    var emergency = fluids(helper, HOT_NITROGEN, Direction.SOUTH).getFluidInTank(0);
                    helper.assertTrue(emergency.getFluid() == NCFluids.HEATED_EMERGENCY_COOLANT.source().get()
                                    && emergency.getAmount() == 100 && amount(helper, HOT, Direction.SOUTH) == 0
                                    && controller(helper).saveWithoutMetadata().getLong("heat") == 22,
                            "The idle vent removes 100 casing heat and passive cooling removes another 1 H");
                })
                .thenSucceed();
    }

    @GameTest(template = "fission", timeoutTicks = 100)
    public static void idleVentFitsCoolantAlternativesToTheRemainingCasingHeat(GameTestHelper helper) {
        buildReactor(helper);
        helper.setBlock(COLD_NITROGEN, NCFission.VENT.get());
        helper.setBlock(HOT_NITROGEN, output(NCFission.VENT.get()));
        helper.startSequence()
                .thenWaitUntil(() -> requireAssembled(helper))
                .thenExecute(() -> {
                    var coolant = new FluidStack(NCFluids.HELIUM_3.source().get(), 28);
                    helper.assertTrue(fluids(helper, COLD_NITROGEN, Direction.NORTH).fill(coolant, FluidAction.EXECUTE) == 28,
                            "The idle reactor must accept the alternate emergency coolant");
                    var stored = controller(helper).saveWithoutMetadata();
                    stored.putLong("heat", 9);
                    controller(helper).load(stored);
                })
                .thenExecuteAfter(4, () -> {
                    helper.assertTrue(amount(helper, COLD_NITROGEN, Direction.NORTH) == 28
                                    && amount(helper, HOT_NITROGEN, Direction.SOUTH) == 0,
                            "A casing heat budget below 10 must hold all coolant");
                    var stored = controller(helper).saveWithoutMetadata();
                    stored.putLong("heat", 10);
                    controller(helper).load(stored);
                })
                .thenWaitUntil(() -> helper.assertTrue(amount(helper, COLD_NITROGEN, Direction.NORTH) == 18
                                && amount(helper, HOT_NITROGEN, Direction.SOUTH) == 7,
                        "Ten casing heat must use only the fallback dose"))
                .thenExecute(() -> {
                    var stored = controller(helper).saveWithoutMetadata();
                    stored.putLong("heat", 18);
                    controller(helper).load(stored);
                })
                .thenWaitUntil(() -> helper.assertTrue(amount(helper, COLD_NITROGEN, Direction.NORTH) == 0
                                && amount(helper, HOT_NITROGEN, Direction.SOUTH) == 14,
                        "The next 18 casing heat must use the preferred dose"))
                .thenSucceed();
    }

    @GameTest(template = "fission", timeoutTicks = 100)
    public static void emergencyVentContinuesFillingAnEstablishedOutlet(GameTestHelper helper) {
        buildReactor(helper);
        helper.setBlock(COLD_NITROGEN, NCFission.VENT.get());
        helper.setBlock(HOT_NITROGEN, output(NCFission.VENT.get()));
        var feed = new FluidStack(ForgeRegistries.FLUIDS.getValue(NuclearCraft.id("benzene")), 28);
        helper.startSequence()
                .thenWaitUntil(() -> requireAssembled(helper))
                .thenExecute(() -> {
                    var saved = controller(helper).saveWithoutMetadata();
                    saved.put("coolant", feed.writeToNBT(new CompoundTag()));
                    saved.putLong("heat", 28);
                    controller(helper).load(saved);
                })
                .thenWaitUntil(() -> helper.assertTrue(amount(helper, COLD_NITROGEN, Direction.NORTH) == 0
                                && controller(helper).saveWithoutMetadata().getLong("heat") == 0,
                        "The full coolant charge must remove casing heat"))
                .thenExecute(() -> {
                    helper.assertTrue(amount(helper, HOT_NITROGEN, Direction.SOUTH) == 0,
                            "A zero roll cannot establish exhaust in an empty tank");
                    var saved = controller(helper).saveWithoutMetadata();
                    saved.put("coolant", feed.writeToNBT(new CompoundTag()));
                    saved.put("steam", new FluidStack(NCFluids.HEATED_EMERGENCY_COOLANT.source().get(), 1)
                            .writeToNBT(new CompoundTag()));
                    saved.putLong("heat", 28);
                    controller(helper).load(saved);
                })
                .thenWaitUntil(() -> helper.assertTrue(amount(helper, COLD_NITROGEN, Direction.NORTH) == 0
                                && amount(helper, HOT_NITROGEN, Direction.SOUTH) == 17,
                        "A tank holding matching exhaust receives two 8 mB batches"))
                .thenSucceed();
    }

    @GameTest(template = "fission", timeoutTicks = 110)
    public static void pebbleFuelWithNoProductPreservesExistingOutput(GameTestHelper helper) {
        buildReactor(helper);
        helper.startSequence()
                .thenWaitUntil(() -> requireAssembled(helper))
                .thenExecute(() -> {
                    var saved = controller(helper).saveWithoutMetadata();
                    var result = new ItemStack(Items.DIAMOND, 5).save(new CompoundTag());
                    result.putByte("Slot", (byte) 1);
                    var contents = new ListTag();
                    contents.add(result);
                    saved.getCompound("inventory").put("Items", contents);
                    controller(helper).load(saved);
                    var input = items(helper, FUEL_IN, Direction.DOWN);
                    helper.assertTrue(input.insertItem(0, new ItemStack(Items.LODESTONE), false).isEmpty(),
                            "The chamber accepts fuel with an empty product");
                    runReactor(helper);
                })
                .thenWaitUntil(() -> helper.assertTrue(helper.getBlockEntity(CHAMBER).saveWithoutMetadata()
                        .getString("reserved_recipe").equals("fission_products_qa:pebble"), "Wait for the fuel reservation"))
                .thenWaitUntil(() -> helper.assertTrue(!helper.getBlockEntity(CHAMBER).saveWithoutMetadata()
                        .contains("reserved_recipe"), "The productless dose must finish"))
                .thenExecute(() -> {
                    var output = items(helper, FUEL_OUT, Direction.UP).getStackInSlot(0);
                    helper.assertTrue(output.is(Items.DIAMOND) && output.getCount() == 5
                                    && items(helper, FUEL_IN, Direction.DOWN).getStackInSlot(0).isEmpty(),
                            "An empty result keeps the existing output and spends the input");
                })
                .thenSucceed();
    }

    @GameTest(template = "fission", timeoutTicks = 200)
    public static void filteredCoolerPortsKeepTaggedGasAlternativesInSeparateCircuits(GameTestHelper helper) {
        buildReactor(helper);
        helper.setBlock(MIN, Blocks.AIR);
        BlockPos secondInput = COLD.east();
        BlockPos secondOutput = HOT.east();
        helper.setBlock(secondInput, NCPebbleFission.port(NCGasCoolants.OXYGEN));
        helper.setBlock(secondOutput, output(NCPebbleFission.port(NCGasCoolants.OXYGEN)));
        var player = inspector(helper);
        var oxygen = NCGasCoolants.OXYGEN.cold();
        var alternate = NCGasCoolants.FLUOROMETHANE.cold();
        // The test datapack adds this second source to forge:oxygen.
        var normalSample = new ItemStack(oxygen.getBucket());
        var alternateSample = new ItemStack(alternate.getBucket());
        player.setItemInHand(InteractionHand.MAIN_HAND, normalSample);
        for (BlockPos part : new BlockPos[]{COLD, HOT, CHAMBER.above()}) {
            helper.useBlock(part, player);
        }
        player.setItemInHand(InteractionHand.MAIN_HAND, alternateSample);
        for (BlockPos part : new BlockPos[]{secondInput, secondOutput, CHAMBER.below()}) {
            helper.useBlock(part, player);
        }
        helper.assertTrue(player.getMainHandItem().is(alternateSample.getItem()) && player.getMainHandItem().getCount() == 1,
                "Setting filters leaves the sample in hand");
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        helper.setBlock(MIN, NCFission.CASING.get());
        int[] savedGas = new int[4];

        helper.startSequence()
                .thenWaitUntil(() -> requireAssembled(helper))
                .thenExecute(() -> {
                    var first = fluids(helper, COLD, Direction.NORTH);
                    var second = fluids(helper, secondInput, Direction.NORTH);
                    var normal = new FluidStack(oxygen, 300);
                    var other = new FluidStack(alternate, 500);
                    helper.assertTrue(!first.isFluidValid(0, other) && !second.isFluidValid(0, normal)
                                    && first.fill(other, FluidAction.SIMULATE) == 0
                                    && second.fill(normal, FluidAction.EXECUTE) == 0,
                            "The input ports reject the other source even though both sources belong to the allowed tag");
                    helper.assertTrue(first.fill(normal, FluidAction.SIMULATE) == 300 && first.getFluidInTank(0).isEmpty()
                                    && first.fill(normal, FluidAction.EXECUTE) == 300
                                    && second.fill(other, FluidAction.EXECUTE) == 500,
                            "Each accepted source goes only into its own buffer");
                    helper.useBlock(CHAMBER.below(), player);
                    var linked = (FissionPartMenu) player.containerMenu;
                    helper.assertTrue(linked.linked() && linked.exactGasFilter() && linked.gasFilter().getFluid() == alternate
                                    && linked.gas(false).isFluidStackIdentical(other),
                            "The chosen cooler uses the matching circuit");
                    helper.useBlock(CHAMBER.north(), player);
                    helper.assertTrue(!((FissionPartMenu) player.containerMenu).linked(),
                            "An unfiltered cooler with two candidate circuits keeps its local tanks");
                    items(helper, FUEL_IN, Direction.DOWN).insertItem(0, new ItemStack(item("leu_235_triso")), false);
                    runReactor(helper);
                })
                .thenWaitUntil(() -> helper.assertTrue(amount(helper, HOT, Direction.SOUTH) > 0
                                && amount(helper, secondOutput, Direction.SOUTH) > 0,
                        "Both filtered circuits cool the running chamber"))
                .thenExecute(() -> {
                    helper.setBlock(CONTROLLER.west(), Blocks.AIR);
                    var stale = helper.getBlockEntity(secondOutput).getCapability(ForgeCapabilities.FLUID_HANDLER, Direction.SOUTH);
                    var old = stale.orElseThrow(AssertionError::new);
                    savedGas[0] = amount(helper, COLD, Direction.NORTH);
                    savedGas[1] = amount(helper, HOT, Direction.SOUTH);
                    savedGas[2] = amount(helper, secondInput, Direction.NORTH);
                    savedGas[3] = amount(helper, secondOutput, Direction.SOUTH);
                    helper.assertTrue(savedGas[1] == 2 * (300 - savedGas[0]) && savedGas[3] == 2 * (500 - savedGas[2]),
                            "Both hot outputs match the amounts used by their own circuit");
                    helper.setBlock(MIN, Blocks.AIR);
                    helper.assertTrue(!stale.isPresent() && old.drain(1, FluidAction.EXECUTE).isEmpty(),
                            "Disassembly revokes the cached filtered output");
                    var pos = helper.absolutePos(secondOutput);
                    player.moveTo(pos.getX() + 0.5D, pos.getY() + 1D, pos.getZ() + 0.5D);
                    helper.useBlock(secondOutput, player);
                    var menu = (FissionPartMenu) player.containerMenu;
                    helper.assertTrue(menu.canClearGasFilter() && menu.clickMenuButton(player, FissionPartMenu.CLEAR_INPUT),
                            "The detached port filter can be cleared from the empty input tank");
                    menu.broadcastChanges();
                    helper.assertTrue(!menu.exactGasFilter(), "The menu reports the cleared filter");
                    player.setItemInHand(InteractionHand.MAIN_HAND, alternateSample);
                    helper.useBlock(secondOutput, player);
                    player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                    var saved = controller(helper).saveWithoutMetadata();
                    helper.setBlock(CONTROLLER, Blocks.AIR);
                    helper.setBlock(CONTROLLER, NCPebbleFission.CONTROLLER.get());
                    controller(helper).load(saved);
                    helper.setBlock(MIN, NCFission.CASING.get());
                })
                .thenWaitUntil(() -> requireAssembled(helper))
                .thenExecute(() -> helper.assertTrue(
                        savedGas[0] == amount(helper, COLD, Direction.NORTH)
                                && savedGas[1] == amount(helper, HOT, Direction.SOUTH)
                                && savedGas[2] == amount(helper, secondInput, Direction.NORTH)
                                && savedGas[3] == amount(helper, secondOutput, Direction.SOUTH),
                        "All filtered gas buffers survive controller reload and filter reselection"))
                .thenSucceed();
    }

    @GameTest(template = "fission", timeoutTicks = 160)
    public static void matchingPebbleChambersShareCriticality(GameTestHelper helper) {
        buildReactor(helper);
        BlockPos second = CHAMBER.south();
        helper.setBlock(second, NCPebbleFission.CHAMBER.get());
        helper.setBlock(CHAMBER.east(3), Blocks.AIR);
        helper.startSequence()
                .thenWaitUntil(() -> requireAssembled(helper))
                .thenExecute(() -> {
                    items(helper, FUEL_IN, Direction.DOWN).insertItem(0, new ItemStack(item("leu_235_triso")), false);
                    runReactor(helper);
                })
                .thenExecuteAfter(14, () -> {
                    helper.assertTrue(helper.getBlockEntity(CHAMBER).saveWithoutMetadata().getDouble("progress") == 0,
                            "An isolated chamber receives 84 flux and remains below criticality");
                    items(helper, FUEL_IN, Direction.DOWN).insertItem(0, new ItemStack(item("leu_235_triso")), false);
                })
                .thenWaitUntil(() -> helper.assertTrue(
                        helper.getBlockEntity(CHAMBER).saveWithoutMetadata().getDouble("progress") > 0
                                && helper.getBlockEntity(second).saveWithoutMetadata().getDouble("progress") > 0,
                        "Two matching neighbors share their intrinsic flux and both reach criticality"))
                .thenSucceed();
    }

    @GameTest(template = "fission", timeoutTicks = 320)
    public static void dryCoolersDoNotConductHeatToTheWall(GameTestHelper helper) {
        buildReactor(helper);
        helper.startSequence()
                .thenWaitUntil(() -> requireAssembled(helper))
                .thenExecute(() -> {
                    items(helper, FUEL_IN, Direction.DOWN).insertItem(0, new ItemStack(item("leu_235_triso")), false);
                    runReactor(helper);
                })
                .thenWaitUntil(() -> {
                    helper.assertBlockPresent(NCFluids.CORIUM.block().get(), CHAMBER);
                    helper.assertBlockPresent(NCPebbleFission.CONTROLLER.get(), CONTROLLER);
                    double leak = WorldRadiation.rate(helper.getLevel(), helper.absolutePos(CHAMBER));
                    helper.assertTrue(leak > 8 * 1.4647403038194445E-5, "The meltdown immediately contaminates the cell's chunk");
                }).thenSucceed();
    }

    @GameTest(template = "fission", timeoutTicks = 130)
    public static void filteredChambersMatchTheirPortsAndKeepReservations(GameTestHelper helper) {
        buildReactor(helper);
        helper.setBlock(MIN, Blocks.AIR);
        BlockPos second = CHAMBER.north().above();
        BlockPos secondInput = FUEL_IN.east();
        BlockPos secondOutput = FUEL_OUT.east();
        helper.setBlock(second, NCPebbleFission.CHAMBER.get());
        helper.setBlock(secondInput, NCPebbleFission.CHAMBER_PORT.get());
        helper.setBlock(secondOutput, output(NCPebbleFission.CHAMBER_PORT.get()));
        FakePlayer player = inspector(helper);
        var leu235 = new ItemStack(item("leu_235_triso"));
        var leu233 = new ItemStack(item("leu_233_triso"));
        player.setItemInHand(InteractionHand.MAIN_HAND, leu235);
        helper.useBlock(CHAMBER, player);
        helper.useBlock(FUEL_IN, player);
        helper.useBlock(FUEL_OUT, player);
        helper.assertTrue(player.getMainHandItem().getCount() == 1, "Setting a filter must preserve its sample");
        player.setItemInHand(InteractionHand.MAIN_HAND, leu233);
        helper.useBlock(second, player);
        helper.useBlock(secondInput, player);
        helper.useBlock(secondOutput, player);
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        helper.setBlock(MIN, NCFission.CASING.get());

        helper.startSequence()
                .thenWaitUntil(() -> requireAssembled(helper))
                .thenExecute(() -> {
                    var first = items(helper, FUEL_IN, Direction.DOWN);
                    var next = items(helper, secondInput, Direction.DOWN);
                    helper.assertTrue(first.insertItem(0, leu233, true).getCount() == 1
                                    && next.insertItem(0, leu235, false).getCount() == 1,
                            "Each outward input rejects the valid fuel of the other circuit");
                    helper.assertTrue(first.insertItem(0, leu235.copyWithCount(2), false).isEmpty()
                                    && next.insertItem(0, leu233.copyWithCount(3), false).isEmpty(),
                            "Matching pellets reach both circuits");
                    helper.useBlock(second, player);
                    var menu = (FissionPartMenu) player.containerMenu;
                    helper.assertTrue(menu.linked() && menu.fuelFilter().is(leu233.getItem())
                                    && menu.getSlot(0).getItem().getCount() == 3,
                            "The chamber menu reads only its matching port circuit");
                    player.getInventory().setItem(9, leu233.copy());
                    helper.assertTrue(menu.quickMoveStack(player, 2).is(leu233.getItem())
                                    && player.getInventory().getItem(9).isEmpty() && next.getStackInSlot(0).getCount() == 4,
                            "Shift-clicking a pellet in the menu inserts into the same circuit");
                    fluids(helper, COLD, Direction.NORTH).fill(new FluidStack(NCGasCoolants.OXYGEN.cold(), 1200), FluidAction.EXECUTE);
                    runReactor(helper);
                })
                .thenWaitUntil(() -> helper.assertTrue(
                        helper.getBlockEntity(CHAMBER).saveWithoutMetadata().getDouble("progress") > 0,
                        "The first chamber reserves and starts consuming its matched fuel"))
                .thenExecute(() -> {
                    var before = helper.getBlockEntity(second).saveWithoutMetadata();
                    helper.assertTrue(ItemStack.of(before.getCompound("fuel")).is(leu233.getItem()),
                            "The second chamber reserved its own fuel");
                    var old = helper.getBlockEntity(FUEL_IN).getCapability(ForgeCapabilities.ITEM_HANDLER, Direction.DOWN);
                    helper.setBlock(MIN, Blocks.AIR);
                    helper.assertTrue(!old.isPresent(), "A broken shell invalidates old item capabilities");
                    var first = helper.getBlockEntity(CHAMBER);
                    var saved = first.saveWithoutMetadata();
                    saved.putDouble("progress", 4800D);
                    first.load(saved);
                    helper.setBlock(MIN, NCFission.CASING.get());
                })
                .thenWaitUntil(() -> {
                    requireAssembled(helper);
                    helper.assertTrue(items(helper, FUEL_OUT, Direction.UP).getStackInSlot(0)
                            .is(item("depleted_leu_235_triso")), "Reassembly discharges reserved fuel into its original filtered circuit");
                })
                .thenExecute(() -> helper.setBlock(FUEL_OUT.above(), Blocks.BARREL))
                .thenWaitUntil(() -> {
                    var barrel = (Container) helper.getBlockEntity(FUEL_OUT.above());
                    helper.assertTrue(barrel.getItem(0).is(item("depleted_leu_235_triso"))
                                    && barrel.getItem(0).getCount() == 1
                                    && items(helper, FUEL_OUT, Direction.UP).getStackInSlot(0).isEmpty(),
                            "The output port pushes the one depleted pellet into adjacent storage");
                })
                .thenSucceed();
    }

    @GameTest(template = "fission", timeoutTicks = 165)
    public static void unportedCoolerProcessesFromItsLocalTanks(GameTestHelper helper) {
        buildReactor(helper);
        helper.setBlock(COLD, NCFission.CASING.get());
        helper.setBlock(HOT, NCFission.CASING.get());
        BlockPos cooler = CHAMBER.above();
        FakePlayer player = inspector(helper);
        BlockPos pos = helper.absolutePos(cooler);
        player.moveTo(pos.getX() + 0.5D, pos.getY() + 1D, pos.getZ() + 0.5D);
        helper.startSequence()
                .thenWaitUntil(() -> requireAssembled(helper))
                .thenExecute(() -> {
                    player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(NCGasCoolants.OXYGEN.cold().getBucket()));
                    helper.useBlock(cooler, player);
                    player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                    helper.useBlock(cooler, player);
                    var menu = (FissionPartMenu) player.containerMenu;
                    helper.assertTrue(menu.connected() && !menu.linked() && menu.gas(false).getAmount() == 1000
                                    && menu.capacity(false) == 1296, "Fill and inspect the cooler without a gas port");
                    items(helper, FUEL_IN, Direction.DOWN).insertItem(0, new ItemStack(item("leu_235_triso")), false);
                    runReactor(helper);
                })
                .thenWaitUntil(() -> {
                    var menu = (FissionPartMenu) player.containerMenu;
                    menu.broadcastChanges();
                    helper.assertTrue(menu.gas(true).getAmount() > 0, "Local cold oxygen is heated inside the reactor");
                })
                .thenExecute(() -> {
                    helper.setBlock(CONTROLLER.west(), Blocks.AIR);
                    var menu = (FissionPartMenu) player.containerMenu;
                    menu.broadcastChanges();
                    int output = menu.gas(true).getAmount();
                    player.moveTo(pos.getX() + 20, pos.getY(), pos.getZ());
                    helper.assertTrue(!menu.clickMenuButton(player, FissionPartMenu.CLEAR_OUTPUT),
                            "A player out of range cannot void cooler contents");
                    player.moveTo(pos.getX() + 0.5D, pos.getY() + 1D, pos.getZ() + 0.5D);
                    helper.setBlock(MIN, Blocks.AIR);
                    menu.broadcastChanges();
                    helper.assertTrue(!menu.connected() && menu.gas(true).getAmount() == output
                                    && menu.clickMenuButton(player, FissionPartMenu.CLEAR_OUTPUT),
                            "Disassembly retains local output and a valid menu can clear it");
                    menu.broadcastChanges();
                    helper.assertTrue(menu.gas(true).isEmpty(), "The clear command affects only the selected output tank");
                })
                .thenSucceed();
    }

    private static FakePlayer inspector(GameTestHelper helper) {
        return new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), "PebbleInspector")) {
            @Override
            public OptionalInt openMenu(MenuProvider provider) {
                containerMenu = provider.createMenu(1, getInventory(), this);
                return OptionalInt.of(1);
            }
        };
    }

    @GameTest(template = "empty")
    public static void pebbleUsesLegacyFuelCatalogAndReturnsCraftingBuckets(GameTestHelper helper) {
        var level = helper.getLevel();
        var recipes = level.getRecipeManager();
        var fuels = recipes.getAllRecipesFor(NCPebbleFission.FUEL_TYPE.get()).stream()
                .filter(recipe -> recipe.id().getNamespace().equals(NuclearCraft.MOD_ID)).toList();
        var leu = fuels.stream().filter(recipe -> recipe.getId().equals(NuclearCraft.id("pebble_fission/leu_235_triso")))
                .findFirst().orElseThrow();
        helper.assertTrue(fuels.size() == 27 && leu.ticks() == 4800 && leu.heat() == 120
                && leu.criticality() == 87 && leu.intrinsicFlux() == 10, "Preserve the full TRISO catalog and individual legacy stats");
        var grid = MaterialGameTests.craftingGrid(2, 2);
        grid.setItem(0, new ItemStack(item("empty_cooler")));
        grid.setItem(1, new ItemStack(NCGasCoolants.NITROGEN.cold().getBucket()));
        var craft = recipes.getRecipeFor(RecipeType.CRAFTING, grid, level).orElseThrow();
        helper.assertTrue(craft.assemble(grid, level.registryAccess()).is(NCPebbleFission.cooler(NCGasCoolants.NITROGEN).asItem())
                        && craft.getRemainingItems(grid).get(1).is(net.minecraft.world.item.Items.BUCKET),
                "Craft a gas cooler and return the bucket");
        helper.succeed();
    }

    private static void buildReactor(GameTestHelper helper) {
        for (BlockPos pos : BlockPos.betweenClosed(MIN, MAX)) {
            if (pos.getX() == MIN.getX() || pos.getX() == MAX.getX() || pos.getY() == MIN.getY()
                    || pos.getY() == MAX.getY() || pos.getZ() == MIN.getZ() || pos.getZ() == MAX.getZ()) {
                helper.setBlock(pos, NCFission.CASING.get());
            }
        }
        helper.setBlock(CONTROLLER, NCPebbleFission.CONTROLLER.get());
        helper.setBlock(FUEL_IN, NCPebbleFission.CHAMBER_PORT.get());
        helper.setBlock(FUEL_OUT, output(NCPebbleFission.CHAMBER_PORT.get()));
        helper.setBlock(COLD, NCPebbleFission.port(NCGasCoolants.OXYGEN));
        helper.setBlock(HOT, output(NCPebbleFission.port(NCGasCoolants.OXYGEN)));
        helper.setBlock(SOURCE, NCFission.RADIUM_BERYLLIUM_SOURCE.get());
        helper.setBlock(CHAMBER, NCPebbleFission.CHAMBER.get());
        Block graphite = ForgeRegistries.BLOCKS.getValue(NuclearCraft.id("graphite_block"));
        Block beryllium = ForgeRegistries.BLOCKS.getValue(NuclearCraft.id("beryllium_block"));
        helper.setBlock(CHAMBER.west(), graphite);
        helper.setBlock(CHAMBER.west(2), beryllium);
        helper.setBlock(CHAMBER.west(3), NCFission.BERYLLIUM_CARBON_REFLECTOR.get());
        helper.setBlock(CHAMBER.east(), beryllium);
        helper.setBlock(CHAMBER.east(2), beryllium);
        helper.setBlock(CHAMBER.east(3), NCFission.LEAD_STEEL_REFLECTOR.get());
        for (Direction side : new Direction[]{Direction.UP, Direction.DOWN, Direction.NORTH, Direction.SOUTH}) {
            helper.setBlock(CHAMBER.relative(side), NCPebbleFission.cooler(NCGasCoolants.OXYGEN));
        }
    }

    private static net.minecraft.world.level.block.state.BlockState output(Block block) {
        return block.defaultBlockState().setValue(FissionWallBlock.ACTIVE, true);
    }

    private static void runReactor(GameTestHelper helper) {
        helper.setBlock(CONTROLLER.west(), Blocks.REDSTONE_BLOCK);
        helper.setBlock(SOURCE.north(), Blocks.REDSTONE_BLOCK);
    }

    private static void requireAssembled(GameTestHelper helper) {
        SolidFissionController reactor = controller(helper);
        helper.assertTrue(reactor.assembled(), reactor.validationMessage().getString());
    }

    private static SolidFissionController controller(GameTestHelper helper) {
        return (SolidFissionController) helper.getBlockEntity(CONTROLLER);
    }

    private static IFluidHandler fluids(GameTestHelper helper, BlockPos pos, Direction side) {
        return helper.getBlockEntity(pos).getCapability(ForgeCapabilities.FLUID_HANDLER, side).orElseThrow(AssertionError::new);
    }

    private static IItemHandler items(GameTestHelper helper, BlockPos pos, Direction side) {
        return helper.getBlockEntity(pos).getCapability(ForgeCapabilities.ITEM_HANDLER, side).orElseThrow(AssertionError::new);
    }

    private static int amount(GameTestHelper helper, BlockPos pos, Direction side) {
        return fluids(helper, pos, side).getFluidInTank(0).getAmount();
    }

    private static Item item(String id) {
        return Objects.requireNonNull(ForgeRegistries.ITEMS.getValue(NuclearCraft.id(id)), id);
    }
}
