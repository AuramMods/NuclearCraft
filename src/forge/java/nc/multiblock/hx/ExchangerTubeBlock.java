package nc.multiblock.hx;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.phys.shapes.Shapes;

import java.util.Locale;

public final class ExchangerTubeBlock extends ExchangerPartBlock {
    public enum Setting implements StringRepresentable {
        CLOSED(false, false), OPEN(true, false), CLOSED_BAFFLE(false, true), OPEN_BAFFLE(true, true);

        final boolean open;
        final boolean baffle;

        Setting(boolean open, boolean baffle) {
            this.open = open;
            this.baffle = baffle;
        }

        Setting next() {
            return switch (this) {
                case CLOSED -> OPEN;
                case OPEN -> CLOSED_BAFFLE;
                case CLOSED_BAFFLE -> OPEN_BAFFLE;
                case OPEN_BAFFLE -> CLOSED;
            };
        }

        @Override
        public String getSerializedName() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    private static final EnumProperty<Setting> DOWN = EnumProperty.create("down", Setting.class);
    private static final EnumProperty<Setting> UP = EnumProperty.create("up", Setting.class);
    private static final EnumProperty<Setting> NORTH = EnumProperty.create("north", Setting.class);
    private static final EnumProperty<Setting> SOUTH = EnumProperty.create("south", Setting.class);
    private static final EnumProperty<Setting> WEST = EnumProperty.create("west", Setting.class);
    private static final EnumProperty<Setting> EAST = EnumProperty.create("east", Setting.class);
    private static final VoxelShape CENTER = Block.box(5, 5, 5, 11, 11, 11);

    public ExchangerTubeBlock(Properties properties, Part part) {
        super(properties, part);
        BlockState defaults = stateDefinition.any();
        for (Direction side : Direction.values()) {
            defaults = withSetting(defaults, side, Setting.CLOSED);
        }
        registerDefaultState(defaults);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(DOWN, UP, NORTH, SOUTH, WEST, EAST);
    }

    private static EnumProperty<Setting> property(Direction side) {
        return switch (side) {
            case DOWN -> DOWN;
            case UP -> UP;
            case NORTH -> NORTH;
            case SOUTH -> SOUTH;
            case WEST -> WEST;
            case EAST -> EAST;
        };
    }

    public static BlockState withSetting(BlockState state, Direction side, Setting setting) {
        return state.setValue(property(side), setting);
    }

    static Setting setting(BlockState state, Direction side) {
        return state.getValue(property(side));
    }

    @Override
    public BlockState rotate(BlockState state, Rotation rotation) {
        BlockState rotated = state;
        for (Direction side : Direction.values()) {
            rotated = withSetting(rotated, rotation.rotate(side), setting(state, side));
        }
        return rotated;
    }

    @Override
    public BlockState mirror(BlockState state, Mirror mirror) {
        BlockState mirrored = state;
        for (Direction side : Direction.values()) {
            mirrored = withSetting(mirrored, mirror.mirror(side), setting(state, side));
        }
        return mirrored;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        VoxelShape shape = CENTER;
        for (Direction side : Direction.values()) {
            Setting setting = setting(state, side);
            if (setting.open) {
                shape = Shapes.or(shape, arm(side, false));
            }
            if (setting.baffle) {
                shape = Shapes.or(shape, arm(side, true));
            }
        }
        return shape;
    }

    private static VoxelShape arm(Direction side, boolean baffle) {
        int min = baffle ? 0 : 5;
        int max = baffle ? 16 : 11;
        return switch (side) {
            case DOWN -> Block.box(min, 0, min, max, baffle ? 2 : 5, max);
            case UP -> Block.box(min, baffle ? 14 : 11, min, max, 16, max);
            case NORTH -> Block.box(min, min, 0, max, max, baffle ? 2 : 5);
            case SOUTH -> Block.box(min, min, baffle ? 14 : 11, max, max, 16);
            case WEST -> Block.box(0, min, min, baffle ? 2 : 5, max, max);
            case EAST -> Block.box(baffle ? 14 : 11, min, min, 16, max, max);
        };
    }

    public InteractionResult configure(BlockState state, UseOnContext context) {
        Player player = context.getPlayer();
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        if (level instanceof ServerLevel server && player != null) {
            Direction clicked = context.getClickedFace();
            Direction side = player.isShiftKeyDown() ? clicked.getOpposite() : clicked;
            Setting setting = setting(state, side).next();
            level.setBlockAndUpdate(pos, withSetting(state, side, setting));
            BlockPos otherPos = pos.relative(side);
            BlockState other = level.getBlockState(otherPos);
            if (other.getBlock() instanceof ExchangerTubeBlock) {
                level.setBlockAndUpdate(otherPos, withSetting(other, side.getOpposite(), setting));
            }
            notifyChange(server, pos);
            player.displayClientMessage(Component.translatable(
                    "message.nuclearcraft.exchanger.tube_" + setting.getSerializedName()), true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
