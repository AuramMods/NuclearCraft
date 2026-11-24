package nc.radiation;

import nc.NuclearCraft;
import nc.init.NCRadiation;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Locale;

@Mod.EventBusSubscriber(modid = NuclearCraft.MOD_ID)
public final class PlayerRadiation {
    private static final String DATA = "nuclearcraft:radiation";
    private static final String DOSE = "dose";
    private static final String RATE = "rate";
    private static final String RAW_RATE = "raw_rate";
    private static final String RESISTANCE = "resistance";
    private static final String RADAWAY_COOLDOWN = "radaway_cooldown";
    private static final String RAD_X_COOLDOWN = "rad_x_cooldown";
    private static final String IMMUNITY = "immunity_ticks";
    private static final ResourceKey<DamageType> FATAL_RADS =
            ResourceKey.create(Registries.DAMAGE_TYPE, NuclearCraft.id("fatal_rads"));

    private PlayerRadiation() {}

    @SubscribeEvent
    public static void onTick(TickEvent.PlayerTickEvent event) {
        if (event.phase == TickEvent.Phase.START && event.player instanceof ServerPlayer player) {
            int interval = RadiationConfig.UPDATE_TICKS.get();
            if ((player.level().getGameTime() + player.getUUID().hashCode()) % interval == 0) {
                if (enabled()) {
                    tick(player, interval);
                }
                RadiationSync.send(player);
            }
        }
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            RadiationSync.send(player);
        }
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            RadiationSync.send(player);
        }
    }

    @SubscribeEvent
    public static void onClone(PlayerEvent.Clone event) {
        CompoundTag old = read(event.getOriginal());
        CompoundTag copy = old.copy();
        if (event.isWasDeath() && RadiationConfig.ENABLED.get()) {
            copy = new CompoundTag();
            double maximum = RadiationConfig.MAX_DOSE.get();
            if (RadiationConfig.PERSIST_DEATH.get()) {
                copy.putDouble(DOSE, old.getDouble(DOSE) * RadiationConfig.DEATH_FRACTION.get() % maximum);
            }
            copy.putDouble(IMMUNITY, RadiationConfig.DEATH_IMMUNITY_SECONDS.get() * 20);
        }
        if (!copy.isEmpty()) {
            save(event.getEntity(), copy);
        }
    }

    public static void tick(ServerPlayer player, int ticks) {
        if (!player.isAlive()) {
            return;
        }
        CompoundTag data = read(player);
        double max = RadiationConfig.MAX_DOSE.get();
        double dose = data.getDouble(DOSE);
        double previousDose = dose;
        double immunity = Math.max(0, data.getDouble(IMMUNITY) - ticks);
        boolean immune = immunity > 0 || permanentlyImmune(player);
        boolean vulnerable = !immune && !player.isCreative() && !player.isSpectator();
        boolean fatal = vulnerable && RadiationConfig.FATAL.get();
        if (fatal && dose >= max && kill(player)) {
            return;
        }

        double internalResistance = data.getDouble(RESISTANCE);
        double resistance = internalResistance;
        for (ItemStack armor : player.getArmorSlots()) {
            resistance += RadiationShielding.armorResistance(armor);
        }
        double multiplier = player.isInWater() ? RadiationConfig.WATER_MULTIPLIER.get()
                : player.isInWaterOrRain() ? RadiationConfig.RAIN_MULTIPLIER.get() : 1;
        double rate = RadiationShielding.attenuate(
                WorldRadiation.rate(player.serverLevel(), player.blockPosition()) * multiplier, resistance);
        var inventory = player.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            double raw = RadiationSources.itemRate(inventory.getItem(i)) * multiplier;
            // The legacy resistance curve runs separately for each stack, before rates are added.
            rate += RadiationShielding.attenuate(raw, resistance);
        }
        if (data.isEmpty() && rate == 0) {
            return;
        }

        dose *= Math.pow(1 - RadiationConfig.DECAY.get(), ticks);
        if (!immune) {
            dose = Mth.clamp(dose + rate * ticks, 0, max);
        }
        data.putDouble(RATE, rate);
        data.putDouble(RAW_RATE, rawRate(rate, resistance));
        data.putDouble(IMMUNITY, immunity);
        if (fatal && dose >= max) {
            data.putDouble(DOSE, dose);
            save(player, data);
            if (kill(player)) {
                return;
            }
        }

        if (internalResistance > 0) {
            double change = Math.max(internalResistance, RadiationConfig.RAD_X_AMOUNT.get())
                    * ticks / RadiationConfig.RAD_X_LIFETIME.get();
            double remaining = Math.max(0, internalResistance - change);
            data.putDouble(RESISTANCE, remaining);
            if (remaining == 0) {
                player.displayClientMessage(Component.translatable("message.nuclearcraft.rad_x_wore_off"), true);
            }
        }
        dose -= drain(data, MedicineItem.Kind.RADAWAY, RadiationConfig.RADAWAY_RATE.get() * ticks);
        dose -= drain(data, MedicineItem.Kind.SLOW_RADAWAY, RadiationConfig.SLOW_RADAWAY_RATE.get() * ticks);
        reduceCooldown(data, RADAWAY_COOLDOWN, ticks);
        reduceCooldown(data, RAD_X_COOLDOWN, ticks);
        data.putDouble(DOSE, Math.max(0, dose));
        save(player, data);

        if (vulnerable) {
            applyEffects(player, dose / max, Math.max(ticks, 19));
            if (previousDose < 0.4 * max && dose >= 0.4 * max && RadiationConfig.REQUIRE_COUNTER.get()
                    && !RadiationDisplay.hasCounter(player)) {
                player.sendSystemMessage(Component.translatable("message.nuclearcraft.rad_warning")
                        .withStyle(ChatFormatting.GOLD));
                player.playNotifySound(NCRadiation.CHEMS_WEAR_OFF.get(), SoundSource.PLAYERS,
                        0.8F * RadiationConfig.soundVolume(6), 0.7F);
            }
        }
    }

    private static double rawRate(double effective, double resistance) {
        // Invert the legacy resistance curve for the aggregate effective reading.
        if (resistance > 0 && effective > 0) {
            return (effective + Math.sqrt(effective) * Math.sqrt(effective + 4 * resistance)) / 2;
        }
        return resistance < 0 ? effective / (1 - resistance) : effective;
    }

    private static double drain(CompoundTag data, MedicineItem.Kind kind, double limit) {
        double buffer = data.getDouble(kind.id());
        double amount = Math.min(buffer, limit);
        if (amount > 0) {
            data.putDouble(kind.id(), buffer - amount);
        }
        return amount;
    }

    public static double dose(Player player) {
        return read(player).getDouble(DOSE);
    }

    public static double rate(Player player) {
        return read(player).getDouble(RATE);
    }

    static boolean enabled() {
        return RadiationConfig.ENABLED.get();
    }

    private static boolean permanentlyImmune(Player player) {
        return RadiationConfig.IMMUNE_PLAYERS.get().contains(player.getStringUUID());
    }

    static RadiationSync.Reading reading(ServerPlayer player) {
        if (!enabled()) {
            return RadiationSync.Reading.HIDDEN;
        }
        CompoundTag data = read(player);
        return new RadiationSync.Reading(true, RadiationConfig.REQUIRE_COUNTER.get(), data.getDouble(DOSE),
                RadiationConfig.MAX_DOSE.get(), data.getDouble(RATE), data.getDouble(RAW_RATE),
                permanentlyImmune(player), data.getDouble(IMMUNITY), RadiationConfig.MINIMUM_RATE.get(),
                RadiationConfig.UNIT_DIGITS.get(), RadiationConfig.soundVolume(0));
    }

    static void report(ServerPlayer player) {
        var reading = reading(player);
        double percent = reading.dosePercent();
        Component raw = Component.literal(reading.format(reading.rawRate(), "Rad/t"))
                .withStyle(RadiationDisplay.rateColor(reading.rawRate()));
        player.sendSystemMessage(Component.translatable("message.nuclearcraft.geiger_reading",
                reading.format(reading.dose(), "Rad"), Math.round(percent), raw)
                .withStyle(RadiationDisplay.doseColor(percent)));
    }

    static String format(double value) {
        return value < RadiationConfig.MINIMUM_RATE.get() ? "0" : String.format(Locale.ROOT, "%.4g", value);
    }

    static boolean canTreat(ServerPlayer player, MedicineItem.Kind kind) {
        return read(player).getDouble(kind == MedicineItem.Kind.RAD_X ? RAD_X_COOLDOWN : RADAWAY_COOLDOWN) <= 0;
    }

    private static void reduceCooldown(CompoundTag data, String key, int ticks) {
        double cooldown = data.getDouble(key);
        if (cooldown > 0) {
            data.putDouble(key, Math.max(0, cooldown - ticks));
        }
    }

    static void treat(ServerPlayer player, MedicineItem.Kind kind) {
        CompoundTag data = read(player);
        switch (kind) {
            case RADAWAY -> data.putDouble(kind.id(), data.getDouble(kind.id()) + RadiationConfig.RADAWAY_AMOUNT.get());
            case SLOW_RADAWAY -> data.putDouble(kind.id(), data.getDouble(kind.id()) + RadiationConfig.SLOW_RADAWAY_AMOUNT.get());
            case RAD_X -> data.putDouble(RESISTANCE, data.getDouble(RESISTANCE) + RadiationConfig.RAD_X_AMOUNT.get());
        }
        if (kind == MedicineItem.Kind.RAD_X) {
            data.putDouble(RAD_X_COOLDOWN, RadiationConfig.RAD_X_COOLDOWN.get());
        } else {
            data.putDouble(RADAWAY_COOLDOWN, RadiationConfig.RADAWAY_COOLDOWN.get());
        }
        save(player, data);
    }

    private static boolean kill(ServerPlayer player) {
        var type = player.level().registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(FATAL_RADS);
        return player.hurt(new DamageSource(type), Float.MAX_VALUE);
    }

    private static void applyEffects(ServerPlayer player, double fraction, int duration) {
        if (fraction >= 0.4) {
            effect(player, MobEffects.WEAKNESS, fraction >= 0.9 ? 2 : fraction >= 0.7 ? 1 : 0, duration);
        }
        if (fraction >= 0.55) {
            effect(player, MobEffects.DIG_SLOWDOWN, fraction >= 0.9 ? 2 : fraction >= 0.8 ? 1 : 0, duration);
        }
        if (fraction >= 0.7) {
            effect(player, MobEffects.HUNGER, fraction >= 0.9 ? 1 : 0, duration);
        }
        if (fraction >= 0.8) {
            effect(player, MobEffects.POISON, 0, duration);
        }
        if (fraction >= 0.9) {
            effect(player, MobEffects.WITHER, 0, duration);
        }
    }

    private static void effect(ServerPlayer player, MobEffect effect, int amplifier, int duration) {
        // 2o.9.7 used the configured levels directly as vanilla effect amplifiers.
        int minimumDuration = effect == MobEffects.POISON ? 25 >> amplifier
                : effect == MobEffects.WITHER ? 40 >> amplifier : 0;
        player.addEffect(new MobEffectInstance(effect, Math.max(duration, minimumDuration), amplifier + 1, false, false));
    }

    private static CompoundTag read(Player player) {
        return player.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG).getCompound(DATA);
    }

    private static void save(Player player, CompoundTag data) {
        CompoundTag persisted = player.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
        persisted.put(DATA, data);
        player.getPersistentData().put(Player.PERSISTED_NBT_TAG, persisted);
    }
}
