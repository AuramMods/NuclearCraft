package nc.multiblock.fission;

import nc.init.NCFission;
import nc.init.NCHeatSinks;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.BlockGetter;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Objects;
import java.util.function.IntSupplier;

public final class SolidHeatSinkBlock extends FissionPartBlock {
    private final String material;
    private final IntSupplier cooling;
    private final @Nullable String scriptedRule;

    public SolidHeatSinkBlock(Properties properties, NCHeatSinks material) {
        this(properties, material.material(), () -> NCFission.cooling(material), null);
    }

    public SolidHeatSinkBlock(Properties properties, String material, int cooling, String rule) {
        this(properties, material, () -> cooling, Objects.requireNonNull(rule));
    }

    private SolidHeatSinkBlock(Properties properties, String material, IntSupplier cooling, @Nullable String scriptedRule) {
        super(properties, Role.HEAT_SINK);
        this.material = material;
        this.cooling = cooling;
        this.scriptedRule = scriptedRule;
    }

    public String material() {
        return material;
    }

    int cooling() {
        return cooling.getAsInt();
    }

    public @Nullable String scriptedRule() {
        return scriptedRule;
    }

    public String placementRule() {
        String effective = NCFission.placementRules().text(material);
        return effective == null ? Objects.requireNonNullElse(scriptedRule, "") : effective;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable BlockGetter level, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, level, tooltip, flag);
        tooltip.add(Component.translatable("tooltip.nuclearcraft.fission.heat_sink_cooling",
                cooling()).withStyle(ChatFormatting.AQUA));
        tooltip.add(Component.translatable("tooltip.nuclearcraft.fission.placement_rule",
                placementRule()).withStyle(ChatFormatting.GRAY));
    }
}
