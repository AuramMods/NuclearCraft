package nc.radiation;

import nc.entity.FeralGhoul;
import nc.entity.GhoulConversion;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraftforge.registries.ForgeRegistries;

final class MobRadiation {
    static final String DATA = "nuclearcraft:radiation";
    private static final String DOSE = "dose";
    private static final String MAXIMUM = "maximum";
    private static final String RATE = "rate";

    private MobRadiation() {}

    static void tick(Mob mob, double ambient, double ticks) {
        CompoundTag tag = mob.getPersistentData();
        CompoundTag data = tag.getCompound(DATA);
        double multiplier = mob.isInWater() ? RadiationConfig.WATER_MULTIPLIER.get()
                : mob.isInWaterOrRain() ? RadiationConfig.RAIN_MULTIPLIER.get() : 1;
        double resistance = resistance(mob, data);
        double rate = RadiationShielding.attenuate(ambient * multiplier, resistance);
        double maximum = maximum(mob, data);
        double retained = data.getDouble(DOSE) * Math.pow(1 - RadiationConfig.ENTITY_DECAY.get(), ticks);
        double dose = Mth.clamp(retained + rate * ticks + RadiationPoison.absorb(data, ticks), 0, maximum);
        if (dose != 0 || rate != 0 || !data.isEmpty()) {
            data.putDouble(MAXIMUM, maximum);
            data.putDouble(DOSE, dose);
            data.putDouble(RATE, rate);
            tag.put(DATA, data);
        }
        boolean hostile = mob instanceof Enemy;
        if (dose >= maximum && !(mob instanceof FeralGhoul)) {
            boolean fatal = hostile ? RadiationConfig.HOSTILE_FATAL.get() : RadiationConfig.PASSIVE_FATAL.get();
            if ((!hostile && GhoulConversion.convert(mob)) || (fatal && RadiationEffects.kill(mob))) {
                RadiationEffects.clear(mob);
                return;
            }
        }
        RadiationEffects.apply(mob, hostile ? RadiationEffects.Profile.HOSTILE : RadiationEffects.Profile.PASSIVE,
                100 * dose / maximum, Math.max(100 / RadiationConfig.CHUNKS_PER_TICK.get(), 39), ticks);
    }

    static void addPoison(Mob mob, double dose) {
        var tag = mob.getPersistentData();
        var data = tag.getCompound(DATA);
        RadiationPoison.add(data, dose);
        tag.put(DATA, data);
    }

    static Reading reading(LivingEntity entity) {
        var data = entity.getPersistentData().getCompound(DATA);
        return new Reading(data.getDouble(DOSE), maximum(entity, data), data.getDouble(RATE));
    }

    static void setDose(LivingEntity entity, double dose) {
        var tag = entity.getPersistentData();
        var data = tag.getCompound(DATA);
        double maximum = maximum(entity, data);
        data.putDouble(MAXIMUM, maximum);
        data.putDouble(DOSE, Mth.clamp(dose, 0, maximum));
        tag.put(DATA, data);
    }

    static double rawRate(LivingEntity entity) {
        var data = entity.getPersistentData().getCompound(DATA);
        return RadiationShielding.rawRate(data.getDouble(RATE), resistance(entity, data));
    }

    private static double resistance(LivingEntity entity, CompoundTag data) {
        return data.getDouble(RadiationTreatment.RESISTANCE) + RadiationShielding.equippedResistance(entity);
    }

    private static double maximum(LivingEntity entity, CompoundTag data) {
        double saved = data.getDouble(MAXIMUM);
        if (saved > 0) {
            return saved;
        }
        var id = ForgeRegistries.ENTITY_TYPES.getKey(entity.getType());
        for (String entry : RadiationConfig.ENTITY_MAX_DOSES.get()) {
            int split = entry.lastIndexOf('_');
            if (id != null && entry.substring(0, split).equals(id.toString())) {
                double configured = Double.parseDouble(entry.substring(split + 1));
                if (configured > 0) {
                    return configured;
                }
            }
        }
        return Math.max(1D, 50D * entity.getMaxHealth());
    }

    record Reading(double dose, double maximum, double rate) {}
}
