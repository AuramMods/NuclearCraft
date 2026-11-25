package nc.multiblock.turbine;

import nc.NuclearCraft;
import nc.init.TurbineConfig;
import nc.multiblock.computer.ComputerPort;
import nc.multiblock.Cuboid;
import nc.multiblock.MultiblockFrame;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.event.level.ChunkDataEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Locale;

@Mod.EventBusSubscriber(modid = NuclearCraft.MOD_ID)
public class TurbinePartBlock extends Block {
    public enum Part {
        CASING, GLASS, COMPUTER, CONTROLLER, INLET, OUTLET, BEARING, SHAFT,
        STEEL, EXTREME, SIC_SIC_CMC, STATOR,
        MAGNESIUM, BERYLLIUM, ALUMINUM, GOLD, COPPER, SILVER,
        CONNECTOR, REDSTONE, SCRIPTED_BLADE, SCRIPTED_STATOR, SCRIPTED_COIL;

        boolean blade() {
            return this == STEEL || this == EXTREME || this == SIC_SIC_CMC || this == SCRIPTED_BLADE;
        }

        boolean stator() {
            return this == STATOR || this == SCRIPTED_STATOR;
        }

        boolean stage() {
            return blade() || stator();
        }

        boolean coil() {
            return this == SCRIPTED_COIL || (ordinal() >= MAGNESIUM.ordinal() && ordinal() <= SILVER.ordinal());
        }

        boolean dynamo() {
            return coil() || this == CONNECTOR;
        }

        boolean rotor() {
            return stage() || this == SHAFT;
        }

        String dynamoName() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    private final Part part;

    public TurbinePartBlock(Properties properties, Part part) {
        super(properties);
        this.part = part;
    }

    Part part() {
        return part;
    }

    static Part part(BlockState state) {
        return state.getBlock() instanceof TurbinePartBlock block ? block.part : null;
    }

    @Override
    public boolean skipRendering(BlockState state, BlockState adjacent, Direction side) {
        return part == Part.GLASS && adjacent.is(this) || super.skipRendering(state, adjacent, side);
    }

    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState previous, boolean moved) {
        super.onPlace(state, level, pos, previous, moved);
        if (level instanceof ServerLevel server && !state.is(previous.getBlock())) {
            Cuboid.visitNearby(server, new ChunkPos(pos), TurbineConfig.searchLength(), TurbineController.class,
                    controller -> controller.partChanged(pos));
        }
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState replacement, boolean moved) {
        if (level instanceof ServerLevel server && !state.is(replacement.getBlock())) {
            Cuboid.visitNearby(server, new ChunkPos(pos), TurbineConfig.searchLength(), TurbineController.class,
                    controller -> controller.partChanged(pos));
        }
        super.onRemove(state, level, pos, replacement, moved);
    }

    @SubscribeEvent
    public static void onChunkDataLoad(ChunkDataEvent.Load event) {
        // A controller may be in another chunk. Restore static models before this chunk is sent to clients.
        // Only touch the decoded sections, which are still local to this load.
        for (var section : event.getChunk().getSections()) {
            if (section == null || !section.maybeHas(TurbinePartBlock::animatedRotor)) {
                continue;
            }
            for (int y = 0; y < 16; y++) {
                for (int z = 0; z < 16; z++) {
                    for (int x = 0; x < 16; x++) {
                        BlockState state = section.getBlockState(x, y, z);
                        if (animatedRotor(state)) {
                            section.setBlockState(x, y, z, state.setValue(TurbineRotorBlock.ANIMATED, false));
                        }
                    }
                }
            }
        }
    }

    private static boolean animatedRotor(BlockState state) {
        return state.getBlock() instanceof TurbineRotorBlock && state.getValue(TurbineRotorBlock.ANIMATED);
    }

    @SubscribeEvent
    public static void onChunkUnload(ChunkEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) {
            ChunkPos pos = event.getChunk().getPos();
            Cuboid.visitNearby(level, pos, TurbineConfig.searchLength(), TurbineController.class,
                    controller -> controller.chunkUnloaded(pos));
        }
    }

    public static final class Casing extends TurbinePartBlock {
        public Casing(Properties properties) {
            super(properties, Part.CASING);
            registerDefaultState(defaultBlockState().setValue(MultiblockFrame.EDGE, false));
        }

        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
            builder.add(MultiblockFrame.EDGE);
        }
    }

    public static final class Computer extends TurbinePartBlock implements EntityBlock {
        public Computer(Properties properties) {
            super(properties, Part.COMPUTER);
        }

        @Override
        public ComputerPort newBlockEntity(BlockPos pos, BlockState state) {
            return new ComputerPort(pos, state);
        }

        @Override
        public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
                                     BlockHitResult hit) {
            return ComputerPort.inspect(level, pos, player, hand);
        }
    }
}
