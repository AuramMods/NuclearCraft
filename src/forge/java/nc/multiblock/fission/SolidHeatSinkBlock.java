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

public final class SolidHeatSinkBlock extends FissionPartBlock {
    private final NCHeatSinks material;

    public SolidHeatSinkBlock(Properties properties, NCHeatSinks material) {
        super(properties, Role.HEAT_SINK);
        this.material = material;
    }

    NCHeatSinks material() {
        return material;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable BlockGetter level, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, level, tooltip, flag);
        tooltip.add(Component.translatable("tooltip.nuclearcraft.fission.heat_sink_cooling",
                NCFission.cooling(material)).withStyle(ChatFormatting.AQUA));
        tooltip.add(Component.translatable("tooltip.nuclearcraft.fission.placement_rule",
                NCFission.placementRules().text(material)).withStyle(ChatFormatting.GRAY));
    }
}
