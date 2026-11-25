package nc.multiblock.fission;

import nc.init.NCFission;
import nc.multiblock.Cuboid;
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
                          List<BlockPos> sources, List<BlockPos> shields, List<BlockPos> managers, List<BlockPos> irradiators,
                          List<BlockPos> conductors, List<BlockPos> monitors, List<BlockPos> computers,
                          List<BlockPos> frames, FissionVariant variant) {
    record Port(BlockPos pos, Direction side, Role role, boolean output, @Nullable PebbleCooling.Channel gas,
                @Nullable FissionCoolant salt, @Nullable ResourceLocation fuelFilter) {
        boolean itemFuel() {
            return role == Role.FUEL_PORT || role == Role.CHAMBER_PORT;
        }

        boolean fluid() {
            return !itemFuel() && role != Role.IRRADIATOR_PORT && role != Role.POWER_PORT;
        }
    }

    record Validation(@Nullable SolidFissionLayout layout, Component message, @Nullable BlockPos errorPosition) {}

    static Validation find(ServerLevel level, BlockPos origin) {
        Cuboid bounds = Cuboid.findConnected(level, origin, NCFission.searchLength(),
                SolidFissionLayout::connectsReactor);
        if (bounds == null) {
            return invalid("too_large", origin);
        }
        int minimum = NCFission.minSize() + 2;
        if (bounds.xLength() < minimum || bounds.yLength() < minimum || bounds.zLength() < minimum) {
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
            case SOLID -> Role.HEAT_SINK;
            case PEBBLE -> Role.PEBBLE_COOLER;
            case SALT -> Role.SALT_HEATER;
        };
        List<BlockPos> cells = new ArrayList<>();
        List<BlockPos> sinks = new ArrayList<>();
        List<Port> ports = new ArrayList<>();
        List<BlockPos> sources = new ArrayList<>();
        List<BlockPos> shields = new ArrayList<>();
        List<BlockPos> managers = new ArrayList<>();
        List<BlockPos> irradiators = new ArrayList<>();
        List<BlockPos> conductors = new ArrayList<>();
        List<BlockPos> monitors = new ArrayList<>();
        List<BlockPos> computers = new ArrayList<>();
        List<BlockPos> frames = new ArrayList<>();
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
                        case HEAT_SINK, PEBBLE_COOLER, SALT_HEATER -> {
                            if (role != coolingPart) {
                                return invalid("interior", pos);
                            }
                            sinks.add(pos.immutable());
                        }
                        case SHIELD -> shields.add(pos.immutable());
                        case IRRADIATOR -> irradiators.add(pos.immutable());
                        case CONDUCTOR -> conductors.add(pos.immutable());
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
                        case CASING, GLASS -> {}
                        case COMPUTER -> computers.add(pos.immutable());
                        case MONITOR -> monitors.add(pos.immutable());
                        case CONTROLLER, PEBBLE_CONTROLLER, SALT_CONTROLLER -> {
                            if (!pos.equals(origin)) {
                                return invalid("multiple_controllers", pos);
                            }
                        }
                        case SOURCE -> sources.add(pos.immutable());
                        case SOURCE_MANAGER, SHIELD_MANAGER -> managers.add(pos.immutable());
                        case VENT, FUEL_PORT, CHAMBER_PORT, COOLER_PORT, VESSEL_PORT, HEATER_PORT, IRRADIATOR_PORT, POWER_PORT -> {
                            if (!validPort(variant, role) || !(level.getBlockEntity(pos) instanceof FissionPortBlockEntity entity)) {
                                return invalid("wall", pos);
                            }
                            var wall = (FissionWallBlock) state.getBlock();
                            ports.add(new Port(pos.immutable(), bounds.outward(pos), role, state.getValue(FissionWallBlock.ACTIVE),
                                    entity.gasChannel(), wall.saltCoolant(), role == Role.FUEL_PORT || role == Role.CHAMBER_PORT
                                            || role == Role.VESSEL_PORT ? entity.fuelFilter() : null));
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
                    frames.add(pos.immutable());
                }
            }
        }

        if (bounds.boundaryAxes(origin) != 1) {
            return invalid("wall", origin);
        }
        if (hasUnpairedPorts(ports, Role.VENT, null)) {
            return invalid("port_pair", origin);
        }
        if (!cells.isEmpty() && variant != FissionVariant.SALT) {
            Role fuelPort = variant == FissionVariant.SOLID ? Role.FUEL_PORT : Role.CHAMBER_PORT;
            var checked = new HashSet<ResourceLocation>();
            for (Port port : ports) {
                if (port.role() == fuelPort && checked.add(port.fuelFilter())
                        && hasUnpairedPorts(ports, fuelPort, port.fuelFilter())) {
                    return invalid("port_pair", port.pos());
                }
            }
        }
        if (variant == FissionVariant.SALT && !cells.isEmpty()) {
            var filters = new HashSet<ResourceLocation>();
            for (Port port : ports) {
                if (port.role() == Role.VESSEL_PORT) {
                    filters.add(port.fuelFilter());
                }
            }
            if (filters.stream().anyMatch(filter -> hasUnpairedPorts(ports, Role.VESSEL_PORT, filter))) {
                return invalid("port_pair", origin);
            }
        }
        return new Validation(new SolidFissionLayout(bounds, List.copyOf(cells), List.copyOf(sinks),
                List.copyOf(ports), List.copyOf(sources), List.copyOf(shields), List.copyOf(managers), List.copyOf(irradiators),
                List.copyOf(conductors), List.copyOf(monitors), List.copyOf(computers), List.copyOf(frames), variant),
                Component.translatable("message.nuclearcraft.fission.assembled"), null);
    }

    private static boolean validPort(FissionVariant variant, Role role) {
        return role == Role.VENT || role == Role.IRRADIATOR_PORT || role == Role.POWER_PORT || switch (variant) {
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
                case FUEL_PORT, CHAMBER_PORT, VESSEL_PORT -> port.fuelFilter();
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
                pos.getX(), pos.getY(), pos.getZ()), pos.immutable());
    }
}
