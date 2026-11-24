package nc.radiation;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraftforge.registries.ForgeRegistries;

final class MobRadiation {
    private static final String DATA = "nuclearcraft:radiation";
    private static final String DOSE = "dose";
    private static final String MAXIMUM = "maximum";
    private static final String RATE = "rate";

    private MobRadiation() {}

    static void tick(Mob mob, double ambient, double ticks) {
        CompoundTag tag = mob.getPersistentData();
        CompoundTag data = tag.getCompound(DATA);
        double multiplier = mob.isInWater() ? RadiationConfig.WATER_MULTIPLIER.get()
                : mob.isInWaterOrRain() ? RadiationConfig.RAIN_MULTIPLIER.get() : 1;
        double resistance = 0;
        for (var armor : mob.getArmorSlots()) {
            resistance += RadiationShielding.armorResistance(armor);
        }
        double rate = RadiationShielding.attenuate(ambient * multiplier, resistance);
        double maximum = maximum(mob, data);
        double retained = data.getDouble(DOSE) * Math.pow(1 - RadiationConfig.ENTITY_DECAY.get(), ticks);
        double dose = Mth.clamp(retained + rate * ticks, 0, maximum);
        if (dose != 0 || rate != 0 || !data.isEmpty()) {
            data.putDouble(MAXIMUM, maximum);
            data.putDouble(DOSE, dose);
            data.putDouble(RATE, rate);
            tag.put(DATA, data);
        }
        boolean hostile = mob instanceof Enemy;
        if (dose >= maximum && (hostile ? RadiationConfig.HOSTILE_FATAL.get() : RadiationConfig.PASSIVE_FATAL.get())
                && RadiationEffects.kill(mob)) {
            RadiationEffects.clear(mob);
            return;
        }
        RadiationEffects.apply(mob, hostile ? RadiationEffects.Profile.HOSTILE : RadiationEffects.Profile.PASSIVE,
                100 * dose / maximum, Math.max(100 / RadiationConfig.CHUNKS_PER_TICK.get(), 39), ticks);
    }

    static Reading reading(Mob mob) {
        var data = mob.getPersistentData().getCompound(DATA);
        return new Reading(data.getDouble(DOSE), maximum(mob, data), data.getDouble(RATE));
    }

    private static double maximum(Mob mob, CompoundTag data) {
        double saved = data.getDouble(MAXIMUM);
        if (saved > 0) {
            return saved;
        }
        var id = ForgeRegistries.ENTITY_TYPES.getKey(mob.getType());
        for (String entry : RadiationConfig.ENTITY_MAX_DOSES.get()) {
            int split = entry.lastIndexOf('_');
            if (id != null && entry.substring(0, split).equals(id.toString())) {
                double configured = Double.parseDouble(entry.substring(split + 1));
                if (configured > 0) {
                    return configured;
                }
            }
        }
        return Math.max(1D, 50D * mob.getMaxHealth());
    }

    record Reading(double dose, double maximum, double rate) {}
}
