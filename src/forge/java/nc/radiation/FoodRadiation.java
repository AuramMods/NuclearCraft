package nc.radiation;

import nc.NuclearCraft;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
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
        if (event.getEntity() instanceof ServerPlayer player) {
            Effects effects = effects(event.getItem(), player);
            if (effects != null) {
                PlayerRadiation.eat(player, effects);
            }
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
