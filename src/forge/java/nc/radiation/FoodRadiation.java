package nc.radiation;

import nc.NuclearCraft;
import nc.init.NCRadiation;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.living.LivingEntityUseItemEvent;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

@Mod.EventBusSubscriber(modid = NuclearCraft.MOD_ID)
public final class FoodRadiation {
    private FoodRadiation() {}

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onEat(LivingEntityUseItemEvent.Finish event) {
        LivingEntity eater = event.getEntity();
        if (eater.level().isClientSide) {
            return;
        }
        Effects effects = effects(event.getItem(), eater);
        if (effects == null) {
            return;
        }
        if (eater instanceof ServerPlayer player) {
            PlayerRadiation.eat(player, effects);
        } else {
            CompoundTag persistent = eater.getPersistentData();
            CompoundTag data = persistent.getCompound(MobRadiation.DATA);
            apply(eater, data, effects);
            persistent.put(MobRadiation.DATA, data);
        }
    }

    static boolean apply(LivingEntity eater, CompoundTag data, Effects effects) {
        double dose = effects.dose();
        if (dose > 0) {
            RadiationPoison.add(data, dose);
            playSound(eater, NCRadiation.RAD_POISONING.get(), Math.sqrt(dose) / 10);
        } else if (dose < 0) {
            RadiationTreatment.addRadaway(data, -dose);
            playSound(eater, NCRadiation.RADAWAY_SOUND.get(), Math.sqrt(-dose) / 10);
        }
        double resistance = effects.resistance();
        if (resistance != 0 && data.getDouble(RadiationTreatment.RAD_X_COOLDOWN) <= 0) {
            RadiationTreatment.addResistance(data, resistance);
            playSound(eater, resistance > 0 ? NCRadiation.RAD_X_SOUND.get() : NCRadiation.CHEMS_WEAR_OFF.get(),
                    5 * Math.sqrt(Math.abs(resistance)) / RadiationConfig.RAD_X_AMOUNT.get());
            return true;
        }
        return false;
    }

    private static void playSound(LivingEntity eater, SoundEvent sound, double strength) {
        if (!eater.isSilent()) {
            eater.level().playSound(null, eater.getX(), eater.getY(), eater.getZ(), sound, eater.getSoundSource(),
                    (float) ((1 - Math.pow(10, -strength)) * RadiationConfig.soundVolume(3)), 1F);
        }
    }

    @SubscribeEvent
    public static void tooltip(ItemTooltipEvent event) {
        Effects effects = effects(event.getItemStack(), event.getEntity());
        if (effects == null) {
            return;
        }
        if (effects.dose() != 0) {
            double dose = effects.dose();
            String amount = RadiationDisplay.format(Math.abs(dose), RadiationConfig.MINIMUM_RATE.get(),
                    RadiationConfig.UNIT_DIGITS.get(), "Rad");
            event.getToolTip().add(Component.translatable("tooltip.nuclearcraft.food.dose", dose < 0 ? "-" + amount : amount)
                    .withStyle(doseColor(dose)));
        }
        if (effects.resistance() != 0) {
            event.getToolTip().add(Component.translatable("tooltip.nuclearcraft.food.resistance",
                    RadiationShielding.format(effects.resistance()))
                    .withStyle(effects.resistance() < 0 ? ChatFormatting.GRAY : ChatFormatting.WHITE));
        }
    }

    private static ChatFormatting doseColor(double dose) {
        if (dose < 0) {
            return dose <= -100 ? ChatFormatting.LIGHT_PURPLE : dose <= -10 ? ChatFormatting.BLUE : ChatFormatting.AQUA;
        }
        return dose < 0.1 ? ChatFormatting.WHITE : dose < 1 ? ChatFormatting.YELLOW
                : dose < 10 ? ChatFormatting.GOLD : dose < 100 ? ChatFormatting.RED : ChatFormatting.DARK_RED;
    }

    private static Effects effects(ItemStack stack, LivingEntity entity) {
        if (!RadiationConfig.loaded() || !PlayerRadiation.enabled() || stack.getFoodProperties(entity) == null) {
            return null;
        }
        ResourceLocation name = ForgeRegistries.ITEMS.getKey(stack.getItem());
        if (name == null) {
            return null;
        }
        String prefix = name + "_";
        var scripted = entity == null || entity.level().isClientSide
                ? RadiationSync.clientFood(name) : RadiationScriptRules.active().foods().get(name);
        if (scripted != null) {
            return scripted.dose() == 0 && scripted.resistance() == 0 ? null : scripted;
        }
        Effects effects = null;
        for (String line : RadiationConfig.FOODS.get()) {
            if (line.startsWith(prefix)) {
                Entry entry = parse(line);
                if (entry != null && entry.item().equals(name)) {
                    effects = entry.effects();
                }
            }
        }
        return effects != null && (effects.dose() != 0 || effects.resistance() != 0) ? effects : null;
    }

    static boolean valid(Object value) {
        return value instanceof String entry && parse(entry) != null;
    }

    private static Entry parse(String line) {
        int resistance = line.lastIndexOf('_');
        int dose = line.lastIndexOf('_', resistance - 1);
        if (dose < 1) {
            return null;
        }
        String name = line.substring(0, dose);
        ResourceLocation item = ResourceLocation.tryParse(name);
        if (item == null || !item.toString().equals(name)) {
            return null;
        }
        try {
            double rads = Double.parseDouble(line.substring(dose + 1, resistance));
            double shielding = Double.parseDouble(line.substring(resistance + 1));
            if (Double.isFinite(rads) && Double.isFinite(shielding)) {
                return new Entry(item, new Effects(rads, shielding));
            }
        } catch (NumberFormatException ignored) {
        }
        return null;
    }

    private record Entry(ResourceLocation item, Effects effects) {}
    record Effects(double dose, double resistance) {}
}
