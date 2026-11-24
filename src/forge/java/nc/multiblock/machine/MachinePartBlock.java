package nc.multiblock.machine;

import nc.NuclearCraft;
import nc.init.NCHeatExchangers;
import nc.init.NCMachines;
import nc.multiblock.Cuboid;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fluids.FluidUtil;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

@Mod.EventBusSubscriber(modid = NuclearCraft.MOD_ID)
public class MachinePartBlock extends Block {
    public enum Part {
        FRAME("machine_frame"), GLASS("machine_glass"), PROCESS("machine_process_port"),
        RESERVOIR("machine_reservoir_port"), REDSTONE("machine_redstone_port"), POWER("machine_power_port"),
        DECAY_POOL("decay_pool_controller"), CONTAINER("decay_pool_container"),
        DISTILLER("distiller_controller"), SIEVE_TRAY("distiller_sieve_tray"),
        STEEL_SIEVE("steel_sieve_assembly"), PTFE_SIEVE("polytetrafluoroethene_sieve_assembly"),
        HASTELLOY_SIEVE("hastelloy_sieve_assembly"), REFLUX("distiller_reflux_unit"),
        REBOILER("distiller_reboiling_unit"), LIQUID_DISTRIBUTOR("distiller_liquid_distributor");

        private final String id;

        Part(String id) { this.id = id; }
        public String id() { return id; }
        boolean port() { return this == PROCESS || this == RESERVOIR || this == POWER; }
        boolean controller() { return this == DECAY_POOL || this == DISTILLER; }
        boolean internal() { return this == CONTAINER || this == SIEVE_TRAY || sieve(); }
        boolean sieve() { return this == STEEL_SIEVE || this == PTFE_SIEVE || this == HASTELLOY_SIEVE; }
        boolean distillerWall() { return this == REFLUX || this == REBOILER || this == LIQUID_DISTRIBUTOR; }
        public boolean wallEntity() { return port() || controller(); }
    }

    public enum Mode implements StringRepresentable {
        ITEM_IN, FLUID_IN, ITEM_OUT, FLUID_OUT;

        public boolean output() { return this == ITEM_OUT || this == FLUID_OUT; }
        public boolean fluid() { return this == FLUID_IN || this == FLUID_OUT; }
        @Override public String getSerializedName() { return name().toLowerCase(Locale.ROOT); }
    }

    public static final EnumProperty<Mode> MODE = EnumProperty.create("mode", Mode.class);
    public static final BooleanProperty FRAME_EDGE = BooleanProperty.create("frame");
    private final Part part;

    public MachinePartBlock(Properties properties, Part part) {
        super(properties);
        this.part = part;
    }

    static @Nullable Part part(BlockState state) {
        return state.getBlock() instanceof MachinePartBlock block ? block.part : null;
    }

    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState former, boolean moved) {
        super.onPlace(state, level, pos, former, moved);
        if (level instanceof ServerLevel server && !former.is(state.getBlock())) {
            partChanged(server, pos);
        }
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState replacement, boolean moved) {
        super.onRemove(state, level, pos, replacement, moved);
        if (level instanceof ServerLevel server && !replacement.is(state.getBlock())) {
            partChanged(server, pos);
        }
    }

    private static void partChanged(ServerLevel server, BlockPos pos) {
        Cuboid.visitNearby(server, new ChunkPos(pos), NCMachines.maxSize() + 2, MachineController.class,
                controller -> controller.partChanged(pos));
    }

    @SubscribeEvent
    public static void unload(ChunkEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel server) {
            ChunkPos pos = event.getChunk().getPos();
            Cuboid.visitNearby(server, pos, NCMachines.maxSize() + 2, MachineController.class,
                    controller -> controller.chunkUnloaded(pos));
        }
    }

    public static final class Frame extends MachinePartBlock {
        public Frame(Properties properties) {
            super(properties, Part.FRAME);
            registerDefaultState(stateDefinition.any().setValue(FRAME_EDGE, false));
        }

        @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
            builder.add(FRAME_EDGE);
        }
    }

    public static final class Redstone extends MachinePartBlock {
        public Redstone(Properties properties) {
            super(properties, Part.REDSTONE);
            registerDefaultState(stateDefinition.any().setValue(BlockStateProperties.LIT, false));
        }

        @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
            builder.add(BlockStateProperties.LIT);
        }

        @Override
        public void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, BlockPos neighbor, boolean moving) {
            if (!level.isClientSide) {
                boolean powered = level.hasNeighborSignal(pos);
                if (state.getValue(BlockStateProperties.LIT) != powered) {
                    level.setBlock(pos, state.setValue(BlockStateProperties.LIT, powered), Block.UPDATE_CLIENTS);
                }
            }
        }
    }

    public static final class Reboiler extends MachinePartBlock {
        public Reboiler(Properties properties) {
            super(properties, Part.REBOILER);
            registerDefaultState(stateDefinition.any().setValue(BlockStateProperties.LIT, false));
        }

        @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
            builder.add(BlockStateProperties.LIT);
        }
    }

    public static final class Wall extends MachinePartBlock implements EntityBlock {
        public Wall(Properties properties, Part part) {
            super(properties, part);
            registerDefaultState(stateDefinition.any().setValue(BlockStateProperties.FACING, Direction.NORTH)
                    .setValue(MODE, Mode.ITEM_IN).setValue(BlockStateProperties.LIT, false));
        }

        @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
            builder.add(BlockStateProperties.FACING, MODE, BlockStateProperties.LIT);
        }

        @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
            return defaultBlockState().setValue(BlockStateProperties.FACING, context.getClickedFace());
        }

        @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
            return switch (part(state)) {
                case DECAY_POOL -> new DecayPool(pos, state);
                case DISTILLER -> new Distiller(pos, state);
                default -> new MachinePort(pos, state);
            };
        }

        @Override
        public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
            if (level.isClientSide || !part(state).controller()) return null;
            return (world, pos, block, entity) -> ((MachineController) entity).tick();
        }

        @Override
        public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
                                     BlockHitResult hit) {
            if (level.getBlockEntity(pos) instanceof MachineController controller) {
                if (!level.isClientSide) controller.open(player);
                return InteractionResult.sidedSuccess(level.isClientSide);
            }

            if (hand == InteractionHand.MAIN_HAND && level.getBlockEntity(pos) instanceof MachinePort port) {
                if (player.getItemInHand(hand).is(NCHeatExchangers.MULTITOOL.get()) && !player.isShiftKeyDown()) {
                    if (!level.isClientSide) {
                        player.displayClientMessage(port.cycleSetting(false), true);
                    }
                    return InteractionResult.sidedSuccess(level.isClientSide);
                }
                if (FluidUtil.getFluidHandler(player.getItemInHand(hand)).isPresent()) {
                    if (level.isClientSide) return InteractionResult.SUCCESS;
                    return FluidUtil.interactWithFluidHandler(player, hand, level, pos, hit.getDirection())
                            ? InteractionResult.CONSUME : InteractionResult.PASS;
                }
            }
            return InteractionResult.PASS;
        }

        public InteractionResult reverseProcessPort(UseOnContext context) {
            var level = context.getLevel();
            var pos = context.getClickedPos();
            var player = context.getPlayer();
            if (player == null || context.getHand() != InteractionHand.MAIN_HAND || part(level.getBlockState(pos)) != Part.PROCESS
                    || !(level.getBlockEntity(pos) instanceof MachinePort port)) return InteractionResult.PASS;
            if (level.isClientSide) return InteractionResult.SUCCESS;
            var setting = port.cycleSetting(true);
            if (setting == null) return InteractionResult.PASS;
            player.displayClientMessage(setting, true);
            return InteractionResult.CONSUME;
        }
    }
}
