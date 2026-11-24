package nc.multiblock.fission;

import nc.init.HeatExchangerConfig;
import nc.init.NCSaltCoolants;
import nc.init.NCSaltFission;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.templates.FluidTank;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

import static net.minecraftforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE;

final class SaltCooling {
    private static final int BASE_VOLUME = 1_296;
    private static final int VOLUME_PER_HEATER = 36;

    private final SolidFissionController owner;
    private final Map<NCSaltCoolants, Tanks> circuits = new EnumMap<>(NCSaltCoolants.class);
    private final Set<NCSaltCoolants> connected = EnumSet.noneOf(NCSaltCoolants.class);
    private final Map<BlockPos, Fraction> credit = new HashMap<>();
    private record Fraction(NCSaltCoolants coolant, double amount) {}

    SaltCooling(SolidFissionController owner) {
        this.owner = owner;
    }

    private Tanks circuit(NCSaltCoolants coolant) {
        return circuits.computeIfAbsent(coolant, kind -> new Tanks(kind, owner));
    }

    FluidTank tank(NCSaltCoolants coolant, boolean output) {
        return circuit(coolant).tank(output);
    }

    @Nullable Tanks connectedTanks(NCSaltCoolants coolant) {
        return connected.contains(coolant) ? circuits.get(coolant) : null;
    }

    double progress(BlockPos pos, NCSaltCoolants coolant) {
        Fraction fraction = credit.get(pos);
        return fraction != null && fraction.coolant() == coolant ? fraction.amount() : 0;
    }

    void clearMaterial() {
        circuits.values().forEach(Tanks::clear);
        credit.clear();
        owner.setChanged();
    }

    void resize(ServerLevel level, SolidFissionLayout layout) {
        connected.clear();
        for (var port : layout.ports()) {
            if (port.salt() != null) {
                connected.add(port.salt());
            }
        }
        var counts = new EnumMap<NCSaltCoolants, Integer>(NCSaltCoolants.class);
        for (BlockPos pos : layout.sinks()) {
            if (SaltHeaterBlock.entity(level, pos) instanceof SaltHeater heater) {
                counts.merge(heater.coolant(), 1, Integer::sum);
                heater.bind(owner);
            }
        }
        for (var coolant : connected) {
            circuit(coolant).resize((long) VOLUME_PER_HEATER * counts.getOrDefault(coolant, 0));
        }
        credit.keySet().retainAll(layout.sinks());
    }

    Map<BlockPos, NCSaltCoolants> available(ServerLevel level, SolidFissionLayout layout,
                                            Set<BlockPos> vessels, Set<BlockPos> moderators) {
        var eligible = new EnumMap<NCSaltCoolants, List<BlockPos>>(NCSaltCoolants.class);
        for (BlockPos pos : layout.sinks()) {
            if (level.getBlockEntity(pos) instanceof SaltHeater heater) {
                NCSaltCoolants coolant = heater.coolant();
                if (heater.tanks().transferable() > 0 && NCSaltFission.cooling(coolant) > 0) {
                    eligible.computeIfAbsent(coolant, ignored -> new ArrayList<>()).add(pos);
                }
            }
        }
        return NCSaltFission.placementRules().active(eligible,
                FissionPlacementRules.neighborhood(level, layout.bounds(), FissionPlacementRules.Part.VESSEL,
                        vessels, moderators));
    }

    boolean heat(ServerLevel level, BlockPos pos, NCSaltCoolants coolant, double efficiency) {
        if (efficiency <= 0 || !(level.getBlockEntity(pos) instanceof SaltHeater heater) || heater.coolant() != coolant) {
            return false;
        }
        Tanks tanks = heater.tanks();
        int transferable = tanks.transferable();
        if (transferable <= 0) {
            return false;
        }
        Fraction previous = credit.get(pos);
        double progress = (previous != null && previous.coolant() == coolant ? previous.amount() : 0)
                + efficiency * NCSaltFission.cooling(coolant) / HeatExchangerConfig.nakHeatPerMb();
        int amount = Math.min(transferable, (int) progress);
        credit.put(pos, new Fraction(coolant, progress - Math.floor(progress)));
        if (amount > 0) {
            tanks.hot.fill(new FluidStack(coolant.hot(), amount), EXECUTE);
            tanks.cold.drain(amount, EXECUTE);
        }
        owner.setChanged();
        return true;
    }

    int fill(Level level, SolidFissionLayout layout, boolean output) {
        long amount = 0;
        long capacity = 0;
        for (var coolant : connected) {
            var tank = tank(coolant, output);
            amount += tank.getFluidAmount();
            capacity += tank.getCapacity();
        }
        for (BlockPos pos : layout.sinks()) {
            if (level.getBlockEntity(pos) instanceof SaltHeater heater && !connected.contains(heater.coolant())) {
                var tank = heater.localTanks().tank(output);
                amount += tank.getFluidAmount();
                capacity += tank.getCapacity();
            }
        }
        return capacity == 0 ? 0 : (int) (1000L * amount / capacity);
    }

    CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        circuits.forEach((coolant, circuit) -> tag.put(coolant.id(), circuit.save()));
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
                circuit(coolant).load(tag.getCompound(coolant.id()));
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

    static final class Tanks {
        private final FluidTank cold;
        private final FluidTank hot;
        private long minimum = BASE_VOLUME;

        Tanks(NCSaltCoolants coolant, BlockEntity owner) {
            cold = makeTank(owner, stack -> stack.getFluid() == coolant.cold());
            hot = makeTank(owner, stack -> stack.getFluid() == coolant.hot());
        }

        private static FluidTank makeTank(BlockEntity owner, Predicate<FluidStack> accepts) {
            return new FluidTank(BASE_VOLUME, accepts) {
                @Override
                protected void onContentsChanged() {
                    owner.setChanged();
                }
            };
        }

        FluidTank tank(boolean output) {
            return output ? hot : cold;
        }

        void resize(long capacity) {
            minimum = Math.max(BASE_VOLUME, capacity);
            SaltFuelBank.resize(cold, minimum);
            SaltFuelBank.resize(hot, minimum);
        }

        int transferable() {
            if (!cold.isFluidValid(cold.getFluid()) || (!hot.isEmpty() && !hot.isFluidValid(hot.getFluid()))) {
                return 0;
            }
            return Math.min(cold.getFluidAmount(), hot.getCapacity() - hot.getFluidAmount());
        }

        void clear() {
            cold.drain(cold.getFluidAmount(), EXECUTE);
            hot.drain(hot.getFluidAmount(), EXECUTE);
        }

        CompoundTag save() {
            var saved = new CompoundTag();
            saved.put("input", cold.writeToNBT(new CompoundTag()));
            saved.put("output", hot.writeToNBT(new CompoundTag()));
            return saved;
        }

        void load(CompoundTag saved) {
            cold.readFromNBT(saved.getCompound("input"));
            hot.readFromNBT(saved.getCompound("output"));
            resize(minimum);
        }
    }
}
