package nc.multiblock.fission;

import nc.init.HeatExchangerConfig;
import nc.init.NCSaltCoolants;
import nc.init.NCSaltFission;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.templates.FluidTank;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

import static net.minecraftforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE;

final class SaltCooling {
    private static final int PORT_VOLUME = 36_000;

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

    @Nullable FluidTank existingTank(NCSaltCoolants coolant, boolean output) {
        Circuit circuit = circuits.get(coolant);
        return circuit == null ? null : output ? circuit.hot : circuit.cold;
    }

    double progress(BlockPos pos, NCSaltCoolants coolant) {
        Fraction fraction = credit.get(pos);
        return fraction != null && fraction.coolant() == coolant ? fraction.amount() : 0;
    }

    void clearMaterial() {
        for (var circuit : circuits.values()) {
            circuit.cold.setFluid(FluidStack.EMPTY);
            circuit.hot.setFluid(FluidStack.EMPTY);
        }
        credit.clear();
        owner.setChanged();
    }

    void resize(SolidFissionLayout layout) {
        var counts = new EnumMap<NCSaltCoolants, Integer>(NCSaltCoolants.class);
        for (var port : layout.ports()) {
            if (port.salt() != null) {
                counts.merge(port.salt(), 1, Integer::sum);
            }
        }
        counts.forEach((coolant, count) -> {
            SaltFuelBank.resize(tank(coolant, false), (long) PORT_VOLUME * count);
            SaltFuelBank.resize(tank(coolant, true), (long) PORT_VOLUME * count);
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
        return NCSaltFission.placementRules().active(eligible,
                FissionPlacementRules.neighborhood(level, layout.bounds(), FissionPlacementRules.Part.VESSEL,
                        vessels, moderators));
    }

    boolean heat(BlockPos pos, NCSaltCoolants coolant, double efficiency) {
        Circuit circuit = circuits.get(coolant);
        if (circuit.transferable() <= 0 || efficiency <= 0) {
            return false;
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
        return true;
    }

    long amount(boolean output) {
        return circuits.values().stream().mapToLong(circuit -> (output ? circuit.hot : circuit.cold).getFluidAmount()).sum();
    }

    long capacity(boolean output) {
        return circuits.values().stream().mapToLong(circuit -> (output ? circuit.hot : circuit.cold).getCapacity()).sum();
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
