package nc.radiation;

import net.minecraft.nbt.CompoundTag;

final class RadiationPoison {
    static final String BUFFER = "poison";
    static final String RECENT = "recent_poison";

    private RadiationPoison() {}

    static void add(CompoundTag data, double amount) {
        data.putDouble(BUFFER, data.getDouble(BUFFER) + amount);
        data.putDouble(RECENT, Math.max(amount, data.getDouble(RECENT)));
    }

    static double absorb(CompoundTag data, double ticks) {
        double pending = data.getDouble(BUFFER);
        if (pending <= 0) {
            data.remove(RECENT);
            return 0;
        }
        double absorbed = Math.min(pending, data.getDouble(RECENT) * ticks / RadiationConfig.POISON_TIME.get());
        if (pending > absorbed) {
            data.putDouble(BUFFER, pending - absorbed);
        } else {
            data.remove(BUFFER);
            data.remove(RECENT);
        }
        return absorbed;
    }
}
