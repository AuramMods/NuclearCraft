package nc.processor;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.items.IItemHandler;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

record ProcessorOutputTarget(Direction side, LazyOptional<IItemHandler> items, LazyOptional<IFluidHandler> fluids) {
    static List<ProcessorOutputTarget> inTransferOrder(Level level, BlockPos source) {
        var connected = new ArrayList<ProcessorOutputTarget>(ProcessorItemPorts.SIDES.length);
        for (Direction side : ProcessorItemPorts.SIDES) {
            BlockPos pos = source.relative(side);
            var neighbor = level.hasChunkAt(pos) ? level.getBlockEntity(pos) : null;
            if (neighbor == null) continue;
            Direction face = side.getOpposite();
            var items = neighbor.getCapability(ForgeCapabilities.ITEM_HANDLER, face);
            var fluids = neighbor.getCapability(ForgeCapabilities.FLUID_HANDLER, face);
            if (items.isPresent() || fluids.isPresent()) connected.add(new ProcessorOutputTarget(side, items, fluids));
        }
        if (connected.size() > 1) {
            Collections.rotate(connected, -Math.floorMod(level.getGameTime(), connected.size()));
        }
        return connected;
    }
}
