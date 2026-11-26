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
import java.util.List;
import java.util.Map;
import java.util.function.BiPredicate;

final class SaltCooling {
    private static final int VOLUME_PER_HEATER = 36;

    private final SolidFissionController owner;
    private final Map<FissionCoolant, CoolantTanks> circuits = new HashMap<>();
    private final Map<FissionCoolant, SolidFissionLayout.Port> connected = new HashMap<>();
    private final Map<BlockPos, Fraction> legacyProgress = new HashMap<>();
    private record Fraction(FissionCoolant coolant, double amount) {}

    SaltCooling(SolidFissionController owner) {
        this.owner = owner;
    }

    private CoolantTanks circuit(FissionCoolant coolant) {
        return circuits.computeIfAbsent(coolant, kind -> new CoolantTanks(kind, owner, () -> {
            var port = connected.get(kind);
            return port == null ? null : port.filter();
        }));
    }

    FluidTank tank(FissionCoolant coolant, boolean output) {
        return circuit(coolant).tank(output);
    }

    @Nullable CoolantTanks connectedTanks(FissionCoolant coolant) {
        return connected.containsKey(coolant) ? circuits.get(coolant) : null;
    }

    void clearMaterial() {
        circuits.values().forEach(CoolantTanks::clear);
        legacyProgress.clear();
        owner.setChanged();
    }

    void resize(ServerLevel level, SolidFissionLayout layout) {
        connected.clear();
        for (var port : layout.ports()) {
            if (port.salt() != null) {
                connected.merge(port.salt(), port, (first, next) ->
                        next.filter() != null && (first.filter() == null || first.output() && !next.output()) ? next : first);
            }
        }
        var counts = new HashMap<FissionCoolant, Integer>();
        for (BlockPos pos : layout.sinks()) {
            if (SaltHeaterBlock.entity(level, pos) instanceof SaltHeater heater) {
                counts.merge(heater.coolant(), 1, Integer::sum);
                heater.bind(owner);
            }
        }
        for (var coolant : connected.keySet()) {
            circuit(coolant).resize((long) VOLUME_PER_HEATER * counts.getOrDefault(coolant, 0));
        }
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

    double heat(ServerLevel level, BlockPos pos, FissionCoolantRecipe recipe, double efficiency, boolean preview) {
        if (efficiency <= 0 || !(level.getBlockEntity(pos) instanceof SaltHeater heater)) {
            return 0D;
        }
        CoolantTanks tanks = heater.tanks();
        if (tanks.ready(null) != recipe) {
            return 0D;
        }
        int inputAmount = recipe.input().matchedAmount(tanks.cold.getFluid(), 1);
        double batchesPerTick = efficiency * recipe.cooling() / (HeatExchangerConfig.nakHeatPerMb() * inputAmount);
        if (!preview) {
            double progress = heater.progress().amount() + batchesPerTick;
            heater.progress().set(progress - Math.floor(progress));
            tanks.process(null, recipe, (int) progress, level.random);
        }
        return batchesPerTick;
    }

    void restoreLegacyProgress(ServerLevel level, SolidFissionLayout layout) {
        if (legacyProgress.isEmpty()) {
            return;
        }
        for (BlockPos pos : layout.sinks()) {
            var saved = legacyProgress.get(pos);
            if (saved != null && SaltHeaterBlock.entity(level, pos) instanceof SaltHeater heater
                    && heater.coolant().equals(saved.coolant())) {
                heater.progress().restoreFromController(saved.amount());
            }
        }
        legacyProgress.clear();
        owner.setChanged();
    }

    void discardBlockedProgress(ServerLevel level, SolidFissionLayout layout) {
        for (BlockPos pos : layout.sinks()) {
            if (level.getBlockEntity(pos) instanceof SaltHeater heater && heater.progress().amount() > 0D
                    && heater.tanks().processableRecipe(null) == null) {
                heater.progress().set(0D);
            }
        }
    }

    FissionTankTotals tankTotals(Level level, SolidFissionLayout layout) {
        var totals = new FissionTankTotals();
        for (var coolant : connected.keySet()) {
            var tanks = circuit(coolant);
            totals.add(tanks.cold, tanks.hot);
        }
        for (BlockPos pos : layout.sinks()) {
            if (level.getBlockEntity(pos) instanceof SaltHeater heater && !connected.containsKey(heater.coolant())) {
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
        legacyProgress.forEach((pos, fraction) -> {
            CompoundTag state = new CompoundTag();
            state.putLong("pos", pos.asLong());
            state.putDouble("amount", fraction.amount());
            if (fraction.coolant() instanceof NCSaltCoolants stock) {
                state.putInt("coolant", stock.ordinal());
            } else {
                state.putString("coolant_id", fraction.coolant().id());
            }
            savedCredit.add(state);
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
        legacyProgress.clear();
        var stock = NCSaltCoolants.values();
        for (Tag entry : tag.getList("fractions", Tag.TAG_COMPOUND)) {
            CompoundTag state = (CompoundTag) entry;
            double amount = state.getDouble("amount");
            int kind = state.getInt("coolant");
            FissionCoolant coolant = state.contains("coolant_id", Tag.TAG_STRING)
                    ? coolants.get(state.getString("coolant_id")) : kind >= 0 && kind < stock.length ? stock[kind] : null;
            if (amount > 0 && amount < 1 && coolant != null) {
                legacyProgress.put(BlockPos.of(state.getLong("pos")), new Fraction(coolant, amount));
            }
        }
    }
}
