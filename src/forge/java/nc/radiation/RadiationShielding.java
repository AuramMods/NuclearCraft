package nc.radiation;

import nc.NuclearCraft;
import nc.compat.curios.RadiationCurios;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ElytraItem;
import net.minecraft.world.item.HorseArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

@Mod.EventBusSubscriber(modid = NuclearCraft.MOD_ID)
public final class RadiationShielding {
    private static final String ARMOR_KEY = "ncRadiationResistance";
    private static final String CONTAINER_KEY = "nuclearcraft:shielding";

    private RadiationShielding() {}

    public static double armorResistance(ItemStack stack) {
        if (stack.isEmpty() || stack.getItem() instanceof HorseArmorItem && !ShieldingSettings.horseArmor()) {
            return 0;
        }
        return addedResistance(stack) + ShieldingSettings.inherent(itemId(stack));
    }

    static double equippedResistance(LivingEntity wearer) {
        double resistance = 0;
        for (ItemStack stack : wearer.getArmorSlots()) {
            resistance += armorResistance(stack);
        }
        var curios = RadiationCurios.equipped(wearer);
        if (curios != null) {
            for (int slot = 0; slot < curios.getSlots(); slot++) {
                resistance += armorResistance(curios.getStackInSlot(slot));
            }
        }
        return resistance;
    }

    public static boolean canUpgrade(ItemStack stack, double resistance) {
        return isArmor(stack) && ShieldingSettings.canUpgrade(itemId(stack)) && addedResistance(stack) < resistance;
    }

    public static ItemStack upgrade(ItemStack stack, double resistance) {
        ItemStack upgraded = stack.copyWithCount(1);
        upgraded.getOrCreateTag().putDouble(ARMOR_KEY, resistance);
        return upgraded;
    }

    private static boolean isArmor(ItemStack stack) {
        var item = stack.getItem();
        return item instanceof ArmorItem || item instanceof ElytraItem
                || item instanceof HorseArmorItem && ShieldingSettings.horseArmor();
    }

    private static String itemId(ItemStack stack) {
        var id = ForgeRegistries.ITEMS.getKey(stack.getItem());
        return id == null ? "" : id.toString();
    }

    private static double addedResistance(ItemStack stack) {
        var tag = stack.getTag();
        double rate = tag == null ? 0 : tag.getDouble(ARMOR_KEY);
        return Double.isFinite(rate) ? rate : 0;
    }

    static double attenuate(double raw, double resistance) {
        return resistance > 0 ? raw * (raw / (raw + resistance)) : raw * (1 - resistance);
    }

    static double containerResistance(BlockEntity container) {
        double rate = container.getPersistentData().getDouble(CONTAINER_KEY);
        return Double.isFinite(rate) ? rate : 0;
    }

    static void install(BlockEntity container, double resistance) {
        container.getPersistentData().putDouble(CONTAINER_KEY, resistance);
        container.setChanged();
    }

    static boolean carriedBy(ItemStack stack) {
        var tag = stack.getTag();
        return tag != null && tag.getCompound("BlockEntityTag").getCompound("ForgeData")
                .contains(CONTAINER_KEY, Tag.TAG_ANY_NUMERIC);
    }

    @SubscribeEvent
    public static void tooltip(ItemTooltipEvent event) {
        double resistance = armorResistance(event.getItemStack());
        if (resistance != 0) {
            event.getToolTip().add(Component.translatable("tooltip.nuclearcraft.shielding.resistance",
                    format(resistance)).withStyle(ChatFormatting.YELLOW));
        }
    }

    static String format(double resistance) {
        int digits = RadiationConfig.loaded() ? Math.max(2, RadiationConfig.UNIT_DIGITS.get()) : 2;
        String value = RadiationDisplay.format(Math.abs(resistance), 0, digits, "").strip();
        return resistance < 0 ? "-" + value : value;
    }

    static double rawRate(double effective, double resistance) {
        // Invert the legacy resistance curve for the aggregate effective reading.
        if (resistance > 0 && effective > 0) {
            return (effective + Math.sqrt(effective) * Math.sqrt(effective + 4 * resistance)) / 2;
        }
        return resistance < 0 ? effective / (1 - resistance) : effective;
    }
}
