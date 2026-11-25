package nc.gametest;

import nc.NuclearCraft;
import nc.init.NCGasCoolants;
import nc.init.NCHeatExchangers;
import nc.init.NCFluids;
import nc.multiblock.hx.ExchangerController;
import nc.multiblock.hx.ExchangerMenu;
import nc.multiblock.hx.ExchangerTubeBlock;
import nc.multiblock.hx.ExchangerTubeBlock.Setting;
import nc.multiblock.hx.ExchangerWallBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandler.FluidAction;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegisterEvent;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

@GameTestHolder(NuclearCraft.MOD_ID)
@PrefixGameTestTemplate(false)
@Mod.EventBusSubscriber(modid = NuclearCraft.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class HeatExchangerGameTests {
    private static final BlockPos CONTROLLER = new BlockPos(2, 3, 1);
    private static final BlockPos SHELL_IN = new BlockPos(5, 3, 3);
    private static final BlockPos SHELL_OUT = new BlockPos(1, 3, 3);
    private static final BlockPos TUBE_IN = new BlockPos(3, 3, 1);
    private static final BlockPos TUBE_OUT = new BlockPos(3, 3, 5);
    private static final BlockPos FRAME = new BlockPos(1, 1, 1);

    @SubscribeEvent
    public static void registerScriptedTube(RegisterEvent event) {
        event.register(ForgeRegistries.Keys.BLOCKS, helper -> helper.register(NuclearCraft.id("heat_exchanger_tube_qa"),
                NCHeatExchangers.createScriptedTube(64D, 0.5D)));
        event.register(ForgeRegistries.Keys.ITEMS, helper -> helper.register(NuclearCraft.id("heat_exchanger_tube_qa"),
                NCHeatExchangers.createScriptedTubeItem("nuclearcraft:heat_exchanger_tube_qa")));
    }

    @GameTest(template = "turbine", timeoutTicks = 180)
    public static void heatExchangerAppliesScriptedTubeRetentionToMixedNetwork(GameTestHelper helper) {
        build(helper, 3);
        BlockPos shellIn = SHELL_IN.south();
        BlockPos shellOut = SHELL_OUT.north();
        helper.setBlock(SHELL_IN, NCHeatExchangers.CASING.get());
        helper.setBlock(SHELL_OUT, NCHeatExchangers.CASING.get());
        helper.setBlock(shellIn, NCHeatExchangers.INLET.get());
        helper.setBlock(shellOut, NCHeatExchangers.OUTLET.get());
        var scripted = ForgeRegistries.BLOCKS.getValue(NuclearCraft.id("heat_exchanger_tube_qa"));
        BlockPos center = new BlockPos(3, 3, 3);
        helper.setBlock(center, straightTube(scripted));
        helper.startSequence()
                .thenWaitUntil(() -> fluid(helper, TUBE_IN, Direction.NORTH))
                .thenExecute(() -> {
                    fill(helper, TUBE_IN, Direction.NORTH, new FluidStack(NCGasCoolants.OXYGEN.exhaust(), 1024));
                    fill(helper, shellIn, Direction.EAST, new FluidStack(Fluids.WATER, 48));
                    helper.setBlock(CONTROLLER.north(), Blocks.REDSTONE_BLOCK);
                })
                .thenWaitUntil(() -> {
                    var oxygen = fluid(helper, TUBE_OUT, Direction.SOUTH).getFluidInTank(0);
                    var steam = fluid(helper, shellOut, Direction.WEST).getFluidInTank(0);
                    helper.assertTrue(oxygen.isFluidStackIdentical(new FluidStack(NCGasCoolants.OXYGEN.cold(), 256))
                                    && steam.isFluidStackIdentical(new FluidStack(NCFluids.HIGH_PRESSURE_STEAM.source().get(), 192)),
                            "Two stock tubes and one scripted tube retain three quarters of the heat, found "
                                    + oxygen.getAmount() + " mB of oxygen and " + steam.getAmount() + " mB of steam");
                    helper.assertTrue(fluid(helper, TUBE_IN, Direction.NORTH).getFluidInTank(0).isEmpty()
                                    && fluid(helper, shellIn, Direction.EAST).getFluidInTank(0).isEmpty(),
                            "The mixed network consumes both balanced inputs exactly");
                })
                .thenExecute(() -> {
                    helper.setBlock(CONTROLLER.north(), Blocks.AIR);
                    var previousOutput = helper.getBlockEntity(TUBE_OUT).getCapability(ForgeCapabilities.FLUID_HANDLER, Direction.SOUTH);
                    var player = FakePlayerFactory.getMinecraft(helper.getLevel());
                    var held = player.getMainHandItem();
                    boolean wasSneaking = player.isShiftKeyDown();
                    var tool = new ItemStack(NCHeatExchangers.MULTITOOL.get());
                    try {
                        player.setItemInHand(InteractionHand.MAIN_HAND, tool);
                        player.setShiftKeyDown(false);
                        BlockPos clicked = helper.absolutePos(center);
                        var hit = new BlockHitResult(Vec3.atCenterOf(clicked), Direction.NORTH, clicked, false);
                        var context = new UseOnContext(player, InteractionHand.MAIN_HAND, hit);
                        var stock = straightTube(NCHeatExchangers.THERMOCONDUCTING.get());
                        for (Setting setting : new Setting[]{Setting.CLOSED_BAFFLE, Setting.OPEN_BAFFLE, Setting.CLOSED, Setting.OPEN}) {
                            helper.assertTrue(tool.getItem().onItemUseFirst(tool, context).consumesAction(),
                                    "The multitool cycles the scripted face");
                            helper.assertTrue(helper.getBlockState(center) == ExchangerTubeBlock.withSetting(
                                            straightTube(scripted), Direction.NORTH, setting)
                                            && helper.getBlockState(center.north()) == ExchangerTubeBlock.withSetting(
                                            stock, Direction.SOUTH, setting),
                                    "The stock tube mirrors the configured scripted face");
                        }
                    } finally {
                        player.setShiftKeyDown(wasSneaking);
                        player.setItemInHand(InteractionHand.MAIN_HAND, held);
                    }
                    helper.assertTrue(!previousOutput.isPresent(), "Reconfiguring the network revokes old port capabilities");
                })
                .thenWaitUntil(() -> helper.assertTrue(
                        fluid(helper,TUBE_OUT,Direction.SOUTH).getFluidInTank(0).getAmount() == 256
                                && fluid(helper,shellOut,Direction.WEST).getFluidInTank(0).getAmount() == 192,
                        "Repairing the mixed connection restores both buffered products"))
                .thenSucceed();
    }

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

    @GameTest(template = "turbine", timeoutTicks = 100)
    public static void heatExchangerUsesOrderedBatchesOnBothSides(GameTestHelper helper) {
        build(helper, 3);
        helper.startSequence()
                .thenWaitUntil(() -> fluid(helper, TUBE_IN, Direction.NORTH))
                .thenExecute(() -> {
                    var fluids = ForgeRegistries.FLUIDS;
                    fill(helper, TUBE_IN, Direction.NORTH,
                            new FluidStack(fluids.getValue(NuclearCraft.id("hydrofluoric_acid")), 28));
                    fill(helper, SHELL_IN, Direction.EAST,
                            new FluidStack(fluids.getValue(NuclearCraft.id("methanol")), 28));
                    helper.setBlock(CONTROLLER.north(), Blocks.REDSTONE_BLOCK);
                })
                .thenWaitUntil(() -> helper.assertTrue(fluid(helper, TUBE_OUT, Direction.SOUTH).getFluidInTank(0).getAmount() == 10
                                && fluid(helper, SHELL_OUT, Direction.WEST).getFluidInTank(0).getAmount() == 8
                                && fluid(helper, TUBE_IN, Direction.NORTH).getFluidInTank(0).isEmpty()
                                && fluid(helper, SHELL_IN, Direction.EAST).getFluidInTank(0).isEmpty(),
                        "Each circuit must use its 18 mB and 10 mB choices under the shared heat budget"))
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
        var recipes = helper.getLevel().getRecipeManager().getAllRecipesFor(NCHeatExchangers.HEAT_EXCHANGER_RECIPES.get())
                .stream().filter(recipe -> recipe.id().getNamespace().equals(NuclearCraft.MOD_ID)).toList();
        helper.assertTrue(recipes.size() == 70, "All 6 steam and water, 32 gas, and 32 NaK recipes must load");
        var oxygen = recipes.stream().filter(r -> r.input().accepts(new FluidStack(NCGasCoolants.OXYGEN.exhaust(), 4)))
                .findFirst().orElseThrow();
        helper.assertTrue(oxygen.input().matchedAmount(new FluidStack(NCGasCoolants.OXYGEN.exhaust(), 4), 1) == 4 && oxygen.output().getFluid() == NCGasCoolants.OXYGEN.cold()
                && oxygen.output().getAmount() == 1 && oxygen.heatRequired() == 16D && !oxygen.heating(),
                "Oxygen exhaust must preserve its legacy compression, fluid, and heat values");
        helper.succeed();
    }

    @GameTest(template = "turbine", timeoutTicks = 120)
    public static void heatExchangerConnectsTubesThroughNormalPlacement(GameTestHelper helper) {
        buildShell(helper, 3);
        var tube = NCHeatExchangers.THERMOCONDUCTING.get();
        for (int z = 1; z <= 3; z++) {
            placeTube(helper, tube, new BlockPos(3, 3, z), Direction.SOUTH, false);
        }
        for (int z = 2; z <= 4; z++) {
            helper.assertTrue(helper.getBlockState(new BlockPos(3, 3, z)) == straightTube(tube),
                    "Placing tubes from the inlet to the outlet must open the whole run");
        }
        helper.startSequence()
                .thenWaitUntil(() -> fluid(helper, TUBE_IN, Direction.NORTH))
                .thenExecute(() -> {
                    fill(helper, TUBE_IN, Direction.NORTH, new FluidStack(NCGasCoolants.OXYGEN.exhaust(), 1024));
                    fill(helper, SHELL_IN, Direction.EAST, new FluidStack(Fluids.WATER, 64));
                    helper.setBlock(CONTROLLER.north(), Blocks.REDSTONE_BLOCK);
                })
                .thenWaitUntil(() -> assertOutputs(helper, SHELL_OUT))
                .thenExecute(() -> {
                    var oldPort = helper.getBlockEntity(TUBE_OUT)
                            .getCapability(ForgeCapabilities.FLUID_HANDLER, Direction.SOUTH);
                    BlockPos removed = new BlockPos(3, 3, 3);
                    rejectPlacement(helper, tube, removed, Direction.WEST);
                    helper.assertTrue(helper.getBlockState(removed) == straightTube(tube)
                                    && helper.getBlockState(removed.west()).isAir() && oldPort.isPresent(),
                            "Forge cancellation must restore both faces and keep the assembled port available");
                    helper.assertTrue(helper.getLevel().destroyBlock(helper.absolutePos(removed), false),
                            "Breaking a tube must remove the selected block");
                    helper.assertTrue(helper.getBlockState(removed.north()) == ExchangerTubeBlock.withSetting(
                                    tube.defaultBlockState(), Direction.NORTH, Setting.OPEN)
                                    && helper.getBlockState(removed.south()) == ExchangerTubeBlock.withSetting(
                                    tube.defaultBlockState(), Direction.SOUTH, Setting.OPEN),
                            "Breaking the middle tube must close the two exposed arms and keep both port links");
                    helper.assertTrue(!oldPort.isPresent(), "Breaking a tube invalidates the old fluid handler");
                })
                .thenSucceed();
    }

    @GameTest(template = "turbine")
    public static void scriptedTubeCopiesConnectionsAndPreservesBaffles(GameTestHelper helper) {
        var stock = NCHeatExchangers.COPPER.get();
        var scripted = ForgeRegistries.ITEMS.getValue(NuclearCraft.id("heat_exchanger_tube_qa"));
        BlockPos source = new BlockPos(2, 2, 2);
        BlockPos placed = source.east();
        BlockState template = ExchangerTubeBlock.withSetting(stock.defaultBlockState(), Direction.EAST, Setting.CLOSED_BAFFLE);
        template = ExchangerTubeBlock.withSetting(template, Direction.SOUTH, Setting.OPEN);
        template = ExchangerTubeBlock.withSetting(template, Direction.UP, Setting.OPEN_BAFFLE);
        helper.setBlock(source, template);
        helper.setBlock(source.south(), ExchangerTubeBlock.withSetting(stock.defaultBlockState(), Direction.NORTH, Setting.OPEN));
        BlockState baffle = ExchangerTubeBlock.withSetting(stock.defaultBlockState(), Direction.NORTH, Setting.CLOSED_BAFFLE);
        helper.setBlock(placed.south(), baffle);

        placeTube(helper, scripted, source, Direction.EAST, true);
        var customTube = ForgeRegistries.BLOCKS.getValue(NuclearCraft.id("heat_exchanger_tube_qa"));
        BlockState copied = ExchangerTubeBlock.withSetting(customTube.defaultBlockState(), Direction.WEST, Setting.CLOSED_BAFFLE);
        copied = ExchangerTubeBlock.withSetting(copied, Direction.EAST, Setting.CLOSED_BAFFLE);
        copied = ExchangerTubeBlock.withSetting(copied, Direction.SOUTH, Setting.OPEN);
        copied = ExchangerTubeBlock.withSetting(copied, Direction.UP, Setting.OPEN_BAFFLE);
        helper.assertTrue(helper.getBlockState(placed) == copied,
                "Sneak-placement must copy the tube settings and mirror the contact face");
        helper.assertTrue(helper.getBlockState(source) == template
                        && helper.getBlockState(placed.south()) == ExchangerTubeBlock.withSetting(
                        baffle, Direction.NORTH, Setting.OPEN_BAFFLE),
                "The copied side run must open its companion without modifying the template or losing the baffle");

        helper.getLevel().destroyBlock(helper.absolutePos(placed), false);
        helper.assertTrue(helper.getBlockState(placed.south()) == baffle,
                "Breaking a connected tube must retain the baffle when closing the neighbor");
        placeTube(helper, stock, source, Direction.EAST, false);
        helper.assertTrue(helper.getBlockState(placed) == ExchangerTubeBlock.withSetting(
                        stock.defaultBlockState(), Direction.WEST, Setting.OPEN_BAFFLE)
                        && helper.getBlockState(source) == ExchangerTubeBlock.withSetting(template, Direction.EAST, Setting.OPEN_BAFFLE),
                "Normal installation opens the paired connection and inherits the attached baffle");
        helper.succeed();
    }

    private static void rejectPlacement(GameTestHelper helper, ItemLike tube, BlockPos pos, Direction side) {
        var player = FakePlayerFactory.getMinecraft(helper.getLevel());
        var placement = helper.absolutePos(pos.relative(side));
        var cancelled = new AtomicBoolean();
        Consumer<BlockEvent.EntityMultiPlaceEvent> guard = event -> {
            if (event.getEntity() == player && event.getPos().equals(placement)) {
                cancelled.set(true);
                event.setCanceled(true);
            }
        };
        MinecraftForge.EVENT_BUS.addListener(guard);
        try {
            helper.assertTrue(useTube(helper, tube, pos, side, false) == InteractionResult.FAIL && cancelled.get(),
                    "Forge must be able to cancel placement of both changed blocks");
        } finally {
            MinecraftForge.EVENT_BUS.unregister(guard);
        }
    }

    private static void placeTube(GameTestHelper helper, ItemLike item, BlockPos anchor, Direction face, boolean sneak) {
        helper.assertTrue(useTube(helper, item, anchor, face, sneak).consumesAction(),
                "The tube block item must place against its clicked neighbor");
    }

    private static InteractionResult useTube(GameTestHelper helper, ItemLike item, BlockPos anchor, Direction face, boolean sneak) {
        var player = FakePlayerFactory.getMinecraft(helper.getLevel());
        var previousItem = player.getMainHandItem();
        boolean previousSneak = player.isShiftKeyDown();
        var held = new ItemStack(item);
        try {
            player.setItemInHand(InteractionHand.MAIN_HAND, held);
            player.setShiftKeyDown(sneak);
            BlockPos clicked = helper.absolutePos(anchor);
            var hit = new BlockHitResult(Vec3.atCenterOf(clicked), face, clicked, false);
            var context = new UseOnContext(player, InteractionHand.MAIN_HAND, hit);
            return held.useOn(context);
        } finally {
            player.setShiftKeyDown(previousSneak);
            player.setItemInHand(InteractionHand.MAIN_HAND, previousItem);
        }
    }

    @GameTest(template = "turbine", timeoutTicks = 120)
    public static void exchangerReportsRatesAndClearsThroughItsController(GameTestHelper helper) {
        build(helper, 3);
        BlockPos redstone = new BlockPos(4, 3, 1);
        helper.setBlock(redstone, NCHeatExchangers.REDSTONE.get());
        var player = FakePlayerFactory.getMinecraft(helper.getLevel());
        var controller = (ExchangerController) helper.getBlockEntity(CONTROLLER);
        ExchangerMenu menu = controller.createMenu(0, player.getInventory(), player);
        helper.startSequence()
                .thenWaitUntil(() -> fluid(helper, TUBE_IN, Direction.NORTH))
                .thenExecute(() -> {
                    fill(helper, TUBE_IN, Direction.NORTH, new FluidStack(NCGasCoolants.OXYGEN.exhaust(), 12000));
                    fill(helper, SHELL_IN, Direction.EAST, new FluidStack(Fluids.WATER, 70001));
                    helper.assertTrue(menu.lengthX() == 3 && menu.lengthY() == 3 && menu.lengthZ() == 3
                                    && menu.networks() == 1 && menu.tubes() == 3,
                            "Expose the interior dimensions and installed networks and tubes");
                    helper.assertTrue(menu.shellInput() == 70001 && menu.shellInputCapacity() == 500000
                                    && menu.shellOutputCapacity() == 2000000,
                            "Preserve complete tank amounts and capacities across the 16-bit menu words");
                    helper.assertTrue(!menu.running() && !menu.enabled()
                                    && !helper.getBlockState(redstone).getValue(ExchangerWallBlock.ACTIVE),
                            "The controller starts without a manual or redstone signal");
                    helper.setBlock(redstone.north(), Blocks.REDSTONE_BLOCK);
                })
                .thenWaitUntil(() -> {
                    helper.assertTrue(menu.running() && !menu.enabled()
                                    && helper.getBlockState(redstone).getValue(ExchangerWallBlock.ACTIVE),
                            "An external signal powers the redstone port and starts the controller");
                    helper.assertTrue(menu.activeNetworks() == 1 && menu.activeTubes() == 1 && menu.shellInputRate() > 0
                                    && menu.tubeInputRate() > 0 && menu.heatRate() > 0 && menu.temperatureDifference() > 0,
                            "Both flows must report consumed fluid alongside heat and temperature");
                })
                .thenExecute(() -> helper.setBlock(redstone.north(), Blocks.AIR))
                .thenWaitUntil(() -> helper.assertTrue(!menu.running()
                                && !helper.getBlockState(redstone).getValue(ExchangerWallBlock.ACTIVE)
                                && menu.shellInputRate() == 0 && menu.tubeInputRate() == 0 && menu.heatRate() == 0,
                        "The redstone port and processing readings turn off when the signal ends"))
                .thenExecute(() -> {
                    var oldPosition = player.position();
                    Vec3 center = Vec3.atCenterOf(helper.absolutePos(CONTROLLER));
                    try {
                        player.setPos(center.x + 20, center.y, center.z);
                        helper.assertTrue(!menu.clickMenuButton(player, ExchangerMenu.CLEAR)
                                        && menu.shellInput() > 0 && menu.shellOutput() > 0,
                                "Reject clear requests outside the registered controller's menu range");
                        player.setPos(center.x, center.y, center.z - 1);
                        helper.assertTrue(menu.clickMenuButton(player, ExchangerMenu.CLEAR),
                                "The nearby player can clear the assembled exchanger");
                    } finally {
                        player.setPos(oldPosition.x, oldPosition.y, oldPosition.z);
                    }
                    helper.assertTrue(menu.shellInput() == 0 && menu.shellOutput() == 0
                                    && fluid(helper, TUBE_IN, Direction.NORTH).getFluidInTank(0).isEmpty()
                                    && fluid(helper, TUBE_OUT, Direction.SOUTH).getFluidInTank(0).isEmpty(),
                            "Clearing must empty both the shell and tube circuit, including output");
                })
                .thenSucceed();
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
        buildShell(helper, shellHeight);
        for (int z = 2; z <= 4; z++) {
            helper.setBlock(new BlockPos(3, 3, z), straightTube(NCHeatExchangers.THERMOCONDUCTING.get()));
        }
    }

    private static void buildShell(GameTestHelper helper, int shellHeight) {
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
    }

    private static BlockState straightTube(Block tube) {
        return ExchangerTubeBlock.withSetting(
                ExchangerTubeBlock.withSetting(tube.defaultBlockState(), Direction.NORTH, Setting.OPEN),
                Direction.SOUTH, Setting.OPEN);
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
