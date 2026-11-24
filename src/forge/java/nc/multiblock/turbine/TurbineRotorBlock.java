package nc.multiblock.turbine;

import nc.init.TurbineConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Locale;

public final class TurbineRotorBlock extends TurbinePartBlock {
    public static final BooleanProperty ANIMATED = BooleanProperty.create("animated");
    static final EnumProperty<Direction.Axis> AXIS = BlockStateProperties.AXIS;
    private final double scriptedEfficiency;
    private final double scriptedExpansion;

    public TurbineRotorBlock(Properties properties, Part part) {
        this(properties, part, 0D, 1D);
    }

    private TurbineRotorBlock(Properties properties, Part part, double efficiency, double expansion) {
        super(properties, part);
        scriptedEfficiency = efficiency;
        scriptedExpansion = expansion;
        registerDefaultState(stateDefinition.any().setValue(AXIS, Direction.Axis.Y).setValue(ANIMATED, false));
    }

    public static TurbineRotorBlock scriptedBlade(Properties properties, double efficiency, double expansion) {
        if (!Double.isFinite(efficiency) || efficiency < 0D) {
            throw new IllegalArgumentException("Turbine blade efficiency must be finite and nonnegative");
        }
        return new TurbineRotorBlock(properties, Part.SCRIPTED_BLADE, efficiency, validExpansion(expansion));
    }

    public static TurbineRotorBlock scriptedStator(Properties properties, double expansion) {
        return new TurbineRotorBlock(properties, Part.SCRIPTED_STATOR, -1D, validExpansion(expansion));
    }

    private static double validExpansion(double expansion) {
        if (!Double.isFinite(expansion) || expansion <= 0D) {
            throw new IllegalArgumentException("Turbine stage expansion must be finite and positive");
        }
        return expansion;
    }

    boolean stage() { return part().stage(); }
    boolean blade() { return part().blade(); }
    public boolean stator() { return part().stator(); }

    double efficiency() {
        if (stator()) {
            return -1D;
        }
        return part() == Part.SCRIPTED_BLADE ? scriptedEfficiency
                : TurbineConfig.bladeEfficiency(part().ordinal() - Part.STEEL.ordinal());
    }

    double expansion() {
        return switch (part()) {
            case SCRIPTED_BLADE, SCRIPTED_STATOR -> scriptedExpansion;
            case STATOR -> TurbineConfig.statorExpansion();
            default -> TurbineConfig.bladeExpansion(part().ordinal() - Part.STEEL.ordinal());
        };
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable BlockGetter level, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, level, tooltip, flag);
        if (blade()) {
            tooltip.add(Component.translatable("tooltip.nuclearcraft.turbine.blade_efficiency", percent(efficiency()))
                    .withStyle(ChatFormatting.YELLOW));
        }
        if (stage()) {
            tooltip.add(Component.translatable("tooltip.nuclearcraft.turbine.stage_expansion", percent(expansion()))
                    .withStyle(ChatFormatting.AQUA));
        }
    }

    private static String percent(double value) {
        return String.format(Locale.ROOT, "%.1f%%", 100D * value);
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
