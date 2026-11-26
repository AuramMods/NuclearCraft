package nc.gametest;

import com.mojang.authlib.GameProfile;
import nc.NuclearCraft;
import nc.init.NCFission;
import nc.init.NCHeatExchangers;
import nc.init.NCMachines;
import nc.init.NCQuantum;
import nc.multiblock.AssemblyDiagnostic;
import nc.multiblock.fission.SolidFissionController;
import nc.multiblock.hx.ExchangerController;
import nc.multiblock.machine.MachinePartBlock.Part;
import nc.multiblock.quantum.QuantumPart;
import nc.multiblock.turbine.TurbineController;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.UUID;

@GameTestHolder(NuclearCraft.MOD_ID)
@PrefixGameTestTemplate(false)
public final class AssemblyDiagnosticGameTests {
    private static final BlockPos CORNER = new BlockPos(1, 1, 1);

    @GameTest(template = "fission")
    public static void reactorReportsOnlyItsConnectedShell(GameTestHelper helper) {
        SolidFissionGameTests.buildReactor(helper);
        BlockPos controllerPos = new BlockPos(1, 3, 3);
        var controller = (SolidFissionController) helper.getBlockEntity(controllerPos);
        var player = new Inspector(helper.getLevel());
        BlockPos missing = new BlockPos(5, 1, 5);
        BlockPos detached = new BlockPos(3, 7, 3);
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(controller.assembled(), "Expected assembled reactor"))
                .thenExecute(() -> {
                    helper.assertTrue(player.use(CORNER, helper) == InteractionResult.PASS && player.message == null,
                            "A healthy shell has no validation issue");
                    helper.setBlock(missing, Blocks.AIR);
                    helper.assertTrue(player.use(CORNER, helper).consumesAction(), "Broken casing offers a diagnosis");
                    requirePosition(helper, player, "message.nuclearcraft.fission.frame", missing);
                    player.use(controllerPos, helper);
                    requirePosition(helper, player, "message.nuclearcraft.fission.frame", missing);
                    helper.setBlock(detached, NCFission.CASING.get());
                    player.use(detached, helper);
                    helper.assertTrue(Component.translatable(AssemblyDiagnostic.NO_CONTROLLER).equals(player.message),
                            "A separate casing reports its own missing controller");
                    player.setItemInHand(InteractionHand.OFF_HAND, Items.BUCKET.getDefaultInstance());
                    helper.assertTrue(player.use(CORNER, helper) == InteractionResult.PASS && player.message == null,
                            "Inspection leaves an offhand bucket action available");
                    player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
                    helper.setBlock(missing, NCFission.CASING.get());
                })
                .thenWaitUntil(() -> helper.assertTrue(controller.assembled(), "Expected repaired reactor"))
                .thenExecute(() -> helper.assertTrue(player.use(CORNER, helper) == InteractionResult.PASS && player.message == null,
                        "The repaired shell clears the earlier diagnosis"))
                .thenSucceed();
    }

    @GameTest(template = "turbine")
    public static void turbinePartsReportMissingShaft(GameTestHelper helper) {
        TurbineGameTests.buildTurbine(helper, BlockPos.ZERO);
        var controller = (TurbineController) helper.getBlockEntity(new BlockPos(1, 3, 2));
        BlockPos shaft = new BlockPos(3, 3, 2);
        var player = new Inspector(helper.getLevel());
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(controller.assembled(), "Expected assembled turbine"))
                .thenExecute(() -> {
                    helper.setBlock(shaft, Blocks.AIR);
                    player.use(CORNER, helper);
                    requirePosition(helper, player, "message.nuclearcraft.turbine.shaft", shaft);
                })
                .thenSucceed();
    }

    @GameTest(template = "turbine")
    public static void condenserPartsReportMissingCasing(GameTestHelper helper) {
        CondenserGameTests.build(helper);
        var controller = (ExchangerController) helper.getBlockEntity(new BlockPos(2, 2, 1));
        BlockPos missing = new BlockPos(5, 1, 4);
        BlockPos extension = new BlockPos(6, 1, 1);
        var player = new Inspector(helper.getLevel());
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(controller.computerBounds() != null, "Expected assembled condenser"))
                .thenExecute(() -> {
                    helper.setBlock(extension, NCHeatExchangers.CASING.get());
                    helper.assertTrue(player.use(CORNER, helper).consumesAction() && player.message != null
                                    && controller.computerBounds() == null,
                            "A newly attached invalid casing must invalidate the previous shell and report the problem");
                    helper.setBlock(extension, Blocks.AIR);
                })
                .thenWaitUntil(() -> helper.assertTrue(controller.computerBounds() != null, "Expected repaired condenser"))
                .thenExecute(() -> {
                    helper.setBlock(missing, Blocks.AIR);
                    player.use(CORNER, helper);
                    requirePosition(helper, player, "message.nuclearcraft.exchanger.wall", missing);
                })
                .thenSucceed();
    }

    @GameTest(template = "fission")
    public static void chemicalMachineReportsMisplacedInteriorPart(GameTestHelper helper) {
        for (var pos : BlockPos.betweenClosed(CORNER, new BlockPos(4, 4, 4))) {
            if (pos.getX() == 1 || pos.getX() == 4 || pos.getY() == 1 || pos.getY() == 4
                    || pos.getZ() == 1 || pos.getZ() == 4) {
                helper.setBlock(pos, NCMachines.block(Part.FRAME));
            }
        }
        helper.setBlock(new BlockPos(1, 2, 2), NCMachines.block(Part.DECAY_POOL));
        BlockPos invalid = new BlockPos(3, 2, 2);
        helper.setBlock(invalid, NCMachines.block(Part.PRESSURE_CHAMBER));
        var player = new Inspector(helper.getLevel());
        player.use(CORNER, helper);
        requirePosition(helper, player, "message.nuclearcraft.decay_pool.interior", invalid);
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void quantumSeparatesMissingAndDuplicateControllers(GameTestHelper helper) {
        var player = new Inspector(helper.getLevel());
        helper.setBlock(CORNER, NCQuantum.block(QuantumPart.CONNECTOR));
        helper.startSequence()
                .thenWaitUntil(() -> {
                    player.use(CORNER, helper);
                    helper.assertTrue(Component.translatable(AssemblyDiagnostic.NO_CONTROLLER).equals(player.message),
                            "An isolated quantum connector needs a controller, got " + player.message);
                })
                .thenExecute(() -> {
                    helper.setBlock(CORNER.above(), NCQuantum.block(QuantumPart.CONTROLLER));
                    helper.setBlock(CORNER.east(), NCQuantum.block(QuantumPart.CONTROLLER));
                })
                .thenWaitUntil(() -> {
                    player.use(CORNER, helper);
                    helper.assertTrue(Component.translatable("message.nuclearcraft.quantum.controllers").equals(player.message),
                            "Two connected controllers report a conflicting controller count, got " + player.message);
                })
                .thenSucceed();
    }

    private static void requirePosition(GameTestHelper helper, Inspector player, String key, BlockPos relative) {
        BlockPos pos = helper.absolutePos(relative);
        helper.assertTrue(player.message != null && player.message.getContents() instanceof TranslatableContents contents
                        && contents.getKey().equals(key) && player.message.equals(
                                Component.translatable(key, pos.getX(), pos.getY(), pos.getZ())),
                "Expected " + key + " at " + pos + ", received " + player.message);
    }

    private static final class Inspector extends FakePlayer {
        private Component message;

        private Inspector(ServerLevel level) {
            super(level, new GameProfile(UUID.randomUUID(), "NCInspector"));
        }

        private InteractionResult use(BlockPos relative, GameTestHelper helper) {
            message = null;
            BlockPos pos = helper.absolutePos(relative);
            BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(pos), Direction.NORTH, pos, false);
            return helper.getLevel().getBlockState(pos).use(helper.getLevel(), this, InteractionHand.MAIN_HAND, hit);
        }

        @Override
        public void displayClientMessage(Component message, boolean actionBar) {
            this.message = message;
        }
    }
}
