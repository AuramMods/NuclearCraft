package nc.multiblock.turbine;

import nc.init.NCTurbine;
import nc.init.TurbineConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
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
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

public final class TurbineWallBlock extends TurbinePartBlock implements EntityBlock {
    public static final DirectionProperty FACING = BlockStateProperties.FACING;
    public static final BooleanProperty ACTIVE = BooleanProperty.create("active");
    private final String dynamoName;
    private final @Nullable ScriptedCoil scripted;

    private record ScriptedCoil(String name, double conductivity, String rule) {}

    public TurbineWallBlock(Properties properties, Part part) {
        this(properties, part, null);
    }

    private TurbineWallBlock(Properties properties, Part part, @Nullable ScriptedCoil scripted) {
        super(properties, part);
        this.scripted = scripted;
        dynamoName = scripted == null ? part.dynamoName() : scripted.name();
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(ACTIVE, false));
    }

    public static TurbineWallBlock scriptedCoil(Properties properties, String name, double conductivity, String rule) {
        if (!TurbineDynamoRules.validScriptedName(name)) {
            throw new IllegalArgumentException("Invalid or reserved turbine coil name " + name);
        }
        if (conductivity < 0D || !Double.isFinite(conductivity)) {
            throw new IllegalArgumentException("Turbine coil conductivity must be finite and nonnegative");
        }
        return new TurbineWallBlock(properties, Part.SCRIPTED_COIL,
                new ScriptedCoil(name, conductivity, Objects.requireNonNull(rule)));
    }

    public String dynamoName() { return dynamoName; }

    public @Nullable String scriptedRule() { return scripted == null ? null : scripted.rule(); }

    double conductivity() {
        return scripted == null ? TurbineConfig.coilConductivity(part().ordinal() - Part.MAGNESIUM.ordinal())
                : scripted.conductivity();
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable BlockGetter level, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, level, tooltip, flag);
        if (part().coil()) {
            String percent = String.format(Locale.ROOT, "%.1f%%", 100D * conductivity());
            tooltip.add(Component.translatable("tooltip.nuclearcraft.turbine.coil_conductivity", percent)
                    .withStyle(ChatFormatting.LIGHT_PURPLE));
        }
        if (part().dynamo()) {
            String rule = TurbineConfig.dynamoRules().text(dynamoName);
            tooltip.add(Component.translatable("tooltip.nuclearcraft.turbine.placement_rule",
                    rule != null ? rule : scriptedRule()).withStyle(ChatFormatting.GRAY));
        }
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return part(state) == Part.CONTROLLER ? new TurbineController(pos, state) : new TurbinePort(pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level instanceof ServerLevel && type == NCTurbine.CONTROLLER_ENTITY.get()) {
            return (world, pos, blockState, entity) -> ((TurbineController) entity).tick((ServerLevel) world);
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
        return state.setValue(FACING, mirror.mirror(state.getValue(FACING)));
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
                                 BlockHitResult hit) {
        if (part(state) != Part.CONTROLLER || hand != InteractionHand.MAIN_HAND) {
            return InteractionResult.PASS;
        }
        if (level instanceof ServerLevel && level.getBlockEntity(pos) instanceof TurbineController controller) {
            if (controller.assembled()) {
                player.openMenu(controller);
            } else {
                player.displayClientMessage(controller.validationMessage(), true);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
