package nc.processor;

import net.minecraft.world.MenuProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
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

import net.minecraftforge.fluids.FluidUtil;
import net.minecraftforge.items.ItemStackHandler;

import java.util.function.Function;
import java.util.function.Supplier;

public class ProcessorBlock<E extends BlockEntity> extends BaseEntityBlock {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final BooleanProperty LIT = BlockStateProperties.LIT;

    private final Supplier<BlockEntityType<E>> entityType;
    private final BlockEntityTicker<E> ticker;
    private final Function<E, ItemStackHandler> inventory;

    public ProcessorBlock(Properties properties, Supplier<BlockEntityType<E>> entityType,
                          BlockEntityTicker<E> ticker, Function<E, ItemStackHandler> inventory) {
        super(properties);
        this.entityType = entityType;
        this.ticker = ticker;
        this.inventory = inventory;
        registerDefaultState(stateDefinition.any().setValue(LIT, false));
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, LIT);
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
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return entityType.get().create(pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide ? null : createTickerHelper(type, entityType.get(), ticker);
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
                                 InteractionHand hand, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof MenuProvider menu)) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide) {
            if (FluidUtil.interactWithFluidHandler(player, hand, level, pos, hit.getDirection())) {
                return InteractionResult.CONSUME;
            }
            player.openMenu(menu);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState replacement, boolean moved) {
        if (!level.isClientSide && !state.is(replacement.getBlock())) {
            E entity = entityType.get().getBlockEntity(level, pos);
            if (entity != null) {
                ItemStackHandler slots = inventory.apply(entity);
                for (int slot = 0; slot < slots.getSlots(); slot++) {
                    Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), slots.getStackInSlot(slot));
                }
            }
        }
        super.onRemove(state, level, pos, replacement, moved);
    }

    static void setRunning(Level level, BlockPos pos, BlockState state, boolean running) {
        if (state.getValue(LIT) != running) {
            level.setBlock(pos, state.setValue(LIT, running), Block.UPDATE_CLIENTS);
        }
    }
}
