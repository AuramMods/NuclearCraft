package nc.multiblock.hx;

import nc.init.HeatExchangerConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.BlockPlaceContext;
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
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Locale;
import java.util.function.DoubleSupplier;

public final class ExchangerTubeBlock extends ExchangerPartBlock {
    public enum Setting implements StringRepresentable {
        CLOSED(false, false), OPEN(true, false), CLOSED_BAFFLE(false, true), OPEN_BAFFLE(true, true);

        final boolean open;
        final boolean baffle;

        Setting(boolean open, boolean baffle) {
            this.open = open;
            this.baffle = baffle;
        }

        Setting withOpen(boolean open) {
            return open ? (baffle ? OPEN_BAFFLE : OPEN) : (baffle ? CLOSED_BAFFLE : CLOSED);
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
    private final DoubleSupplier conductivity;
    private final DoubleSupplier retention;

    public ExchangerTubeBlock(Properties properties, Part part) {
        this(properties, part, () -> HeatExchangerConfig.conductivity(stockMaterial(part)),
                () -> HeatExchangerConfig.retention(stockMaterial(part)));
    }

    public ExchangerTubeBlock(Properties properties, double conductivity, double retention) {
        this(properties, Part.SCRIPTED_TUBE, () -> conductivity, () -> retention);
        if (conductivity < 0D || retention < 0D || !Double.isFinite(conductivity * retention)) {
            throw new IllegalArgumentException("Tube conductivity, retention and their product must be finite and nonnegative");
        }
    }

    private ExchangerTubeBlock(Properties properties, Part part, DoubleSupplier conductivity, DoubleSupplier retention) {
        super(properties, part);
        this.conductivity = conductivity;
        this.retention = retention;
        BlockState defaults = stateDefinition.any();
        for (Direction side : Direction.values()) {
            defaults = withSetting(defaults, side, Setting.CLOSED);
        }
        registerDefaultState(defaults);
    }

    private static int stockMaterial(Part part) {
        return switch (part) {
            case COPPER -> 0;
            case HARD_CARBON -> 1;
            case THERMOCONDUCTING -> 2;
            default -> throw new IllegalArgumentException("Unknown stock heat exchanger tube " + part);
        };
    }

    double conductivity() {
        return conductivity.getAsDouble();
    }

    double retention() {
        return retention.getAsDouble();
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable BlockGetter level, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, level, tooltip, flag);
        tooltip.add(Component.translatable("tooltip.nuclearcraft.exchanger.tube_conductivity", conductivity())
                .withStyle(ChatFormatting.YELLOW));
        String percent = String.format(Locale.ROOT, "%.2f%%", retention() * 100D);
        tooltip.add(Component.translatable("tooltip.nuclearcraft.exchanger.tube_retention", percent)
                .withStyle(ChatFormatting.BLUE));
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
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        var level = context.getLevel();
        var pos = context.getClickedPos();
        Direction attached = context.getClickedFace().getOpposite();
        BlockState adjacent = level.getBlockState(pos.relative(attached));
        BlockState placed = defaultBlockState();
        if (adjacent.getBlock() instanceof ExchangerTubeBlock) {
            Setting connection = setting(adjacent, attached.getOpposite());
            if (context.isSecondaryUseActive()) {
                for (Direction side : Direction.values()) {
                    placed = withSetting(placed, side, setting(adjacent, side));
                }
            } else {
                connection = connection.withOpen(true);
            }
            placed = withSetting(placed, attached, connection);
        }
        for (Direction side : List.of(attached, attached.getOpposite())) {
            Part part = part(level.getBlockState(pos.relative(side)));
            if (part == Part.INLET || part == Part.OUTLET) {
                return withSetting(placed, side, Setting.OPEN);
            }
        }
        return placed;
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState next, boolean moving) {
        if (level instanceof ServerLevel server && !(next.getBlock() instanceof ExchangerTubeBlock)
                && !moving && !level.restoringBlockSnapshots) {
            for (Direction side : Direction.values()) {
                BlockPos neighbor = pos.relative(side);
                if (setting(state, side).open && level.hasChunkAt(neighbor)) {
                    setNeighborOpen(server, neighbor, side.getOpposite(), false);
                }
            }
        }
        super.onRemove(state, level, pos, next, moving);
    }

    private static void setNeighborOpen(ServerLevel level, BlockPos pos, Direction side, boolean open) {
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof ExchangerTubeBlock) {
            Setting previous = setting(state, side);
            if (previous.open != open) {
                level.setBlockAndUpdate(pos, withSetting(state, side, previous.withOpen(open)));
            }
        }
    }

    public static final class TubeItem extends BlockItem {
        public TubeItem(ExchangerTubeBlock tube, Properties properties) {
            super(tube, properties);
        }

        @Override
        public InteractionResult place(BlockPlaceContext context) {
            InteractionResult result = super.place(context);
            if (result.consumesAction() && context.getLevel() instanceof ServerLevel level) {
                var pos = context.getClickedPos();
                var placed = level.getBlockState(pos);
                if (placed.is(getBlock())) {
                    connectPlacement(level, pos, placed, context.getClickedFace().getOpposite(),
                            context.isSecondaryUseActive());
                }
            }
            return result;
        }

        private static void connectPlacement(ServerLevel level, BlockPos pos, BlockState placed,
                                             Direction attached, boolean copy) {
            BlockPos source = pos.relative(attached);
            BlockState sourceState = level.getBlockState(source);
            if (!(sourceState.getBlock() instanceof ExchangerTubeBlock)) {
                return;
            }
            if (copy) {
                for (Direction side : Direction.values()) {
                    if (side == attached || !setting(sourceState, side).open || !setting(placed, side).open) {
                        continue;
                    }
                    BlockState template = level.getBlockState(source.relative(side));
                    if (template.getBlock() instanceof ExchangerTubeBlock && setting(template, side.getOpposite()).open) {
                        setNeighborOpen(level, pos.relative(side), side.getOpposite(), true);
                    }
                }
            } else if (setting(placed, attached).open) {
                setNeighborOpen(level, source, attached.getOpposite(), true);
            }
        }
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

    public static Direction multitoolFace(Player player, Direction hitFace) {
        return player.isShiftKeyDown() ? hitFace.getOpposite() : hitFace;
    }

    public InteractionResult configure(BlockState state, UseOnContext context) {
        Player player = context.getPlayer();
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        if (level instanceof ServerLevel server && player != null) {
            Direction side = multitoolFace(player, context.getClickedFace());
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
