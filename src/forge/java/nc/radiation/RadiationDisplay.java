package nc.radiation;

import nc.compat.curios.RadiationCurios;
import nc.init.NCRadiation;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;

import java.math.BigDecimal;
import java.math.MathContext;

public final class RadiationDisplay {
    private static final String[] PREFIXES =
            {"q", "r", "y", "z", "a", "f", "p", "n", "u", "m", "", "k", "M", "G", "T", "P", "E", "Z", "Y", "R", "Q"};
    private static final int BASE_PREFIX = 10;
    private static final MathContext SI_PRECISION = new MathContext(3);

    private RadiationDisplay() {}

    public static boolean hasCounter(Player player) {
        var inventory = player.getInventory();
        var handheld = NCRadiation.GEIGER_COUNTER.get();
        var fixed = NCRadiation.GEIGER_BLOCK.get().asItem();
        for (int slot = 0; slot < inventory.getContainerSize(); ++slot) {
            var stack = inventory.getItem(slot);
            if (stack.is(handheld) || stack.is(fixed)) {
                return true;
            }
        }
        return equippedCounter(player);
    }

    public static boolean equippedCounter(Player player) {
        var equipped = RadiationCurios.equipped(player);
        if (equipped == null) {
            return false;
        }
        for (int slot = 0; slot < equipped.getSlots(); slot++) {
            if (equipped.getStackInSlot(slot).is(NCRadiation.GEIGER_COUNTER.get())) {
                return true;
            }
        }
        return false;
    }

    public static ChatFormatting rateColor(double rate) {
        return rate < 1E-9 ? ChatFormatting.WHITE : rate < 0.001 ? ChatFormatting.YELLOW
                : rate < 0.1 ? ChatFormatting.GOLD : rate < 1 ? ChatFormatting.RED : ChatFormatting.DARK_RED;
    }

    static ChatFormatting doseColor(double percentage) {
        return percentage < 30 ? ChatFormatting.WHITE : percentage < 50 ? ChatFormatting.YELLOW
                : percentage < 70 ? ChatFormatting.GOLD : percentage < 90 ? ChatFormatting.RED : ChatFormatting.DARK_RED;
    }

    static void report(ServerPlayer player, double dose, double maximum, double rate) {
        double percent = Math.min(100, 100 * dose / maximum);
        double minimum = RadiationConfig.MINIMUM_RATE.get();
        int digits = RadiationConfig.UNIT_DIGITS.get();
        Component reading = Component.literal(format(rate, minimum, digits, "Rad/t")).withStyle(rateColor(rate));
        player.sendSystemMessage(Component.translatable("message.nuclearcraft.geiger_reading",
                format(dose, minimum, digits, "Rad"), Math.round(percent), reading).withStyle(doseColor(percent)));
    }

    static String format(double value, double minimumRate, int digits, String unit) {
        if (value <= 0 || value < minimumRate) {
            return "0 " + unit;
        }
        if (!Double.isFinite(value)) {
            return value + " " + unit;
        }
        BigDecimal amount = BigDecimal.valueOf(value);
        if (digits > 0) {
            return amount.round(new MathContext(digits)).stripTrailingZeros().toString() + " " + unit;
        }

        BigDecimal rounded = amount.round(SI_PRECISION);
        int exponent = rounded.precision() - rounded.scale() - 1;
        int prefix = Mth.clamp(BASE_PREFIX + Math.floorDiv(exponent, 3), 0, PREFIXES.length - 1);
        return rounded.scaleByPowerOfTen(3 * (BASE_PREFIX - prefix)).stripTrailingZeros().toPlainString()
                + " " + PREFIXES[prefix] + unit;
    }
}
