package nc.multiblock.fission;

import nc.init.NCFission;
import nc.init.NCGasCoolants;
import nc.init.NCPebbleFission;
import org.jetbrains.annotations.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
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

public final class FissionWallBlock extends FissionPartBlock implements EntityBlock {
    public static final DirectionProperty FACING = BlockStateProperties.FACING;
    public static final BooleanProperty ACTIVE = BooleanProperty.create("active");

    private final @Nullable NCGasCoolants coolant;

    public FissionWallBlock(Properties properties, Role role) {
        this(properties, role, null);
    }

    public FissionWallBlock(Properties properties, Role role, @Nullable NCGasCoolants coolant) {
        super(properties, role);
        this.coolant = coolant;
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(ACTIVE, false));
    }

    @Nullable NCGasCoolants coolant() {
        return coolant;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return switch (role(state)) {
            case CONTROLLER, PEBBLE_CONTROLLER -> new SolidFissionController(pos, state);
            case VENT, FUEL_PORT, CHAMBER_PORT, COOLER_PORT -> new FissionPortBlockEntity(pos, state);
            default -> throw new IllegalStateException("Unexpected reactor wall block " + state);
        };
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level instanceof ServerLevel && (type == NCFission.CONTROLLER_ENTITY.get() || type == NCPebbleFission.CONTROLLER_ENTITY.get())) {
            return (world, pos, blockState, entity) -> ((SolidFissionController) entity).tick((ServerLevel) world);
        }
        return null;
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
            for (int slot = 0; slot < controller.inventory().getSlots(); slot++) {
                Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(),
                        controller.inventory().getStackInSlot(slot));
            }
        }
        super.onRemove(state, level, pos, replacement, moved);
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
                                 BlockHitResult hit) {
        if (hand != InteractionHand.MAIN_HAND) {
            return InteractionResult.PASS;
        }

        if (level instanceof ServerLevel server) {
            BlockEntity entity = level.getBlockEntity(pos);
            if (entity instanceof FissionPortBlockEntity && player.isShiftKeyDown()) {
                boolean output = !state.getValue(ACTIVE);
                level.setBlock(pos, state.setValue(ACTIVE, output), Block.UPDATE_ALL);
                geometryChanged(server, pos);
                player.displayClientMessage(Component.translatable(output
                        ? "message.nuclearcraft.fission.port_output" : "message.nuclearcraft.fission.port_input"), true);
            } else if (entity instanceof SolidFissionController controller) {
                if (controller.assembled()) {
                    player.openMenu(controller);
                } else {
                    player.displayClientMessage(controller.validationMessage(), true);
                }
            } else {
                return InteractionResult.PASS;
            }
        } else if (role(state) != Role.CONTROLLER && role(state) != Role.PEBBLE_CONTROLLER && !player.isShiftKeyDown()) {
            return InteractionResult.PASS;
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
