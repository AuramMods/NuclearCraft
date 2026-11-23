package nc.multiblock.fission;

import nc.multiblock.Cuboid;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

record SolidFissionLayout(Cuboid bounds, List<BlockPos> cells, List<BlockPos> sinks, List<Port> ports,
                          List<BlockPos> sources) {
    record Port(BlockPos pos, Direction side, boolean fluid, boolean output) {}

    record Validation(SolidFissionLayout layout, Component message) {}

    static Validation find(ServerLevel level, BlockPos origin) {
        Cuboid bounds = Cuboid.findConnected(level, origin, FissionPartBlock.MAX_INTERIOR_LENGTH + 2,
                state -> FissionPartBlock.role(state) != null);
        if (bounds == null) {
            return invalid("too_large", origin);
        }
        if (bounds.xLength() < 3 || bounds.yLength() < 3 || bounds.zLength() < 3) {
            return invalid("too_small", origin);
        }
        if (!bounds.isLoaded(level)) {
            return invalid("unloaded", origin);
        }

        List<BlockPos> cells = new ArrayList<>();
        List<BlockPos> sinks = new ArrayList<>();
        List<Port> ports = new ArrayList<>();
        List<BlockPos> sources = new ArrayList<>();
        for (BlockPos pos : BlockPos.betweenClosed(bounds.min(), bounds.max())) {
            BlockState state = level.getBlockState(pos);
            FissionPartBlock.Role role = FissionPartBlock.role(state);
            switch (bounds.boundaryAxes(pos)) {
                case 0 -> {
                    if (role == null) {
                        continue;
                    }
                    switch (role) {
                        case CELL -> cells.add(pos.immutable());
                        case WATER_SINK -> sinks.add(pos.immutable());
                        case MODERATOR, REFLECTOR -> {}
                        default -> {
                            return invalid("interior", pos);
                        }
                    }
                }
                case 1 -> {
                    if (role == null) {
                        return invalid("wall", pos);
                    }
                    switch (role) {
                        case CASING -> {}
                        case CONTROLLER -> {
                            if (!pos.equals(origin)) {
                                return invalid("multiple_controllers", pos);
                            }
                        }
                        case SOURCE -> sources.add(pos.immutable());
                        case VENT, FUEL_PORT -> {
                            if (!(level.getBlockEntity(pos) instanceof FissionPortBlockEntity)) {
                                return invalid("wall", pos);
                            }
                            ports.add(new Port(pos.immutable(), bounds.outward(pos), role == FissionPartBlock.Role.VENT,
                                    state.getValue(FissionWallBlock.ACTIVE)));
                        }
                        default -> {
                            return invalid("wall", pos);
                        }
                    }
                }
                default -> {
                    if (role != FissionPartBlock.Role.CASING) {
                        return invalid("frame", pos);
                    }
                }
            }
        }

        if (bounds.boundaryAxes(origin) != 1) {
            return invalid("wall", origin);
        }
        if (hasUnpairedPorts(ports, true) || (!cells.isEmpty() && hasUnpairedPorts(ports, false))) {
            return invalid("port_pair", origin);
        }
        return new Validation(new SolidFissionLayout(bounds, List.copyOf(cells), List.copyOf(sinks),
                List.copyOf(ports), List.copyOf(sources)),
                Component.translatable("message.nuclearcraft.fission.assembled"));
    }

    private static boolean hasUnpairedPorts(List<Port> ports, boolean fluid) {
        boolean input = false;
        boolean output = false;
        for (Port port : ports) {
            if (port.fluid() == fluid) {
                input |= !port.output();
                output |= port.output();
            }
        }
        return input != output;
    }

    static Validation invalid(String reason, BlockPos pos) {
        return new Validation(null, Component.translatable("message.nuclearcraft.fission." + reason,
                pos.getX(), pos.getY(), pos.getZ()));
    }
}
