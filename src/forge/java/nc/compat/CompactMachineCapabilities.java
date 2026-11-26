package nc.compat;

import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.common.util.NonNullConsumer;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

public final class CompactMachineCapabilities {
    private final Map<LazyOptional<?>, Binding<?>> bindings = new IdentityHashMap<>();

    public static boolean isTunnelCapability(Capability<?> capability) {
        return capability == ForgeCapabilities.ITEM_HANDLER
                || capability == ForgeCapabilities.FLUID_HANDLER
                || capability == ForgeCapabilities.ENERGY;
    }

    public <T> LazyOptional<T> bind(LazyOptional<T> source) {
        if (!source.isPresent()) {
            return source;
        }
        Binding<?> existing = bindings.get(source);
        if (existing != null) {
            return existing.handle().cast();
        }
        // The outer machine and the room wall have separate lifetimes, even though they share a buffer.
        LazyOptional<T> handle = source.lazyMap(value -> value);
        NonNullConsumer<LazyOptional<T>> upstreamInvalidated = ignored -> {
            bindings.remove(source);
            handle.invalidate();
        };
        bindings.put(source, new Binding<>(source, handle, upstreamInvalidated));
        source.addListener(upstreamInvalidated);
        return handle;
    }

    public void invalidate() {
        var previous = List.copyOf(bindings.values());
        bindings.clear();
        for (Binding<?> binding : previous) {
            binding.invalidate();
        }
    }

    private record Binding<T>(LazyOptional<T> source, LazyOptional<T> handle,
                              NonNullConsumer<LazyOptional<T>> listener) {
        void invalidate() {
            source.removeListener(listener);
            handle.invalidate();
        }
    }
}
