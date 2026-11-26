package nc.multiblock.fission;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

final class FissionItemFuelBank {
    private final SolidFissionController owner;
    private final FissionVariant variant;
    private final FissionFuelItems unfiltered;
    private final Map<ResourceLocation, FissionFuelItems> filtered = new HashMap<>();
    private final Map<ResourceLocation, Integer> connected = new HashMap<>();

    FissionItemFuelBank(SolidFissionController owner, FissionVariant variant) {
        this.owner = owner;
        this.variant = variant;
        unfiltered = new FissionFuelItems(owner, variant, null);
    }

    FissionFuelItems items(@Nullable ResourceLocation filter) {
        return filter == null ? unfiltered : filtered.computeIfAbsent(filter, id -> new FissionFuelItems(owner, variant, id));
    }

    boolean connected(@Nullable ResourceLocation filter) {
        return connected.containsKey(filter);
    }

    void bind(ServerLevel level, SolidFissionLayout layout) {
        connected.clear();
        for (var port : layout.ports()) {
            if (port.itemFuel()) {
                connected.put(port.filter(), 0);
            }
        }
        for (var pos : layout.cells()) {
            if (level.getBlockEntity(pos) instanceof FissionFuelCell cell && connected.containsKey(cell.filter())) {
                connected.merge(cell.filter(), 1, Integer::sum);
            }
        }
        unfiltered.resize(connected.getOrDefault(null, 0));
        filtered.forEach((filter, items) -> items.resize(connected.getOrDefault(filter, 0)));
        connected.forEach((filter, count) -> items(filter).resize(count));
    }

    void clear() {
        unfiltered.clearContents();
        filtered.values().forEach(FissionFuelItems::clearContents);
    }

    void dropContents() {
        unfiltered.dropContents();
        filtered.values().forEach(FissionFuelItems::dropContents);
    }

    ListTag save() {
        ListTag list = new ListTag();
        for (var items : filtered.values()) {
            if (items.hasContents()) {
                CompoundTag tag = new CompoundTag();
                items.save(tag);
                list.add(tag);
            }
        }
        return list;
    }

    void load(ListTag list) {
        filtered.values().forEach(FissionFuelItems::clearContents);
        for (Tag tag : list) {
            CompoundTag data = (CompoundTag) tag;
            ResourceLocation filter = FissionFuelItems.readFilter(data, variant);
            if (filter != null) {
                items(filter).load(data);
            }
        }
    }
}
