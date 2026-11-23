package nc.multiblock.fission;

import nc.NuclearCraft;
import nc.multiblock.Cuboid;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = NuclearCraft.MOD_ID)
public class FissionPartBlock extends Block {
    public static final int MAX_INTERIOR_LENGTH = 24;

    public enum Role {
        CASING, CONTROLLER, PEBBLE_CONTROLLER, SALT_CONTROLLER, VENT, FUEL_PORT, CHAMBER_PORT,
        COOLER_PORT, VESSEL_PORT, HEATER_PORT, SOURCE,
        CELL, PEBBLE_CHAMBER, VESSEL, WATER_SINK, PEBBLE_COOLER, SALT_HEATER, MODERATOR, REFLECTOR
    }

    private final Role role;

    public FissionPartBlock(Properties properties, Role role) {
        super(properties);
        this.role = role;
    }

    static Role role(BlockState state) {
        return state.getBlock() instanceof FissionPartBlock part ? part.role : null;
    }

    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState previous, boolean moved) {
        super.onPlace(state, level, pos, previous, moved);
        if (level instanceof ServerLevel server && !state.is(previous.getBlock())) {
            geometryChanged(server, pos);
        }
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState replacement, boolean moved) {
        if (level instanceof ServerLevel server && !state.is(replacement.getBlock())) {
            geometryChanged(server, pos);
        }
        super.onRemove(state, level, pos, replacement, moved);
    }

    static void geometryChanged(ServerLevel level, BlockPos pos) {
        Cuboid.visitNearby(level, new ChunkPos(pos), MAX_INTERIOR_LENGTH + 2, SolidFissionController.class,
                controller -> controller.partChanged(pos));
    }

    @SubscribeEvent
    public static void onChunkUnload(ChunkEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) {
            ChunkPos pos = event.getChunk().getPos();
            Cuboid.visitNearby(level, pos, MAX_INTERIOR_LENGTH + 2, SolidFissionController.class,
                    controller -> controller.chunkUnloaded(pos));
        }
    }
}
