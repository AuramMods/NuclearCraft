package nc.multiblock.fission;

import nc.multiblock.Cuboid;
import nc.init.NCGasCoolants;
import org.jetbrains.annotations.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

record SolidFissionLayout(Cuboid bounds, List<BlockPos> cells, List<BlockPos> sinks, List<Port> ports,
                          List<BlockPos> sources, boolean pebbleBed) {
    record Port(BlockPos pos, Direction side, boolean fluid, boolean output, @Nullable NCGasCoolants gas) {}

    record Validation(SolidFissionLayout layout, Component message) {}

    static Validation find(ServerLevel level, BlockPos origin) {
        Cuboid bounds = Cuboid.findConnected(level, origin, FissionPartBlock.MAX_INTERIOR_LENGTH + 2,
                SolidFissionLayout::connectsReactor);
        if (bounds == null) {
            return invalid("too_large", origin);
        }
        if (bounds.xLength() < 3 || bounds.yLength() < 3 || bounds.zLength() < 3) {
            return invalid("too_small", origin);
        }
        if (!bounds.isLoaded(level)) {
            return invalid("unloaded", origin);
        }

        boolean pebble = FissionPartBlock.role(level.getBlockState(origin)) == FissionPartBlock.Role.PEBBLE_CONTROLLER;
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
                        case CELL, PEBBLE_CHAMBER -> {
                            if (pebble != (role == FissionPartBlock.Role.PEBBLE_CHAMBER)) {
                                return invalid("interior", pos);
                            }
                            cells.add(pos.immutable());
                        }
                        case WATER_SINK, PEBBLE_COOLER -> {
                            if (pebble != (role == FissionPartBlock.Role.PEBBLE_COOLER)) {
                                return invalid("interior", pos);
                            }
                            sinks.add(pos.immutable());
                        }
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
                        case CONTROLLER, PEBBLE_CONTROLLER -> {
                            if (!pos.equals(origin)) {
                                return invalid("multiple_controllers", pos);
                            }
                        }
                        case SOURCE -> sources.add(pos.immutable());
                        case VENT, FUEL_PORT, CHAMBER_PORT, COOLER_PORT -> {
                            if (role != FissionPartBlock.Role.VENT && pebble == (role == FissionPartBlock.Role.FUEL_PORT)) {
                                return invalid("wall", pos);
                            }
                            if (!(level.getBlockEntity(pos) instanceof FissionPortBlockEntity)) {
                                return invalid("wall", pos);
                            }
                            var gas = ((FissionWallBlock) state.getBlock()).coolant();
                            ports.add(new Port(pos.immutable(), bounds.outward(pos), role == FissionPartBlock.Role.VENT || gas != null,
                                    state.getValue(FissionWallBlock.ACTIVE), gas));
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
        if (hasUnpairedPorts(ports, true, null) || (!cells.isEmpty() && hasUnpairedPorts(ports, false, null))) {
            return invalid("port_pair", origin);
        }
        if (pebble) {
            for (NCGasCoolants gas : NCGasCoolants.values()) {
                if (hasUnpairedPorts(ports, true, gas)) {
                    return invalid("port_pair", origin);
                }
            }
        }
        return new Validation(new SolidFissionLayout(bounds, List.copyOf(cells), List.copyOf(sinks),
                List.copyOf(ports), List.copyOf(sources), pebble),
                Component.translatable("message.nuclearcraft.fission.assembled"));
    }

    private static boolean connectsReactor(BlockState state) {
        var role = FissionPartBlock.role(state);
        return role != null && role != FissionPartBlock.Role.MODERATOR && role != FissionPartBlock.Role.REFLECTOR;
    }

    private static boolean hasUnpairedPorts(List<Port> ports, boolean fluid, @Nullable NCGasCoolants gas) {
        boolean input = false;
        boolean output = false;
        for (Port port : ports) {
            if (port.fluid() == fluid && port.gas() == gas) {
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
