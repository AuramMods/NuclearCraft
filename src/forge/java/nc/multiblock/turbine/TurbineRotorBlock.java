package nc.multiblock.turbine;

import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

public final class TurbineRotorBlock extends TurbinePartBlock {
    public static final BooleanProperty ANIMATED = BooleanProperty.create("animated");
    static final EnumProperty<Direction.Axis> AXIS = BlockStateProperties.AXIS;

    public TurbineRotorBlock(Properties properties, Part part) {
        super(properties, part);
        registerDefaultState(stateDefinition.any().setValue(AXIS, Direction.Axis.Y).setValue(ANIMATED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(AXIS, ANIMATED);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return state.getValue(ANIMATED) ? RenderShape.INVISIBLE : RenderShape.MODEL;
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(AXIS, context.getClickedFace().getAxis());
    }

    @Override
    public BlockState rotate(BlockState state, Rotation rotation) {
        Direction positive = Direction.fromAxisAndDirection(state.getValue(AXIS), Direction.AxisDirection.POSITIVE);
        return state.setValue(AXIS, rotation.rotate(positive).getAxis());
    }
}
