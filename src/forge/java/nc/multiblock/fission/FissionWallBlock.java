package nc.multiblock.fission;

import nc.init.NCAdvancements;
import nc.init.NCFission;
import nc.init.NCHeatExchangers;
import org.jetbrains.annotations.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidUtil;

public final class FissionWallBlock extends FissionPartBlock implements EntityBlock {
    public static final DirectionProperty FACING = BlockStateProperties.FACING;
    public static final BooleanProperty ACTIVE = BooleanProperty.create("active");

    private final @Nullable PebbleCoolant coolant;
    private final @Nullable FissionCoolant saltCoolant;

    public FissionWallBlock(Properties properties, Role role) {
        this(properties, role, null);
    }

    public FissionWallBlock(Properties properties, Role role, @Nullable PebbleCoolant coolant) {
        this(properties, role, coolant, null);
    }

    public static FissionWallBlock heaterPort(Properties properties, FissionCoolant coolant) {
        return new FissionWallBlock(properties, Role.HEATER_PORT, null, coolant);
    }

    private FissionWallBlock(Properties properties, Role role, @Nullable PebbleCoolant coolant,
                             @Nullable FissionCoolant saltCoolant) {
        super(properties, role);
        this.coolant = coolant;
        this.saltCoolant = saltCoolant;
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(ACTIVE, false));
    }

    public @Nullable PebbleCoolant coolant() {
        return coolant;
    }

    public @Nullable FissionCoolant saltCoolant() {
        return saltCoolant;
    }

    private static boolean isController(Role role) {
        return role == Role.CONTROLLER || role == Role.PEBBLE_CONTROLLER || role == Role.SALT_CONTROLLER;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return switch (role(state)) {
            case CONTROLLER, PEBBLE_CONTROLLER, SALT_CONTROLLER -> new SolidFissionController(pos, state);
            case MONITOR -> new FissionMonitor(pos, state);
            case VENT, FUEL_PORT, CHAMBER_PORT, COOLER_PORT, VESSEL_PORT, HEATER_PORT,
                    IRRADIATOR_PORT, POWER_PORT -> new FissionPortBlockEntity(pos, state);
            default -> throw new IllegalStateException("Unexpected reactor wall block " + state);
        };
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        Role role = role(state);
        if (level instanceof ServerLevel && role == Role.IRRADIATOR_PORT && type == NCFission.IRRADIATOR_PORT_ENTITY.get()) {
            return (world, pos, blockState, entity) -> ((FissionPortBlockEntity) entity).exportIrradiation((ServerLevel) world);
        }
        if (level instanceof ServerLevel && (role == Role.FUEL_PORT || role == Role.CHAMBER_PORT || role == Role.COOLER_PORT
                || role == Role.HEATER_PORT || role == Role.VESSEL_PORT) && type == NCFission.PORT_ENTITY.get()) {
            return (world, pos, blockState, entity) -> ((FissionPortBlockEntity) entity).exportFuelOrCoolant((ServerLevel) world);
        }
        if (level instanceof ServerLevel && isController(role) && type == FissionVariant.forController(role).entity()) {
            return (world, pos, blockState, entity) -> ((SolidFissionController) entity).tick((ServerLevel) world);
        }
        return null;
    }

    @Override
    public boolean canConnectRedstone(BlockState state, BlockGetter level, BlockPos pos, @Nullable Direction side) {
        return side != null && isController(role(state));
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getNearestLookingDirection().getOpposite());
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, ACTIVE);
    }

    @Override
    public BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    public BlockState mirror(BlockState state, Mirror mirror) {
        return rotate(state, mirror.getRotation(state.getValue(FACING)));
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState replacement, boolean moved) {
        if (!level.isClientSide && !state.is(replacement.getBlock())
                && level.getBlockEntity(pos) instanceof SolidFissionController controller) {
            controller.dropFuelItems();
        }
        if (!level.isClientSide && (role(state) == Role.FUEL_PORT || role(state) == Role.CHAMBER_PORT)
                && !state.is(replacement.getBlock()) && level.getBlockEntity(pos) instanceof FissionPortBlockEntity port) {
            port.localFuelItems().dropContents();
        }
        if (!level.isClientSide && role(state) == Role.IRRADIATOR_PORT && !state.is(replacement.getBlock())
                && level.getBlockEntity(pos) instanceof FissionPortBlockEntity port) {
            port.localIrradiatorItems().dropContents(level);
        }
        super.onRemove(state, level, pos, replacement, moved);
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
                                 BlockHitResult hit) {
        if (hand != InteractionHand.MAIN_HAND) {
            return InteractionResult.PASS;
        }

        Role role = role(state);
        boolean sneaking = player.isShiftKeyDown();
        boolean multitool = player.getItemInHand(hand).is(NCHeatExchangers.MULTITOOL.get());
        if (role == Role.MONITOR && (sneaking || multitool)) {
            return InteractionResult.PASS;
        }
        if (level instanceof ServerLevel server) {
            BlockEntity entity = level.getBlockEntity(pos);
            if (entity instanceof FissionMonitor monitor && player instanceof ServerPlayer user) {
                FissionMultitool.inspectCluster(user, monitor);
            } else if (entity instanceof FissionPortBlockEntity port) {
                if (!usePort(server, state, pos, player, hand, hit, port, role, multitool, sneaking)) {
                    return super.use(state, level, pos, player, hand, hit);
                }
            } else if (entity instanceof SolidFissionController controller) {
                var diagnostic = controller.diagnostic(server);
                if (diagnostic == null) {
                    NCAdvancements.inspectAssembly(player, hand, state);
                    player.openMenu(controller);
                } else {
                    diagnostic.show(player, true);
                }
            } else {
                return super.use(state, level, pos, player, hand, hit);
            }
        } else if (!isController(role) && role != Role.MONITOR && role != Role.IRRADIATOR_PORT
                && role != Role.VESSEL_PORT && role != Role.HEATER_PORT
                && role != Role.FUEL_PORT && role != Role.CHAMBER_PORT && role != Role.COOLER_PORT && !sneaking && !multitool) {
            return InteractionResult.PASS;
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    private static boolean usePort(ServerLevel level, BlockState state, BlockPos pos, Player player,
                                   InteractionHand hand, BlockHitResult hit, FissionPortBlockEntity port,
                                   Role role, boolean multitool, boolean sneaking) {
        var held = player.getItemInHand(hand);
        if ((multitool && !sneaking && (role == Role.POWER_PORT || !port.assembled()))
                || (sneaking && held.isEmpty())) {
            togglePort(level, state, pos, player);
            return true;
        }
        if (role == Role.IRRADIATOR_PORT) {
            FissionIrradiatorMenu.interact(level, player, hand, port);
            return true;
        }
        if (sneaking) {
            if (role == Role.VESSEL_PORT) {
                return SaltVesselBlock.selectFilter(player, hand, level, port::selectVesselFilter).consumesAction();
            }
            if (role == Role.HEATER_PORT || role == Role.POWER_PORT) {
                return false;
            }
            togglePort(level, state, pos, player);
            return true;
        }
        if (role == Role.FUEL_PORT || role == Role.CHAMBER_PORT) {
            FissionPartMenu.interactFuel(player, hand, port, role == Role.FUEL_PORT
                    ? FissionPartMenu.Kind.CELL_PORT : FissionPartMenu.Kind.CHAMBER_PORT);
            return true;
        }
        if (role == Role.COOLER_PORT) {
            var sample = FluidUtil.getFluidContained(held).orElse(FluidStack.EMPTY);
            if (!sample.isEmpty() && port.selectGasFilter(sample)) {
                player.displayClientMessage(Component.translatable("gui.nuclearcraft.salt_part.filter", sample.getDisplayName()), true);
            } else if (!FluidUtil.interactWithFluidHandler(player, hand, level, pos, hit.getDirection())) {
                FissionPartMenu.open(player, port, FissionPartMenu.Kind.COOLER_PORT);
            }
            return true;
        }
        if (role == Role.VESSEL_PORT || role == Role.HEATER_PORT) {
            if (!FluidUtil.interactWithFluidHandler(player, hand, level, pos, hit.getDirection())) {
                SaltFissionPartMenu.open(player, level, pos, role == Role.VESSEL_PORT
                        ? SaltFissionPartMenu.Kind.VESSEL_PORT : SaltFissionPartMenu.Kind.HEATER_PORT);
            }
            return true;
        }
        return false;
    }

    private static void togglePort(ServerLevel level, BlockState state, BlockPos pos, Player player) {
        boolean output = !state.getValue(ACTIVE);
        level.setBlock(pos, state.setValue(ACTIVE, output), Block.UPDATE_ALL);
        geometryChanged(level, pos);
        player.displayClientMessage(Component.translatable(output ? "message.nuclearcraft.fission.port_output"
                : "message.nuclearcraft.fission.port_input"), true);
    }

}
