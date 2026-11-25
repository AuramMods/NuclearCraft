package nc.gametest;

import nc.NuclearCraft;
import nc.init.NCFluids;
import nc.init.NCMachines;
import nc.multiblock.machine.DecayPool;
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
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
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
public final class DecayPoolGameTests {
    private static final BlockPos CONTROLLER = new BlockPos(1, 2, 2);
    private static final BlockPos FEED = new BlockPos(2, 2, 1);
    private static final BlockPos OUTPUT = new BlockPos(2, 2, 4);
    private static final BlockPos RESERVOIR = new BlockPos(1, 2, 3);
    private static final BlockPos PRODUCTS = new BlockPos(4, 2, 3);
    private static final BlockPos REDSTONE = new BlockPos(3, 2, 4);
    private static final BlockPos CORNER = new BlockPos(1, 1, 1);

    @GameTest(template = "empty")
    public static void heatsWaterFromMixedLegacySourcesAndHonorsRedstone(GameTestHelper helper) {
        build(helper);
        var pool = (DecayPool) helper.getBlockEntity(CONTROLLER);
        int[] productBeforePause = new int[1];
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(pool.assembled(), "Pool assembles with valid casing and ports"))
                .thenExecute(() -> {
                    var promethium = helper.getLevel().getRecipeManager().byKey(NuclearCraft.id("decay_pool/promethium_147_item"))
                            .orElseThrow(AssertionError::new);
                    helper.assertTrue(promethium.getResultItem(helper.getLevel().registryAccess())
                                    .is(ForgeRegistries.ITEMS.getValue(NuclearCraft.id("samarium_ingot"))),
                            "The Samarium Ingot fallback loads on clean Forge");
                    var feed = fluid(helper, FEED, Direction.NORTH);
                    var tank = fluid(helper, RESERVOIR, Direction.WEST);
                    var fuel = item(helper, RESERVOIR, Direction.WEST);
                    var isotope = new ItemStack(ForgeRegistries.ITEMS.getValue(NuclearCraft.id("ruthenium_106_dust")));
                    var source = new FluidStack(ForgeRegistries.FLUIDS.getValue(NuclearCraft.id("strontium_90")), 144);
                    helper.assertTrue(pool.fuelCapacity() == 288 && fuel.getSlotLimit(0) == 2 && feed.getTankCapacity(0) == 64_000,
                            "Each container adds 144 units of storage and each exterior block volume adds 1,000 mB");
                    helper.assertTrue(!helper.getBlockEntity(FEED).getCapability(ForgeCapabilities.FLUID_HANDLER, Direction.SOUTH).isPresent(),
                            "The process port exposes only its outward side");
                    helper.assertTrue(feed.fill(new FluidStack(Fluids.WATER, 1_000), SIMULATE) == 1_000
                                    && feed.getFluidInTank(0).isEmpty(),
                            "Simulated fill leaves the feed tank untouched");
                    helper.assertTrue(fuel.insertItem(0, isotope, false).isEmpty() && tank.fill(source, EXECUTE) == 144
                                    && fuel.insertItem(0, isotope, false).is(isotope.getItem()),
                            "Item and molten source share the reservoir capacity");
                    feed.fill(new FluidStack(Fluids.WATER, 1_000), EXECUTE);
                })
                .thenIdle(6)
                .thenExecute(() -> {
                    var output = fluid(helper, OUTPUT, Direction.SOUTH);
                    var product = output.getFluidInTank(0);
                    int remaining = fluid(helper, FEED, Direction.NORTH).getFluidInTank(0).getAmount();
                    helper.assertTrue(product.getFluid() == NCFluids.PREHEATED_WATER.source().get()
                                    && product.getAmount() > 0 && remaining + product.getAmount() == 1_000,
                            "The pool converts exactly one mB of water into one mB of product");
                    double expectedHeat = helper.getLevel().getRecipeManager().getAllRecipesFor(NCMachines.HEAT_SOURCES.get()).stream()
                            .filter(recipe -> recipe.id().equals(NuclearCraft.id("decay_pool/ruthenium_106_item"))
                                    || recipe.id().equals(NuclearCraft.id("decay_pool/strontium_90_fluid")))
                            .mapToDouble(recipe -> recipe.heat()).sum();
                    helper.assertTrue(expectedHeat > 0 && pool.heatingRate() == expectedHeat,
                            "Both legacy sources contribute their original heat");
                    productBeforePause[0] = product.getAmount();
                    helper.setBlock(REDSTONE.south(), Blocks.REDSTONE_BLOCK);
                })
                .thenIdle(8)
                .thenExecute(() -> {
                    helper.assertTrue(fluid(helper, OUTPUT, Direction.SOUTH).getFluidInTank(0).getAmount() == productBeforePause[0]
                                    && pool.decayRate() > 0,
                            "Redstone stops water processing while decay continues");
                    helper.setBlock(REDSTONE.south(), Blocks.AIR);
                })
                .thenWaitUntil(() -> helper.assertTrue(fluid(helper, OUTPUT, Direction.SOUTH).getFluidInTank(0).getAmount()
                        > productBeforePause[0], "Cooling resumes when redstone is removed"))
                .thenSucceed();
    }

    @GameTest(template = "empty")
    public static void retainsBlockedDecayAndInvalidatesPortsWhenTheShellBreaks(GameTestHelper helper) {
        build(helper);
        var pool = (DecayPool) helper.getBlockEntity(CONTROLLER);
        CompoundTag[] saved = new CompoundTag[1];
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(pool.assembled(), "Pool assembles"))
                .thenExecute(() -> helper.assertTrue(item(helper, RESERVOIR, Direction.WEST)
                        .insertItem(0, new ItemStack(Items.CLAY_BALL, 2), false).isEmpty(), "Two batches fit in the pool"))
                .thenIdle(12)
                .thenExecute(() -> {
                    var handle = helper.getBlockEntity(PRODUCTS).getCapability(ForgeCapabilities.ITEM_HANDLER, Direction.EAST);
                    var port = handle.orElseThrow(AssertionError::new);
                    helper.assertTrue(port.getStackInSlot(0).getCount() == 2 && port.getStackInSlot(0).is(Items.BRICK),
                            "A full product reservoir leaves the second decay pending");
                    helper.assertTrue(port.extractItem(0, 2, true).getCount() == 2 && port.getStackInSlot(0).getCount() == 2,
                            "Simulated item extraction preserves the decay product");
                    saved[0] = pool.saveWithoutMetadata();
                    helper.setBlock(CORNER, Blocks.AIR);
                    helper.assertTrue(!pool.assembled() && !handle.isPresent() && port.extractItem(0, 1, false).isEmpty(),
                            "Breaking casing invalidates old handles immediately");
                    pool.load(saved[0]);
                    helper.setBlock(CORNER, NCMachines.block(Part.FRAME));
                })
                .thenWaitUntil(() -> helper.assertTrue(pool.assembled(), "The shell reforms"))
                .thenExecute(() -> helper.assertTrue(item(helper, PRODUCTS, Direction.EAST).extractItem(0, 2, false).getCount() == 2,
                        "Reassembly preserves the first product batch"))
                .thenWaitUntil(() -> helper.assertTrue(item(helper, PRODUCTS, Direction.EAST).getStackInSlot(0).getCount() == 2,
                        "The saved pending progress generates the second product batch"))
                .thenExecute(() -> helper.assertTrue(item(helper, PRODUCTS, Direction.EAST).extractItem(0, 3, false).getCount() == 2,
                        "Two inputs gave exactly two batches"))
                .thenIdle(8)
                .thenExecute(() -> helper.assertTrue(item(helper, PRODUCTS, Direction.EAST).getStackInSlot(0).isEmpty(),
                        "Both input units have been consumed"))
                .thenSucceed();
    }

    @GameTest(template = "empty")
    public static void drainsTypedDecayProductsFromAFluidReservoir(GameTestHelper helper) {
        build(helper);
        var pool = (DecayPool) helper.getBlockEntity(CONTROLLER);
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(pool.assembled(), "Pool assembles"))
                .thenExecute(() -> helper.assertTrue(fluid(helper, RESERVOIR, Direction.WEST)
                        .fill(new FluidStack(Fluids.LAVA, 48), EXECUTE) == 48, "A measured molten source fits"))
                .thenWaitUntil(() -> helper.assertTrue(fluid(helper, PRODUCTS, Direction.EAST).getFluidInTank(0).getAmount() == 48,
                        "A fluid recipe produces 48 mB"))
                .thenExecute(() -> {
                    var port = fluid(helper, PRODUCTS, Direction.EAST);
                    var product = port.getFluidInTank(0);
                    helper.assertTrue(product.getFluid() == ForgeRegistries.FLUIDS.getValue(NuclearCraft.id("gadolinium"))
                                    && port.drain(new FluidStack(Fluids.WATER, 48), EXECUTE).isEmpty()
                                    && port.drain(product, SIMULATE).getAmount() == 48
                                    && port.drain(product, EXECUTE).getAmount() == 48 && port.getFluidInTank(0).isEmpty(),
                            "Typed simulation and extraction preserve the exact fluid mass");
                })
                .thenSucceed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void reservoirKeepsCountedSourceChoicesAndEstablishesProducts(GameTestHelper helper) {
        build(helper);
        for (BlockPos pos : BlockPos.betweenClosed(new BlockPos(2, 2, 2), new BlockPos(3, 3, 3))) {
            helper.setBlock(pos, NCMachines.block(Part.CONTAINER));
        }
        var pool = (DecayPool) helper.getBlockEntity(CONTROLLER);
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(pool.assembled(), "Pool assembles"))
                .thenExecute(() -> {
                    var inlet = item(helper, RESERVOIR, Direction.WEST);
                    var fluids = fluid(helper, RESERVOIR, Direction.WEST);
                    helper.assertTrue(inlet.insertItem(0, new ItemStack(Items.RED_DYE, 2), false).isEmpty()
                                    && fluids.fill(stack("benzene", 10), EXECUTE) == 10,
                            "Both precursor types accept their partial doses");
                })
                .thenExecuteAfter(3, () -> {
                    var products = fluid(helper, PRODUCTS, Direction.EAST);
                    var feeds = pool.saveWithoutMetadata().getCompound("reservoir");
                    helper.assertTrue(products.getFluidInTank(0).isEmpty()
                                    && feeds.getList("item_inputs", Tag.TAG_COMPOUND).getCompound(0).getInt("amount") == 2
                                    && fluid(helper, RESERVOIR, Direction.WEST).getFluidInTank(0).getAmount() == 10,
                            "The reservoir assigns the first matching 3-item and 18 mB selectors even when short");
                    item(helper, RESERVOIR, Direction.WEST).insertItem(0, new ItemStack(Items.RED_DYE), false);
                    fluid(helper, RESERVOIR, Direction.WEST).fill(stack("benzene", 8), EXECUTE);
                })
                .thenWaitUntil(() -> helper.assertTrue(fluid(helper, PRODUCTS, Direction.EAST).getFluidInTank(0)
                                .isFluidStackIdentical(stack("heavy_water", 8))
                                && item(helper, PRODUCTS, Direction.EAST).getStackInSlot(0).isEmpty(),
                        "A positive fluid roll adds its maximum and the zero item roll adds nothing"))
                .thenExecute(() -> {
                    item(helper, RESERVOIR, Direction.WEST).insertItem(0, new ItemStack(Items.BLUE_DYE, 2), false);
                    fluid(helper, RESERVOIR, Direction.WEST).fill(stack("methanol", 10), EXECUTE);
                })
                .thenWaitUntil(() -> helper.assertTrue(fluid(helper, PRODUCTS, Direction.EAST).getFluidInTank(0)
                                .isFluidStackIdentical(stack("heavy_water", 16)),
                        "Distinct alternatives use their own two-item and 10 mB doses"))
                .thenExecute(() -> item(helper, RESERVOIR, Direction.WEST)
                        .insertItem(0, new ItemStack(Items.MAGENTA_DYE), false))
                .thenWaitUntil(() -> {
                    var items = item(helper, PRODUCTS, Direction.EAST).getStackInSlot(0);
                    helper.assertTrue(items.is(Items.REDSTONE) && items.getCount() == 2
                                    && fluid(helper, PRODUCTS, Direction.EAST).getFluidInTank(0).getAmount() == 16,
                            "A positive item roll adds the maximum and a zero fluid roll leaves the existing fluid");
                })
                .thenSucceed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void emptyDecayProductsCompleteAtFullCapacity(GameTestHelper helper) {
        build(helper);
        var pool = (DecayPool) helper.getBlockEntity(CONTROLLER);
        var saved = pool.saveWithoutMetadata();
        var products = new ListTag();
        products.add(stack("heavy_water", 288).writeToNBT(new CompoundTag()));
        saved.getCompound("reservoir").put("fluid_outputs", products);
        pool.load(saved);
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(pool.assembled(), "Pool assembles"))
                .thenExecute(() -> helper.assertTrue(item(helper, RESERVOIR, Direction.WEST)
                        .insertItem(0, new ItemStack(Items.TARGET), false).isEmpty(), "The reservoir accepts a precursor with empty products"))
                .thenWaitUntil(() -> helper.assertTrue(pool.saveWithoutMetadata().getCompound("reservoir")
                                .getList("item_inputs", Tag.TAG_COMPOUND).isEmpty()
                                && fluid(helper, PRODUCTS, Direction.EAST).getFluidInTank(0)
                                .isFluidStackIdentical(stack("heavy_water", 288)),
                        "The empty-product decay completes even with a full product reservoir"))
                .thenSucceed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void poolHeatsEachFluidAlternativeAtItsOwnCost(GameTestHelper helper) {
        build(helper);
        var pool = (DecayPool) helper.getBlockEntity(CONTROLLER);
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(pool.assembled(), "Pool assembles"))
                .thenExecute(() -> {
                    helper.assertTrue(item(helper, RESERVOIR, Direction.WEST)
                                    .insertItem(0, new ItemStack(Items.SPONGE), false).isEmpty()
                                    && fluid(helper, FEED, Direction.NORTH).fill(stack("chlorine", 28), EXECUTE) == 28,
                            "Supply one stable source and both heating doses");
                })
                .thenWaitUntil(() -> helper.assertTrue(pool.saveWithoutMetadata().getDouble("process_heat") > 0D,
                        "Wait for the first 30 units of heat"))
                .thenExecute(() -> helper.assertTrue(pool.saveWithoutMetadata().getDouble("process_heat") == 30D
                                && fluid(helper, FEED, Direction.NORTH).getFluidInTank(0).getAmount() == 28,
                        "The preferred 18 mB dose holds until its 36-unit cost is met"))
                .thenWaitUntil(() -> helper.assertTrue(fluid(helper, OUTPUT, Direction.SOUTH).getFluidInTank(0).getAmount() == 6
                                && fluid(helper, FEED, Direction.NORTH).getFluidInTank(0).isEmpty(),
                        "The 36-unit and 20-unit doses complete once 60 units are available"))
                .thenExecute(() -> {
                    helper.assertTrue(pool.saveWithoutMetadata().getDouble("process_heat") == 4D,
                            "The pool retains exactly four units of unused heat");
                    helper.assertTrue(fluid(helper, FEED, Direction.NORTH).fill(stack("boric_acid", 10), EXECUTE) == 10,
                            "An empty product recipe still accepts its feed");
                })
                .thenWaitUntil(() -> helper.assertTrue(fluid(helper, FEED, Direction.NORTH).getFluidInTank(0).isEmpty()
                                && fluid(helper, OUTPUT, Direction.SOUTH).getFluidInTank(0).getAmount() == 6,
                        "The empty heating product completes and keeps the condensate"))
                .thenSucceed();
    }

    private static FluidStack stack(String id, int amount) {
        return new FluidStack(ForgeRegistries.FLUIDS.getValue(NuclearCraft.id(id)), amount);
    }

    private static void build(GameTestHelper helper) {
        for (BlockPos pos : BlockPos.betweenClosed(CORNER, new BlockPos(4, 4, 4))) {
            if (pos.getX() == 1 || pos.getX() == 4 || pos.getY() == 1 || pos.getY() == 4
                    || pos.getZ() == 1 || pos.getZ() == 4) helper.setBlock(pos, NCMachines.block(Part.FRAME));
        }
        helper.setBlock(CONTROLLER, NCMachines.block(Part.DECAY_POOL));
        helper.setBlock(RESERVOIR, NCMachines.block(Part.RESERVOIR));
        helper.setBlock(PRODUCTS, NCMachines.block(Part.RESERVOIR).defaultBlockState().setValue(MachinePartBlock.MODE, Mode.ITEM_OUT));
        helper.setBlock(FEED, NCMachines.block(Part.PROCESS).defaultBlockState().setValue(MachinePartBlock.MODE, Mode.FLUID_IN));
        helper.setBlock(OUTPUT, NCMachines.block(Part.PROCESS).defaultBlockState().setValue(MachinePartBlock.MODE, Mode.FLUID_OUT));
        helper.setBlock(REDSTONE, NCMachines.block(Part.REDSTONE));
        helper.setBlock(new BlockPos(2, 2, 2), NCMachines.block(Part.CONTAINER));
        helper.setBlock(new BlockPos(2, 2, 3), NCMachines.block(Part.CONTAINER));
    }

    private static IItemHandler item(GameTestHelper helper, BlockPos pos, Direction side) {
        return helper.getBlockEntity(pos).getCapability(ForgeCapabilities.ITEM_HANDLER, side).orElseThrow(AssertionError::new);
    }

    private static IFluidHandler fluid(GameTestHelper helper, BlockPos pos, Direction side) {
        return helper.getBlockEntity(pos).getCapability(ForgeCapabilities.FLUID_HANDLER, side).orElseThrow(AssertionError::new);
    }
}
