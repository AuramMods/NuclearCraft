package nc.multiblock.fission;

import com.mojang.authlib.GameProfile;
import nc.NuclearCraft;
import nc.init.NCFission;
import nc.init.NCGasCoolants;
import nc.init.NCHeatExchangers;
import nc.init.NCHeatSinks;
import nc.init.NCPebbleFission;
import nc.init.NCSaltCoolants;
import nc.init.NCSaltFission;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler.FluidAction;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegisterEvent;

import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.UUID;

import static nc.multiblock.fission.FissionWallBlock.ACTIVE;
import static nc.multiblock.fission.FissionWallBlock.FACING;

@GameTestHolder(NuclearCraft.MOD_ID)
@PrefixGameTestTemplate(false)
@Mod.EventBusSubscriber(modid = NuclearCraft.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class FissionControlGameTests {
    private static final BlockPos CONTROLLER = new BlockPos(3, 3, 1);
    private static final BlockPos SOURCE = new BlockPos(1, 3, 3);
    private static final BlockPos SECOND_SOURCE = new BlockPos(7, 3, 2);
    private static final BlockPos SOURCE_MANAGER = new BlockPos(2, 3, 1);
    private static final BlockPos OTHER_MANAGER = new BlockPos(5, 3, 1);
    private static final BlockPos SHIELD_MANAGER = new BlockPos(5, 3, 5);
    private static final BlockPos BEAM_SHIELD = new BlockPos(2, 3, 3);
    private static final BlockPos SHIELD = new BlockPos(5, 3, 3);
    private static final BlockPos FUEL = new BlockPos(3, 3, 3);

    private static final BlockPos PRIMARY = FUEL.north();
    private static final BlockPos SECONDARY = PRIMARY.above();

    private FissionControlGameTests() {}

    @SubscribeEvent
    public static void registerScriptedParts(RegisterEvent event) {
        event.register(ForgeRegistries.Keys.BLOCKS, helper -> {
            helper.register(NuclearCraft.id("fission_source_qa"), NCFission.createScriptedNeutronSource(0.73D));
            helper.register(NuclearCraft.id("fission_shield_qa"), NCFission.createScriptedNeutronShield(7D, 0.65D));
            helper.register(NuclearCraft.id("solid_fission_sink_qa_primary"),
                    NCFission.createScriptedHeatSink("qa_primary", 71, "one cell"));
            helper.register(NuclearCraft.id("solid_fission_sink_qa_secondary"),
                    NCFission.createScriptedHeatSink("qa_secondary", 97, "one qa_primary sink && one water sink"));
        });
    }

    @GameTest(template = "fission", timeoutTicks = 150)
    public static void controlledShieldsInterruptPrimingAndModeratorHeat(GameTestHelper helper) {
        controlledShields(helper, false);
    }

    @GameTest(template = "fission", timeoutTicks = 150)
    public static void scriptedPartsUseRealFissionControlsAndCooling(GameTestHelper helper) {
        controlledShields(helper, true);
    }

    @GameTest(template = "fission", timeoutTicks = 100)
    public static void solidCoolingRequiresAnActiveModerator(GameTestHelper helper) {
        coolingRequiresAnActiveModerator(helper, FissionVariant.SOLID, NCFission.CELL.get(),
                NCFission.heatSink(NCHeatSinks.IRON));
    }

    @GameTest(template = "fission", timeoutTicks = 100)
    public static void pebbleCoolingRequiresAnActiveModerator(GameTestHelper helper) {
        coolingRequiresAnActiveModerator(helper, FissionVariant.PEBBLE, NCPebbleFission.CHAMBER.get(),
                NCPebbleFission.cooler(NCGasCoolants.FLUORINE));
    }

    @GameTest(template = "fission", timeoutTicks = 100)
    public static void saltCoolingRequiresAnActiveModerator(GameTestHelper helper) {
        coolingRequiresAnActiveModerator(helper, FissionVariant.SALT, NCSaltFission.VESSEL.get(),
                NCSaltFission.heater(NCSaltCoolants.IRON));
    }

    private static void coolingRequiresAnActiveModerator(GameTestHelper helper, FissionVariant variant,
                                                         Block fuelPart, Block coolingPart) {
        build(helper, variant.controller());
        BlockPos first = FUEL.west();
        BlockPos last = FUEL.east(3);
        helper.setBlock(first, fuelPart);
        helper.setBlock(last, fuelPart);
        helper.setBlock(first.below(), NCFission.CONDUCTOR.get());
        helper.setBlock(last.below(), NCFission.CONDUCTOR.get());
        for (int offset = 1; offset <= 3; offset++) {
            BlockPos moderator = first.east(offset);
            helper.setBlock(moderator, NCFission.HEAVY_WATER_MODERATOR.get());
            BlockPos cooler = moderator.below();
            helper.setBlock(cooler, coolingPart);
            if (helper.getBlockEntity(cooler) instanceof PebbleCooler pebble) {
                pebble.localTanks().tank(false).fill(fluid("fluorine", 1_000), FluidAction.EXECUTE);
            } else if (helper.getBlockEntity(cooler) instanceof SaltHeater salt) {
                salt.localTanks().tank(false).fill(fluid("iron_nak", 1_000), FluidAction.EXECUTE);
            }
        }
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(controller(helper).assembled(), "The reactor must assemble"))
                .thenExecute(() -> {
                    fuel(helper, first, variant);
                    fuel(helper, last, variant);
                    controller(helper).toggle();
                })
                .thenWaitUntil(() -> helper.assertTrue(active(helper, CONTROLLER)
                                && controller(helper).isCooling(helper.absolutePos(first.east().below()))
                                && controller(helper).isCooling(helper.absolutePos(last.west().below())),
                        "The three moderators sustain both fuel parts and activate cooling at both ends"))
                .thenExecute(() -> {
                    helper.assertTrue(!controller(helper).isCooling(helper.absolutePos(first.east(2).below())),
                            "The middle moderator carries flux but does not satisfy a cooling placement rule");
                })
                .thenWaitUntil(() -> {
                    if (variant != FissionVariant.SOLID) {
                        helper.assertTrue(hotCoolant(helper, first.east().below()) > 0
                                        && hotCoolant(helper, last.west().below()) > 0,
                                "Both end coolers must actually heat their fluid");
                        helper.assertTrue(hotCoolant(helper, first.east(2).below()) == 0,
                                "The middle cooler leaves its fluid cold");
                    }
                })
                .thenExecute(() -> {
                    helper.setBlock(last, Blocks.AIR);
                    helper.setBlock(last.west(), NCFission.BERYLLIUM_CARBON_REFLECTOR.get());
                })
                .thenWaitUntil(() -> helper.assertTrue(active(helper, CONTROLLER)
                                && controller(helper).isCooling(helper.absolutePos(first.east().below()))
                                && !controller(helper).isCooling(helper.absolutePos(last.west().below())),
                        "The reflected line sustains fuel and keeps the first moderator active"))
                .thenExecute(() -> helper.assertTrue(!controller(helper).isCooling(helper.absolutePos(first.east(2).below())),
                        "The moderator next to a reflector stays passive"))
                .thenSucceed();
    }

    private static void fuel(GameTestHelper helper, BlockPos pos, FissionVariant variant) {
        if (helper.getBlockEntity(pos) instanceof SaltVessel vessel) {
            vessel.localTanks().tank(false).fill(fluid("lecf_251_fluoride_flibe", 16), FluidAction.EXECUTE);
            return;
        }
        String id = variant == FissionVariant.SOLID ? "lecf_251_oxide" : "lecf_251_triso";
        var item = ForgeRegistries.ITEMS.getValue(NuclearCraft.id(id));
        ((FissionFuelCell) helper.getBlockEntity(pos)).localItems().setStackInSlot(0, new ItemStack(item));
    }

    private static FluidStack fluid(String id, int amount) {
        return new FluidStack(ForgeRegistries.FLUIDS.getValue(NuclearCraft.id(id)), amount);
    }

    private static int hotCoolant(GameTestHelper helper, BlockPos pos) {
        var part = helper.getBlockEntity(pos);
        return part instanceof PebbleCooler cooler ? cooler.localTanks().tank(true).getFluidAmount()
                : ((SaltHeater) part).localTanks().tank(true).getFluidAmount();
    }

    private static void controlledShields(GameTestHelper helper, boolean scripted) {
        build(helper, NCFission.CONTROLLER.get());
        if (scripted) {
            helper.setBlock(SOURCE, testPart("fission_source_qa"));
            helper.setBlock(SHIELD, testPart("fission_shield_qa"));
            helper.setBlock(PRIMARY, testPart("solid_fission_sink_qa_primary"));
            helper.setBlock(SECONDARY, testPart("solid_fission_sink_qa_secondary"));
        }
        helper.setBlock(FUEL, NCFission.CELL.get());
        helper.setBlock(FUEL.above(), NCFission.WATER_SINK.get());
        helper.setBlock(FUEL.east(), NCFission.HEAVY_WATER_MODERATOR.get());
        helper.setBlock(FUEL.east(3), NCFission.BERYLLIUM_CARBON_REFLECTOR.get());
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(controller(helper).assembled(), "The solid reactor must assemble"))
                .thenExecute(() -> {
                    var player = player(helper);
                    select(helper, player, SOURCE_MANAGER, true);
                    select(helper, player, SOURCE, true);
                    select(helper, player, SOURCE_MANAGER, false);
                    select(helper, player, SHIELD_MANAGER, true);
                    select(helper, player, SHIELD_MANAGER, false);
                    helper.assertTrue(control(helper, SOURCE_MANAGER).listeners().equals(Set.of(helper.absolutePos(SOURCE))),
                            "An explicit selection should link only one source");
                    helper.assertTrue(control(helper, SHIELD_MANAGER).listeners().size() == 2,
                            "An empty selection should link both shields in the reactor");
                    helper.setBlock(SOURCE_MANAGER.north(), Blocks.REDSTONE_BLOCK);
                    helper.setBlock(SHIELD_MANAGER.south(), Blocks.REDSTONE_BLOCK);
                    helper.assertTrue(active(helper, SOURCE) && !active(helper, SECOND_SOURCE)
                                    && active(helper, BEAM_SHIELD) && active(helper, SHIELD),
                            "Power should activate only the selected source and close both shields");
                    helper.assertTrue(controller(helper).primingTarget(helper.getLevel(), helper.absolutePos(SOURCE)) == null,
                            "A closed shield should block priming");
                    controller(helper).inventory().setStackInSlot(0, new ItemStack(
                            ForgeRegistries.ITEMS.getValue(NuclearCraft.id("heu_233_za"))));
                    controller(helper).toggle();
                })
                .thenExecuteAfter(4, () -> {
                    helper.assertTrue(progress(helper) == 0, "Closed shields must prevent the reaction");
                    helper.setBlock(SHIELD_MANAGER.south(), Blocks.AIR);
                    helper.assertTrue(controller(helper).primingTarget(helper.getLevel(), helper.absolutePos(SOURCE))
                                    .equals(helper.absolutePos(FUEL)), "An open shield must pass the priming beam");
                })
                .thenWaitUntil(() -> helper.assertTrue(progress(helper) > 0, "The open shield must pass the reflected neutron path"))
                .thenExecute(() -> {
                    var heat = controller(helper).partHeat(helper.absolutePos(SHIELD));
                    int shieldHeat = scripted ? 504 : 360;
                    helper.assertTrue(heat.processing() && heat.amount() > 0 && heat.amount() % shieldHeat == 0,
                            "The shield must collect " + shieldHeat + " heat per tick from reflected heavy water: " + heat);
                    if (scripted) {
                        helper.assertTrue(helper.getBlockEntity(FUEL).saveWithoutMetadata().getDouble("source_efficiency") == 0.73D
                                        && controller(helper).isCooling(helper.absolutePos(PRIMARY))
                                        && controller(helper).isCooling(helper.absolutePos(SECONDARY)),
                                "The scripted source primes the fuel and both dependent sinks become active");
                    }
                    helper.setBlock(SHIELD_MANAGER.south(), Blocks.REDSTONE_BLOCK);
                })
                .thenWaitUntil(() -> helper.assertTrue(!active(helper, CONTROLLER) && active(helper, SHIELD),
                        "Closing the shield must shut down the sustaining path"))
                .thenSucceed();
    }

    @GameTest(template = "fission", timeoutTicks = 120)
    public static void pebbleReactorsReassignAndRestoreManagerLinks(GameTestHelper helper) {
        linksSurviveReassembly(helper, NCPebbleFission.CONTROLLER.get());
    }

    @GameTest(template = "fission", timeoutTicks = 120)
    public static void saltReactorsReassignAndRestoreManagerLinks(GameTestHelper helper) {
        linksSurviveReassembly(helper, NCSaltFission.CONTROLLER.get());
    }

    private static void linksSurviveReassembly(GameTestHelper helper, Block controller) {
        build(helper, controller);
        var toolUser = player(helper);
        select(helper, toolUser, SOURCE_MANAGER, true);
        select(helper, toolUser, SOURCE, true);
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(controller(helper).assembled(), "The reactor must assemble"))
                .thenExecute(() -> {
                    var player = player(helper);
                    helper.assertTrue(helper.getBlockState(SHIELD_MANAGER).getValue(FACING) == Direction.SOUTH,
                            "The manager must face outward after assembly");
                    select(helper, player, SOURCE_MANAGER, false);
                    helper.assertTrue(control(helper, SOURCE_MANAGER).listeners().equals(Set.of(helper.absolutePos(SOURCE))),
                            "Selection before assembly must link the chosen component after assembly");
                    select(helper, player, SOURCE_MANAGER, true);
                    select(helper, player, SOURCE_MANAGER, false);
                    helper.setBlock(SOURCE_MANAGER.north(), Blocks.REDSTONE_BLOCK);
                    helper.assertTrue(active(helper, SOURCE) && active(helper, SECOND_SOURCE),
                            "A bulk link must power both sources");
                    select(helper, player, OTHER_MANAGER, true);
                    select(helper, player, SOURCE, true);
                    select(helper, player, OTHER_MANAGER, false);
                    helper.assertTrue(!active(helper, SOURCE) && active(helper, SECOND_SOURCE)
                                    && control(helper, SOURCE_MANAGER).listeners().size() == 1,
                            "Reassigning a source must release the earlier manager's control");
                    helper.setBlock(SOURCE.west(), Blocks.REDSTONE_BLOCK);
                    helper.assertTrue(active(helper, SOURCE), "Direct source redstone must still work");
                    helper.setBlock(SOURCE.west(), Blocks.AIR);
                    helper.setBlock(OTHER_MANAGER.north(), Blocks.REDSTONE_BLOCK);
                    CompoundTag saved = control(helper, OTHER_MANAGER).saveWithoutMetadata();
                    if (controller == NCSaltFission.CONTROLLER.get()) {
                        BlockPos position = helper.absolutePos(SOURCE);
                        long legacyPos = (long) (position.getX() & 0x3ffffff) << 38
                                | (long) (position.getY() & 0xfff) << 26 | position.getZ() & 0x3ffffff;
                        var legacy = new CompoundTag();
                        legacy.putLong("long0", legacyPos);
                        saved.remove("listeners");
                        saved.put("listenerPosSet", legacy);
                    }
                    helper.setBlock(OTHER_MANAGER, NCFission.CASING.get());
                    helper.setBlock(OTHER_MANAGER, NCFission.SOURCE_MANAGER.get());
                    control(helper, OTHER_MANAGER).load(saved);
                    select(helper, player, SHIELD_MANAGER, true);
                })
                .thenWaitUntil(() -> helper.assertTrue(controller(helper).assembled(), "The reactor must reassemble"))
                .thenExecute(() -> {
                    helper.assertTrue(active(helper, SOURCE) && control(helper, OTHER_MANAGER).listeners()
                                    .equals(Set.of(helper.absolutePos(SOURCE))), "The saved link must resume after reassembly");
                    var player = player(helper);
                    select(helper, player, SHIELD_MANAGER, false);
                    helper.setBlock(SHIELD_MANAGER.south(), Blocks.REDSTONE_BLOCK);
                    helper.assertTrue(active(helper, BEAM_SHIELD) && active(helper, SHIELD),
                            "Shield controls must work in this reactor variant");
                    helper.setBlock(SOURCE, NCFission.CASING.get());
                    helper.setBlock(SOURCE, NCFission.RADIUM_BERYLLIUM_SOURCE.get());
                })
                .thenWaitUntil(() -> helper.assertTrue(controller(helper).assembled(), "Restoring the source must repair the shell"))
                .thenExecute(() -> helper.assertTrue(!active(helper, SOURCE) && control(helper, OTHER_MANAGER).listeners().isEmpty(),
                        "A replacement source must not inherit the removed component's link"))
                .thenSucceed();
    }

    private static void build(GameTestHelper helper, Block controller) {
        for (BlockPos pos : BlockPos.betweenClosed(new BlockPos(1, 1, 1), new BlockPos(7, 5, 5))) {
            if (pos.getX() == 1 || pos.getX() == 7 || pos.getY() == 1 || pos.getY() == 5
                    || pos.getZ() == 1 || pos.getZ() == 5) {
                helper.setBlock(pos, NCFission.CASING.get());
            } else {
                helper.setBlock(pos, Blocks.AIR);
            }
        }
        helper.setBlock(CONTROLLER, controller);
        helper.setBlock(SOURCE, NCFission.RADIUM_BERYLLIUM_SOURCE.get());
        helper.setBlock(SECOND_SOURCE, NCFission.POLONIUM_BERYLLIUM_SOURCE.get());
        helper.setBlock(SOURCE_MANAGER, NCFission.SOURCE_MANAGER.get());
        helper.setBlock(OTHER_MANAGER, NCFission.SOURCE_MANAGER.get());
        helper.setBlock(SHIELD_MANAGER, NCFission.SHIELD_MANAGER.get());
        helper.setBlock(BEAM_SHIELD, NCFission.GADOLINIUM_SAMARIUM_SHIELD.get());
        helper.setBlock(SHIELD, NCFission.BORON_SILVER_SHIELD.get());
    }

    private static Block testPart(String path) {
        return ForgeRegistries.BLOCKS.getValue(NuclearCraft.id(path));
    }

    private static FakePlayer player(GameTestHelper helper) {
        var profile = new GameProfile(UUID.nameUUIDFromBytes(helper.absolutePos(CONTROLLER).toString().getBytes(
                StandardCharsets.UTF_8)), "FissionControls");
        var player = FakePlayerFactory.get(helper.getLevel(), profile);
        if (!player.getMainHandItem().is(NCHeatExchangers.MULTITOOL.get())) {
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(NCHeatExchangers.MULTITOOL.get()));
        }
        return player;
    }

    private static void select(GameTestHelper helper, FakePlayer player, BlockPos pos, boolean sneaking) {
        player.setShiftKeyDown(sneaking);
        var absolute = helper.absolutePos(pos);
        var hit = new BlockHitResult(Vec3.atCenterOf(absolute), Direction.NORTH, absolute, false);
        var tool = player.getMainHandItem();
        helper.assertTrue(tool.getItem().onItemUseFirst(tool, new UseOnContext(player, InteractionHand.MAIN_HAND, hit))
                .consumesAction(), "The multitool should handle the fission part");
    }

    private static SolidFissionController controller(GameTestHelper helper) {
        return (SolidFissionController) helper.getBlockEntity(CONTROLLER);
    }

    private static FissionControlBlockEntity control(GameTestHelper helper, BlockPos pos) {
        return (FissionControlBlockEntity) helper.getBlockEntity(pos);
    }

    private static boolean active(GameTestHelper helper, BlockPos pos) {
        return helper.getBlockState(pos).getValue(ACTIVE);
    }

    private static double progress(GameTestHelper helper) {
        return helper.getBlockEntity(FUEL).saveWithoutMetadata().getDouble("progress");
    }
}
