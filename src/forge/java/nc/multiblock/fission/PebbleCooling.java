package nc.multiblock.fission;

import nc.init.NCPebbleFission;
import nc.recipe.FissionCoolantRecipe;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiPredicate;

final class PebbleCooling {
    private final SolidFissionController owner;
    private final Map<Channel, CoolantTanks> circuits = new LinkedHashMap<>();
    private final Map<BlockPos, Channel> coolerRoutes = new HashMap<>();
    private final Map<BlockPos, Double> legacyProgress = new HashMap<>();

    record Channel(PebbleCoolant gas, @Nullable ResourceLocation filter) {}

    PebbleCooling(SolidFissionController owner) {
        this.owner = owner;
    }

    CoolantTanks circuit(Channel channel) {
        return circuits.computeIfAbsent(channel, key -> new CoolantTanks(key.gas(), owner, key::filter));
    }

    CoolantTanks tanks(PebbleCooler cooler) {
        Channel channel = coolerRoutes.get(cooler.getBlockPos());
        return channel != null ? circuit(channel) : cooler.localTanks();
    }

    void clearMaterial() {
        circuits.values().forEach(CoolantTanks::clear);
        legacyProgress.clear();
        owner.setChanged();
    }

    void resize(ServerLevel level, SolidFissionLayout layout) {
        var portCounts = new LinkedHashMap<Channel, Integer>();
        for (var port : layout.ports()) {
            if (port.gas() != null) {
                portCounts.merge(port.gas(), 1, Integer::sum);
            }
        }
        var available = new HashMap<PebbleCoolant, List<Channel>>();
        portCounts.keySet().forEach(channel -> available.computeIfAbsent(channel.gas(), gas -> new ArrayList<>()).add(channel));
        coolerRoutes.clear();
        var coolerCounts = new HashMap<Channel, Integer>();
        for (BlockPos pos : layout.sinks()) {
            if (PebbleCoolerBlock.entity(level, pos) instanceof PebbleCooler cooler) {
                Channel channel = route(cooler, available.getOrDefault(cooler.gas(), List.of()));
                if (channel != null) {
                    coolerRoutes.put(pos, channel);
                    coolerCounts.merge(channel, 1, Integer::sum);
                }
            }
        }
        for (var entry : portCounts.entrySet()) {
            CoolantTanks tanks = circuit(entry.getKey());
            long capacity = Math.max((long) CoolantTanks.PORT_VOLUME * entry.getValue(),
                    36L * coolerCounts.getOrDefault(entry.getKey(), 0));
            tanks.resize(capacity);
        }
    }

    private static @Nullable Channel route(PebbleCooler cooler, List<Channel> channels) {
        Channel exact = new Channel(cooler.gas(), cooler.filter());
        if (channels.contains(exact)) {
            return exact;
        }
        Channel unfiltered = new Channel(cooler.gas(), null);
        if (channels.contains(unfiltered)) {
            return unfiltered;
        }
        return cooler.filter() == null && channels.size() == 1 ? channels.get(0) : null;
    }

    Map<BlockPos, FissionCoolantRecipe> available(ServerLevel level, SolidFissionLayout layout,
                                                 BiPredicate<FissionPlacementRules.Part, BlockPos> neighbors) {
        var eligible = new HashMap<String, List<BlockPos>>();
        var recipes = new HashMap<BlockPos, FissionCoolantRecipe>();
        for (BlockPos pos : layout.sinks()) {
            if (level.getBlockEntity(pos) instanceof PebbleCooler cooler) {
                var recipe = tanks(cooler).ready(cooler.filter());
                if (recipe != null) {
                    eligible.computeIfAbsent(cooler.gas().id(), ignored -> new ArrayList<>()).add(pos);
                    recipes.put(pos, recipe);
                }
            }
        }
        recipes.keySet().retainAll(NCPebbleFission.placementRules().active(eligible, neighbors).keySet());
        return recipes;
    }

    double heatGas(ServerLevel level, BlockPos pos, FissionCoolantRecipe recipe, double multiplier, boolean preview) {
        if (multiplier <= 0 || !(level.getBlockEntity(pos) instanceof PebbleCooler cooler)) {
            return 0D;
        }
        CoolantTanks tanks = tanks(cooler);
        if (tanks.ready(cooler.filter()) != recipe) {
            return 0D;
        }
        int inputAmount = recipe.input().matchedAmount(tanks.cold.getFluid(), 1);
        double batchesPerTick = multiplier * recipe.cooling() / (NCPebbleFission.heatPerMb() * inputAmount);
        if (!preview) {
            double progress = cooler.progress().amount() + batchesPerTick;
            cooler.progress().set(progress - Math.floor(progress));
            tanks.process(cooler.filter(), recipe, (int) progress, level.random);
        }
        return batchesPerTick;
    }

    void restoreLegacyProgress(ServerLevel level, SolidFissionLayout layout) {
        if (legacyProgress.isEmpty()) {
            return;
        }
        for (BlockPos pos : layout.sinks()) {
            Double saved = legacyProgress.get(pos);
            if (saved != null && PebbleCoolerBlock.entity(level, pos) instanceof PebbleCooler cooler) {
                cooler.progress().restoreFromController(saved);
            }
        }
        legacyProgress.clear();
        owner.setChanged();
    }

    void discardBlockedProgress(ServerLevel level, SolidFissionLayout layout) {
        for (BlockPos pos : layout.sinks()) {
            if (level.getBlockEntity(pos) instanceof PebbleCooler cooler && cooler.progress().amount() > 0D
                    && tanks(cooler).processableRecipe(cooler.filter()) == null) {
                cooler.progress().set(0D);
            }
        }
    }

    FissionTankTotals tankTotals(ServerLevel level, SolidFissionLayout layout) {
        var totals = new FissionTankTotals();
        for (CoolantTanks tanks : circuits.values()) {
            totals.add(tanks.cold, tanks.hot);
        }
        for (BlockPos pos : layout.sinks()) {
            if (!coolerRoutes.containsKey(pos) && level.getBlockEntity(pos) instanceof PebbleCooler cooler) {
                CoolantTanks tanks = cooler.localTanks();
                totals.add(tanks.cold, tanks.hot);
            }
        }
        return totals;
    }

    CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        ListTag filtered = new ListTag();
        for (var entry : circuits.entrySet()) {
            Channel channel = entry.getKey();
            CompoundTag saved = entry.getValue().save();
            if (channel.filter() == null) {
                tag.put(channel.gas().id(), saved);
            } else {
                saved.putString("gas", channel.gas().id());
                saved.putString("filter", channel.filter().toString());
                filtered.add(saved);
            }
        }
        tag.put("filtered", filtered);
        ListTag fractions = new ListTag();
        legacyProgress.forEach((pos, amount) -> {
            CompoundTag value = new CompoundTag();
            value.putLong("pos", pos.asLong());
            value.putDouble("value", amount);
            fractions.add(value);
        });
        tag.put("fractions", fractions);
        return tag;
    }

    void load(CompoundTag tag) {
        var loaded = new HashSet<Channel>();
        for (PebbleCoolant gas : NCPebbleFission.coolants().values()) {
            if (tag.contains(gas.id(), Tag.TAG_COMPOUND)) {
                Channel channel = new Channel(gas, null);
                circuit(channel).load(tag.getCompound(gas.id()));
                loaded.add(channel);
            }
        }
        for (Tag stored : tag.getList("filtered", Tag.TAG_COMPOUND)) {
            CompoundTag saved = (CompoundTag) stored;
            var gas = NCPebbleFission.coolants().get(saved.getString("gas"));
            var filter = ResourceLocation.tryParse(saved.getString("filter"));
            if (gas != null && filter != null) {
                Channel channel = new Channel(gas, filter);
                circuit(channel).load(saved);
                loaded.add(channel);
            }
        }
        circuits.forEach((channel, tanks) -> {
            if (!loaded.contains(channel)) {
                tanks.load(new CompoundTag());
            }
        });
        legacyProgress.clear();
        for (Tag stored : tag.getList("fractions", Tag.TAG_COMPOUND)) {
            CompoundTag fraction = (CompoundTag) stored;
            double value = fraction.getDouble("value");
            if (value > 0 && value < 1) {
                legacyProgress.put(BlockPos.of(fraction.getLong("pos")), value);
            }
        }
    }
}
