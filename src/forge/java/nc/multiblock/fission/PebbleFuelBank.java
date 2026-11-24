package nc.multiblock.fission;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Containers;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

final class PebbleFuelBank {
    private final SolidFissionController owner;
    private final PebbleFuelItems unfiltered;
    private final Map<ResourceLocation, PebbleFuelItems> filtered = new HashMap<>();
    private final Set<ResourceLocation> connected = new HashSet<>();

    PebbleFuelBank(SolidFissionController owner) {
        this.owner = owner;
        unfiltered = new PebbleFuelItems(owner, null);
    }

    PebbleFuelItems items(@Nullable ResourceLocation filter) {
        return filter == null ? unfiltered : filtered.computeIfAbsent(filter, id -> new PebbleFuelItems(owner, id));
    }

    boolean connected(@Nullable ResourceLocation filter) {
        return connected.contains(filter);
    }

    void bind(SolidFissionLayout layout) {
        connected.clear();
        for (var port : layout.ports()) {
            if (port.role() == FissionPartBlock.Role.CHAMBER_PORT) {
                connected.add(port.fuelFilter());
            }
        }
    }

    void clear() {
        unfiltered.clear();
        filtered.values().forEach(PebbleFuelItems::clear);
    }

    void dropFiltered() {
        var pos = owner.getBlockPos();
        var level = owner.getLevel();
        for (var items : filtered.values()) {
            for (int slot = 0; slot < items.getSlots(); slot++) {
                Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), items.getStackInSlot(slot));
            }
        }
    }

    ListTag save() {
        ListTag list = new ListTag();
        filtered.forEach((id, items) -> {
            if (items.hasContents()) {
                CompoundTag tag = new CompoundTag();
                items.save(tag);
                list.add(tag);
            }
        });
        return list;
    }

    void load(ListTag list) {
        filtered.values().forEach(PebbleFuelItems::clear);
        for (Tag tag : list) {
            CompoundTag data = (CompoundTag) tag;
            ResourceLocation filter = ResourceLocation.tryParse(data.getString("pebble_filter"));
            if (filter != null) {
                items(filter).load(data);
            }
        }
    }
}
