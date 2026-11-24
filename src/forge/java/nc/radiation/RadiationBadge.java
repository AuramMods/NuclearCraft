package nc.radiation;

import nc.compat.curios.RadiationCurios;
import nc.init.NCRadiation;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;

public final class RadiationBadge extends Item {
    private static final String EXPOSURE = "nuclearcraft:badge_exposure";
    private static final double DEFAULT_CAPACITY = 250D;
    private static final double DEFAULT_INTERVAL = 0.1D;

    public RadiationBadge(Properties properties) {
        super(properties);
    }

    static void measure(ServerPlayer player, double absorbed) {
        var equipped = RadiationCurios.equipped(player);
        if (equipped != null) {
            for (int slot = 0; slot < equipped.getSlots(); slot++) {
                var stack = equipped.getStackInSlot(slot);
                if (stack.getItem() instanceof RadiationBadge) {
                    ItemStack updated = stack.copy();
                    if (measure(player, updated, absorbed)) {
                        equipped.setStackInSlot(slot, updated);
                    }
                }
            }
        } else {
            var inventory = player.getInventory();
            for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
                var stack = inventory.getItem(slot);
                if (stack.getItem() instanceof RadiationBadge) {
                    measure(player, stack, absorbed);
                }
            }
        }
    }

    private static boolean measure(ServerPlayer player, ItemStack stack, double absorbed) {
        double previous = exposure(stack);
        double total = previous + absorbed;
        double capacity = capacity();
        double interval = capacity * RadiationConfig.BADGE_INFO_FRACTION.get();
        if (total >= capacity || Math.floor(total / interval) > Math.floor(previous / interval)) {
            player.sendSystemMessage(Component.translatable("message.nuclearcraft.badge.exposure", format(total))
                    .withStyle(ChatFormatting.ITALIC));
        }
        if (total >= capacity) {
            player.sendSystemMessage(Component.translatable("message.nuclearcraft.badge.broken").withStyle(ChatFormatting.ITALIC));
            player.playNotifySound(NCRadiation.CHEMS_WEAR_OFF.get(), SoundSource.PLAYERS,
                    0.65F * RadiationConfig.soundVolume(5), 1F);
            stack.shrink(1);
            return true;
        }
        if (total != previous) {
            stack.getOrCreateTag().putDouble(EXPOSURE, total);
            return true;
        }
        return false;
    }

    public static double exposure(ItemStack stack) {
        if (stack.getTag() == null) {
            return 0;
        }
        double stored = stack.getTag().getDouble(EXPOSURE);
        return Double.isFinite(stored) ? Math.max(0, stored) : 0;
    }

    private static double capacity() {
        return RadiationConfig.loaded() ? RadiationConfig.BADGE_CAPACITY.get() : DEFAULT_CAPACITY;
    }

    @Override
    public boolean isBarVisible(ItemStack stack) {
        return exposure(stack) > 0;
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        return (int) Math.round(13 * (1 - fraction(stack)));
    }

    @Override
    public int getBarColor(ItemStack stack) {
        return Mth.hsvToRgb((float) ((1 - fraction(stack)) / 3), 1F, 1F);
    }

    private static double fraction(ItemStack stack) {
        return Mth.clamp(exposure(stack) / capacity(), 0, 1);
    }

    @Override
    public void appendHoverText(ItemStack stack, Level level, List<Component> lines, TooltipFlag flag) {
        double capacity = capacity();
        double period = RadiationConfig.loaded() ? RadiationConfig.BADGE_INFO_FRACTION.get() : DEFAULT_INTERVAL;
        lines.add(Component.translatable("tooltip.nuclearcraft.badge.interval", format(capacity * period), format(capacity))
                .withStyle(ChatFormatting.GRAY));
        if (exposure(stack) > 0) {
            lines.add(Component.translatable("tooltip.nuclearcraft.badge.exposure", format(exposure(stack)))
                    .withStyle(ChatFormatting.YELLOW));
        }
    }

    private static String format(double rads) {
        return RadiationDisplay.format(rads, 0, 0, "Rad");
    }
}
