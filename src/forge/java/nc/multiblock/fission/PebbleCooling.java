package nc.multiblock.fission;

import nc.init.NCPebbleFission;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler.FluidAction;
import net.minecraftforge.fluids.capability.templates.FluidTank;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.function.Supplier;

final class PebbleCooling {
    private static final int PORT_VOLUME = 1296;
    private final SolidFissionController owner;
    private final Map<Channel, Tanks> circuits = new LinkedHashMap<>();
    private final Map<BlockPos, Channel> coolerRoutes = new HashMap<>();
    private final Map<BlockPos, Double> credit = new HashMap<>();

    record Channel(PebbleCoolant gas, @Nullable ResourceLocation filter) {}

    PebbleCooling(SolidFissionController owner) {
        this.owner = owner;
    }

    Tanks circuit(Channel channel) {
        return circuits.computeIfAbsent(channel, key -> new Tanks(key.gas(), owner, key::filter));
    }

    Tanks tanks(PebbleCooler cooler) {
        Channel channel = coolerRoutes.get(cooler.getBlockPos());
        return channel != null ? circuit(channel) : cooler.localTanks();
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
            Tanks tanks = circuit(entry.getKey());
            int capacity = Math.max(PORT_VOLUME * entry.getValue(), 36 * coolerCounts.getOrDefault(entry.getKey(), 0));
            // Retain both inputs and products through changes to the shell.
            resize(tanks.hot, Math.max(capacity, entry.getKey().gas().outputAmount()));
            resize(tanks.cold, Math.max(capacity, entry.getKey().gas().inputIngredient().amount()));
        }
        credit.keySet().retainAll(layout.sinks());
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

    private static void resize(FluidTank tank, int minimum) {
        tank.setCapacity(Math.max(minimum, tank.getFluidAmount()));
    }

    Map<BlockPos, PebbleCoolant> available(ServerLevel level, SolidFissionLayout layout, Set<BlockPos> chambers,
                                            Set<BlockPos> moderators) {
        var eligible = new HashMap<String, List<BlockPos>>();
        for (BlockPos pos : layout.sinks()) {
            if (level.getBlockEntity(pos) instanceof PebbleCooler cooler) {
                PebbleCoolant gas = cooler.gas();
                if (tanks(cooler).transferable(cooler.filter()) > 0 && gas.cooling() > 0) {
                    eligible.computeIfAbsent(gas.id(), ignored -> new ArrayList<>()).add(pos);
                }
            }
        }
        var active = NCPebbleFission.placementRules().active(eligible,
                FissionPlacementRules.neighborhood(level, layout.bounds(), FissionPlacementRules.Part.CHAMBER,
                        chambers, moderators));
        var result = new HashMap<BlockPos, PebbleCoolant>();
        active.forEach((pos, id) -> result.put(pos, NCPebbleFission.coolants().get(id)));
        return result;
    }

    void heatGas(BlockPos pos, PebbleCoolant gas, double multiplier) {
        if (!(owner.getLevel().getBlockEntity(pos) instanceof PebbleCooler cooler)) {
            return;
        }
        Tanks tanks = tanks(cooler);
        int transferable = tanks.transferable(cooler.filter());
        if (transferable <= 0 || multiplier <= 0) {
            return;
        }
        int inputAmount = gas.inputIngredient().amount();
        double progress = credit.getOrDefault(pos, 0D)
                + multiplier * gas.cooling() / (NCPebbleFission.heatPerMb() * inputAmount);
        int amount = Math.min(transferable, (int) progress);
        credit.put(pos, progress - Math.floor(progress));
        if (amount > 0) {
            tanks.hot.fill(new FluidStack(gas.hot(), gas.outputAmount() * amount), FluidAction.EXECUTE);
            tanks.cold.drain(inputAmount * amount, FluidAction.EXECUTE);
        }
        owner.setChanged();
    }

    long gasAmount(boolean output) {
        return circuits.values().stream().mapToLong(tanks -> (output ? tanks.hot : tanks.cold).getFluidAmount()).sum();
    }

    long gasCapacity(boolean output) {
        return circuits.values().stream().mapToLong(tanks -> (output ? tanks.hot : tanks.cold).getCapacity()).sum();
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
        credit.clear();
        for (Tag stored : tag.getList("fractions", Tag.TAG_COMPOUND)) {
            CompoundTag fraction = (CompoundTag) stored;
            double value = fraction.getDouble("value");
            if (value > 0 && value < 1) {
                credit.put(BlockPos.of(fraction.getLong("pos")), value);
            }
        }
    }

    private static FluidTank createTank(BlockEntity owner, int batch, Predicate<FluidStack> accepts) {
        return new FluidTank(Math.max(PORT_VOLUME, batch), accepts) {
            @Override
            protected void onContentsChanged() {
                owner.setChanged();
            }
        };
    }

    static final class Tanks {
        final FluidTank cold;
        final FluidTank hot;
        private final PebbleCoolant gas;
        private final Supplier<@Nullable ResourceLocation> filter;

        Tanks(PebbleCoolant gas, BlockEntity owner, Supplier<@Nullable ResourceLocation> filter) {
            this.gas = gas;
            this.filter = filter;
            cold = createTank(owner, gas.inputIngredient().amount(),
                    stack -> gas.inputIngredient().accepts(stack) && matches(stack, filter()));
            hot = createTank(owner, gas.outputAmount(), stack -> stack.getFluid() == gas.hot());
        }

        @Nullable ResourceLocation filter() {
            return filter.get();
        }

        FluidTank tank(boolean output) {
            return output ? hot : cold;
        }

        void clear() {
            cold.drain(cold.getFluidAmount(), FluidAction.EXECUTE);
            hot.drain(hot.getFluidAmount(), FluidAction.EXECUTE);
        }

        int transferable(@Nullable ResourceLocation coolerFilter) {
            FluidStack input = cold.getFluid();
            var output = gas.hot();
            if (input.isEmpty() || !cold.isFluidValid(input) || !matches(input, coolerFilter) || output == Fluids.EMPTY
                    || !hot.isEmpty() && hot.getFluid().getFluid() != output) {
                return 0;
            }
            return Math.min(cold.getFluidAmount() / gas.inputIngredient().amount(),
                    (hot.getCapacity() - hot.getFluidAmount()) / gas.outputAmount());
        }

        private static boolean matches(FluidStack stack, @Nullable ResourceLocation filter) {
            return filter == null || filter.equals(ForgeRegistries.FLUIDS.getKey(stack.getFluid()));
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
