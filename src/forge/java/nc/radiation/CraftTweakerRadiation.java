package nc.radiation;

import com.blamejared.crafttweaker.api.annotation.ZenRegister;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.openzen.zencode.java.ZenCodeType;

@ZenRegister
@ZenCodeType.Expansion("crafttweaker.api.entity.LivingEntity")
public final class CraftTweakerRadiation {
    private CraftTweakerRadiation() {}

    @ZenCodeType.Method
    public static void addRadiation(LivingEntity entity, double amount, @ZenCodeType.OptionalBoolean boolean useImmunity) {
        setRadiation(entity, getRadiation(entity) + amount, useImmunity);
    }

    @ZenCodeType.Method
    public static void setRadiation(LivingEntity entity, double amount, @ZenCodeType.OptionalBoolean boolean useImmunity) {
        if (entity.level().isClientSide || !Double.isFinite(amount)) {
            return;
        }
        if (entity instanceof ServerPlayer player) {
            if (!useImmunity || !PlayerRadiation.isImmune(player)) {
                PlayerRadiation.setDose(player, amount);
            }
        } else {
            MobRadiation.setDose(entity, amount);
        }
    }

    @ZenCodeType.Method
    public static double getRadiation(LivingEntity entity) {
        return entity instanceof Player player ? PlayerRadiation.dose(player) : MobRadiation.reading(entity).dose();
    }

    @ZenCodeType.Method
    public static void addRadawayBuffer(LivingEntity entity, double amount, @ZenCodeType.OptionalBoolean boolean slow) {
        update(entity, radaway(slow), amount, true);
    }

    @ZenCodeType.Method
    public static void setRadawayBuffer(LivingEntity entity, double amount, @ZenCodeType.OptionalBoolean boolean slow) {
        update(entity, radaway(slow), amount, false);
    }

    @ZenCodeType.Method
    public static double getRadawayBuffer(LivingEntity entity, @ZenCodeType.OptionalBoolean boolean slow) {
        return data(entity).getDouble(radaway(slow).key);
    }

    @ZenCodeType.Method
    public static void addPoisonBuffer(LivingEntity entity, double amount) {
        update(entity, Quantity.POISON, amount, true);
    }

    @ZenCodeType.Method
    public static void setPoisonBuffer(LivingEntity entity, double amount) {
        update(entity, Quantity.POISON, amount, false);
    }

    @ZenCodeType.Method
    public static double getPoisonBuffer(LivingEntity entity) {
        return data(entity).getDouble(Quantity.POISON.key);
    }

    @ZenCodeType.Method
    public static void addRadiationResistance(LivingEntity entity, double amount) {
        update(entity, Quantity.RESISTANCE, amount, true);
    }

    @ZenCodeType.Method
    public static void setRadiationResistance(LivingEntity entity, double amount) {
        update(entity, Quantity.RESISTANCE, amount, false);
    }

    @ZenCodeType.Method
    public static double getRadiationResistance(LivingEntity entity) {
        return data(entity).getDouble(Quantity.RESISTANCE.key);
    }

    @ZenCodeType.Method
    public static double getRawRadiationLevel(LivingEntity entity) {
        return entity instanceof Player player ? PlayerRadiation.read(player).getDouble(PlayerRadiation.RAW_RATE)
                : MobRadiation.rawRate(entity);
    }

    @ZenCodeType.Method
    public static double getRadiationLevel(LivingEntity entity) {
        return entity instanceof Player player ? PlayerRadiation.rate(player) : MobRadiation.reading(entity).rate();
    }

    private static CompoundTag data(LivingEntity entity) {
        return entity instanceof Player player ? PlayerRadiation.read(player)
                : entity.getPersistentData().getCompound(MobRadiation.DATA);
    }

    private static Quantity radaway(boolean slow) {
        return slow ? Quantity.SLOW_RADAWAY : Quantity.RADAWAY;
    }

    private static void update(LivingEntity entity, Quantity quantity, double amount, boolean additive) {
        if (entity.level().isClientSide || !Double.isFinite(amount)) {
            return;
        }
        var data = data(entity);
        double next = additive ? data.getDouble(quantity.key) + amount : amount;
        if (!Double.isFinite(next)) {
            return;
        }
        data.putDouble(quantity.key, quantity == Quantity.RESISTANCE ? next : Math.max(0, next));
        if (quantity.recent != null) {
            double previous = data.getDouble(quantity.recent);
            data.putDouble(quantity.recent, Math.max(previous, Math.abs(amount)));
        }
        if (entity instanceof Player player) {
            PlayerRadiation.save(player, data);
        } else {
            entity.getPersistentData().put(MobRadiation.DATA, data);
        }
    }

    private enum Quantity {
        RADAWAY(MedicineItem.Kind.RADAWAY.id(), RadiationTreatment.RECENT_RADAWAY),
        SLOW_RADAWAY(MedicineItem.Kind.SLOW_RADAWAY.id(), null),
        POISON(RadiationPoison.BUFFER, RadiationPoison.RECENT),
        RESISTANCE(RadiationTreatment.RESISTANCE, RadiationTreatment.RECENT_RESISTANCE);

        private final String key;
        private final String recent;

        Quantity(String key, String recent) {
            this.key = key;
            this.recent = recent;
        }
    }
}
