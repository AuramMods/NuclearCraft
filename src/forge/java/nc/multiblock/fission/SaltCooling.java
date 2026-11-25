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
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.templates.FluidTank;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiPredicate;
import java.util.function.Predicate;

import static net.minecraftforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE;

final class SaltCooling {
    private static final int BASE_VOLUME = 1_296;
    private static final int VOLUME_PER_HEATER = 36;

    private final SolidFissionController owner;
    private final Map<FissionCoolant, Tanks> circuits = new HashMap<>();
    private final Set<FissionCoolant> connected = new HashSet<>();
    private final Map<BlockPos, Fraction> credit = new HashMap<>();
    private record Fraction(FissionCoolant coolant, double amount) {}

    SaltCooling(SolidFissionController owner) {
        this.owner = owner;
    }

    private Tanks circuit(FissionCoolant coolant) {
        return circuits.computeIfAbsent(coolant, kind -> new Tanks(kind, owner));
    }

    FluidTank tank(FissionCoolant coolant, boolean output) {
        return circuit(coolant).tank(output);
    }

    @Nullable Tanks connectedTanks(FissionCoolant coolant) {
        return connected.contains(coolant) ? circuits.get(coolant) : null;
    }

    double progress(BlockPos pos, FissionCoolant coolant) {
        Fraction fraction = credit.get(pos);
        return fraction != null && coolant.equals(fraction.coolant()) ? fraction.amount() : 0;
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
        var counts = new HashMap<FissionCoolant, Integer>();
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

    Map<BlockPos, FissionCoolant> available(ServerLevel level, SolidFissionLayout layout,
                                            BiPredicate<FissionPlacementRules.Part, BlockPos> neighbors) {
        var eligible = new HashMap<FissionCoolant, List<BlockPos>>();
        for (BlockPos pos : layout.sinks()) {
            if (level.getBlockEntity(pos) instanceof SaltHeater heater) {
                FissionCoolant coolant = heater.coolant();
                if (coolant.cooling() > 0 && heater.tanks().transferable() > 0) {
                    eligible.computeIfAbsent(coolant, ignored -> new ArrayList<>()).add(pos);
                }
            }
        }
        return NCSaltFission.placementRules().active(eligible, neighbors);
    }

    boolean heat(ServerLevel level, BlockPos pos, FissionCoolant coolant, double efficiency) {
        if (efficiency <= 0 || !(level.getBlockEntity(pos) instanceof SaltHeater heater) || !coolant.equals(heater.coolant())) {
            return false;
        }
        Tanks tanks = heater.tanks();
        int transferable = tanks.transferable();
        if (transferable <= 0) {
            return false;
        }
        Fraction previous = credit.get(pos);
        int inputAmount = coolant.inputIngredient().amount();
        double progress = (previous != null && coolant.equals(previous.coolant()) ? previous.amount() : 0)
                + efficiency * coolant.cooling() / (HeatExchangerConfig.nakHeatPerMb() * inputAmount);
        int amount = Math.min(transferable, (int) progress);
        credit.put(pos, new Fraction(coolant, progress - Math.floor(progress)));
        if (amount > 0) {
            tanks.hot.fill(new FluidStack(coolant.hot(), coolant.outputAmount() * amount), EXECUTE);
            tanks.cold.drain(inputAmount * amount, EXECUTE);
        }
        owner.setChanged();
        return true;
    }

    FissionTankTotals tankTotals(Level level, SolidFissionLayout layout) {
        var totals = new FissionTankTotals();
        for (var coolant : connected) {
            var tanks = circuit(coolant);
            totals.add(tanks.cold, tanks.hot);
        }
        for (BlockPos pos : layout.sinks()) {
            if (level.getBlockEntity(pos) instanceof SaltHeater heater && !connected.contains(heater.coolant())) {
                var tanks = heater.localTanks();
                totals.add(tanks.cold, tanks.hot);
            }
        }
        return totals;
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
                if (fraction.coolant() instanceof NCSaltCoolants stock) {
                    state.putInt("coolant", stock.ordinal());
                } else {
                    state.putString("coolant_id", fraction.coolant().id());
                }
                savedCredit.add(state);
            }
        });
        tag.put("fractions", savedCredit);
        return tag;
    }

    void load(CompoundTag tag) {
        var coolants = NCSaltFission.coolants();
        for (FissionCoolant coolant : coolants.values()) {
            if (tag.contains(coolant.id(), Tag.TAG_COMPOUND) || circuits.containsKey(coolant)) {
                circuit(coolant).load(tag.getCompound(coolant.id()));
            }
        }
        credit.clear();
        var stock = NCSaltCoolants.values();
        for (Tag entry : tag.getList("fractions", Tag.TAG_COMPOUND)) {
            CompoundTag state = (CompoundTag) entry;
            double amount = state.getDouble("amount");
            int kind = state.getInt("coolant");
            FissionCoolant coolant = state.contains("coolant_id", Tag.TAG_STRING)
                    ? coolants.get(state.getString("coolant_id")) : kind >= 0 && kind < stock.length ? stock[kind] : null;
            if (amount > 0 && amount < 1 && coolant != null) {
                credit.put(BlockPos.of(state.getLong("pos")), new Fraction(coolant, amount));
            }
        }
    }

    static final class Tanks {
        private final FissionCoolant coolant;
        private final FluidTank cold;
        private final FluidTank hot;
        private long minimum = BASE_VOLUME;

        Tanks(FissionCoolant coolant, BlockEntity owner) {
            this.coolant = coolant;
            cold = makeTank(owner, coolant.inputIngredient().amount(), coolant.inputIngredient()::accepts);
            hot = makeTank(owner, coolant.outputAmount(), stack -> !stack.isEmpty() && stack.getFluid() == coolant.hot());
        }

        private static FluidTank makeTank(BlockEntity owner, int batch, Predicate<FluidStack> accepts) {
            return new FluidTank(Math.max(BASE_VOLUME, batch), accepts) {
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
            SaltFuelBank.resize(cold, Math.max(minimum, coolant.inputIngredient().amount()));
            SaltFuelBank.resize(hot, Math.max(minimum, coolant.outputAmount()));
        }

        int transferable() {
            if (!cold.isFluidValid(cold.getFluid()) || coolant.hot() == Fluids.EMPTY
                    || (!hot.isEmpty() && !hot.isFluidValid(hot.getFluid()))) {
                return 0;
            }
            return Math.min(cold.getFluidAmount() / coolant.inputIngredient().amount(),
                    (hot.getCapacity() - hot.getFluidAmount()) / coolant.outputAmount());
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
