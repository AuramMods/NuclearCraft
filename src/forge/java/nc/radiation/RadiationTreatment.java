package nc.radiation;

import net.minecraft.nbt.CompoundTag;

final class RadiationTreatment {
    static final String RESISTANCE = "resistance";
    static final String RECENT_RESISTANCE = "recent_resistance";
    static final String RECENT_RADAWAY = "recent_radaway";
    static final String RAD_X_COOLDOWN = "rad_x_cooldown";

    private RadiationTreatment() {}

    static void addRadaway(CompoundTag data, double amount) {
        String key = MedicineItem.Kind.RADAWAY.id();
        double buffer = data.getDouble(key);
        double previous = buffer > 0 && !data.contains(RECENT_RADAWAY)
                ? RadiationConfig.RADAWAY_AMOUNT.get() : data.getDouble(RECENT_RADAWAY);
        data.putDouble(key, buffer + amount);
        data.putDouble(RECENT_RADAWAY, Math.max(previous, amount));
    }

    static void addResistance(CompoundTag data, double amount) {
        double old = data.getDouble(RESISTANCE);
        double previous = old != 0 && !data.contains(RECENT_RESISTANCE)
                ? RadiationConfig.RAD_X_AMOUNT.get() : data.getDouble(RECENT_RESISTANCE);
        data.putDouble(RESISTANCE, old + amount);
        data.putDouble(RECENT_RESISTANCE, Math.max(previous, Math.abs(amount)));
    }
}
