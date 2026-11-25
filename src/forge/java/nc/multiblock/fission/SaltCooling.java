package nc.multiblock.fission;

import nc.init.HeatExchangerConfig;
import nc.init.NCSaltCoolants;
import nc.init.NCSaltFission;
import nc.recipe.FissionCoolantRecipe;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraftforge.fluids.capability.templates.FluidTank;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiPredicate;

final class SaltCooling {
    private static final int VOLUME_PER_HEATER = 36;

    private final SolidFissionController owner;
    private final Map<FissionCoolant, CoolantTanks> circuits = new HashMap<>();
    private final Set<FissionCoolant> connected = new HashSet<>();
    private final Map<BlockPos, Fraction> credit = new HashMap<>();
    private record Fraction(FissionCoolant coolant, double amount) {}

    SaltCooling(SolidFissionController owner) {
        this.owner = owner;
    }

    private CoolantTanks circuit(FissionCoolant coolant) {
        return circuits.computeIfAbsent(coolant, kind -> new CoolantTanks(kind, owner));
    }

    FluidTank tank(FissionCoolant coolant, boolean output) {
        return circuit(coolant).tank(output);
    }

    @Nullable CoolantTanks connectedTanks(FissionCoolant coolant) {
        return connected.contains(coolant) ? circuits.get(coolant) : null;
    }

    double progress(BlockPos pos, FissionCoolant coolant) {
        Fraction fraction = credit.get(pos);
        return fraction != null && coolant.equals(fraction.coolant()) ? fraction.amount() : 0;
    }

    void clearMaterial() {
        circuits.values().forEach(CoolantTanks::clear);
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

    Map<BlockPos, FissionCoolantRecipe> available(ServerLevel level, SolidFissionLayout layout,
                                                 BiPredicate<FissionPlacementRules.Part, BlockPos> neighbors) {
        var eligible = new HashMap<FissionCoolant, List<BlockPos>>();
        var recipes = new HashMap<BlockPos, FissionCoolantRecipe>();
        for (BlockPos pos : layout.sinks()) {
            if (level.getBlockEntity(pos) instanceof SaltHeater heater) {
                var recipe = heater.tanks().ready(null);
                if (recipe != null) {
                    eligible.computeIfAbsent(heater.coolant(), ignored -> new ArrayList<>()).add(pos);
                    recipes.put(pos, recipe);
                }
            }
        }
        recipes.keySet().retainAll(NCSaltFission.placementRules().active(eligible, neighbors).keySet());
        return recipes;
    }

    double heat(ServerLevel level, BlockPos pos, FissionCoolantRecipe recipe, double efficiency) {
        if (efficiency <= 0 || !(level.getBlockEntity(pos) instanceof SaltHeater heater)) {
            return 0D;
        }
        CoolantTanks tanks = heater.tanks();
        if (tanks.ready(null) != recipe) {
            return 0D;
        }
        FissionCoolant coolant = heater.coolant();
        int inputAmount = recipe.input().matchedAmount(tanks.cold.getFluid(), 1);
        double batchesPerTick = efficiency * recipe.cooling() / (HeatExchangerConfig.nakHeatPerMb() * inputAmount);
        double progress = progress(pos, coolant) + batchesPerTick;
        credit.put(pos, new Fraction(coolant, progress - Math.floor(progress)));
        tanks.process(null, recipe, (int) progress, level.random);
        owner.setChanged();
        return batchesPerTick;
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
}
