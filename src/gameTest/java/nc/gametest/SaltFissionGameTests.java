package nc.gametest;

import com.mojang.authlib.GameProfile;
import nc.NuclearCraft;
import nc.init.NCFission;
import nc.init.NCSaltCoolants;
import nc.init.NCSaltFission;
import nc.multiblock.fission.FissionWallBlock;
import nc.multiblock.fission.SaltFissionPartMenu;
import nc.multiblock.fission.SolidFissionController;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.OptionalInt;
import java.util.UUID;

import static net.minecraftforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE;
import static net.minecraftforge.fluids.capability.IFluidHandler.FluidAction.SIMULATE;

@GameTestHolder(NuclearCraft.MOD_ID)
@PrefixGameTestTemplate(false)
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

    @GameTest(template = "empty")
    public static void saltFissionLoadsTheLegacyFluidFuelsAndHeaters(GameTestHelper helper) {
        var manager = helper.getLevel().getRecipeManager();
        var recipes = manager.getAllRecipesFor(NCSaltFission.FUEL_TYPE.get());
        helper.assertTrue(recipes.size() == 27, "All 27 legacy molten-salt fuels must load");
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

    @GameTest(template = "empty", timeoutTicks = 270)
    public static void saltFissionResumesAfterFullOutputAndReassembly(GameTestHelper helper) {
        shell(helper);
        addVessel(helper, FIRST_VESSEL, Direction.EAST, FUEL, FUEL_IN, FUEL_OUT);
        var controller = (SolidFissionController) helper.getBlockEntity(CONTROLLER);
        CompoundTag state = controller.saveWithoutMetadata();
        ListTag fuel = new ListTag();
        CompoundTag circuit = new CompoundTag();
        circuit.putString("fuel_filter", NuclearCraft.id(FUEL).toString());
        circuit.put("output", stack(DEPLETED, 72_000).writeToNBT(new CompoundTag()));
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
                helper.assertTrue(depleted.isFluidStackIdentical(stack(DEPLETED, 72_000)) && input.getFluidInTank(0).isEmpty()
                                && hot.isFluidEqual(stack("nak_hot", 1)) && hot.getAmount() + cold.getAmount() == 300,
                        "All 12 reserved-and-fed fuel doses finish once and the heater conserves coolant across reload");
            });
        });
    }

    @GameTest(template = "empty", timeoutTicks = 200)
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
            helper.succeedWhen(() -> {
                var first = fluids(helper, FUEL_OUT, Direction.SOUTH).getFluidInTank(0);
                var second = fluids(helper, SECOND_OUT, Direction.SOUTH).getFluidInTank(0);
                helper.assertTrue(first.isFluidStackIdentical(stack(DEPLETED, 6))
                                && second.isFluidStackIdentical(stack("depleted_" + secondFuel, 4)),
                        "Each vessel discharges its own fuel products through the matching outlet");
            });
        });
    }

    @GameTest(template = "empty", timeoutTicks = 70)
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

    @GameTest(template = "empty", timeoutTicks = 95)
    public static void saltInspectionChecksDistanceAndDisassemblyBeforeChangingTanks(GameTestHelper helper) {
        shell(helper);
        addVessel(helper, FIRST_VESSEL, Direction.EAST, FUEL, FUEL_IN, FUEL_OUT);
        helper.runAtTickTime(15, () -> {
            var player = new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), "SaltInspector")) {
                @Override
                public OptionalInt openMenu(MenuProvider provider) {
                    containerMenu = provider.createMenu(1, getInventory(), this);
                    return OptionalInt.of(1);
                }
            };
            BlockPos absolutePort = helper.absolutePos(FUEL_IN);
            player.moveTo(absolutePort.getX() + 0.5, absolutePort.getY(), absolutePort.getZ() - 1.0);
            var initial = fluids(helper, FUEL_IN, Direction.NORTH);
            helper.assertTrue(initial.fill(stack(FUEL, 50_001), EXECUTE) == 50_001,
                    "Seed more than a signed short can represent through the input port");
            helper.useBlock(FUEL_IN, player);
            helper.assertTrue(player.containerMenu instanceof SaltFissionPartMenu, "Use the port to open the real inspection menu");
            var menu = (SaltFissionPartMenu) player.containerMenu;
            helper.assertTrue(menu.kind() == SaltFissionPartMenu.Kind.VESSEL_PORT && menu.connected()
                            && menu.fluid(false).getAmount() == 50_001 && menu.capacity(false) == 72_000,
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
