package nc.multiblock.fission;

import nc.init.NCGasCoolants;
import nc.init.NCPebbleFission;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler.FluidAction;
import net.minecraftforge.fluids.capability.templates.FluidTank;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

final class PebbleCooling {
    private static final int HOT_PER_COLD = 2;
    private static final int PORT_VOLUME = 1296;
    private final SolidFissionController owner;
    private final EnumMap<NCGasCoolants, Tanks> circuits = new EnumMap<>(NCGasCoolants.class);
    private final EnumSet<NCGasCoolants> connected = EnumSet.noneOf(NCGasCoolants.class);
    private final Map<BlockPos, Double> credit = new HashMap<>();

    PebbleCooling(SolidFissionController owner) {
        this.owner = owner;
    }

    Tanks circuit(NCGasCoolants gas) {
        return circuits.computeIfAbsent(gas, key -> new Tanks(key, owner, stack -> true));
    }

    Tanks tanks(PebbleCooler cooler) {
        return connected.contains(cooler.gas()) ? circuit(cooler.gas()) : cooler.localTanks();
    }

    void clearMaterial() {
        circuits.values().forEach(Tanks::clear);
        credit.clear();
        owner.setChanged();
    }

    double progress(BlockPos pos) {
        return credit.getOrDefault(pos, 0D);
    }

    void resize(ServerLevel level, SolidFissionLayout layout) {
        var portCounts = new EnumMap<NCGasCoolants, Integer>(NCGasCoolants.class);
        for (var port : layout.ports()) {
            if (port.gas() != null) {
                portCounts.merge(port.gas(), 1, Integer::sum);
            }
        }
        connected.clear();
        connected.addAll(portCounts.keySet());
        var coolerCounts = new EnumMap<NCGasCoolants, Integer>(NCGasCoolants.class);
        for (BlockPos pos : layout.sinks()) {
            if (level.getBlockState(pos).getBlock() instanceof PebbleCoolerBlock cooler) {
                coolerCounts.merge(cooler.gas(), 1, Integer::sum);
            }
        }
        for (var entry : portCounts.entrySet()) {
            Tanks tanks = circuit(entry.getKey());
            int capacity = Math.max(PORT_VOLUME * entry.getValue(), 36 * coolerCounts.getOrDefault(entry.getKey(), 0));
            // Retain both inputs and products through changes to the shell.
            resize(tanks.hot, capacity);
            resize(tanks.cold, capacity);
        }
        credit.keySet().retainAll(layout.sinks());
    }

    private static void resize(FluidTank tank, int minimum) {
        tank.setCapacity(Math.max(minimum, tank.getFluidAmount()));
    }

    Map<BlockPos, NCGasCoolants> available(ServerLevel level, SolidFissionLayout layout, Set<BlockPos> chambers,
                                            Set<BlockPos> moderators) {
        var eligible = new EnumMap<NCGasCoolants, List<BlockPos>>(NCGasCoolants.class);
        for (BlockPos pos : layout.sinks()) {
            if (level.getBlockEntity(pos) instanceof PebbleCooler cooler) {
                NCGasCoolants gas = cooler.gas();
                if (tanks(cooler).transferable() > 0 && NCPebbleFission.cooling(gas) > 0) {
                    eligible.computeIfAbsent(gas, ignored -> new ArrayList<>()).add(pos);
                }
            }
        }
        return NCPebbleFission.placementRules().active(eligible,
                FissionPlacementRules.neighborhood(level, layout.bounds(), FissionPlacementRules.Part.CHAMBER,
                        chambers, moderators));
    }

    void heatGas(BlockPos pos, NCGasCoolants gas, double multiplier) {
        if (!(owner.getLevel().getBlockEntity(pos) instanceof PebbleCooler cooler)) {
            return;
        }
        Tanks tanks = tanks(cooler);
        int transferable = tanks.transferable();
        if (transferable <= 0 || multiplier <= 0) {
            return;
        }
        double progress = credit.getOrDefault(pos, 0D) + multiplier * NCPebbleFission.cooling(gas) / NCPebbleFission.heatPerMb();
        int amount = Math.min(transferable, (int) progress);
        credit.put(pos, progress - Math.floor(progress));
        if (amount > 0) {
            tanks.hot.fill(new FluidStack(gas.hot(), HOT_PER_COLD * amount), FluidAction.EXECUTE);
            tanks.cold.drain(amount, FluidAction.EXECUTE);
        }
        owner.setChanged();
    }

    int gasAmount(boolean output) {
        return circuits.values().stream().mapToInt(tanks -> (output ? tanks.hot : tanks.cold).getFluidAmount()).sum();
    }

    int gasCapacity(boolean output) {
        return circuits.values().stream().mapToInt(tanks -> (output ? tanks.hot : tanks.cold).getCapacity()).sum();
    }

    CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        for (var entry : circuits.entrySet()) {
            tag.put(entry.getKey().id(), entry.getValue().save());
        }
        ListTag fractions = new ListTag();
        for (var entry : credit.entrySet()) {
            if (entry.getValue() > 0) {
                CompoundTag value = new CompoundTag();
                value.putLong("pos", entry.getKey().asLong());
                value.putDouble("value", entry.getValue());
                fractions.add(value);
            }
        }
        tag.put("fractions", fractions);
        return tag;
    }

    void load(CompoundTag tag) {
        for (NCGasCoolants gas : NCGasCoolants.values()) {
            if (tag.contains(gas.id(), Tag.TAG_COMPOUND) || circuits.containsKey(gas)) {
                circuit(gas).load(tag.getCompound(gas.id()));
            }
        }
        credit.clear();
        for (Tag stored : tag.getList("fractions", Tag.TAG_COMPOUND)) {
            CompoundTag fraction = (CompoundTag) stored;
            double value = fraction.getDouble("value");
            if (value > 0 && value < 1) {
                credit.put(BlockPos.of(fraction.getLong("pos")), value);
            }
        }
    }

    private static FluidTank createTank(BlockEntity owner, Predicate<FluidStack> accepts) {
        return new FluidTank(PORT_VOLUME, accepts) {
            @Override
            protected void onContentsChanged() {
                owner.setChanged();
            }
        };
    }

    static final class Tanks {
        final FluidTank cold;
        final FluidTank hot;

        Tanks(NCGasCoolants gas, BlockEntity owner, Predicate<FluidStack> acceptsCold) {
            cold = createTank(owner, stack -> stack.getFluid().is(gas.input()) && acceptsCold.test(stack));
            hot = createTank(owner, stack -> stack.getFluid() == gas.hot());
        }

        FluidTank tank(boolean output) {
            return output ? hot : cold;
        }

        void clear() {
            cold.drain(cold.getFluidAmount(), FluidAction.EXECUTE);
            hot.drain(hot.getFluidAmount(), FluidAction.EXECUTE);
        }

        int transferable() {
            return Math.min(cold.getFluidAmount(), (hot.getCapacity() - hot.getFluidAmount()) / HOT_PER_COLD);
        }

        CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.put("input", cold.writeToNBT(new CompoundTag()));
            tag.put("output", hot.writeToNBT(new CompoundTag()));
            return tag;
        }

        void load(CompoundTag tag) {
            cold.readFromNBT(tag.getCompound("input"));
            hot.readFromNBT(tag.getCompound("output"));
            resize(cold, cold.getCapacity());
            resize(hot, hot.getCapacity());
        }
    }
}
