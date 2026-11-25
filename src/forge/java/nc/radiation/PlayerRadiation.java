package nc.radiation;

import nc.NuclearCraft;
import nc.compat.gamestages.RadiationGameStages;
import nc.init.NCRadiation;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Locale;

import static nc.radiation.RadiationTreatment.RAD_X_COOLDOWN;
import static nc.radiation.RadiationTreatment.RECENT_RADAWAY;
import static nc.radiation.RadiationTreatment.RECENT_RESISTANCE;
import static nc.radiation.RadiationTreatment.RESISTANCE;

@Mod.EventBusSubscriber(modid = NuclearCraft.MOD_ID)
public final class PlayerRadiation {
    private static final String DATA = "nuclearcraft:radiation";
    private static final String DOSE = "dose";
    private static final String RATE = "rate";
    private static final String RAW_RATE = "raw_rate";
    private static final String RAD_X_USED = "rad_x_used";
    private static final String RADAWAY_COOLDOWN = "radaway_cooldown";
    private static final String IMMUNITY = "immunity_ticks";

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
        boolean immune = immunity > 0 || hasUntimedImmunity(player);
        boolean vulnerable = !immune && !player.isCreative() && !player.isSpectator();
        boolean fatal = vulnerable && RadiationConfig.FATAL.get();
        if (fatal && dose >= max && RadiationEffects.kill(player)) {
            return;
        }

        double internalResistance = data.getDouble(RESISTANCE);
        double resistance = internalResistance + RadiationShielding.equippedResistance(player);
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
        rate += RadiationPoison.absorb(data, ticks) / ticks;
        RadiationBadge.measure(player, rate * ticks);
        if (data.isEmpty() && rate == 0) {
            refreshEffects(player, vulnerable, 0, ticks);
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
            if (RadiationEffects.kill(player)) {
                return;
            }
        }

        decayResistance(player, data, internalResistance, ticks);
        double normalRate = RadiationConfig.RADAWAY_RATE.get();
        if (data.contains(RECENT_RADAWAY)) {
            normalRate *= data.getDouble(RECENT_RADAWAY) / RadiationConfig.RADAWAY_AMOUNT.get();
        }
        dose -= drain(data, MedicineItem.Kind.RADAWAY.id(), RECENT_RADAWAY, normalRate * ticks);
        dose -= drain(data, MedicineItem.Kind.SLOW_RADAWAY.id(), null, RadiationConfig.SLOW_RADAWAY_RATE.get() * ticks);
        reduceCooldown(data, RADAWAY_COOLDOWN, ticks);
        reduceCooldown(data, RAD_X_COOLDOWN, ticks);
        data.putDouble(DOSE, Math.max(0, dose));
        save(player, data);

        refreshEffects(player, vulnerable, 100 * dose / max, ticks);
        if (vulnerable) {
            double threshold = RadiationEffects.warningThreshold() / 100 * max;
            if (previousDose < threshold && dose >= threshold && RadiationConfig.REQUIRE_COUNTER.get()
                    && !RadiationDisplay.hasCounter(player)) {
                player.sendSystemMessage(Component.translatable("message.nuclearcraft.rad_warning")
                        .withStyle(ChatFormatting.GOLD));
                player.playNotifySound(NCRadiation.CHEMS_WEAR_OFF.get(), SoundSource.PLAYERS,
                        0.8F * RadiationConfig.soundVolume(6), 0.7F);
            }
        }
    }

    private static void refreshEffects(ServerPlayer player, boolean vulnerable, double percent, int ticks) {
        if (vulnerable) {
            RadiationEffects.apply(player, RadiationEffects.Profile.PLAYER, percent, Math.max(ticks, 19), 1);
        } else {
            RadiationEffects.clear(player);
        }
    }

    private static double rawRate(double effective, double resistance) {
        // Invert the legacy resistance curve for the aggregate effective reading.
        if (resistance > 0 && effective > 0) {
            return (effective + Math.sqrt(effective) * Math.sqrt(effective + 4 * resistance)) / 2;
        }
        return resistance < 0 ? effective / (1 - resistance) : effective;
    }

    private static double drain(CompoundTag data, String key, String recentKey, double limit) {
        double buffer = data.getDouble(key);
        double amount = Math.min(buffer, limit);
        if (amount > 0) {
            remainder(data, key, recentKey, buffer - amount);
        }
        return amount;
    }

    private static void remainder(CompoundTag data, String key, String recentKey, double remaining) {
        if (remaining <= 0) {
            data.remove(key);
            if (recentKey != null) {
                data.remove(recentKey);
            }
        } else {
            data.putDouble(key, remaining);
        }
    }

    private static void decayResistance(ServerPlayer player, CompoundTag data, double resistance, int ticks) {
        if (resistance == 0) {
            data.remove(RECENT_RESISTANCE);
            return;
        }
        double radX = RadiationConfig.RAD_X_AMOUNT.get();
        double recent = data.contains(RECENT_RESISTANCE) ? data.getDouble(RECENT_RESISTANCE) : radX;
        double magnitude = Math.abs(resistance);
        double change = Math.max(magnitude, radX * (1 + recent) / (1 + radX))
                * ticks / RadiationConfig.RAD_X_LIFETIME.get();
        double remaining = Math.max(0, magnitude - change);
        data.putDouble(RESISTANCE, Math.copySign(remaining, resistance));
        if (remaining == 0) {
            data.remove(RECENT_RESISTANCE);
            if (resistance > 0 && (!data.contains(RAD_X_USED) || data.getBoolean(RAD_X_USED))) {
                data.putBoolean(RAD_X_USED, false);
                player.displayClientMessage(Component.translatable("message.nuclearcraft.rad_x_wore_off"), true);
            }
        }
    }

    public static double dose(Player player) {
        return read(player).getDouble(DOSE);
    }

    static double setDose(ServerPlayer player, double dose) {
        CompoundTag data = read(player);
        double value = Math.max(0, Math.min(dose, RadiationConfig.MAX_DOSE.get()));
        data.putDouble(DOSE, value);
        save(player, data);
        RadiationSync.send(player);
        return value;
    }

    public static double rate(Player player) {
        return read(player).getDouble(RATE);
    }

    static boolean enabled() {
        return RadiationConfig.ENABLED.get();
    }

    private static boolean hasUntimedImmunity(Player player) {
        return RadiationConfig.IMMUNE_PLAYERS.get().contains(player.getStringUUID())
                || RadiationGameStages.grantsImmunity(player, RadiationConfig.DEFAULT_STAGE_IMMUNITY.get(),
                        RadiationConfig.IMMUNITY_STAGES.get());
    }

    static RadiationSync.Reading reading(ServerPlayer player) {
        if (!enabled()) {
            return RadiationSync.Reading.HIDDEN;
        }
        CompoundTag data = read(player);
        return new RadiationSync.Reading(true, RadiationConfig.REQUIRE_COUNTER.get(), data.getDouble(DOSE),
                RadiationConfig.MAX_DOSE.get(), data.getDouble(RATE), data.getDouble(RAW_RATE),
                hasUntimedImmunity(player), data.getDouble(IMMUNITY), RadiationConfig.MINIMUM_RATE.get(),
                RadiationConfig.UNIT_DIGITS.get(), RadiationConfig.soundVolume(0));
    }

    static void report(ServerPlayer player) {
        var reading = reading(player);
        RadiationDisplay.report(player, reading.dose(), reading.maximum(), reading.rawRate());
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
            case RADAWAY -> RadiationTreatment.addRadaway(data, RadiationConfig.RADAWAY_AMOUNT.get());
            case SLOW_RADAWAY -> data.putDouble(kind.id(), data.getDouble(kind.id()) + RadiationConfig.SLOW_RADAWAY_AMOUNT.get());
            case RAD_X -> {
                RadiationTreatment.addResistance(data, RadiationConfig.RAD_X_AMOUNT.get());
                data.putBoolean(RAD_X_USED, true);
            }
        }
        if (kind == MedicineItem.Kind.RAD_X) {
            data.putDouble(RAD_X_COOLDOWN, RadiationConfig.RAD_X_COOLDOWN.get());
        } else {
            data.putDouble(RADAWAY_COOLDOWN, RadiationConfig.RADAWAY_COOLDOWN.get());
        }
        save(player, data);
    }

    static void addPoison(ServerPlayer player, double dose) {
        CompoundTag data = read(player);
        RadiationPoison.add(data, dose);
        save(player, data);
    }

    static void eat(ServerPlayer player, FoodRadiation.Effects effects) {
        CompoundTag data = read(player);
        double previousResistance = data.getDouble(RESISTANCE);
        if (FoodRadiation.apply(player, data, effects) && !data.contains(RAD_X_USED)) {
            data.putBoolean(RAD_X_USED, previousResistance > 0);
        }
        save(player, data);
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
