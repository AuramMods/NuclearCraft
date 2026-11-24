package nc.fluid;

import nc.NuclearCraft;
import nc.radiation.HazmatMaterial;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ArmorItem;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = NuclearCraft.MOD_ID)
public final class FluidHazard {
    private static final TagKey<DamageType> HAZMAT_PROTECTED =
            TagKey.create(Registries.DAMAGE_TYPE, NuclearCraft.id("hazmat_protected"));

    public enum Kind {
        LIQUID, FLAMMABLE, ACID, MOLTEN, FISSION, CORIUM, GLOWSTONE, COOLANT, HOT_COOLANT,
        GAS, HOT_GAS, STEAM, PLASMA, CRYOTHEUM, SUPERFLUID, CHOCOLATE, STICKY
    }

    private FluidHazard() {}

    static void contact(Kind kind, Entity entity, int temperature) {
        switch (kind) {
            case ACID -> {
                hurt(entity, "acid_burn", 3F);
                effect(entity, MobEffects.WEAKNESS, 1);
                effect(entity, MobEffects.POISON, 1);
            }
            case MOLTEN, GLOWSTONE -> {
                hurt(entity, "molten_burn", 4F);
                entity.setSecondsOnFire(10);
            }
            case FISSION, CORIUM -> {
                if (kind == Kind.CORIUM) {
                    hurt(entity, "corium_burn", 4F);
                }
                entity.setSecondsOnFire(10);
                effect(entity, MobEffects.WEAKNESS, 0);
                effect(entity, MobEffects.POISON, 0);
            }
            case COOLANT, HOT_COOLANT -> {
                if (kind == Kind.HOT_COOLANT) {
                    hurt(entity, "hot_coolant_burn", 2F);
                    entity.setSecondsOnFire(3);
                }
                effect(entity, MobEffects.MOVEMENT_SLOWDOWN, 0);
                effect(entity, MobEffects.WEAKNESS, 0);
            }
            case STEAM, HOT_GAS -> {
                hurt(entity, kind == Kind.STEAM ? "steam_burn" : "gas_burn", 3F);
                entity.setSecondsOnFire(1);
            }
            case PLASMA -> {
                hurt(entity, "plasma_burn", 8F);
                entity.setSecondsOnFire(10);
            }
            case CRYOTHEUM -> hurt(entity, "hypothermia", 4F);
            case SUPERFLUID -> hurt(entity, "superfluid_freeze", 6F);
            case CHOCOLATE -> {
                effect(entity, MobEffects.MOVEMENT_SLOWDOWN, 0);
                effect(entity, MobEffects.REGENERATION, 0);
            }
            case STICKY -> effect(entity, MobEffects.MOVEMENT_SLOWDOWN, 2);
            case LIQUID, FLAMMABLE -> {
                if (temperature < 250) {
                    hurt(entity, "hypothermia", 0.024F * (250 - temperature));
                } else if (temperature > 350) {
                    hurt(entity, "fluid_burn", (float) Math.min(6D, Math.log10(temperature - 350)));
                }
            }
            case GAS -> {}
        }
    }

    private static void effect(Entity entity, MobEffect effect, int amplifier) {
        if (entity instanceof LivingEntity living) {
            living.addEffect(new MobEffectInstance(effect, 100, amplifier));
        }
    }

    public static void hurt(Entity entity, String type, float amount) {
        var key = ResourceKey.create(Registries.DAMAGE_TYPE, NuclearCraft.id(type));
        var damage = entity.level().registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(key);
        entity.hurt(new DamageSource(damage), amount);
    }

    @SubscribeEvent
    public static void protectHazmat(LivingHurtEvent event) {
        if (!event.getSource().is(HAZMAT_PROTECTED)) {
            return;
        }
        LivingEntity entity = event.getEntity();
        int protection = 0;
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            if (slot.getType() == EquipmentSlot.Type.ARMOR
                    && entity.getItemBySlot(slot).getItem() instanceof ArmorItem armor
                    && armor.getMaterial() == HazmatMaterial.INSTANCE && armor.getEquipmentSlot() == slot) {
                protection += slot == EquipmentSlot.CHEST ? 40 : 20;
            }
        }
        event.setAmount(event.getAmount() * (100 - protection) / 100F);
    }
}
