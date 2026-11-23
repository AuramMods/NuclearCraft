package nc.multiblock.fission;

import nc.init.HeatExchangerConfig;
import nc.init.NCFission;
import nc.init.NCSaltCoolants;
import nc.init.NCSaltFission;
import nc.multiblock.Cuboid;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.templates.FluidTank;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

import static nc.init.NCSaltCoolants.*;
import static net.minecraftforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE;

final class SaltCooling {
    private static final int PORT_VOLUME = 36_000;
    // Dependencies precede the heaters that test them, including predicates with exact counts.
    private static final List<NCSaltCoolants> RULE_ORDER = List.of(NAK, IRON, REDSTONE, QUARTZ, GLOWSTONE, OBSIDIAN,
            NETHER_BRICK, LAPIS, GOLD, PRISMARINE, LEAD, SLIME, END_STONE, PURPUR, DIAMOND, EMERALD, COPPER,
            TIN, BORON, LITHIUM, MAGNESIUM, MANGANESE, ALUMINUM, SILVER, FLUORITE, VILLIAUMITE, CAROBBIITE,
            ARSENIC, LIQUID_NITROGEN, LIQUID_HELIUM, ENDERIUM, CRYOTHEUM);
    private static final Direction[] SIDES = Direction.values();
    private static final List<Direction> AXES = List.of(Direction.UP, Direction.NORTH, Direction.EAST);

    private final SolidFissionController owner;
    private final Map<NCSaltCoolants, Circuit> circuits = new EnumMap<>(NCSaltCoolants.class);
    private final Map<BlockPos, Fraction> credit = new HashMap<>();
    private record Fraction(NCSaltCoolants coolant, double amount) {}

    SaltCooling(SolidFissionController owner) {
        this.owner = owner;
    }

    FluidTank tank(NCSaltCoolants coolant, boolean output) {
        Circuit circuit = circuits.computeIfAbsent(coolant, Circuit::new);
        return output ? circuit.hot : circuit.cold;
    }

    void resize(SolidFissionLayout layout) {
        var counts = new EnumMap<NCSaltCoolants, Integer>(NCSaltCoolants.class);
        for (var port : layout.ports()) {
            if (port.salt() != null) {
                counts.merge(port.salt(), 1, Integer::sum);
            }
        }
        counts.forEach((coolant, count) -> {
            SaltFuelBank.resize(tank(coolant, false), PORT_VOLUME * count);
            SaltFuelBank.resize(tank(coolant, true), PORT_VOLUME * count);
        });
        credit.keySet().retainAll(layout.sinks());
    }

    Map<BlockPos, NCSaltCoolants> available(ServerLevel level, SolidFissionLayout layout,
                                            Set<BlockPos> vessels, Set<BlockPos> moderators) {
        var eligible = new EnumMap<NCSaltCoolants, List<BlockPos>>(NCSaltCoolants.class);
        for (BlockPos pos : layout.sinks()) {
            if (level.getBlockState(pos).getBlock() instanceof SaltHeaterBlock heater) {
                NCSaltCoolants coolant = heater.coolant();
                Circuit circuit = circuits.get(coolant);
                if (circuit != null && circuit.transferable() > 0 && NCSaltFission.cooling(coolant) > 0) {
                    eligible.computeIfAbsent(coolant, ignored -> new ArrayList<>()).add(pos);
                }
            }
        }
        var active = new HashMap<BlockPos, NCSaltCoolants>();
        for (NCSaltCoolants coolant : RULE_ORDER) {
            for (BlockPos pos : eligible.getOrDefault(coolant, List.of())) {
                if (matchesRule(level, layout.bounds(), pos, coolant, vessels, moderators, active)) {
                    active.put(pos, coolant);
                }
            }
        }
        return active;
    }

    private record Neighbors(ServerLevel level, Cuboid bounds, BlockPos pos, Set<BlockPos> vessels,
                             Set<BlockPos> moderators, Map<BlockPos, NCSaltCoolants> heaters) {
        int vesselsCount() { return count(vessels::contains); }

        boolean moderator(BlockPos part) {
            return moderators.contains(part) && NCFission.moderator(level.getBlockState(part)) != null;
        }

        boolean reflector(BlockPos part) {
            return moderators.contains(part) && NCFission.reflector(level.getBlockState(part)) != null;
        }

        int moderatorsCount() { return count(this::moderator); }
        int casings() { return count(part -> bounds.boundaryAxes(part) == 1); }
        int heaters(NCSaltCoolants coolant) { return count(part -> heaters.get(part) == coolant); }
        boolean axialHeaters(NCSaltCoolants coolant) { return axial(part -> heaters.get(part) == coolant); }

        int count(Predicate<BlockPos> matches) {
            int count = 0;
            for (Direction direction : SIDES) {
                if (matches.test(pos.relative(direction))) {
                    count++;
                }
            }
            return count;
        }

        boolean axial(Predicate<BlockPos> matches) {
            for (Direction direction : AXES) {
                if (matches.test(pos.relative(direction)) && matches.test(pos.relative(direction.getOpposite()))) {
                    return true;
                }
            }
            return false;
        }
    }

    private static boolean matchesRule(ServerLevel level, Cuboid bounds, BlockPos pos, NCSaltCoolants coolant,
                                       Set<BlockPos> vessels, Set<BlockPos> moderators, Map<BlockPos, NCSaltCoolants> active) {
        var n = new Neighbors(level, bounds, pos, vessels, moderators, active);
        return switch (coolant) {
            case NAK -> n.vesselsCount() >= 1;
            case IRON -> n.moderatorsCount() >= 1;
            case REDSTONE -> n.vesselsCount() >= 1 && n.moderatorsCount() >= 1;
            case QUARTZ -> n.heaters(REDSTONE) >= 1;
            case OBSIDIAN -> n.axialHeaters(GLOWSTONE);
            case NETHER_BRICK -> n.heaters(OBSIDIAN) >= 1;
            case GLOWSTONE -> n.moderatorsCount() >= 2;
            case LAPIS -> n.vesselsCount() >= 1 && n.casings() >= 1;
            case GOLD -> n.heaters(IRON) == 2;
            case PRISMARINE -> n.heaters(NAK) >= 2;
            case SLIME -> n.heaters(NAK) == 1 && n.heaters(LEAD) >= 2;
            case END_STONE -> n.count(n::reflector) >= 1;
            case PURPUR -> n.count(n::reflector) >= 1 && n.heaters(IRON) >= 1;
            case DIAMOND -> n.vesselsCount() >= 1 && n.heaters(GOLD) >= 1;
            case EMERALD -> n.moderatorsCount() >= 1 && n.heaters(PRISMARINE) >= 1;
            case COPPER -> n.heaters(NAK) >= 1;
            case TIN -> n.axialHeaters(LAPIS);
            case LEAD -> n.heaters(IRON) >= 1;
            case BORON -> n.heaters(QUARTZ) == 1 && n.casings() >= 1;
            case LITHIUM -> n.heaters(LEAD) == 2 && n.axialHeaters(LEAD) && n.casings() >= 1;
            case MAGNESIUM -> n.moderatorsCount() == 1 && n.casings() >= 1;
            case MANGANESE -> n.vesselsCount() >= 2;
            case ALUMINUM -> n.heaters(QUARTZ) >= 1 && n.heaters(LAPIS) >= 1;
            case SILVER -> n.heaters(GLOWSTONE) >= 2 && n.heaters(TIN) >= 1;
            case FLUORITE -> n.heaters(GOLD) >= 1 && n.heaters(PRISMARINE) >= 1;
            case VILLIAUMITE -> n.heaters(REDSTONE) >= 1 && n.heaters(END_STONE) >= 1;
            case CAROBBIITE -> n.heaters(END_STONE) >= 1 && n.heaters(COPPER) >= 1;
            case ARSENIC -> n.axial(n::reflector);
            case LIQUID_NITROGEN -> n.heaters(COPPER) >= 2 && n.heaters(PURPUR) >= 1;
            case LIQUID_HELIUM -> n.heaters(REDSTONE) == 2;
            case ENDERIUM -> n.moderatorsCount() >= 3;
            case CRYOTHEUM -> n.vesselsCount() >= 3;
        };
    }

    void heat(BlockPos pos, NCSaltCoolants coolant, double efficiency) {
        Circuit circuit = circuits.get(coolant);
        if (circuit.transferable() <= 0 || efficiency <= 0) {
            return;
        }
        Fraction previous = credit.get(pos);
        double progress = (previous != null && previous.coolant() == coolant ? previous.amount() : 0)
                + efficiency * NCSaltFission.cooling(coolant) / HeatExchangerConfig.nakHeatPerMb();
        int amount = Math.min(circuit.transferable(), (int) progress);
        credit.put(pos, new Fraction(coolant, progress - Math.floor(progress)));
        if (amount > 0) {
            circuit.hot.fill(new FluidStack(coolant.hot(), amount), EXECUTE);
            circuit.cold.drain(amount, EXECUTE);
        }
        owner.setChanged();
    }

    int amount(boolean output) {
        return circuits.values().stream().mapToInt(circuit -> (output ? circuit.hot : circuit.cold).getFluidAmount()).sum();
    }

    int capacity(boolean output) {
        return circuits.values().stream().mapToInt(circuit -> (output ? circuit.hot : circuit.cold).getCapacity()).sum();
    }

    CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        circuits.forEach((coolant, circuit) -> {
            CompoundTag state = new CompoundTag();
            state.put("input", circuit.cold.writeToNBT(new CompoundTag()));
            state.put("output", circuit.hot.writeToNBT(new CompoundTag()));
            tag.put(coolant.id(), state);
        });
        ListTag savedCredit = new ListTag();
        credit.forEach((pos, fraction) -> {
            if (fraction.amount() > 0) {
                CompoundTag state = new CompoundTag();
                state.putLong("pos", pos.asLong());
                state.putDouble("amount", fraction.amount());
                state.putInt("coolant", fraction.coolant().ordinal());
                savedCredit.add(state);
            }
        });
        tag.put("fractions", savedCredit);
        return tag;
    }

    void load(CompoundTag tag) {
        var coolants = NCSaltCoolants.values();
        for (NCSaltCoolants coolant : coolants) {
            if (tag.contains(coolant.id(), Tag.TAG_COMPOUND) || circuits.containsKey(coolant)) {
                Circuit circuit = circuits.computeIfAbsent(coolant, Circuit::new);
                CompoundTag state = tag.getCompound(coolant.id());
                circuit.cold.readFromNBT(state.getCompound("input"));
                circuit.hot.readFromNBT(state.getCompound("output"));
                SaltFuelBank.resize(circuit.cold, PORT_VOLUME);
                SaltFuelBank.resize(circuit.hot, PORT_VOLUME);
            }
        }
        credit.clear();
        for (Tag entry : tag.getList("fractions", Tag.TAG_COMPOUND)) {
            CompoundTag state = (CompoundTag) entry;
            double amount = state.getDouble("amount");
            int kind = state.getInt("coolant");
            if (amount > 0 && amount < 1 && kind >= 0 && kind < coolants.length) {
                credit.put(BlockPos.of(state.getLong("pos")), new Fraction(coolants[kind], amount));
            }
        }
    }

    private FluidTank makeTank(Predicate<FluidStack> accepts) {
        return new FluidTank(PORT_VOLUME, accepts) {
            @Override
            protected void onContentsChanged() {
                owner.setChanged();
            }
        };
    }

    private final class Circuit {
        final FluidTank cold;
        final FluidTank hot;

        Circuit(NCSaltCoolants coolant) {
            cold = makeTank(stack -> stack.getFluid() == coolant.cold());
            hot = makeTank(stack -> stack.getFluid() == coolant.hot());
        }

        int transferable() {
            return Math.min(cold.getFluidAmount(), hot.getCapacity() - hot.getFluidAmount());
        }
    }
}
