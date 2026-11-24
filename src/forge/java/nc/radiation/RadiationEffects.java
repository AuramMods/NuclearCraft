package nc.radiation;

import nc.NuclearCraft;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Mod.EventBusSubscriber(modid = NuclearCraft.MOD_ID)
public final class RadiationEffects {
    private static final ResourceKey<DamageType> FATAL_RADS =
            ResourceKey.create(Registries.DAMAGE_TYPE, NuclearCraft.id("fatal_rads"));
    private static final List<String> DEBUFFS = List.of(
            "40.0_minecraft:weakness@1",
            "55.0_minecraft:weakness@1,minecraft:mining_fatigue@1",
            "70.0_minecraft:weakness@2,minecraft:mining_fatigue@1,minecraft:hunger@1",
            "80.0_minecraft:weakness@2,minecraft:mining_fatigue@2,minecraft:hunger@1,minecraft:poison@1",
            "90.0_minecraft:weakness@3,minecraft:mining_fatigue@3,minecraft:hunger@2,minecraft:poison@1,minecraft:wither@1");
    private static final List<String> BUFFS = List.of(
            "40.0_minecraft:speed@1",
            "55.0_minecraft:speed@1,minecraft:strength@1",
            "70.0_minecraft:speed@1,minecraft:strength@1,minecraft:resistance@1",
            "80.0_minecraft:speed@1,minecraft:strength@1,minecraft:resistance@1,minecraft:absorption@1",
            "90.0_minecraft:speed@1,minecraft:strength@1,minecraft:resistance@1,minecraft:absorption@1,minecraft:regeneration@1");
    private static final Map<Profile, ForgeConfigSpec.ConfigValue<List<? extends String>>> CONFIG = new EnumMap<>(Profile.class);
    private static final Map<LivingEntity, Applied> APPLIED = new IdentityHashMap<>();
    private static volatile Map<Profile, List<Tier>> tiers = Map.of();

    private RadiationEffects() {}

    static void define(ForgeConfigSpec.Builder builder) {
        addProfile(builder, Profile.PLAYER, "radiation_player_debuff_lists", DEBUFFS);
        addProfile(builder, Profile.PASSIVE, "radiation_passive_debuff_lists", DEBUFFS);
        addProfile(builder, Profile.HOSTILE, "radiation_mob_buff_lists", BUFFS);
    }

    private static void addProfile(ForgeConfigSpec.Builder builder, Profile profile, String name, List<String> defaults) {
        CONFIG.put(profile, builder.comment("threshold_namespace:effect@amplifier,namespace:attribute@amount@operation")
                .defineListAllowEmpty(name, defaults, entry -> entry instanceof String text && parse(text) != null));
    }

    static void refresh() {
        var configured = new EnumMap<Profile, List<Tier>>(Profile.class);
        CONFIG.forEach((profile, value) -> {
            var byThreshold = new ArrayList<Tier>();
            for (String line : value.get()) {
                Definition definition = parse(line);
                if (definition != null) {
                    Tier tier = definition.resolve(profile);
                    if (!tier.potions().isEmpty() || !tier.attributes().isEmpty()) {
                        byThreshold.add(tier);
                    }
                }
            }
            byThreshold.sort(Comparator.comparingDouble(Tier::threshold).reversed());
            configured.put(profile, List.copyOf(byThreshold));
        });
        tiers = Map.copyOf(configured);
    }

    static void unload() {
        tiers = Map.of();
    }

    static double warningThreshold() {
        var player = tiers.getOrDefault(Profile.PLAYER, List.of());
        return player.isEmpty() ? Double.POSITIVE_INFINITY : player.get(player.size() - 1).threshold();
    }

    static void apply(LivingEntity entity, Profile profile, double percent, int baseDuration, double tickMultiplier) {
        Tier selected = null;
        for (var tier : tiers.getOrDefault(profile, List.of())) {
            if (percent >= tier.threshold()) {
                selected = tier;
                break;
            }
        }
        var previous = APPLIED.get(entity);
        if (previous != null && previous.tier() != selected) {
            clear(entity);
            previous = null;
        }
        if (selected != null) {
            for (var potion : selected.potions()) {
                int duration = (int) (tickMultiplier * potion.duration(baseDuration));
                entity.addEffect(new MobEffectInstance(potion.effect(), duration, potion.amplifier(), false, false));
            }
            if (previous == null && !selected.attributes().isEmpty()) {
                var attributes = new ArrayList<AppliedAttribute>();
                for (var bonus : selected.attributes()) {
                    var instance = entity.getAttribute(bonus.attribute());
                    if (instance != null) {
                        instance.addTransientModifier(bonus.modifier());
                        attributes.add(new AppliedAttribute(instance, bonus.modifier().getId()));
                    }
                }
                APPLIED.put(entity, new Applied(selected, List.copyOf(attributes)));
            }
        }
    }

    static void clear(LivingEntity entity) {
        Applied existing = APPLIED.remove(entity);
        if (existing != null) {
            existing.clear();
        }
    }

    static boolean kill(LivingEntity entity) {
        var type = entity.level().registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(FATAL_RADS);
        return entity.hurt(new DamageSource(type), Float.MAX_VALUE);
    }

    @SubscribeEvent
    public static void onEntityLeave(EntityLeaveLevelEvent event) {
        if (!event.getLevel().isClientSide && event.getEntity() instanceof LivingEntity living) {
            clear(living);
        }
    }

    @SubscribeEvent
    public static void removeDisabledEffects(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END && !APPLIED.isEmpty()
                && (!RadiationConfig.loaded() || !PlayerRadiation.enabled())) {
            APPLIED.values().forEach(Applied::clear);
            APPLIED.clear();
        }
    }

    private static Definition parse(String line) {
        int split = line.indexOf('_');
        if (split < 1) {
            return null;
        }
        try {
            double threshold = Double.parseDouble(line.substring(0, split));
            if (!Double.isFinite(threshold) || threshold < 0 || threshold > 100) {
                return null;
            }
            var entries = new ArrayList<EffectEntry>();
            for (String effect : line.substring(split + 1).split(",", -1)) {
                var parts = effect.strip().split("@", -1);
                ResourceLocation id = ResourceLocation.tryParse(parts[0]);
                if (id == null || (parts.length != 2 && parts.length != 3)) {
                    return null;
                }
                int kind = Integer.parseInt(parts[parts.length - 1]);
                double amount = parts.length == 3 ? Double.parseDouble(parts[1]) : 0;
                if (kind < 0 || kind > (parts.length == 3 ? 2 : 255) || !Double.isFinite(amount)) {
                    return null;
                }
                entries.add(new EffectEntry(id, parts.length == 3, kind, amount));
            }
            return new Definition(threshold, entries);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    enum Profile { PLAYER, PASSIVE, HOSTILE }

    private record Definition(double threshold, List<EffectEntry> entries) {
        Tier resolve(Profile profile) {
            var potions = new ArrayList<Potion>();
            var attributes = new ArrayList<Bonus>();
            for (var entry : entries) {
                if (entry.attribute()) {
                    var attribute = ForgeRegistries.ATTRIBUTES.getValue(entry.id());
                    if (attribute != null) {
                        String name = NuclearCraft.MOD_ID + ":radiation/" + profile + "/" + threshold + "/" + attributes.size();
                        var id = UUID.nameUUIDFromBytes(name.getBytes(StandardCharsets.UTF_8));
                        var modifier = new AttributeModifier(id, name, entry.amount(), AttributeModifier.Operation.values()[entry.kind()]);
                        attributes.add(new Bonus(attribute, modifier));
                    }
                } else {
                    var effect = ForgeRegistries.MOB_EFFECTS.getValue(entry.id());
                    if (effect != null) {
                        potions.add(new Potion(effect, entry.kind()));
                    }
                }
            }
            return new Tier(threshold, List.copyOf(potions), List.copyOf(attributes));
        }
    }

    private record EffectEntry(ResourceLocation id, boolean attribute, int kind, double amount) {}
    private record Tier(double threshold, List<Potion> potions, List<Bonus> attributes) {}
    private record Bonus(Attribute attribute, AttributeModifier modifier) {}

    private record Potion(MobEffect effect, int amplifier) {
        int duration(int base) {
            int shift = Math.max(0, Math.min(30, amplifier - 1));
            if (effect == MobEffects.REGENERATION) return Math.max(base, 50 >> shift);
            if (effect == MobEffects.WITHER) return Math.max(base, 40 >> shift);
            if (effect == MobEffects.POISON) return Math.max(base, 25 >> shift);
            return effect == MobEffects.BLINDNESS ? base + 25 : base;
        }
    }

    private record AppliedAttribute(AttributeInstance attribute, UUID id) {
        void clear() {
            attribute.removeModifier(id);
        }
    }

    private record Applied(Tier tier, List<AppliedAttribute> attributes) {
        void clear() {
            attributes.forEach(AppliedAttribute::clear);
        }
    }
}
