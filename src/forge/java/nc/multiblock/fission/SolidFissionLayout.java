package nc.multiblock.fission;

import nc.multiblock.Cuboid;
import nc.init.NCGasCoolants;
import nc.init.NCSaltCoolants;
import nc.multiblock.fission.FissionPartBlock.Role;
import org.jetbrains.annotations.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;
import java.util.HashSet;
import java.util.Objects;

record SolidFissionLayout(Cuboid bounds, List<BlockPos> cells, List<BlockPos> sinks, List<Port> ports,
                          List<BlockPos> sources, FissionVariant variant) {
    record Port(BlockPos pos, Direction side, Role role, boolean output, @Nullable NCGasCoolants gas,
                @Nullable NCSaltCoolants salt, @Nullable ResourceLocation fuelFilter) {
        boolean fluid() {
            return role != Role.FUEL_PORT && role != Role.CHAMBER_PORT;
        }
    }

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

        FissionVariant variant = FissionVariant.forController(FissionPartBlock.role(level.getBlockState(origin)));
        Role fuelPart = switch (variant) {
            case SOLID -> Role.CELL;
            case PEBBLE -> Role.PEBBLE_CHAMBER;
            case SALT -> Role.VESSEL;
        };
        Role coolingPart = switch (variant) {
            case SOLID -> Role.WATER_SINK;
            case PEBBLE -> Role.PEBBLE_COOLER;
            case SALT -> Role.SALT_HEATER;
        };
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
                        case CELL, PEBBLE_CHAMBER, VESSEL -> {
                            if (role != fuelPart) {
                                return invalid("interior", pos);
                            }
                            cells.add(pos.immutable());
                        }
                        case WATER_SINK, PEBBLE_COOLER, SALT_HEATER -> {
                            if (role != coolingPart) {
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
                        case CONTROLLER, PEBBLE_CONTROLLER, SALT_CONTROLLER -> {
                            if (!pos.equals(origin)) {
                                return invalid("multiple_controllers", pos);
                            }
                        }
                        case SOURCE -> sources.add(pos.immutable());
                        case VENT, FUEL_PORT, CHAMBER_PORT, COOLER_PORT, VESSEL_PORT, HEATER_PORT -> {
                            if (!validPort(variant, role) || !(level.getBlockEntity(pos) instanceof FissionPortBlockEntity entity)) {
                                return invalid("wall", pos);
                            }
                            var wall = (FissionWallBlock) state.getBlock();
                            ports.add(new Port(pos.immutable(), bounds.outward(pos), role, state.getValue(FissionWallBlock.ACTIVE),
                                    wall.coolant(), wall.saltCoolant(), role == Role.VESSEL_PORT ? entity.fuelFilter() : null));
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
        if (hasUnpairedPorts(ports, Role.VENT, null)
                || (!cells.isEmpty() && (hasUnpairedPorts(ports, Role.FUEL_PORT, null)
                || hasUnpairedPorts(ports, Role.CHAMBER_PORT, null)))) {
            return invalid("port_pair", origin);
        }
        if (variant == FissionVariant.PEBBLE) {
            for (NCGasCoolants gas : NCGasCoolants.values()) {
                if (hasUnpairedPorts(ports, Role.COOLER_PORT, gas)) {
                    return invalid("port_pair", origin);
                }
            }
        }
        if (variant == FissionVariant.SALT) {
            var filters = new HashSet<ResourceLocation>();
            for (Port port : ports) {
                if (port.role() == Role.VESSEL_PORT) {
                    filters.add(port.fuelFilter());
                }
            }
            if (filters.stream().anyMatch(filter -> hasUnpairedPorts(ports, Role.VESSEL_PORT, filter))) {
                return invalid("port_pair", origin);
            }
            for (NCSaltCoolants coolant : NCSaltCoolants.values()) {
                if (hasUnpairedPorts(ports, Role.HEATER_PORT, coolant)) {
                    return invalid("port_pair", origin);
                }
            }
        }
        return new Validation(new SolidFissionLayout(bounds, List.copyOf(cells), List.copyOf(sinks),
                List.copyOf(ports), List.copyOf(sources), variant),
                Component.translatable("message.nuclearcraft.fission.assembled"));
    }

    private static boolean validPort(FissionVariant variant, Role role) {
        return role == Role.VENT || switch (variant) {
            case SOLID -> role == Role.FUEL_PORT;
            case PEBBLE -> role == Role.CHAMBER_PORT || role == Role.COOLER_PORT;
            case SALT -> role == Role.VESSEL_PORT || role == Role.HEATER_PORT;
        };
    }

    private static boolean connectsReactor(BlockState state) {
        var role = FissionPartBlock.role(state);
        return role != null && role != Role.MODERATOR && role != Role.REFLECTOR;
    }

    private static boolean hasUnpairedPorts(List<Port> ports, Role role, @Nullable Object filter) {
        boolean input = false;
        boolean output = false;
        for (Port port : ports) {
            Object current = switch (role) {
                case VESSEL_PORT -> port.fuelFilter();
                case HEATER_PORT -> port.salt();
                case COOLER_PORT -> port.gas();
                default -> null;
            };
            if (port.role() == role && Objects.equals(current, filter)) {
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
