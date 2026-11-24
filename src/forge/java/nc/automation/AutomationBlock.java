package nc.automation;

import nc.init.NCAutomation;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.fluids.FluidUtil;
import org.jetbrains.annotations.Nullable;

public final class AutomationBlock extends BaseEntityBlock {
    private final boolean forwards;

    public AutomationBlock(Properties properties, boolean forwards) {
        super(properties);
        this.forwards = forwards;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return forwards ? new MachineInterface(pos, state) : new UniversalBin(pos, state);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return forwards && !level.isClientSide
                ? createTickerHelper(type, NCAutomation.INTERFACE_ENTITY.get(), MachineInterface::tick) : null;
    }

    @Override
    public void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighbor, BlockPos from, boolean moving) {
        super.neighborChanged(state, level, pos, neighbor, from, moving);
        if (!level.isClientSide && forwards && level.getBlockEntity(pos) instanceof MachineInterface machine) {
            machine.refreshTarget();
        }
    }

    @Override
    public boolean canConnectRedstone(BlockState state, BlockGetter level, BlockPos pos, @Nullable Direction side) {
        return forwards && side != null;
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (!forwards || hand != InteractionHand.MAIN_HAND || player.isShiftKeyDown()) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (level.getBlockEntity(pos) instanceof MachineInterface machine) {
            BlockEntity target = machine.refreshTarget();
            if (target != null && FluidUtil.interactWithFluidHandler(player, hand, level, pos, hit.getDirection())) {
                return InteractionResult.CONSUME;
            }
            if (target instanceof MenuProvider menu) {
                player.openMenu(menu);
                return InteractionResult.CONSUME;
            }
        }
        return InteractionResult.PASS;
    }
}
