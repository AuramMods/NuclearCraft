package nc.gametest;

import com.mojang.authlib.GameProfile;
import nc.NuclearCraft;
import nc.radiation.ChunkRadiationSource;
import nc.init.NCFission;
import nc.init.NCSaltCoolants;
import nc.init.NCSaltFission;
import nc.multiblock.fission.FissionWallBlock;
import nc.multiblock.fission.SaltFissionPartMenu;
import nc.multiblock.fission.SaltHeaterBlock;
import nc.multiblock.fission.SolidFissionController;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegisterEvent;

import java.util.OptionalInt;
import java.util.UUID;

import static net.minecraftforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE;
import static net.minecraftforge.fluids.capability.IFluidHandler.FluidAction.SIMULATE;

@GameTestHolder(NuclearCraft.MOD_ID)
@PrefixGameTestTemplate(false)
@Mod.EventBusSubscriber(modid = NuclearCraft.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class SaltFissionGameTests {
    private static final String FUEL = "lecf_251_fluoride_flibe";
    private static final String DEPLETED = "depleted_" + FUEL;
    private static final BlockPos CONTROLLER = new BlockPos(1, 3, 3);
    private static final BlockPos FIRST_VESSEL = new BlockPos(2, 3, 2);
    private static final BlockPos SECOND_VESSEL = new BlockPos(6, 3, 6);
    private static final BlockPos CASE = new BlockPos(1, 1, 1);
    private static final BlockPos FUEL_IN = new BlockPos(2, 3, 1);
    private static final BlockPos FUEL_OUT = new BlockPos(2, 3, 7);
    private static final BlockPos SECOND_IN = new BlockPos(6, 3, 1);
    private static final BlockPos SECOND_OUT = new BlockPos(6, 3, 7);
    private static final BlockPos NAK_IN = new BlockPos(3, 1, 3);
    private static final BlockPos NAK_OUT = new BlockPos(3, 5, 3);

    @SubscribeEvent
    public static void registerScriptedHeater(RegisterEvent event) {
        event.register(ForgeRegistries.Keys.BLOCKS, helper -> {
            var coolant = NCSaltFission.scriptedCoolant("qa_nak", "nuclearcraft:nak", 3,
                    "nuclearcraft:nak_hot", 5, 110, "one vessel");
            helper.register(NuclearCraft.id("salt_fission_heater_qa_nak"), NCSaltFission.createScriptedHeater(coolant));
            helper.register(NuclearCraft.id("fission_heater_port_qa_nak"), NCSaltFission.createScriptedHeaterPort(coolant));
            var recipeCoolant = NCSaltFission.scriptedCoolant("qa_salt_recipe", "nuclearcraft:nak", 3,
                    "nuclearcraft:nak_hot", 5, 110, "one vessel");
            helper.register(NuclearCraft.id("salt_fission_heater_qa_salt_recipe"), NCSaltFission.createScriptedHeater(recipeCoolant));
            helper.register(NuclearCraft.id("fission_heater_port_qa_salt_recipe"), NCSaltFission.createScriptedHeaterPort(recipeCoolant));
        });
    }

    @GameTest(template = "fission", timeoutTicks = 240)
    public static void heaterFiltersPreserveTheSharedCircuitAndSavedCoolant(GameTestHelper helper) {
        shell(helper);
        addVessel(helper, FIRST_VESSEL, Direction.EAST, FUEL, FUEL_IN, FUEL_OUT);
        var heater = ForgeRegistries.BLOCKS.getValue(NuclearCraft.id("salt_fission_heater_qa_salt_recipe"));
        var port = ForgeRegistries.BLOCKS.getValue(NuclearCraft.id("fission_heater_port_qa_salt_recipe"));
        BlockPos heaterPos = FIRST_VESSEL.below();
        helper.setBlock(heaterPos, heater);
        helper.setBlock(NAK_IN, port);
        helper.setBlock(NAK_OUT, port.defaultBlockState().setValue(FissionWallBlock.ACTIVE, true));
        helper.setBlock(CASE, Blocks.AIR);

        var player = saltInspector(helper);
        var iron = stack("iron_nak", 18);
        var gold = stack("gold_nak", 18);
        useCoolantSample(helper, player, heaterPos, gold);
        useCoolantSample(helper, player, NAK_OUT, gold);
        var detached = helper.getBlockEntity(NAK_IN).getCapability(ForgeCapabilities.FLUID_HANDLER);
        var previous = detached.orElseThrow(AssertionError::new);
        useCoolantSample(helper, player, NAK_IN, iron);
        helper.assertTrue(!detached.isPresent() && previous.fill(iron, EXECUTE) == 0,
                "Changing the inlet filter releases cached detached capabilities");
        var inputPart = helper.getBlockEntity(NAK_IN);
        var savedPort = inputPart.saveWithoutMetadata();
        helper.assertTrue(savedPort.getCompound("heater_coolant").getString("filter").equals("nuclearcraft:iron_nak")
                        && heaterFluid(helper, heaterPos, false).isEmpty(),
                "Selecting a fluid saves its ID without filling a tank");
        inputPart.load(savedPort);

        var controller = (SolidFissionController) helper.getBlockEntity(CONTROLLER);
        var saved = controller.saveWithoutMetadata();
        var coolant = new CompoundTag();
        coolant.put("input", gold.writeToNBT(new CompoundTag()));
        var circuits = new CompoundTag();
        circuits.put("qa_salt_recipe", coolant);
        saved.put("salt_cooling", circuits);
        controller.load(saved);
        helper.setBlock(CASE, NCFission.CASING.get());

        helper.startSequence().thenWaitUntil(() -> helper.assertTrue(controller.assembled(), "The coolant circuit assembles"))
                .thenExecute(() -> {
                    helper.useBlock(heaterPos, player);
                    var menu = (SaltFissionPartMenu) player.containerMenu;
                    var input = fluids(helper, NAK_IN, Direction.DOWN);
                    helper.assertTrue(menu.linked() && menu.exactCoolantFilter() && menu.filter().isFluidEqual(iron)
                                    && menu.fluid(false).isFluidStackIdentical(gold) && !menu.canClearFilter(),
                            "The heater sees the selected inlet fluid and the earlier stored coolant in one shared circuit");
                    helper.assertTrue(input.isFluidValid(0, iron) && !input.isFluidValid(0, gold)
                                    && input.fill(gold, SIMULATE) == 0 && input.fill(iron, SIMULATE) == 0,
                            "The selected filter constrains new fluid and physical contents still prevent mixing");
                    useCoolantSample(helper, player, NAK_IN, gold);
                    helper.assertTrue(input.isFluidValid(0, iron), "An assembled port keeps its selected filter");
                    fluids(helper, FUEL_IN, Direction.NORTH).fill(stack(FUEL, 180), EXECUTE);
                    helper.setBlock(CONTROLLER.west(), Blocks.REDSTONE_BLOCK);
                })
                .thenWaitUntil(() -> {
                    var input = fluids(helper, NAK_IN, Direction.DOWN);
                    var output = fluids(helper, NAK_OUT, Direction.UP);
                    helper.assertTrue(input.getFluidInTank(0).isEmpty() && output.getFluidInTank(0).getAmount() == 3,
                            "Earlier gold coolant finishes its batch after the inlet filter changes");
                    helper.assertTrue(input.fill(gold, EXECUTE) == 0 && input.fill(iron, SIMULATE) == 18
                                    && input.getFluidInTank(0).isEmpty() && input.fill(iron, EXECUTE) == 18,
                            "A simulated or executed transfer accepts the matching fluid in the same inlet");
                })
                .thenWaitUntil(() -> helper.assertTrue(fluids(helper, NAK_OUT, Direction.UP).getFluidInTank(0).getAmount() == 6,
                        "Both fluids use the same product tank"))
                .thenExecute(() -> {
                    helper.setBlock(CONTROLLER.west(), Blocks.AIR);
                    helper.setBlock(CASE, Blocks.AIR);
                    var pos = helper.absolutePos(NAK_IN);
                    player.moveTo(pos.getX() + 0.5D, pos.getY() - 1, pos.getZ() + 0.5D);
                    helper.useBlock(NAK_IN, player);
                    var menu = (SaltFissionPartMenu) player.containerMenu;
                    helper.assertTrue(menu.canClearFilter() && menu.clickMenuButton(player, SaltFissionPartMenu.CLEAR_INPUT),
                            "The disconnected empty inlet can clear its saved filter from the menu");
                    menu.broadcastChanges();
                    helper.assertTrue(!menu.exactCoolantFilter() && !inputPart.saveWithoutMetadata()
                                    .getCompound("heater_coolant").contains("filter"),
                            "Clearing the filter restores the unfiltered inlet");
                }).thenSucceed();
    }

    private static void useCoolantSample(GameTestHelper helper, FakePlayer player, BlockPos pos, FluidStack sample) {
        var bucket = new ItemStack(sample.getFluid().getBucket());
        player.setItemInHand(InteractionHand.MAIN_HAND, bucket);
        player.setShiftKeyDown(true);
        helper.useBlock(pos, player);
        helper.assertTrue(player.getMainHandItem().is(bucket.getItem()), "Selecting a filter leaves the bucket intact");
        player.setShiftKeyDown(false);
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
    }

    @GameTest(template = "fission", timeoutTicks = 500)
    public static void scriptedSaltHeaterKeepsWholeBatchesAcrossBackpressureAndReload(GameTestHelper helper) {
        shell(helper);
        addVessel(helper, FIRST_VESSEL, Direction.EAST, FUEL, FUEL_IN, FUEL_OUT);
        var heater = ForgeRegistries.BLOCKS.getValue(NuclearCraft.id("salt_fission_heater_qa_nak"));
        var port = ForgeRegistries.BLOCKS.getValue(NuclearCraft.id("fission_heater_port_qa_nak"));
        helper.setBlock(FIRST_VESSEL.below(), heater);
        helper.setBlock(NAK_IN, port);
        helper.setBlock(NAK_OUT, port.defaultBlockState().setValue(FissionWallBlock.ACTIVE, true));
        var controller = (SolidFissionController) helper.getBlockEntity(CONTROLLER);
        var state = controller.saveWithoutMetadata();
        var circuit = new CompoundTag();
        circuit.put("output", stack("nak_hot", 1292).writeToNBT(new CompoundTag()));
        var storage = new CompoundTag();
        storage.put("qa_nak", circuit);
        state.put("salt_cooling", storage);
        controller.load(state);

        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(controller.assembled(), "The scripted heater ports form a shared circuit"))
                .thenExecute(() -> {
                    var profile = ((SaltHeaterBlock) heater).coolant();
                    var rule = NCSaltFission.placementRules().text(profile);
                    helper.assertTrue("one vessel".equals(rule), "The custom rule should be compiled, found " + rule);
                    var player = saltInspector(helper);
                    helper.useBlock(NAK_IN, player);
                    var menu = (SaltFissionPartMenu) player.containerMenu;
                    helper.assertTrue(menu.linked() && menu.coolantInput() != null && !menu.coolantInput().tag()
                                    && menu.coolantInput().id().equals(NuclearCraft.id("nak"))
                                    && menu.coolantInput().amount() == 3 && menu.cooling() == 110,
                            "The port menu shows the configured input and cooling rate");
                    var input = fluids(helper, NAK_IN, Direction.DOWN);
                    helper.assertTrue(input.fill(stack("iron_nak", 62), SIMULATE) == 0
                                    && input.fill(stack("nak", 62), EXECUTE) == 62,
                            "The input port accepts only the registered cold coolant");
                    fluids(helper, FUEL_IN, Direction.NORTH).fill(stack(FUEL, 180), EXECUTE);
                    helper.setBlock(CONTROLLER.west(), Blocks.REDSTONE_BLOCK);
                })
                .thenExecuteAfter(12, () -> {
                    var output = fluids(helper, NAK_OUT, Direction.UP);
                    helper.assertTrue(fluids(helper, NAK_IN, Direction.DOWN).getFluidInTank(0).getAmount() == 62
                                    && output.getFluidInTank(0).getAmount() == 1292,
                            "Four millibuckets of room cannot fit a whole five-millibucket product");
                    helper.assertTrue(output.drain(100, EXECUTE).isFluidStackIdentical(stack("nak_hot", 100)),
                            "Drawing product through the outward side makes room for whole batches");
                })
                .thenWaitUntil(() -> {
                    int cold = fluids(helper, NAK_IN, Direction.DOWN).getFluidInTank(0).getAmount();
                    var hot = fluids(helper, NAK_OUT, Direction.UP).getFluidInTank(0);
                    helper.assertTrue(cold == 2 && hot.isFluidStackIdentical(stack("nak_hot", 1292)),
                            "Twenty whole batches convert 60 mB to 100 mB, found " + cold + " mB of cold and "
                                    + hot.getAmount() + " mB of hot coolant");
                })
                .thenExecute(() -> {
                    helper.setBlock(CONTROLLER.west(), Blocks.AIR);
                    helper.setBlock(CASE, Blocks.AIR);
                    var saved = controller.saveWithoutMetadata();
                    helper.setBlock(CONTROLLER, Blocks.AIR);
                    helper.setBlock(CONTROLLER, NCSaltFission.CONTROLLER.get());
                    helper.getBlockEntity(CONTROLLER).load(saved);
                    helper.setBlock(CASE, NCFission.CASING.get());
                })
                .thenWaitUntil(() -> helper.assertTrue(((SolidFissionController) helper.getBlockEntity(CONTROLLER)).assembled(),
                        "The saved reactor reassembles with the custom port type"))
                .thenExecute(() -> helper.assertTrue(
                        fluids(helper, NAK_IN, Direction.DOWN).getFluidInTank(0).isFluidStackIdentical(stack("nak", 2))
                                && fluids(helper, NAK_OUT, Direction.UP).drain(1292, EXECUTE)
                                    .isFluidStackIdentical(stack("nak_hot", 1292)),
                        "The saved circuit restores its exact cold remainder and hot product"))
                .thenSucceed();
    }

    @GameTest(template = "fission", timeoutTicks = 300)
    public static void authoredSaltHeaterReservesMaximumOutputAndProcessesEmptyFallback(GameTestHelper helper) {
        shell(helper);
        addVessel(helper, FIRST_VESSEL, Direction.EAST, FUEL, FUEL_IN, FUEL_OUT);
        var heater = ForgeRegistries.BLOCKS.getValue(NuclearCraft.id("salt_fission_heater_qa_salt_recipe"));
        var port = ForgeRegistries.BLOCKS.getValue(NuclearCraft.id("fission_heater_port_qa_salt_recipe"));
        helper.setBlock(FIRST_VESSEL.below(), heater);
        helper.setBlock(NAK_IN, port);
        helper.setBlock(NAK_OUT, port.defaultBlockState().setValue(FissionWallBlock.ACTIVE, true));
        var controller = (SolidFissionController) helper.getBlockEntity(CONTROLLER);
        var state = controller.saveWithoutMetadata();
        var circuit = new CompoundTag();
        circuit.put("output", stack("iron_nak_hot", 1288).writeToNBT(new CompoundTag()));
        var storage = new CompoundTag();
        storage.put("qa_salt_recipe", circuit);
        state.put("salt_cooling", storage);
        controller.load(state);
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(controller.assembled(), "The authored heater circuit assembles"))
                .thenExecute(() -> {
                    var input = fluids(helper, NAK_IN, Direction.DOWN);
                    helper.assertTrue(input.fill(stack("nak", 31), SIMULATE) == 0
                                    && input.fill(stack("iron_nak", 31), EXECUTE) == 31,
                            "Only the authored cold fluid enters the assembled outward port");
                    var player = saltInspector(helper);
                    helper.useBlock(NAK_IN, player);
                    var menu = (SaltFissionPartMenu) player.containerMenu;
                    helper.assertTrue(menu.coolantInput() != null && menu.coolantInput().amount() == 18 && menu.cooling() == 180
                                    && menu.filter().getFluid() == stack("iron_nak", 1).getFluid(),
                            "The higher-priority recipe is selected and displays the actual alternative fluid");
                    var oldHeater = helper.getBlockEntity(FIRST_VESSEL.below());
                    var oldPart = oldHeater.saveWithoutMetadata();
                    oldPart.remove("progress");
                    oldHeater.load(oldPart);
                    var saved = controller.saveWithoutMetadata();
                    var fractions = new ListTag();
                    var paid = new CompoundTag();
                    paid.putLong("pos", helper.absolutePos(FIRST_VESSEL.below()).asLong());
                    paid.putString("coolant_id", "qa_salt_recipe");
                    paid.putDouble("amount", 0.75D);
                    fractions.add(paid);
                    saved.getCompound("salt_cooling").put("fractions", fractions);
                    controller.load(saved);
                })
                .thenExecuteAfter(5, () -> {
                    var saved = controller.saveWithoutMetadata();
                    var cooling = saved.getCompound("salt_cooling");
                    helper.assertTrue(cooling.getList("fractions", Tag.TAG_COMPOUND).isEmpty()
                                    && helper.getBlockEntity(FIRST_VESSEL.below()).saveWithoutMetadata()
                                            .getDouble("progress") == 0.75D,
                            "An old named heater fraction moves to its existing block entity");
                    cooling.getCompound("qa_salt_recipe").put("output",
                            stack("iron_nak_hot", 1289).writeToNBT(new CompoundTag()));
                    controller.load(saved);
                })
                .thenExecuteAfter(5, () -> {
                    helper.assertTrue(helper.getBlockEntity(FIRST_VESSEL.below()).saveWithoutMetadata()
                                    .getDouble("progress") == 0D,
                            "An unpowered heater discards progress when its selected maximum product will not fit");
                    fluids(helper, FUEL_IN, Direction.NORTH).fill(stack(FUEL, 180), EXECUTE);
                    helper.setBlock(CONTROLLER.west(), Blocks.REDSTONE_BLOCK);
                })
                .thenExecuteAfter(12, () -> {
                    var input = fluids(helper, NAK_IN, Direction.DOWN);
                    var output = fluids(helper, NAK_OUT, Direction.UP);
                    helper.assertTrue(input.getFluidInTank(0).getAmount() == 31
                                    && output.getFluidInTank(0).getAmount() == 1289,
                            "Seven millibuckets of room stall the eight-millibucket maximum, even with an empty fallback");
                    helper.assertTrue(output.drain(1, EXECUTE).isFluidStackIdentical(stack("iron_nak_hot", 1)),
                            "Drawing through the port allows the first batch to finish");
                })
                .thenWaitUntil(() -> {
                    var input = fluids(helper, NAK_IN, Direction.DOWN);
                    var output = fluids(helper, NAK_OUT, Direction.UP);
                    helper.assertTrue(input.getFluidInTank(0).isFluidStackIdentical(stack("iron_nak", 3))
                                    && output.getFluidInTank(0).isFluidStackIdentical(stack("iron_nak_hot", 1291)),
                            "The 18 mB recipe makes 3 mB of product and the 10 mB fallback makes no product");
                })
                .thenExecute(() -> {
                    helper.assertTrue(helper.getBlockEntity(FIRST_VESSEL.below()).saveWithoutMetadata()
                                    .getDouble("progress") == 0D,
                            "The incomplete feed left by the empty-product batch clears progress");
                    var player = saltInspector(helper);
                    helper.useBlock(NAK_IN, player);
                    var menu = (SaltFissionPartMenu) player.containerMenu;
                    var input = fluids(helper, NAK_IN, Direction.DOWN);
                    helper.assertTrue(input.fill(stack("iron_nak", 7), EXECUTE) == 7, "Complete another fallback batch");
                    menu.broadcastChanges();
                    helper.assertTrue(menu.coolantInput() != null && menu.coolantInput().amount() == 10 && menu.cooling() == 110,
                            "The fallback inherits the material cooling rate and shows its own dose");
                })
                .thenSucceed();
    }

    @GameTest(template = "empty")
    public static void saltFissionLoadsTheLegacyFluidFuelsAndHeaters(GameTestHelper helper) {
        var manager = helper.getLevel().getRecipeManager();
        var recipes = manager.getAllRecipesFor(NCSaltFission.FUEL_TYPE.get());
        helper.assertTrue(recipes.stream().filter(recipe -> recipe.id().getNamespace().equals(NuclearCraft.MOD_ID)).count() == 27, "All 27 legacy molten-salt fuels must load");
        var sample = recipes.stream().filter(recipe -> recipe.id().equals(NuclearCraft.id("salt_fission/lecf_251")))
                .findFirst().orElseThrow();
        helper.assertTrue(sample.fuel().matches(stack(FUEL, 1)) && sample.depleted().isFluidStackIdentical(stack(DEPLETED, 1))
                        && sample.ticks() == 2504D / 144 && sample.criticality() == 71 && sample.selfPriming(),
                "Keep the fractional burn duration and per-millibucket balance from the original reactor");
        for (var coolant : NCSaltCoolants.values()) {
            helper.assertTrue(manager.byKey(NuclearCraft.id("crafting/" + coolant.material() + "_coolant_heater")).isPresent()
                            && manager.byKey(NuclearCraft.id("crafting/fission_heater_port_" + coolant.material())).isPresent(),
                    "Every legacy coolant heater and port has a crafting recipe");
        }
        helper.succeed();
    }

    @GameTest(template = "fission")
    public static void saltVesselUsesTheFallbackAmountWithinTheSameTick(GameTestHelper helper) {
        shell(helper);
        addVessel(helper, FIRST_VESSEL, Direction.EAST, "ethanol", FUEL_IN, FUEL_OUT);
        var controller = (SolidFissionController) helper.getBlockEntity(CONTROLLER);
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(controller.assembled(), "The counted fuel circuit assembles"))
                .thenExecute(() -> {
                    var in = fluids(helper, FUEL_IN, Direction.NORTH);
                    helper.assertTrue(in.fill(stack("ethanol", 28), EXECUTE) == 28, "Fill both counted doses");
                    helper.setBlock(CONTROLLER.west(), Blocks.REDSTONE_BLOCK);
                })
                .thenWaitUntil(() -> helper.assertTrue(!fluids(helper, FUEL_OUT, Direction.SOUTH).getFluidInTank(0).isEmpty(),
                        "Wait for the first active tick"))
                .thenExecute(() -> helper.assertTrue(
                        fluids(helper, FUEL_OUT, Direction.SOUTH).getFluidInTank(0).isFluidStackIdentical(stack("heavy_water", 12))
                                && fluids(helper, FUEL_IN, Direction.NORTH).getFluidInTank(0).isEmpty()
                                && FluidStack.loadFluidStackFromNBT(vesselTag(helper).getCompound("fuel")).isEmpty(),
                        "Consume 18 mB and then 10 mB and discharge exactly two 6 mB products"))
                .thenSucceed();
    }

    @GameTest(template = "fission")
    public static void moltenSaltUsesSpaceFreedByAChanceProductInTheSameTick(GameTestHelper helper) {
        String fuel = "methanol";
        shell(helper);
        addVessel(helper, FIRST_VESSEL, Direction.EAST, fuel, FUEL_IN, FUEL_OUT);
        var controller = (SolidFissionController) helper.getBlockEntity(CONTROLLER);
        var stored = controller.saveWithoutMetadata();
        var circuits = new ListTag();
        var circuit = new CompoundTag();
        circuit.putString("fuel_filter", NuclearCraft.id(fuel).toString());
        circuit.put("input", stack(fuel, 28).writeToNBT(new CompoundTag()));
        circuit.put("output", stack("heavy_water", 1289).writeToNBT(new CompoundTag()));
        circuits.add(circuit);
        stored.put("salt_fuel", circuits);
        controller.load(stored);
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(controller.assembled(), "The counted fuel circuit assembles"))
                .thenExecute(() -> helper.setBlock(CONTROLLER.west(), Blocks.REDSTONE_BLOCK))
                .thenExecuteAfter(4, () -> {
                    var input = fluids(helper, FUEL_IN, Direction.NORTH).getFluidInTank(0);
                    var output = fluids(helper, FUEL_OUT, Direction.SOUTH);
                    helper.assertTrue(input.getAmount() == 28 && output.getFluidInTank(0).getAmount() == 1289,
                            "Seven open mB cannot hold the maximum 8 mB product");
                    helper.assertTrue(output.drain(4, EXECUTE).getAmount() == 4,
                            "Open enough room for the maximum and one guaranteed product");
                })
                .thenWaitUntil(() -> helper.assertTrue(
                        fluids(helper, FUEL_OUT, Direction.SOUTH).getFluidInTank(0).getAmount() > 1285,
                        "The first product must leave the vessel"))
                .thenExecute(() -> helper.assertTrue(
                        fluids(helper, FUEL_OUT, Direction.SOUTH).getFluidInTank(0).isFluidStackIdentical(stack("heavy_water", 1291))
                                && fluids(helper, FUEL_IN, Direction.NORTH).getFluidInTank(0).isEmpty()
                                && FluidStack.loadFluidStackFromNBT(vesselTag(helper).getCompound("fuel")).isEmpty(),
                        "The 3 mB roll leaves room for the second maximum and both doses finish in one tick"))
                .thenSucceed();
    }

    @GameTest(template = "fission", timeoutTicks = 270)
    public static void saltFissionResumesAfterFullOutputAndReassembly(GameTestHelper helper) {
        shell(helper);
        addVessel(helper, FIRST_VESSEL, Direction.EAST, FUEL, FUEL_IN, FUEL_OUT);
        var controller = (SolidFissionController) helper.getBlockEntity(CONTROLLER);
        CompoundTag state = controller.saveWithoutMetadata();
        ListTag fuel = new ListTag();
        CompoundTag circuit = new CompoundTag();
        circuit.putString("fuel_filter", NuclearCraft.id(FUEL).toString());
        circuit.put("output", stack(DEPLETED, 1_296).writeToNBT(new CompoundTag()));
        fuel.add(circuit);
        state.put("salt_fuel", fuel);
        controller.load(state);

        helper.runAtTickTime(15, () -> {
            helper.assertTrue(controller.assembled(), "A matched pair of fuel and heater ports must assemble");
            var in = fluids(helper, FUEL_IN, Direction.NORTH);
            var out = fluids(helper, FUEL_OUT, Direction.SOUTH);
            var coolant = fluids(helper, NAK_IN, Direction.DOWN);
            helper.assertTrue(!helper.getBlockEntity(FUEL_IN).getCapability(ForgeCapabilities.FLUID_HANDLER, Direction.SOUTH).isPresent()
                            && in.fill(stack("hecf_251_fluoride_flibe", 1), EXECUTE) == 0
                            && in.fill(stack(FUEL, 12), SIMULATE) == 12 && in.getFluidInTank(0).isEmpty()
                            && in.fill(stack(FUEL, 12), EXECUTE) == 12 && in.drain(1, EXECUTE).isEmpty()
                            && out.fill(stack(DEPLETED, 1), EXECUTE) == 0,
                    "The circuit exposes only its outside faces and honors filters, direction, and simulated fill");
            helper.assertTrue(coolant.fill(stack("iron_nak", 1), EXECUTE) == 0 && coolant.fill(stack("nak", 300), EXECUTE) == 300,
                    "The standard heater circuit accepts only its own cold NaK");
            helper.setBlock(CONTROLLER.west(), Blocks.REDSTONE_BLOCK);
        });
        helper.runAtTickTime(30, () -> {
            var input = fluids(helper, FUEL_IN, Direction.NORTH);
            var output = fluids(helper, FUEL_OUT, Direction.SOUTH);
            helper.assertTrue(input.getFluidInTank(0).getAmount() == 12
                            && fluids(helper, NAK_OUT, Direction.UP).getFluidInTank(0).isEmpty()
                            && output.drain(stack(DEPLETED, 12), EXECUTE).getAmount() == 12,
                    "A full depleted-salt tank protects the untouched fuel, and opening room restarts it");
        });
        helper.runAtTickTime(48, () -> {
            var old = helper.getBlockEntity(NAK_OUT).getCapability(ForgeCapabilities.FLUID_HANDLER, Direction.UP);
            var stale = old.orElseThrow(AssertionError::new);
            var vessel = helper.getBlockEntity(FIRST_VESSEL);
            CompoundTag vesselState = vessel.saveWithoutMetadata();
            CompoundTag controllerState = controller.saveWithoutMetadata();
            helper.assertTrue(vesselState.getDouble("progress") > 0, "Save a partly consumed fluid fuel dose");
            helper.setBlock(CASE, Blocks.AIR);
            helper.assertTrue(!old.isPresent() && stale.drain(300, EXECUTE).isEmpty(),
                    "Breaking the casing invalidates retained pipe handlers immediately");
            controller.load(controllerState);
            vessel.load(vesselState);
            helper.setBlock(CASE, NCFission.CASING.get());
            helper.succeedWhen(() -> {
                helper.assertTrue(controller.assembled(), "The restored shell must reassemble");
                var input = fluids(helper, FUEL_IN, Direction.NORTH);
                var depleted = fluids(helper, FUEL_OUT, Direction.SOUTH).getFluidInTank(0);
                var cold = fluids(helper, NAK_IN, Direction.DOWN).getFluidInTank(0);
                var hot = fluids(helper, NAK_OUT, Direction.UP).getFluidInTank(0);
                helper.assertTrue(depleted.isFluidStackIdentical(stack(DEPLETED, 1_296)) && input.getFluidInTank(0).isEmpty()
                                && hot.isFluidEqual(stack("nak_hot", 1)) && hot.getAmount() + cold.getAmount() == 300,
                        "All 12 reserved-and-fed fuel doses finish once and the heater conserves coolant across reload");
            });
        });
    }

    @GameTest(template = "fission", timeoutTicks = 200)
    public static void saltFissionKeepsTwoFilteredFuelCircuitsSeparate(GameTestHelper helper) {
        String secondFuel = "hecf_251_fluoride_flibe";
        shell(helper);
        addVessel(helper, FIRST_VESSEL, Direction.EAST, FUEL, FUEL_IN, FUEL_OUT);
        addVessel(helper, SECOND_VESSEL, Direction.WEST, secondFuel, SECOND_IN, SECOND_OUT);
        helper.runAtTickTime(15, () -> {
            var a = fluids(helper, FUEL_IN, Direction.NORTH);
            var b = fluids(helper, SECOND_IN, Direction.NORTH);
            helper.assertTrue(a.fill(stack(secondFuel, 4), EXECUTE) == 0 && b.fill(stack(FUEL, 4), EXECUTE) == 0
                            && a.fill(stack(FUEL, 6), EXECUTE) == 6 && b.fill(stack(secondFuel, 4), EXECUTE) == 4,
                    "Two filter groups accept different fuel salts through isolated inlet tanks");
            fluids(helper, NAK_IN, Direction.DOWN).fill(stack("nak", 100), EXECUTE);
            helper.setBlock(CONTROLLER.west(), Blocks.REDSTONE_BLOCK);
            helper.startSequence().thenWaitUntil(() -> helper.assertTrue(
                    ((ChunkRadiationSource) helper.getBlockEntity(FIRST_VESSEL)).radiationEmission() > 0,
                    "An active molten-salt vessel emits radiation")).thenWaitUntil(() -> {
                var first = fluids(helper, FUEL_OUT, Direction.SOUTH).getFluidInTank(0);
                var second = fluids(helper, SECOND_OUT, Direction.SOUTH).getFluidInTank(0);
                helper.assertTrue(first.isFluidStackIdentical(stack(DEPLETED, 6))
                                && second.isFluidStackIdentical(stack("depleted_" + secondFuel, 4)),
                        "Each vessel discharges its own fuel products through the matching outlet");
            }).thenSucceed();
        });
    }

    @GameTest(template = "fission", timeoutTicks = 70)
    public static void saltFissionCompletesMultipleDosesWhenUndercoolingSpeedsItUp(GameTestHelper helper) {
        String accelerated = "hecf_251_fluoride_flibe";
        shell(helper);
        addVessel(helper, FIRST_VESSEL, Direction.EAST, accelerated, FUEL_IN, FUEL_OUT);
        helper.startSequence()
                .thenIdle(15)
                .thenExecute(() -> {
                    fluids(helper, FUEL_IN, Direction.NORTH).fill(stack(accelerated, 20), EXECUTE);
                    helper.setBlock(CONTROLLER.west(), Blocks.REDSTONE_BLOCK);
                })
                .thenWaitUntil(() -> helper.assertTrue(!fluids(helper, FUEL_OUT, Direction.SOUTH)
                        .getFluidInTank(0).isEmpty(), "Waiting for the first undercooled batch"))
                .thenExecute(() -> helper.assertTrue(fluids(helper, FUEL_OUT, Direction.SOUTH).getFluidInTank(0)
                                .isFluidStackIdentical(stack("depleted_" + accelerated, 3)),
                        "69 ticks of fuel progress complete three whole doses during the first undercooled tick"))
                .thenSucceed();
    }

    @GameTest(template = "fission", timeoutTicks = 95)
    public static void saltInspectionChecksDistanceAndDisassemblyBeforeChangingTanks(GameTestHelper helper) {
        shell(helper);
        addVessel(helper, FIRST_VESSEL, Direction.EAST, FUEL, FUEL_IN, FUEL_OUT);
        helper.runAtTickTime(15, () -> {
            var player = saltInspector(helper);
            BlockPos absolutePort = helper.absolutePos(FUEL_IN);
            player.moveTo(absolutePort.getX() + 0.5, absolutePort.getY(), absolutePort.getZ() - 1.0);
            var controller = (SolidFissionController) helper.getBlockEntity(CONTROLLER);
            var saved = controller.saveWithoutMetadata();
            var circuits = new ListTag();
            var circuit = new CompoundTag();
            circuit.putString("fuel_filter", NuclearCraft.id(FUEL).toString());
            circuit.put("input", stack(FUEL, 50_001).writeToNBT(new CompoundTag()));
            circuits.add(circuit);
            saved.put("salt_fuel", circuits);
            controller.load(saved);
            var initial = fluids(helper, FUEL_IN, Direction.NORTH);
            helper.assertTrue(initial.getFluidInTank(0).getAmount() == 50_001 && initial.fill(stack(FUEL, 1), SIMULATE) == 0,
                    "An older tank retains every mB and cannot exceed its current amount");
            helper.useBlock(FUEL_IN, player);
            helper.assertTrue(player.containerMenu instanceof SaltFissionPartMenu, "Use the port to open the real inspection menu");
            var menu = (SaltFissionPartMenu) player.containerMenu;
            helper.assertTrue(menu.kind() == SaltFissionPartMenu.Kind.VESSEL_PORT && menu.connected()
                            && menu.fluid(false).getAmount() == 50_001 && menu.capacity(false) == 50_001,
                    "The menu reads the exact input, its capacity, and its current assembly");
            player.moveTo(absolutePort.getX() + 20, absolutePort.getY(), absolutePort.getZ());
            helper.assertTrue(!menu.clickMenuButton(player, SaltFissionPartMenu.CLEAR_INPUT)
                            && initial.getFluidInTank(0).getAmount() == 50_001,
                    "An out-of-range clear request must leave the tank alone");
            player.moveTo(absolutePort.getX() + 0.5, absolutePort.getY(), absolutePort.getZ() - 1.0);
            helper.setBlock(CASE, Blocks.AIR);
            menu.broadcastChanges();
            helper.assertTrue(!menu.connected() && !menu.clickMenuButton(player, SaltFissionPartMenu.CLEAR_OUTPUT),
                    "A disconnected port cannot clear the controller's previous output");
            helper.setBlock(CASE, NCFission.CASING.get());
            helper.startSequence()
                    .thenIdle(7)
                    .thenExecute(() -> {
                        menu.broadcastChanges();
                        helper.assertTrue(menu.connected() && menu.fluid(false).getAmount() == 50_001
                                        && menu.clickMenuButton(player, SaltFissionPartMenu.CLEAR_INPUT),
                                "The linked screen follows reassembly and clears only the currently bound tank");
                        helper.assertTrue(fluids(helper, FUEL_IN, Direction.NORTH).getFluidInTank(0).isEmpty(),
                                "A valid tank clear reaches the shared buffer");
                        helper.setBlock(FUEL_IN, Blocks.AIR);
                        helper.setBlock(FUEL_IN, NCSaltFission.VESSEL_PORT.get());
                        helper.assertTrue(!menu.stillValid(player), "Replacing the port invalidates its old menu");
                    })
                    .thenSucceed();
        });
    }

    @GameTest(template = "fission", timeoutTicks = 320)
    public static void saltVesselKeepsReservedFuelInItsOriginalTanks(GameTestHelper helper) {
        shell(helper);
        addVessel(helper, FIRST_VESSEL, Direction.EAST, FUEL, FUEL_IN, FUEL_OUT);
        helper.setBlock(CASE, Blocks.AIR);
        var controller = (SolidFissionController) helper.getBlockEntity(CONTROLLER);
        var portState = NCSaltFission.VESSEL_PORT.get().defaultBlockState().setValue(FissionWallBlock.FACING, Direction.NORTH);
        var outputState = portState.setValue(FissionWallBlock.FACING, Direction.SOUTH).setValue(FissionWallBlock.ACTIVE, true);
        helper.setBlock(FUEL_IN, portState);
        var unfiltered = helper.getBlockEntity(FUEL_IN).saveWithoutMetadata();
        unfiltered.remove("fuel_filter");
        helper.getBlockEntity(FUEL_IN).load(unfiltered);
        var player = saltInspector(helper);
        var portPos = helper.absolutePos(FUEL_IN);
        player.moveTo(portPos.getX() + 0.5, portPos.getY(), portPos.getZ() - 1);
        var fuelBucket = new ItemStack(stack(FUEL, 1).getFluid().getBucket());
        player.setShiftKeyDown(true);
        player.setItemInHand(InteractionHand.MAIN_HAND, fuelBucket);
        helper.useBlock(FUEL_IN, player);
        helper.assertTrue(player.getMainHandItem().is(fuelBucket.getItem())
                        && helper.getBlockEntity(FUEL_IN).saveWithoutMetadata().getString("fuel_filter").equals(NuclearCraft.id(FUEL).toString()),
                "Using a sample on the unassembled fuel port sets its filter and preserves the bucket");
        player.setShiftKeyDown(false);
        var detached = helper.getBlockEntity(FUEL_IN).getCapability(ForgeCapabilities.FLUID_HANDLER, Direction.NORTH);
        var localPort = detached.orElseThrow(AssertionError::new);
        helper.assertTrue(!helper.getBlockEntity(FUEL_IN).getCapability(ForgeCapabilities.FLUID_HANDLER, Direction.SOUTH).isPresent()
                        && localPort.fill(stack("hecf_251_fluoride_flibe", 4), EXECUTE) == 0
                        && localPort.fill(stack(FUEL, 81), SIMULATE) == 81 && localPort.getFluidInTank(0).isEmpty()
                        && localPort.fill(stack(FUEL, 81), EXECUTE) == 81 && localPort.drain(1, EXECUTE).isEmpty(),
                "Detached pipes use the local tank, its exact fuel filter, and its configured side");
        helper.useBlock(FIRST_VESSEL, player);
        helper.assertTrue(player.getMainHandItem().is(Items.BUCKET)
                        && vesselStock(helper, false).isFluidStackIdentical(stack(FUEL, 1000)),
                "A bucket supplies the independent vessel tank while unassembled");
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        var savedPort = helper.getBlockEntity(FUEL_IN).saveWithoutMetadata();
        var power = CONTROLLER.west();
        helper.setBlock(CASE, NCFission.CASING.get());
        int[] sharedStock = new int[2];

        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(controller.assembled(), "Both filtered ports are paired"))
                .thenExecute(() -> {
                    var input = fluids(helper, FUEL_IN, Direction.NORTH);
                    helper.assertTrue(!detached.isPresent() && localPort.fill(stack(FUEL, 1), EXECUTE) == 0
                                    && input.getTankCapacity(0) == 1296 && input.getFluidInTank(0).isEmpty(),
                            "Shared fuel and detached buffers are independent and old local pipe handles are revoked");
                    helper.useBlock(FUEL_IN, player);
                    var menu = (SaltFissionPartMenu) player.containerMenu;
                    helper.assertTrue(menu.linked() && menu.clickMenuButton(player, SaltFissionPartMenu.SWITCH_BUFFER)
                                    && menu.fluid(false).isFluidStackIdentical(stack(FUEL, 81)),
                            "The linked fuel port can inspect its independent local contents");
                    helper.assertTrue(input.fill(stack(FUEL, 20), EXECUTE) == 20, "Supply the shared circuit");
                    fluids(helper, NAK_IN, Direction.DOWN).fill(stack("nak", 300), EXECUTE);
                    helper.setBlock(power, Blocks.REDSTONE_BLOCK);
                })
                .thenWaitUntil(() -> helper.assertTrue(vesselTag(helper).getDouble("progress") > 0
                                && !vesselTag(helper).getBoolean("reserved_local"),
                        "Wait for a live dose reserved from shared storage"))
                .thenExecute(() -> {
                    helper.setBlock(power, Blocks.AIR);
                    sharedStock[0] = circuitAmount(controller, "input");
                    sharedStock[1] = circuitAmount(controller, "output");
                    helper.assertTrue(sharedStock[0] > 0 && vesselStock(helper, false).getAmount() == 1000,
                            "The active circuit has fuel left and the local dose count is unchanged");
                    helper.setBlock(FUEL_IN, NCFission.CASING.get());
                    helper.setBlock(FUEL_OUT, NCFission.CASING.get());
                })
                .thenWaitUntil(() -> helper.assertTrue(controller.assembled(), "A vessel can operate without a port"))
                .thenExecute(() -> helper.setBlock(power, Blocks.REDSTONE_BLOCK))
                .thenWaitUntil(() -> helper.assertTrue(circuitAmount(controller, "output") == sharedStock[1] + 1,
                        "The reserved shared dose discharges to its original output after port removal"))
                .thenExecute(() -> helper.assertTrue(circuitAmount(controller, "input") == sharedStock[0]
                                && vesselStock(helper, false).getAmount() == 1000 && vesselStock(helper, true).isEmpty(),
                        "Discharging the old dose does not consume the parked shared feed or touch local storage"))
                .thenWaitUntil(() -> helper.assertTrue(vesselTag(helper).getBoolean("reserved_local")
                                && vesselTag(helper).getDouble("progress") > 0,
                        "The next dose comes from the local fuel supply"))
                .thenExecute(() -> {
                    helper.setBlock(power, Blocks.AIR);
                    var saved = vesselTag(helper);
                    helper.setBlock(FIRST_VESSEL, Blocks.AIR);
                    helper.setBlock(FIRST_VESSEL, NCSaltFission.VESSEL.get());
                    helper.getBlockEntity(FIRST_VESSEL).load(saved);
                    helper.setBlock(FUEL_IN, portState);
                    helper.getBlockEntity(FUEL_IN).load(savedPort);
                })
                .thenIdle(5)
                .thenExecute(() -> {
                    helper.assertTrue(!controller.assembled(), "A lone fuel port still needs a matching output");
                    helper.setBlock(FUEL_OUT, outputState);
                    var data = helper.getBlockEntity(FUEL_OUT).saveWithoutMetadata();
                    data.putString("fuel_filter", NuclearCraft.id(FUEL).toString());
                    helper.getBlockEntity(FUEL_OUT).load(data);
                })
                .thenWaitUntil(() -> helper.assertTrue(controller.assembled(), "Reattach the original shared circuit"))
                .thenExecute(() -> {
                    helper.useBlock(FIRST_VESSEL, player);
                    var menu = (SaltFissionPartMenu) player.containerMenu;
                    helper.assertTrue(menu.linked() && menu.reserved().isFluidStackIdentical(stack(FUEL, 1))
                                    && menu.fluid(false).getAmount() == sharedStock[0] && menu.fluid(true).isEmpty(),
                            "The rebuilt vessel reports the new shared feed and the reserved dose's local output");
                    helper.setBlock(power, Blocks.REDSTONE_BLOCK);
                })
                .thenWaitUntil(() -> helper.assertTrue(vesselStock(helper, true).isFluidStackIdentical(stack(DEPLETED, 1)),
                        "Reloaded local fuel completes in the original local output"))
                .thenExecute(() -> {
                    helper.setBlock(power, Blocks.AIR);
                    int producedShared = sharedStock[1] + 1;
                    helper.assertTrue(vesselStock(helper, false).getAmount() == 999
                                    && circuitAmount(controller, "input") == sharedStock[0]
                                    && fluids(helper, FUEL_OUT, Direction.SOUTH).drain(producedShared, EXECUTE)
                                        .isFluidStackIdentical(stack(DEPLETED, producedShared))
                                    && circuitAmount(controller, "output") == 0,
                            "The shared output returns once and the local dose stays in local storage");
                })
                .thenSucceed();
    }

    private static CompoundTag vesselTag(GameTestHelper helper) {
        return helper.getBlockEntity(FIRST_VESSEL).saveWithoutMetadata();
    }

    private static FluidStack vesselStock(GameTestHelper helper, boolean output) {
        return FluidStack.loadFluidStackFromNBT(vesselTag(helper).getCompound("fuel_tanks")
                .getCompound(output ? "output" : "input"));
    }

    private static int circuitAmount(SolidFissionController controller, String tank) {
        ListTag circuits = controller.saveWithoutMetadata().getList("salt_fuel", Tag.TAG_COMPOUND);
        for (int index = 0; index < circuits.size(); index++) {
            CompoundTag circuit = circuits.getCompound(index);
            if (circuit.getString("fuel_filter").equals(NuclearCraft.id(FUEL).toString())) {
                return circuit.getCompound(tank).getInt("Amount");
            }
        }
        return 0;
    }

    @GameTest(template = "fission", timeoutTicks = 240)
    public static void saltHeatersKeepIndependentLocalAndSharedCoolant(GameTestHelper helper) {
        shell(helper);
        addVessel(helper, FIRST_VESSEL, Direction.EAST, FUEL, FUEL_IN, FUEL_OUT);
        var heater = FIRST_VESSEL.below();
        helper.setBlock(CASE, Blocks.AIR);
        helper.setBlock(NAK_OUT, NCFission.CASING.get());
        var portState = NCSaltFission.port(NCSaltCoolants.NAK).defaultBlockState()
                .setValue(FissionWallBlock.FACING, Direction.DOWN);
        helper.setBlock(NAK_IN, portState);
        var port = helper.getBlockEntity(NAK_IN);
        var detached = port.getCapability(ForgeCapabilities.FLUID_HANDLER, Direction.DOWN);
        var local = detached.orElseThrow(AssertionError::new);
        helper.assertTrue(!port.getCapability(ForgeCapabilities.FLUID_HANDLER, Direction.UP).isPresent()
                        && local.getTankCapacity(0) == 1296 && local.fill(stack("iron_nak", 1), EXECUTE) == 0
                        && local.fill(stack("nak", 111), SIMULATE) == 111 && local.getFluidInTank(0).isEmpty()
                        && local.fill(stack("nak", 111), EXECUTE) == 111 && local.drain(1, EXECUTE).isEmpty(),
                "A detached port fills its own tanks with the proper coolant from its configured face");
        helper.setBlock(NAK_IN, portState.setValue(FissionWallBlock.ACTIVE, true));
        helper.assertTrue(!detached.isPresent() && local.fill(stack("nak", 1), EXECUTE) == 0
                        && fluids(helper, NAK_IN, Direction.DOWN).fill(stack("nak", 1), EXECUTE) == 0,
                "Changing detached direction revokes the previous input handler");
        helper.setBlock(NAK_IN, portState);
        var beforeAssembly = helper.getBlockEntity(NAK_IN).getCapability(ForgeCapabilities.FLUID_HANDLER, Direction.DOWN);
        var stale = beforeAssembly.orElseThrow(AssertionError::new);

        var player = saltInspector(helper);
        var portPos = helper.absolutePos(NAK_IN);
        player.moveTo(portPos.getX() + 0.5, portPos.getY(), portPos.getZ() - 1);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(NCSaltCoolants.NAK.cold().getBucket()));
        helper.useBlock(heater, player);
        helper.assertTrue(player.getMainHandItem().is(Items.BUCKET)
                        && heaterFluid(helper, heater, false).isFluidStackIdentical(stack("nak", 1000)),
                "A coolant bucket fills the unassembled heater's local tank");
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        helper.setBlock(CASE, NCFission.CASING.get());
        var controller = (SolidFissionController) helper.getBlockEntity(CONTROLLER);
        var shared = new CompoundTag[1];
        var detachedHeater = new CompoundTag[1];

        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(controller.assembled(), "One coolant port can serve a reactor"))
                .thenExecute(() -> {
                    var input = fluids(helper, NAK_IN, Direction.DOWN);
                    helper.assertTrue(!beforeAssembly.isPresent() && stale.getFluidInTank(0).isEmpty()
                                    && input.getTankCapacity(0) == 1296 && input.getFluidInTank(0).isEmpty(),
                            "Assembly switches the port to the shared bank and revokes detached access");
                    helper.useBlock(NAK_IN, player);
                    var menu = (SaltFissionPartMenu) player.containerMenu;
                    helper.assertTrue(menu.linked() && menu.clickMenuButton(player, SaltFissionPartMenu.SWITCH_BUFFER)
                                    && menu.local() && menu.fluid(false).isFluidStackIdentical(stack("nak", 111)),
                            "The linked port menu still lets the player inspect its preserved local buffer");
                    input.fill(stack("nak", 240), EXECUTE);
                    fluids(helper, FUEL_IN, Direction.NORTH).fill(stack(FUEL, 30), EXECUTE);
                    helper.setBlock(CONTROLLER.west(), Blocks.REDSTONE_BLOCK);
                })
                .thenWaitUntil(() -> helper.assertTrue(sharedCoolant(controller).getCompound("output").getInt("Amount") > 0,
                        "The lone input port provides coolant to its heater"))
                .thenExecute(() -> {
                    helper.setBlock(CONTROLLER.west(), Blocks.AIR);
                    shared[0] = sharedCoolant(controller);
                    helper.assertTrue(heaterFluid(helper, heater, false).getAmount() == 1000
                                    && heaterFluid(helper, heater, true).isEmpty(),
                            "A linked heater leaves its local supply intact");
                    helper.setBlock(NAK_IN, NCFission.CASING.get());
                })
                .thenWaitUntil(() -> helper.assertTrue(controller.assembled(), "The shell can run without heater ports"))
                .thenExecute(() -> helper.setBlock(CONTROLLER.west(), Blocks.REDSTONE_BLOCK))
                .thenWaitUntil(() -> helper.assertTrue(heaterFluid(helper, heater, true).getAmount() > 0,
                        "The unlinked heater uses its local supply"))
                .thenExecute(() -> {
                    helper.setBlock(CONTROLLER.west(), Blocks.AIR);
                    int hot = heaterFluid(helper, heater, true).getAmount();
                    helper.assertTrue(heaterFluid(helper, heater, false).getAmount() + hot == 1000
                                    && sharedCoolant(controller).equals(shared[0]),
                            "Heating transfers coolant once and leaves the removed circuit's stock intact");
                    detachedHeater[0] = helper.getBlockEntity(heater).saveWithoutMetadata();
                    helper.setBlock(heater, Blocks.AIR);
                    helper.setBlock(heater, NCSaltFission.heater(NCSaltCoolants.NAK));
                    helper.getBlockEntity(heater).load(detachedHeater[0]);
                    helper.setBlock(NAK_OUT, NCSaltFission.port(NCSaltCoolants.NAK).defaultBlockState()
                            .setValue(FissionWallBlock.ACTIVE, true));
                })
                .thenWaitUntil(() -> helper.assertTrue(controller.assembled(), "A lone output port reconnects the same circuit"))
                .thenExecute(() -> {
                    var output = fluids(helper, NAK_OUT, Direction.UP);
                    var savedHot = FluidStack.loadFluidStackFromNBT(shared[0].getCompound("output"));
                    helper.assertTrue(output.drain(savedHot, SIMULATE).isFluidStackIdentical(savedHot)
                                    && output.getFluidInTank(0).isFluidStackIdentical(savedHot)
                                    && output.drain(savedHot, EXECUTE).isFluidStackIdentical(savedHot)
                                    && output.getFluidInTank(0).isEmpty(),
                            "Both simulated and live drains use the reconnected shared buffer");
                    helper.assertTrue(helper.getBlockEntity(heater).saveWithoutMetadata().equals(detachedHeater[0]),
                            "Reloading the local tanks and attaching a hot port leaves the local stock intact");
                })
                .thenSucceed();
    }

    private static FluidStack heaterFluid(GameTestHelper helper, BlockPos heater, boolean output) {
        return FluidStack.loadFluidStackFromNBT(helper.getBlockEntity(heater).saveWithoutMetadata()
                .getCompound("coolant").getCompound(output ? "output" : "input"));
    }

    private static CompoundTag sharedCoolant(SolidFissionController controller) {
        return controller.saveWithoutMetadata().getCompound("salt_cooling").getCompound("nak");
    }

    private static FakePlayer saltInspector(GameTestHelper helper) {
        return new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), "SaltInspector")) {
            @Override
            public OptionalInt openMenu(MenuProvider provider) {
                containerMenu = provider.createMenu(1, getInventory(), this);
                return OptionalInt.of(1);
            }
        };
    }

    private static void shell(GameTestHelper helper) {
        for (BlockPos pos : BlockPos.betweenClosed(new BlockPos(1, 1, 1), new BlockPos(7, 5, 7))) {
            if (pos.getX() == 1 || pos.getX() == 7 || pos.getY() == 1 || pos.getY() == 5 || pos.getZ() == 1 || pos.getZ() == 7) {
                helper.setBlock(pos, NCFission.CASING.get());
            }
        }
        helper.setBlock(CONTROLLER, NCSaltFission.CONTROLLER.get());
        helper.setBlock(NAK_IN, NCSaltFission.port(NCSaltCoolants.NAK));
        helper.setBlock(NAK_OUT, NCSaltFission.port(NCSaltCoolants.NAK).defaultBlockState().setValue(FissionWallBlock.ACTIVE, true));
    }

    private static void addVessel(GameTestHelper helper, BlockPos position, Direction line, String fuel, BlockPos in, BlockPos out) {
        helper.setBlock(position, NCSaltFission.VESSEL.get());
        helper.setBlock(position.relative(line), NCFission.HEAVY_WATER_MODERATOR.get());
        helper.setBlock(position.relative(line, 2), NCFission.BERYLLIUM_CARBON_REFLECTOR.get());
        helper.setBlock(position.below(), NCSaltFission.heater(NCSaltCoolants.NAK));
        helper.setBlock(in, NCSaltFission.VESSEL_PORT.get());
        helper.setBlock(out, NCSaltFission.VESSEL_PORT.get().defaultBlockState().setValue(FissionWallBlock.ACTIVE, true));
        for (BlockPos pos : new BlockPos[]{position, in, out}) {
            BlockEntity part = helper.getBlockEntity(pos);
            CompoundTag state = part.saveWithoutMetadata();
            state.putString("fuel_filter", NuclearCraft.id(fuel).toString());
            part.load(state);
        }
    }

    private static IFluidHandler fluids(GameTestHelper helper, BlockPos pos, Direction face) {
        return helper.getBlockEntity(pos).getCapability(ForgeCapabilities.FLUID_HANDLER, face).orElseThrow(AssertionError::new);
    }

    private static FluidStack stack(String fluid, int amount) {
        return new FluidStack(ForgeRegistries.FLUIDS.getValue(NuclearCraft.id(fluid)), amount);
    }
}
